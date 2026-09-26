//! Jalur software yang sesuai batas penerimaan H264 Level 3.1 pada SDP.
//! Capture tetap pada desktop asli; hanya gambar kirim yang diperkecil.
use openh264::encoder::Encoder;
use openh264::formats::{RgbaSliceU8, YUVBuffer};

pub const MAX_WIDTH: usize = 1280;
pub const MAX_HEIGHT: usize = 720;
pub const MAX_FPS: u32 = 30;
pub const MAX_BITRATE: u32 = 14_000_000;

pub fn output_size(width: usize, height: usize) -> Result<(usize, usize), String> {
    if width < 2 || height < 2 {
        return Err("capture lebih kecil dari 2x2".into());
    }
    // Jalur software ini adalah output HD: jangan pernah mengembalikan
    // tinggi di bawah 720, meski capture RDP hanya 940x529 atau sumbernya
    // ber-aspek berbeda. Encoder utama memakai VideoLayout yang sama.
    Ok((MAX_WIDTH, MAX_HEIGHT))
}

pub fn sps_profile_level(data: &[u8]) -> Option<[u8; 3]> {
    for i in 0..data.len() {
        let tail = &data[i..];
        let prefix = if tail.starts_with(&[0, 0, 0, 1]) {
            4
        } else if tail.starts_with(&[0, 0, 1]) {
            3
        } else {
            continue;
        };
        if tail.len() >= prefix + 4 && tail[prefix] & 31 == 7 {
            return Some([tail[prefix + 1], tail[prefix + 2], tail[prefix + 3]]);
        }
    }
    None
}

// Bobot fixed-point dipersiapkan hanya saat dimensi berubah. Empat sampel
// per pixel mengurangi gerigi nearest-neighbor tanpa menaikkan resolusi/SPS.
#[derive(Default)]
struct ResizePlan {
    shape: (usize, usize, usize, usize),
    xs: Vec<(usize, usize, u32)>,
    ys: Vec<(usize, usize, u32)>,
}
impl ResizePlan {
    fn axis(src: usize, dst: usize) -> Vec<(usize, usize, u32)> {
        (0..dst)
            .map(|i| {
                let pos =
                    ((i as f64 + 0.5) * src as f64 / dst as f64 - 0.5).clamp(0.0, (src - 1) as f64);
                let lo = pos.floor() as usize;
                (
                    lo,
                    (lo + 1).min(src - 1),
                    ((pos - lo as f64) * 256.0).round() as u32,
                )
            })
            .collect()
    }
    fn resize(
        &mut self,
        rgba: &[u8],
        width: usize,
        height: usize,
        w: usize,
        h: usize,
        out: &mut Vec<u8>,
    ) {
        if self.shape != (width, height, w, h) {
            self.xs = Self::axis(width, w);
            self.ys = Self::axis(height, h);
            self.shape = (width, height, w, h);
        }
        out.resize(w * h * 4, 0);
        for (y, &(y0, y1, wy)) in self.ys.iter().enumerate() {
            for (x, &(x0, x1, wx)) in self.xs.iter().enumerate() {
                for c in 0..4 {
                    let top = rgba[(y0 * width + x0) * 4 + c] as u32 * (256 - wx)
                        + rgba[(y0 * width + x1) * 4 + c] as u32 * wx;
                    let bottom = rgba[(y1 * width + x0) * 4 + c] as u32 * (256 - wx)
                        + rgba[(y1 * width + x1) * 4 + c] as u32 * wx;
                    out[(y * w + x) * 4 + c] =
                        ((top * (256 - wy) + bottom * wy + 32768) >> 16) as u8;
                }
            }
        }
    }
}

