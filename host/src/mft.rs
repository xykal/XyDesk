//! MFT — encoder H.264 hardware lewat Media Foundation Transform.
//!
//! Kenapa ada jalur ini di samping NVENC: AMD (AMF) dan Intel (Quick Sync)
//! tidak punya API vendor yang dimuat dinamis di sini, tetapi keduanya
//! mendaftarkan encoder H.264 sebagai MFT hardware. Urutan pilihan encoder
//! di `screen.rs`: NVENC → MFT → openh264. Vendor NVIDIA juga terekspos
//! lewat MFT, jadi mesin dengan driver NVIDIA lama pun masih dapat hardware.
//!
//! Model async MFT (wajib untuk MFT hardware): `MF_TRANSFORM_ASYNC_UNLOCK`,
//! lalu setiap frame menunggu `METransformNeedInput`, `ProcessInput`, dan
//! menguras `METransformHaveOutput`. Input NV12 dari CPU (hasil konversi
//! RGBA); jalur zero-copy DXGI menyusul bila pengukuran menunjukkan salinan
//! ini yang jadi pembatas.

#![allow(dead_code)]

use std::mem::ManuallyDrop;
use std::time::{Duration, Instant};

use windows::core::Interface;
use windows::Win32::Media::MediaFoundation::*;

pub use crate::mft_setup::{available, unavailable_reason};
use crate::mft_setup::{enumerate, friendly_name, input_type, output_type, tune_codec, FPS};

const OUTPUT_WAIT: Duration = Duration::from_millis(150);
const PIPELINE_WAIT: Duration = Duration::from_millis(8);
const HNS_PER_FRAME: i64 = 10_000_000 / FPS as i64;

pub struct Mft {
    transform: IMFTransform,
    events: IMFMediaEventGenerator,
    activate: IMFActivate,
    input_id: u32,
    output_id: u32,
    provides_samples: bool,
    output_size: u32,
    frame_bytes: usize,
    frames: i64,
    need_input: bool,
}

unsafe impl Send for Mft {}

impl Mft {
    /// Buat encoder H264 hardware [width]x[height] (harus genap) lewat MFT
    /// pertama hasil `MFTEnumEx` (sudah diurutkan Windows: hardware dulu).
    pub fn new(width: u32, height: u32, bitrate_bps: u32) -> Result<Self, String> {
        if !width.is_multiple_of(2) || !height.is_multiple_of(2) {
            return Err(format!("dimensi MFT harus genap: {width}x{height}"));
        }
        let layout = crate::video_layout::VideoLayout::new(
            width as usize,
            height as usize,
            crate::video_policy::requested(),
            crate::video_policy::level(),
        )?;
        if layout.canvas != [width as usize, height as usize]
            || layout.crop != [0, 0, width as usize, height as usize]
        {
            return Err("crop/resize diperlukan; gunakan software".into());
        }
        let activate = enumerate()?
            .into_iter()
            .next()
            .ok_or_else(|| "tidak ada MFT encoder H264 hardware".to_string())?;
        let name = friendly_name(&activate);
        unsafe {
            let transform: IMFTransform = activate
                .ActivateObject()
                .map_err(|e| format!("{name}: ActivateObject gagal: {e}"))?;
            let attrs = transform
                .GetAttributes()
                .map_err(|e| format!("{name}: GetAttributes gagal: {e}"))?;
            attrs
                .SetUINT32(&MF_TRANSFORM_ASYNC_UNLOCK, 1)
                .map_err(|e| format!("{name}: async unlock gagal: {e}"))?;
            let _ = attrs.SetUINT32(&MF_LOW_LATENCY, 1);
            let events = transform
                .cast::<IMFMediaEventGenerator>()
                .map_err(|e| format!("{name}: bukan MFT async: {e}"))?;

            let (mut input_ids, mut output_ids) = ([0u32; 1], [0u32; 1]);
            let _ = transform.GetStreamIDs(&mut input_ids, &mut output_ids);
            let (input_id, output_id) = (input_ids[0], output_ids[0]);

            tune_codec(&transform, bitrate_bps);
            transform
                .SetOutputType(output_id, &output_type(width, height, bitrate_bps)?, 0)
                .map_err(|e| format!("{name}: SetOutputType gagal: {e}"))?;
            transform
                .SetInputType(input_id, &input_type(width, height)?, 0)
                .map_err(|e| format!("{name}: SetInputType gagal: {e}"))?;

            let info = transform
                .GetOutputStreamInfo(output_id)
                .map_err(|e| format!("{name}: GetOutputStreamInfo gagal: {e}"))?;
            let provides_samples =
                (info.dwFlags & MFT_OUTPUT_STREAM_PROVIDES_SAMPLES.0 as u32) != 0;
            let frame_bytes = width as usize * height as usize * 3 / 2;
            let output_size = info.cbSize.max(frame_bytes as u32);

            for msg in [
                MFT_MESSAGE_COMMAND_FLUSH,
                MFT_MESSAGE_NOTIFY_BEGIN_STREAMING,
                MFT_MESSAGE_NOTIFY_START_OF_STREAM,
            ] {
                transform
                    .ProcessMessage(msg, 0)
                    .map_err(|e| format!("{name}: ProcessMessage gagal: {e}"))?;
            }
            println!("[xydesk-host] MFT encoder: {name}");
            Ok(Self {
                transform,
                events,
                activate,
                input_id,
                output_id,
                provides_samples,
                output_size,
                frame_bytes,
                frames: 0,
                need_input: false,
            })
        }
    }

