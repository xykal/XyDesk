//! RTP header extension `abs-capture-time` — stempel waktu capture di paket
//! video, supaya client bisa mengukur glass-to-glass tanpa foto layar.
//!
//! Spesifikasi: <https://webrtc.googlesource.com/src/+/refs/heads/main/docs/native-code/rtp-hdrext/abs-capture-time/README.md>
//!
//!   0                   1                   2                   3
//!   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//!  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//!  |  ID   | len=7 |     absolute capture timestamp (NTP 64-bit)   |
//!  +-+-+-+-+-+-+-+-+                                               |
//!  |                                                               |
//!  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//!
//! Bentuk 8-byte (tanpa `estimated capture clock offset`) — host adalah
//! sumber capture, bukan relay, jadi offset tidak berlaku.
//!
//! Chrome mengisi `captureTime` di `requestVideoFrameCallback` hanya bila
//! extension ini dinegosiasi; probe web (`web/src/latency_probe.ts`) lalu
//! beralih dari estimasi lower-bound ke pengukuran langsung. Client yang
//! tidak menawarkan extension ini (APK lama) tidak terpengaruh:
//! `write_sample_with_extensions` melewatkan extension yang tidak ada di
//! parameter negosiasi.
//!
//! rtp 0.11 belum punya varian bawaan untuk extension ini, jadi dipakai
//! `HeaderExtension::Custom` dengan `Marshal` sendiri. Modul lintas platform,
//! tanpa I/O — diuji unit di semua OS.

use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use webrtc::api::media_engine::MediaEngine;
use webrtc::rtp::extension::HeaderExtension;
use webrtc::rtp_transceiver::rtp_codec::{RTCRtpHeaderExtensionCapability, RTPCodecType};
use webrtc::util::marshal::{Marshal, MarshalSize};

/// URI resmi; harus sama persis dengan yang ditawarkan client di SDP.
pub const URI: &str = "http://www.webrtc.org/experiments/rtp-hdrext/abs-capture-time";

/// Selisih epoch NTP (1900) dan Unix (1970) dalam detik.
const NTP_UNIX_OFFSET_SECS: u64 = 2_208_988_800;

const SIZE: usize = 8;

/// Payload extension: NTP 64-bit (32 bit detik sejak 1900, 32 bit pecahan).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct AbsCaptureTime {
    pub ntp: u64,
}

impl AbsCaptureTime {
    /// Dari waktu dinding. Waktu sebelum 1970 tidak mungkin di sini; bila
    /// jam sistem rusak, jatuh ke 0 (client mengabaikan nilai yang mustahil).
    pub fn from_system_time(t: SystemTime) -> Self {
        Self { ntp: ntp_from_system_time(t) }
    }

    pub fn into_header_extension(self) -> HeaderExtension {
        HeaderExtension::Custom { uri: URI.into(), extension: Box::new(self) }
    }
}

impl MarshalSize for AbsCaptureTime {
    fn marshal_size(&self) -> usize {
        SIZE
    }
}

impl Marshal for AbsCaptureTime {
    fn marshal_to(&self, buf: &mut [u8]) -> webrtc::util::Result<usize> {
        if buf.len() < SIZE {
            return Err(webrtc::util::Error::Other(format!(
                "buffer abs-capture-time terlalu kecil: {} < {SIZE}",
                buf.len()
            )));
        }
        buf[..SIZE].copy_from_slice(&self.ntp.to_be_bytes());
        Ok(SIZE)
    }
}

/// Konversi `SystemTime` → NTP 64-bit (Q32.32).
pub fn ntp_from_system_time(t: SystemTime) -> u64 {
    let since_unix = t.duration_since(UNIX_EPOCH).unwrap_or(Duration::ZERO);
    let secs = since_unix.as_secs().saturating_add(NTP_UNIX_OFFSET_SECS);
    // pecahan: nanos × 2^32 / 1e9, tanpa float supaya deterministik.
    let frac = ((since_unix.subsec_nanos() as u128) << 32) / 1_000_000_000u128;
    (secs << 32) | (frac as u64)
}

