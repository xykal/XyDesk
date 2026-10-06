//! Negotiated H264 limits and requested spatial quality; never change Windows mode.
use std::sync::{
    atomic::{AtomicU8, Ordering},
    Mutex,
};
static FPS: AtomicU8 = AtomicU8::new(30);
static LEVEL: AtomicU8 = AtomicU8::new(31);
static REQUESTED: AtomicU8 = AtomicU8::new(0);
static APPLIED: Mutex<Option<crate::video_layout::VideoLayout>> = Mutex::new(None);
fn effective_mode(locked720: bool, requested: u8) -> u8 {
    if locked720 {
        0
    } else {
        requested
    }
}
pub fn level() -> u8 {
    LEVEL.load(Ordering::Relaxed)
}
pub fn requested() -> u8 {
    effective_mode(
        crate::virtual_target::enabled(),
        REQUESTED.load(Ordering::Relaxed),
    )
}
pub fn configure(level: u8) {
    LEVEL.store(
        if level >= 51 {
            51
        } else if level >= 40 {
            40
        } else {
            31
        },
        Ordering::Relaxed,
    );
    FPS.store(30, Ordering::Relaxed);
    // Mulai pada HD 1280x720; client tetap boleh memilih 1080p setelah meta.
    REQUESTED.store(0, Ordering::Relaxed);
    record(None);
}
pub fn promote_level(min_level: u8) {
    let target = if min_level >= 51 {
        51
    } else if min_level >= 40 {
        40
    } else {
        31
    };
    let cur = LEVEL.load(Ordering::Relaxed);
    if target > cur {
        LEVEL.store(target, Ordering::Relaxed);
    }
}
pub fn request(mode: u8) -> bool {
    if mode > 2 || (crate::virtual_target::enabled() && mode != 0) {
        return false;
    }
    REQUESTED.store(mode, Ordering::Relaxed);
    true
}
/// Laju yang boleh diminta client lewat kontrol `0x0f`. 120/144 hanya benar-benar
/// dipakai bila level H264 hasil negosiasi memang sanggup (lihat [`fps_limit`]);
/// permintaan di luar daftar ini ditolak supaya nilai liar tidak masuk ke encoder.
pub const SUPPORTED_FPS: [u8; 4] = [30, 60, 120, 144];

/// MaxMBPS (macroblock per detik) per level H264 — Tabel A-1 ITU-T H.264.
/// Angka ini yang membatasi fps, bukan selera: melampauinya membuat stream
/// keluar dari level yang sudah diumumkan di SDP dan decoder HP boleh menolak.
fn max_mbps(level: u8) -> u32 {
    if level >= 51 {
        983_040
    } else if level >= 40 {
        245_760
    } else {
        108_000
    }
}

/// Jumlah macroblock kanvas yang benar-benar dikirim untuk `mode`+`level`.
/// Harus mengikuti [`output_size`]: mode 0 atau level < 40 selalu 1280x720.
fn canvas_macroblocks(mode: u8, level: u8) -> u32 {
    if mode == 0 || level < 40 {
        80 * 45 // 1280x720  = 3_600
    } else if mode == 2 && level >= 51 {
        256 * 135 // 4096x2160 = 34_560
    } else {
        120 * 68 // 1920x1080 = 8_160
    }
}

pub fn request_fps(fps: u8) -> bool {
    if !SUPPORTED_FPS.contains(&fps) {
        return false;
    }
    FPS.store(fps, Ordering::Relaxed);
    true
}

