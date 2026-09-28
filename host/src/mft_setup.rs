//! Penyiapan MFT: enumerasi, tipe media, dan VARIANT untuk ICodecAPI.
//! Dipisah dari `mft.rs` supaya alur per frame di sana tetap pendek.

use std::mem::ManuallyDrop;
use std::sync::OnceLock;

use windows::core::{Interface, GUID};
use windows::Win32::Foundation::VARIANT_BOOL;
use windows::Win32::Media::MediaFoundation::*;
use windows::Win32::System::Com::{CoInitializeEx, CoTaskMemFree, COINIT_MULTITHREADED};
use windows::Win32::System::Variant::{
    VARIANT, VARIANT_0, VARIANT_0_0, VARIANT_0_0_0, VT_BOOL, VT_UI4,
};

pub(crate) const FPS: u32 = 60;
const GOP_FRAMES: u32 = FPS * 4;

pub(crate) fn startup() -> Result<(), String> {
    static STARTED: OnceLock<Result<(), String>> = OnceLock::new();
    STARTED
        .get_or_init(|| unsafe {
            let _ = CoInitializeEx(None, COINIT_MULTITHREADED);
            MFStartup(MF_VERSION, MFSTARTUP_NOSOCKET)
                .map_err(|e| format!("MFStartup gagal: {e}"))
        })
        .clone()
}

pub(crate) fn enumerate() -> Result<Vec<IMFActivate>, String> {
    startup()?;
    let input = MFT_REGISTER_TYPE_INFO {
        guidMajorType: MFMediaType_Video,
        guidSubtype: MFVideoFormat_NV12,
    };
    let output = MFT_REGISTER_TYPE_INFO {
        guidMajorType: MFMediaType_Video,
        guidSubtype: MFVideoFormat_H264,
    };
    let mut list: *mut Option<IMFActivate> = std::ptr::null_mut();
    let mut count = 0u32;
    unsafe {
        MFTEnumEx(
            MFT_CATEGORY_VIDEO_ENCODER,
            MFT_ENUM_FLAG_HARDWARE | MFT_ENUM_FLAG_SORTANDFILTER,
            Some(&input as *const _),
            Some(&output as *const _),
            &mut list,
            &mut count,
        )
        .map_err(|e| format!("MFTEnumEx gagal: {e}"))?;
        if list.is_null() {
            return Ok(Vec::new());
        }
        let found = std::slice::from_raw_parts(list, count as usize)
            .iter()
            .filter_map(|a| a.clone())
            .collect();
        CoTaskMemFree(Some(list as *const _));
        Ok(found)
    }
}

pub(crate) fn friendly_name(activate: &IMFActivate) -> String {
    let mut ptr = windows::core::PWSTR::null();
    let mut len = 0u32;
    unsafe {
        let read = activate.GetAllocatedString(&MFT_FRIENDLY_NAME_Attribute, &mut ptr, &mut len);
        if read.is_err() || ptr.is_null() {
            return "MFT H264".into();
        }
        let name = ptr.to_string().unwrap_or_else(|_| "MFT H264".into());
        CoTaskMemFree(Some(ptr.as_ptr() as *const _));
        name
    }
}

pub fn available() -> bool {
    enumerate().map(|l| !l.is_empty()).unwrap_or(false)
}

pub fn unavailable_reason() -> Option<String> {
    match enumerate() {
        Ok(l) if l.is_empty() => Some("tidak ada MFT encoder H264 hardware".into()),
        Ok(_) => None,
        Err(e) => Some(e),
    }
}

fn variant_u32(v: u32) -> VARIANT {
    VARIANT {
        Anonymous: VARIANT_0 {
            Anonymous: ManuallyDrop::new(VARIANT_0_0 {
                vt: VT_UI4,
                wReserved1: 0,
                wReserved2: 0,
                wReserved3: 0,
                Anonymous: VARIANT_0_0_0 { ulVal: v },
            }),
        },
    }
}