pub struct SoftwareEncoder {
    encoder: Encoder,
    resized: Vec<u8>,
    scaled: Vec<u8>,
    canvas: Vec<u8>,
    resize_plan: ResizePlan,
    logged_size: Option<(usize, usize)>,
    mode: u8,
    level: u8,
    fps: u32,
}
impl SoftwareEncoder {
    pub fn new() -> Result<Self, openh264::Error> {
        Self::with_fps(
            crate::video_policy::requested(),
            crate::video_policy::level(),
            crate::video_policy::fps(),
        )
    }
    pub fn with_policy(mode: u8, level: u8) -> Result<Self, openh264::Error> {
        Self::with_fps(mode, level, if mode == 2 && level >= 51 { 15 } else { 30 })
    }
    fn with_fps(mode: u8, level: u8, fps: u32) -> Result<Self, openh264::Error> {
        Ok(Self {
            encoder: Encoder::with_api_config(
                openh264::OpenH264API::from_source(),
                crate::screen::prod_encoder_config_for(level, fps),
            )?,
            resized: Vec::new(),
            scaled: Vec::new(),
            canvas: Vec::new(),
            resize_plan: ResizePlan::default(),
            logged_size: None,
            mode,
            level,
            fps,
        })
    }
    pub fn encode(&mut self, rgba: &[u8], width: usize, height: usize) -> Result<Vec<u8>, String> {
        let len = width
            .checked_mul(height)
            .and_then(|n| n.checked_mul(4))
            .ok_or("dimensi capture meluap")?;
        if rgba.len() != len {
            return Err("panjang RGBA tidak cocok dengan dimensi capture".into());
        }
        let layout = crate::video_layout::VideoLayout::new(width, height, self.mode, self.level)?;
        let [crx, cry, crw, crh] = layout.crop;
        let src: &[u8] = if (crx, cry, crw, crh) == (0, 0, width, height) {
            rgba
        } else {
            self.resized.resize(crw * crh * 4, 0);
            for y in 0..crh {
                let s = ((cry + y) * width + crx) * 4;
                self.resized[y * crw * 4..(y + 1) * crw * 4].copy_from_slice(&rgba[s..s + crw * 4]);
            }
            &self.resized
        };
        let [cw, ch] = layout.canvas;
        let [cx, cy, content_w, content_h] = layout.content;
        let pixels = if (crw, crh) == (cw, ch) && layout.content == [0, 0, cw, ch] {
            src
        } else {
            // Pertahankan rasio sumber di dalam canvas HD. Bar internal
            // sengaja dibuat di encoder, bukan dengan menarik gambar, dan
            // contentRect dikirim ke client agar input mengabaikan bar.
            self.canvas.resize(cw * ch * 4, 0);
            self.scaled.resize(content_w * content_h * 4, 0);
            self.resize_plan
                .resize(src, crw, crh, content_w, content_h, &mut self.scaled);
            for row in 0..content_h {
                let from = row * content_w * 4;
                let to = ((cy + row) * cw + cx) * 4;
                self.canvas[to..to + content_w * 4]
                    .copy_from_slice(&self.scaled[from..from + content_w * 4]);
            }
            &self.canvas
        };
        let yuv = YUVBuffer::from_rgb_source(RgbaSliceU8::new(pixels, (cw, ch)));
        let data = self
            .encoder
            .encode(&yuv)
            .map_err(|e| format!("openh264: {e}"))?
            .to_vec();
        if self.logged_size != Some((width, height)) {
            if let Some([profile, constraints, level]) = sps_profile_level(&data) {
                let fps = self.fps;
                println!("[xydesk-host] video software: capture {width}x{height} -> canvas {cw}x{ch}, content {}x{} tanpa stretch/crop, maks {fps} fps, bitrate {} bps, SPS {profile:02x}{constraints:02x}{level:02x}", layout.content[2], layout.content[3], crate::screen::target_bitrate_bps().min(MAX_BITRATE));
                self.logged_size = Some((width, height));
            }
        }
        crate::video_policy::record_layout(Some(layout));
        Ok(data)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use openh264::formats::YUVSource;
    #[test]
    fn ukuran_aspek_genap_dan_batas_macroblock() {
        assert_eq!(output_size(2336, 1080).unwrap(), (1280, 720));
        for (w, h) in [
            (2336, 1080),
            (3840, 2160),
            (1080, 2336),
            (1280, 720),
            (321, 181),
            (2, 2),
        ] {
            let (ow, oh) = output_size(w, h).unwrap();
            assert!(ow <= 1280 && oh <= 720 && ow % 2 == 0 && oh % 2 == 0);
            assert!(ow.div_ceil(16) * oh.div_ceil(16) <= 3600);
            assert!(ow.div_ceil(16) * oh.div_ceil(16) * MAX_FPS as usize <= 108000);
        }
        assert!(output_size(0, 100).is_err());
    }
    #[test]
    fn produksi_rgba_rdp_menjadi_sps_level31_dan_bisa_didecode() {
        let (w, h) = (2336, 1080);
        let pixels = vec![220u8; w * h * 4];
        let mut enc = SoftwareEncoder::new().unwrap();
        let data = enc.encode(&pixels, w, h).unwrap();
        let [profile, constraints, level] = sps_profile_level(&data).unwrap();
        assert_eq!(profile, 66);
        assert_ne!(constraints & 0x40, 0);
        assert_eq!(level, 31, "SPS harus sesuai batas Level3.1, bukan 5.1");
        let mut decoder = openh264::decoder::Decoder::new().unwrap();
        let decoded = decoder.decode(&data).unwrap().expect("IDR harus terdecode");
        // Kebijakan 2026-09-26: sumber lebar mengikuti rasio tanpa bar —
        // 2336x1080 menjadi canvas 1392x644 (budget macroblock 3600).
        assert_eq!(decoded.dimensions(), (1392, 644));
    }
    #[test]
    fn negotiated_hd_and_native_are_real_decodable_pixels() {
        for (mode, level, w, h, expected) in [
            (1, 40, 1920, 1080, (1920, 1080)),
            // Mode asli: desktop utuh pada dimensi natif — tanpa pita, tanpa crop.
            (2, 51, 2336, 1080, (2336, 1080)),
            (0, 51, 1920, 1080, (1280, 720)),
            // RDP kecil harus tetap keluar sebagai HD 720p pada mode HD.
            (0, 31, 940, 529, (1280, 720)),
        ] {
            let mut encoder = SoftwareEncoder::with_policy(mode, level).unwrap();
            let bytes = encoder.encode(&vec![100; w * h * 4], w, h).unwrap();
            assert_eq!(sps_profile_level(&bytes).unwrap()[2], level);
            let mut decoder = openh264::decoder::Decoder::new().unwrap();
            let decoded = decoder.decode(&bytes).unwrap().unwrap();
            assert_eq!(decoded.dimensions(), expected);
        }
    }
    #[test]
    fn odd_aspect_desktop_kept_whole_without_stretch_or_crop() {
        for (mode, level) in [(0u8, 31u8), (1, 40), (1, 51)] {
            let (w, h) = (2336, 1080);
            let layout = crate::video_layout::VideoLayout::new(w, h, mode, level).unwrap();
            // Tanpa crop: sumber adalah seluruh desktop.
            assert_eq!(layout.crop, [0, 0, w, h]);
            let mut encoder = SoftwareEncoder::with_policy(mode, level).unwrap();
            let bytes = encoder.encode(&vec![220; w * h * 4], w, h).unwrap();
            let mut decoder = openh264::decoder::Decoder::new().unwrap();
            let frame = decoder.decode(&bytes).unwrap().unwrap();
            assert_eq!(frame.dimensions(), (layout.canvas[0], layout.canvas[1]));
            let mut rgb = vec![0; layout.canvas[0] * layout.canvas[1] * 3];
            frame.write_rgb8(&mut rgb);
            let pixel = |x: usize, y: usize| rgb[(y * layout.canvas[0] + x) * 3];
            let [cx, cy, cw, ch] = layout.content;
            assert!(pixel(cx + 6, cy + 6) > 190);
            assert!(pixel(cx + cw - 6, cy + ch - 6) > 190);
            if mode == 0 {
                // Kebijakan 2026-09-26: sumber lebar tidak lagi mendapat bar
                // letterbox di mode HD — content memenuhi canvas mengikuti
                // rasio sumber, jadi sudut frame ikut terang.
                assert_eq!([cx, cy], [0, 0]);
                assert_eq!([cw, ch], layout.canvas);
                assert!(pixel(6, 6) > 190, "HD lebar tanpa bar letterbox");
            } else {
                assert!(pixel(6, 6) > 190);
                assert!(pixel(layout.canvas[0] - 6, layout.canvas[1] - 6) > 190);
            }
        }
        // Desktop tinggi: seluruh tinggi ikut terkirim — baris atas TIDAK dibuang.
        let (w, h) = (1920, 1200);
        let mut pixels = vec![40u8; w * h * 4];
        for y in 0..120 {
            pixels[y * w * 4..(y + 1) * w * 4].fill(230);
        }
        let layout = crate::video_layout::VideoLayout::new(w, h, 1, 40).unwrap();
        assert_eq!(layout.crop, [0, 0, 1920, 1200]);
        assert_eq!(layout.canvas, [1728, 1080]);
        let mut encoder = SoftwareEncoder::with_policy(1, 40).unwrap();
        let bytes = encoder.encode(&pixels, w, h).unwrap();
        let mut decoder = openh264::decoder::Decoder::new().unwrap();
        let frame = decoder.decode(&bytes).unwrap().unwrap();
        assert_eq!(frame.dimensions(), (1728, 1080));
        let mut rgb = vec![0; 1728 * 1080 * 3];
        frame.write_rgb8(&mut rgb);
        assert!(
            rgb[0] > 190,
            "baris atas desktop harus tetap ada di frame, tidak di-crop"
        );
        assert!(
            rgb[(1000 * 1728) * 3] < 90,
            "isi desktop bagian bawah juga tetap ada"
        );
    }
    #[test]
    fn bilinear_mencampur_detail_bukan_memilih_satu_pixel() {
        let mut plan = ResizePlan::default();
        let mut out = Vec::new();
        let pixels: Vec<u8> = [0, 255, 255, 0]
            .into_iter()
            .flat_map(|v| [v, v, v, 255])
            .collect();
        plan.resize(&pixels, 2, 2, 1, 1, &mut out);
        assert_eq!(out, vec![128, 128, 128, 255]);
        plan.resize(&pixels, 2, 2, 2, 2, &mut out);
        assert_eq!(out, pixels);
        let capacity = out.capacity();
        plan.resize(&[255; 16], 2, 2, 2, 2, &mut out);
        assert_eq!(out, vec![255; 16]);
        assert_eq!(out.capacity(), capacity);
    }

    #[test]
    fn input_rgba_rusak_ditolak() {
        assert!(SoftwareEncoder::new()
            .unwrap()
            .encode(&[0; 4], 320, 180)
            .is_err());
    }
}