/// Waktu dinding saat frame di-capture, diturunkan dari `Instant` capture
/// dan pasangan (`Instant`, `SystemTime`) "sekarang". Capture selalu terjadi
/// sebelum "sekarang"; kalau tidak (jam monotonic aneh), pakai "sekarang".
pub fn capture_wall_time(captured_at: Instant, now_instant: Instant, now_wall: SystemTime) -> SystemTime {
    let age = now_instant.saturating_duration_since(captured_at);
    now_wall.checked_sub(age).unwrap_or(now_wall)
}

/// Extension siap kirim untuk frame yang di-capture pada `captured_at`.
pub fn header_extension_for(captured_at: Instant) -> HeaderExtension {
    let wall = capture_wall_time(captured_at, Instant::now(), SystemTime::now());
    AbsCaptureTime::from_system_time(wall).into_header_extension()
}

/// Daftarkan ke `MediaEngine` host supaya jawaban SDP memuat `a=extmap` bila
/// client menawarkannya. Dipanggil SETELAH `register_default_codecs`.
pub fn register(media: &mut MediaEngine) -> webrtc::error::Result<()> {
    media.register_header_extension(
        RTCRtpHeaderExtensionCapability { uri: URI.to_string() },
        RTPCodecType::Video,
        None,
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ntp_epoch_unix_nol_adalah_offset_1900() {
        assert_eq!(ntp_from_system_time(UNIX_EPOCH), NTP_UNIX_OFFSET_SECS << 32);
    }

    #[test]
    fn ntp_pecahan_setengah_detik_adalah_2_pangkat_31() {
        let t = UNIX_EPOCH + Duration::from_millis(500);
        let ntp = ntp_from_system_time(t);
        assert_eq!(ntp >> 32, NTP_UNIX_OFFSET_SECS);
        assert_eq!(ntp & 0xFFFF_FFFF, 1u64 << 31);
    }

    #[test]
    fn ntp_contoh_terkenal_2036_pembungkus_belum_terjadi() {
        // 2026-01-01T00:00:00Z = 1767225600 Unix → 3976214400 NTP (< 2^32).
        let t = UNIX_EPOCH + Duration::from_secs(1_767_225_600);
        assert_eq!(ntp_from_system_time(t) >> 32, 3_976_214_400);
    }

    #[test]
    fn marshal_big_endian_8_byte() {
        let ext = AbsCaptureTime { ntp: 0x0102_0304_0506_0708 };
        let mut buf = [0u8; 8];
        assert_eq!(ext.marshal_to(&mut buf).unwrap(), 8);
        assert_eq!(buf, [1, 2, 3, 4, 5, 6, 7, 8]);
        assert_eq!(ext.marshal_size(), 8);
        let mut kecil = [0u8; 7];
        assert!(ext.marshal_to(&mut kecil).is_err());
    }

    #[test]
    fn header_extension_memakai_uri_resmi_dan_ukuran_benar() {
        let ext = AbsCaptureTime { ntp: 1 }.into_header_extension();
        assert_eq!(ext.uri(), URI);
        assert_eq!(ext.marshal_size(), 8);
        let bytes = ext.marshal().unwrap();
        assert_eq!(&bytes[..], &[0, 0, 0, 0, 0, 0, 0, 1]);
    }

    #[test]
    fn capture_wall_time_mundur_sebesar_umur_frame() {
        let now_i = Instant::now();
        let captured = now_i - Duration::from_millis(25);
        let now_w = UNIX_EPOCH + Duration::from_secs(1_000_000);
        let wall = capture_wall_time(captured, now_i, now_w);
        assert_eq!(now_w.duration_since(wall).unwrap(), Duration::from_millis(25));
    }

    #[test]
    fn capture_wall_time_tidak_pernah_di_masa_depan() {
        let now_i = Instant::now();
        let now_w = SystemTime::now();
        // captured_at "setelah" now_instant (mustahil di produksi) → clamp.
        let wall = capture_wall_time(now_i + Duration::from_millis(5), now_i, now_w);
        assert_eq!(wall, now_w);
    }

    #[test]
    fn register_menambah_extension_ke_media_engine() {
        let mut media = MediaEngine::default();
        media.register_default_codecs().unwrap();
        register(&mut media).expect("register abs-capture-time");
        // Daftar dua kali tidak boleh panik/duplikat — Session::new bisa
        // dipanggil berkali-kali per proses, tapi tiap MediaEngine baru.
        register(&mut media).expect("register ulang aman");
    }
}