/// Laju efektif = minimum dari yang diminta client dan yang diizinkan level.
///
/// Headroom nyata (MaxMBPS / macroblock kanvas):
/// 720p → 30 (L3.1), 68 (L4.0), 273 (L5.1); 1080p → 30 (L4.0), 120 (L5.1).
/// Jadi 144 fps hanya mungkin di 720p + L5.1, dan 120 fps di 1080p + L5.1.
/// Mode asli/4K tetap dibatasi 15 fps: bandwidth dan encoder lebih dulu habis
/// daripada 28 fps yang secara level masih boleh.
pub fn fps_limit(mode: u8, level: u8, wanted: u8) -> u32 {
    let wanted = if SUPPORTED_FPS.contains(&wanted) {
        u32::from(wanted)
    } else {
        30
    };
    if mode == 2 && level >= 51 {
        return 15;
    }
    let ceiling = max_mbps(level) / canvas_macroblocks(mode, level);
    // Turunkan ke anak tangga yang didukung, bukan ke angka sisa bagi seperti 68:
    // pacing capture, VBV, dan GOP semuanya dihitung dari laju bulat.
    let step = SUPPORTED_FPS
        .iter()
        .filter(|f| u32::from(**f) <= ceiling)
        .map(|f| u32::from(*f))
        .max()
        .unwrap_or(30);
    wanted.min(step)
}
pub fn fps() -> u32 {
    fps_limit(requested(), level(), FPS.load(Ordering::Relaxed))
}
pub fn record(size: Option<(usize, usize)>) {
    record_layout(
        size.and_then(|(w, h)| {
            crate::video_layout::VideoLayout::new(w, h, requested(), level()).ok()
        }),
    );
}
pub fn record_layout(layout: Option<crate::video_layout::VideoLayout>) {
    *APPLIED
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner) = layout;
}
pub fn layout() -> Option<crate::video_layout::VideoLayout> {
    *APPLIED
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
}
pub fn telemetry() -> serde_json::Value {
    let layout = layout();
    serde_json::json!({"level":level(),"requested":requested(),"applied":layout.map(|r|r.canvas),"contentRect":layout.map(|r|r.content),"fpsLimit":fps(),"fpsRequested":FPS.load(Ordering::Relaxed),"fpsControl":true,"cropRect":layout.map(|r|r.crop)})
}
pub fn output_size(w: usize, h: usize, mode: u8, level: u8) -> Result<(usize, usize), String> {
    if w < 2 || h < 2 {
        return Err("capture lebih kecil dari 2x2".into());
    }
    let (mw, mh) = if mode == 0 || level < 40 {
        (1280, 720)
    } else if mode == 2 && level >= 51 {
        (4096, 2160)
    } else {
        (1920, 1080)
    };
    let scale = (mw as f64 / w as f64).min(mh as f64 / h as f64);
    let scale = if mode == 0 { scale } else { scale.min(1.0) };
    if mode == 0 {
        // 720p adalah kontrak minimum/mutlak: tinggi tidak boleh turun
        // menjadi 529 atau ukuran lain hanya karena sumber RDP lebih kecil
        // atau rasio sumber sedikit berbeda.
        return Ok((mw, mh));
    }
    Ok((
        ((w as f64 * scale).round() as usize & !1).max(2),
        ((h as f64 * scale).round() as usize & !1).max(2),
    ))
}
pub fn offer_level(sdp: &str) -> u8 {
    let mut video = false;
    let mut codecs = std::collections::HashSet::new();
    let mut fmtps = Vec::new();
    for line in sdp.lines() {
        let line = line.trim();
        if line.starts_with("m=") {
            video = line.starts_with("m=video ");
        }
        if !video {
            continue;
        }
        if let Some(rest) = line.strip_prefix("a=rtpmap:") {
            if let Some((pt, codec)) = rest.split_once(' ') {
                if codec.eq_ignore_ascii_case("H264/90000") {
                    codecs.insert(pt);
                }
            }
        }
        if let Some(rest) = line.strip_prefix("a=fmtp:") {
            fmtps.push(rest);
        }
    }
    fmtps
        .into_iter()
        .filter_map(|s| {
            let (pt, params) = s.split_once(' ')?;
            if !codecs.contains(pt) {
                return None;
            }
            let p: std::collections::HashMap<_, _> = params
                .split(';')
                .filter_map(|x| x.trim().split_once('='))
                .collect();
            if p.get("packetization-mode") != Some(&"1") {
                return None;
            }
            let profile = p.get("profile-level-id")?;
            if !profile.is_ascii()
                || profile.len() != 6
                || !profile[..4].eq_ignore_ascii_case("42e0")
            {
                return None;
            }
            u8::from_str_radix(&profile[4..], 16).ok()
        })
        .max()
        .map(|n| {
            if n >= 51 {
                51
            } else if n >= 40 {
                40
            } else {
                31
            }
        })
        .unwrap_or(31)
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn dimensions_no_crop_upscale_or_level_overflow() {
        assert_eq!(output_size(2336, 1080, 0, 31).unwrap(), (1280, 720));
        assert_eq!(output_size(2336, 1080, 1, 40).unwrap(), (1920, 888));
        assert_eq!(output_size(2336, 1080, 2, 51).unwrap(), (2336, 1080));
        assert_eq!(output_size(1920, 1080, 1, 40).unwrap(), (1920, 1080));
        assert_eq!(output_size(640, 360, 1, 51).unwrap(), (640, 360));
        assert_eq!(output_size(940, 529, 0, 31).unwrap(), (1280, 720));
        for level in [31, 40, 51] {
            for mode in 0..=2 {
                for (w, h) in [(3840, 2160), (2336, 1080), (2160, 3840), (7680, 4320)] {
                    let (w, h) = output_size(w, h, mode, level).unwrap();
                    assert!(
                        w.div_ceil(16) * h.div_ceil(16)
                            <= if level == 31 {
                                3600
                            } else if level == 40 {
                                8192
                            } else {
                                36864
                            }
                    );
                }
            }
        }
    }
    #[test]
    fn only_video_constrained_baseline_packetization_one() {
        let s="m=video 9 UDP/TLS/RTP/SAVPF 108\r\na=rtpmap:108 H264/90000\r\na=fmtp:108 packetization-mode=1;profile-level-id=42e028\r\n";
        assert_eq!(offer_level(s), 40);
        assert_eq!(offer_level(&s.replace("42e028", "42e033")), 51);
        assert_eq!(offer_level(&s.replace("m=video", "m=audio")), 31);
        assert_eq!(offer_level(&s.replace("H264", "VP8")), 31);
        assert_eq!(offer_level(&s.replace("mode=1", "mode=0")), 31);
    }
}