    /// Encode satu frame NV12 (stride = width). Mengembalikan Annex-B; frame
    /// pertama selalu IDR dengan SPS/PPS. Bila encoder belum melepas output
    /// dalam batas waktu, frame dianggap gagal dan pemanggil melewatinya.
    pub fn encode(&mut self, nv12: &[u8]) -> Result<Vec<u8>, String> {
        if nv12.len() != self.frame_bytes {
            return Err(format!(
                "ukuran input NV12 {} != ekspektasi {}",
                nv12.len(),
                self.frame_bytes
            ));
        }
        unsafe {
            let mut out = Vec::new();
            let mut submitted = false;
            if self.need_input {
                self.submit(nv12)?;
                self.need_input = false;
                submitted = true;
            }
            let deadline = Instant::now() + OUTPUT_WAIT;
            let short = Instant::now() + PIPELINE_WAIT;
            loop {
                let event = match self.events.GetEvent(MF_EVENT_FLAG_NO_WAIT) {
                    Ok(ev) => ev,
                    Err(_) => {
                        if !out.is_empty() {
                            return Ok(out);
                        }
                        // Encoder yang sudah minta frame berikutnya tanpa
                        // melepas output = pipeline satu frame; jangan
                        // tunggu penuh, frame berikut yang akan mengambilnya.
                        let limit = if self.need_input { short } else { deadline };
                        if Instant::now() >= limit {
                            return Err("MFT belum melepas output".into());
                        }
                        std::thread::sleep(Duration::from_micros(500));
                        continue;
                    }
                };
                match MF_EVENT_TYPE(event.GetType().unwrap_or(0) as i32) {
                    METransformNeedInput if !submitted => {
                        self.submit(nv12)?;
                        submitted = true;
                    }
                    METransformNeedInput => self.need_input = true,
                    METransformHaveOutput => self.drain(&mut out)?,
                    _ => {}
                }
            }
        }
    }

    unsafe fn submit(&mut self, nv12: &[u8]) -> Result<(), String> {
        let sample = MFCreateSample().map_err(|e| format!("MFCreateSample: {e}"))?;
        let buffer = MFCreateMemoryBuffer(nv12.len() as u32)
            .map_err(|e| format!("MFCreateMemoryBuffer: {e}"))?;
        let mut dst: *mut u8 = std::ptr::null_mut();
        buffer.Lock(&mut dst, None, None).map_err(|e| format!("Lock input: {e}"))?;
        std::ptr::copy_nonoverlapping(nv12.as_ptr(), dst, nv12.len());
        buffer.Unlock().map_err(|e| format!("Unlock input: {e}"))?;
        buffer
            .SetCurrentLength(nv12.len() as u32)
            .map_err(|e| format!("SetCurrentLength: {e}"))?;
        sample.AddBuffer(&buffer).map_err(|e| format!("AddBuffer: {e}"))?;
        sample
            .SetSampleTime(self.frames * HNS_PER_FRAME)
            .map_err(|e| format!("SetSampleTime: {e}"))?;
        sample
            .SetSampleDuration(HNS_PER_FRAME)
            .map_err(|e| format!("SetSampleDuration: {e}"))?;
        self.transform
            .ProcessInput(self.input_id, &sample, 0)
            .map_err(|e| format!("ProcessInput: {e}"))?;
        self.frames += 1;
        Ok(())
    }

    unsafe fn drain(&mut self, out: &mut Vec<u8>) -> Result<(), String> {
        let own_sample = if self.provides_samples {
            None
        } else {
            let s = MFCreateSample().map_err(|e| format!("MFCreateSample: {e}"))?;
            let b = MFCreateMemoryBuffer(self.output_size)
                .map_err(|e| format!("MFCreateMemoryBuffer: {e}"))?;
            s.AddBuffer(&b).map_err(|e| format!("AddBuffer: {e}"))?;
            Some(s)
        };
        let mut buffers = [MFT_OUTPUT_DATA_BUFFER {
            dwStreamID: self.output_id,
            pSample: ManuallyDrop::new(own_sample),
            dwStatus: 0,
            pEvents: ManuallyDrop::new(None),
        }];
        let mut status = 0u32;
        let result = self.transform.ProcessOutput(0, &mut buffers, &mut status);
        let sample = ManuallyDrop::take(&mut buffers[0].pSample);
        ManuallyDrop::drop(&mut buffers[0].pEvents);
        match result {
            Ok(()) => {}
            Err(e) if e.code() == MF_E_TRANSFORM_NEED_MORE_INPUT => return Ok(()),
            Err(e) if e.code() == MF_E_TRANSFORM_STREAM_CHANGE => {
                let t = self
                    .transform
                    .GetOutputAvailableType(self.output_id, 0)
                    .map_err(|e| e.to_string())?;
                self.transform
                    .SetOutputType(self.output_id, &t, 0)
                    .map_err(|e| e.to_string())?;
                return Ok(());
            }
            Err(e) => return Err(format!("ProcessOutput: {e}")),
        }
        let Some(sample) = sample else { return Ok(()) };
        let buffer = sample
            .ConvertToContiguousBuffer()
            .map_err(|e| format!("ConvertToContiguousBuffer: {e}"))?;
        let mut src: *mut u8 = std::ptr::null_mut();
        let mut len = 0u32;
        buffer.Lock(&mut src, None, Some(&mut len)).map_err(|e| format!("Lock output: {e}"))?;
        out.extend_from_slice(std::slice::from_raw_parts(src, len as usize));
        let _ = buffer.Unlock();
        Ok(())
    }
}

impl Drop for Mft {
    fn drop(&mut self) {
        unsafe {
            let _ = self.transform.ProcessMessage(MFT_MESSAGE_NOTIFY_END_OF_STREAM, 0);
            let _ = self.transform.ProcessMessage(MFT_MESSAGE_NOTIFY_END_STREAMING, 0);
            let _ = self.activate.ShutdownObject();
        }
    }
}