fn variant_bool(v: bool) -> VARIANT {
    VARIANT {
        Anonymous: VARIANT_0 {
            Anonymous: ManuallyDrop::new(VARIANT_0_0 {
                vt: VT_BOOL,
                wReserved1: 0,
                wReserved2: 0,
                wReserved3: 0,
                Anonymous: VARIANT_0_0_0 { boolVal: VARIANT_BOOL::from(v) },
            }),
        },
    }
}

pub(crate) unsafe fn output_type(
    width: u32,
    height: u32,
    bitrate_bps: u32,
) -> Result<IMFMediaType, String> {
    let t = MFCreateMediaType().map_err(|e| format!("MFCreateMediaType: {e}"))?;
    let set = |r: windows::core::Result<()>| r.map_err(|e| format!("set tipe output: {e}"));
    set(t.SetGUID(&MF_MT_MAJOR_TYPE, &MFMediaType_Video))?;
    set(t.SetGUID(&MF_MT_SUBTYPE, &MFVideoFormat_H264))?;
    set(t.SetUINT32(&MF_MT_AVG_BITRATE, bitrate_bps))?;
    set(t.SetUINT64(&MF_MT_FRAME_SIZE, ((width as u64) << 32) | height as u64))?;
    set(t.SetUINT64(&MF_MT_FRAME_RATE, ((FPS as u64) << 32) | 1))?;
    set(t.SetUINT64(&MF_MT_PIXEL_ASPECT_RATIO, (1u64 << 32) | 1))?;
    set(t.SetUINT32(&MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive.0 as u32))?;
    set(t.SetUINT32(&MF_MT_MPEG2_PROFILE, eAVEncH264VProfile_Main.0 as u32))?;
    set(t.SetUINT32(&MF_MT_ALL_SAMPLES_INDEPENDENT, 0))?;
    Ok(t)
}

pub(crate) unsafe fn input_type(width: u32, height: u32) -> Result<IMFMediaType, String> {
    let t = MFCreateMediaType().map_err(|e| format!("MFCreateMediaType: {e}"))?;
    let set = |r: windows::core::Result<()>| r.map_err(|e| format!("set tipe input: {e}"));
    set(t.SetGUID(&MF_MT_MAJOR_TYPE, &MFMediaType_Video))?;
    set(t.SetGUID(&MF_MT_SUBTYPE, &MFVideoFormat_NV12))?;
    set(t.SetUINT64(&MF_MT_FRAME_SIZE, ((width as u64) << 32) | height as u64))?;
    set(t.SetUINT64(&MF_MT_FRAME_RATE, ((FPS as u64) << 32) | 1))?;
    set(t.SetUINT64(&MF_MT_PIXEL_ASPECT_RATIO, (1u64 << 32) | 1))?;
    set(t.SetUINT32(&MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive.0 as u32))?;
    set(t.SetUINT32(&MF_MT_DEFAULT_STRIDE, width))?;
    set(t.SetUINT32(&MF_MT_ALL_SAMPLES_INDEPENDENT, 1))?;
    Ok(t)
}

pub(crate) unsafe fn tune_codec(transform: &IMFTransform, bitrate_bps: u32) {
    let Ok(api) = transform.cast::<ICodecAPI>() else { return };
    let opts: [(&GUID, VARIANT); 6] = [
        (&CODECAPI_AVLowLatencyMode, variant_bool(true)),
        (&CODECAPI_AVEncCommonRealTime, variant_u32(1)),
        (
            &CODECAPI_AVEncCommonRateControlMode,
            variant_u32(eAVEncCommonRateControlMode_CBR.0 as u32),
        ),
        (&CODECAPI_AVEncCommonMeanBitRate, variant_u32(bitrate_bps)),
        (&CODECAPI_AVEncMPVGOPSize, variant_u32(GOP_FRAMES)),
        (&CODECAPI_AVEncMPVDefaultBPictureCount, variant_u32(0)),
    ];
    // Tiap vendor mendukung subset berbeda; yang ditolak dilewati saja.
    for (guid, value) in &opts {
        let _ = api.SetValue(*guid, value);
    }
}