#[cfg(test)]
mod virtual720_tests {
    #[test]
    fn strict_canvas_cannot_be_changed_by_client_preset() {
        for requested in 0..=2 {
            let mode = super::effective_mode(true, requested);
            assert_eq!(mode, 0);
            let layout = crate::video_layout::VideoLayout::new(1280, 720, mode, 51).unwrap();
            assert_eq!(layout.canvas, [1280, 720]);
            assert_eq!(layout.content, [0, 0, 1280, 720]);
            assert_eq!(super::effective_mode(false, requested), requested);
        }
    }
}

#[cfg(test)]
mod fps_tests {
    #[test]
    fn negotiated_macroblock_limits() {
        for mode in 0..3 {
            assert_eq!(super::fps_limit(mode, 31, 60), 30);
        }
        assert_eq!(super::fps_limit(0, 40, 60), 60);
        assert_eq!(super::fps_limit(1, 40, 60), 30);
        assert_eq!(super::fps_limit(1, 51, 60), 60);
        assert_eq!(super::fps_limit(2, 51, 60), 15);
        assert_eq!(super::fps_limit(0, 51, 30), 30);
        assert!(!super::request_fps(0));
    }

    #[test]
    fn laju_tinggi_hanya_bila_level_sanggup() {
        // 720p + L5.1: headroom 983040/3600 = 273 fps → 144 benar-benar boleh.
        assert_eq!(super::fps_limit(0, 51, 144), 144);
        assert_eq!(super::fps_limit(0, 51, 120), 120);
        // 1080p + L5.1: headroom 983040/8160 = 120,4 fps → 144 turun ke 120.
        assert_eq!(super::fps_limit(1, 51, 144), 120);
        assert_eq!(super::fps_limit(1, 51, 120), 120);
        // L4.0 tidak pernah melewati 60, L3.1 tidak pernah melewati 30.
        assert_eq!(super::fps_limit(0, 40, 144), 60);
        assert_eq!(super::fps_limit(1, 40, 144), 30);
        assert_eq!(super::fps_limit(0, 31, 144), 30);
        // 4K tetap 15 fps: bandwidth habis lebih dulu daripada level.
        assert_eq!(super::fps_limit(2, 51, 144), 15);
    }

    #[test]
    fn hasil_selalu_anak_tangga_yang_didukung() {
        // Jangan pernah mengembalikan sisa bagi seperti 68 fps: pacing capture,
        // VBV, dan GOP semuanya mengasumsikan laju bulat yang dikenal.
        for mode in 0..3u8 {
            for level in [31u8, 40, 51] {
                for wanted in [30u8, 60, 120, 144] {
                    let got = super::fps_limit(mode, level, wanted);
                    assert!(
                        super::SUPPORTED_FPS.contains(&(got as u8)) || got == 15,
                        "fps_limit({mode},{level},{wanted}) = {got} bukan anak tangga"
                    );
                    assert!(got <= u32::from(wanted), "tidak boleh melebihi permintaan");
                }
            }
        }
    }

    #[test]
    fn permintaan_liar_ditolak_dan_tidak_mengubah_state() {
        for bad in [0u8, 1, 24, 45, 59, 61, 100, 143, 145, 255] {
            assert!(!super::request_fps(bad), "{bad} seharusnya ditolak");
        }
        for ok in super::SUPPORTED_FPS {
            assert!(super::request_fps(ok), "{ok} seharusnya diterima");
        }
        // Nilai liar yang entah bagaimana lolos ke fps_limit tetap aman.
        assert_eq!(super::fps_limit(0, 51, 255), 30);
        super::request_fps(30);
    }

    #[test]
    fn batas_level_sesuai_tabel_a1_h264() {
        // Pembuktian angka, bukan sekadar mengunci hasil fungsi.
        assert_eq!(super::max_mbps(31), 108_000);
        assert_eq!(super::max_mbps(40), 245_760);
        assert_eq!(super::max_mbps(51), 983_040);
        assert_eq!(super::canvas_macroblocks(0, 51), 3_600); // 1280x720
        assert_eq!(super::canvas_macroblocks(1, 51), 8_160); // 1920x1080
        assert_eq!(super::canvas_macroblocks(2, 51), 34_560); // 4096x2160
                                                              // Level < 40 selalu 720p, apa pun mode yang diminta — ikut output_size().
        for mode in 0..3u8 {
            assert_eq!(super::canvas_macroblocks(mode, 31), 3_600);
        }
        // Kanvas yang dipakai fps_limit tidak boleh MEREMEHKAN kanvas nyata dari
        // output_size(): meremehkan = fps ketinggian = stream keluar dari level.
        // Melebihkan boleh (mode asli bergantung ukuran desktop, jadi kita pakai
        // batas atas 4096x2160) — konsekuensinya cuma lebih konservatif.
        for desktop in [
            (3840usize, 2160usize),
            (2560, 1440),
            (1920, 1080),
            (1366, 768),
        ] {
            for mode in 0..3u8 {
                for level in [31u8, 40, 51] {
                    let (w, h) = super::output_size(desktop.0, desktop.1, mode, level).unwrap();
                    let nyata = (w.div_ceil(16) * h.div_ceil(16)) as u32;
                    assert!(
                        super::canvas_macroblocks(mode, level) >= nyata,
                        "kanvas mode {mode} level {level} desktop {desktop:?}: \
                         anggapan {} < nyata {nyata}",
                        super::canvas_macroblocks(mode, level)
                    );
                    // Dan laju yang dihasilkan memang muat di level tersebut.
                    let fps = super::fps_limit(mode, level, 144);
                    assert!(
                        nyata * fps <= super::max_mbps(level),
                        "mode {mode} level {level}: {nyata} MB x {fps} fps melebihi MaxMBPS"
                    );
                }
            }
        }
    }
}
