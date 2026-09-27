//! Capture layar GDI BitBlt — **primitif capture mentah**, tanpa encode.
//!
//! ## Kenapa modul ini terpisah dari `screen.rs`
//!
//! Dua alasan, dan yang kedua sama pentingnya dengan yang pertama.
//!
//! 1. **Arsitektur.** Backend capture seharusnya hanya menyerahkan piksel.
//!    Encode (NVENC/openh264), simpanan IDR penyelamat layar hitam, pengiriman
//!    ke channel, dan pace fps adalah urusan `screen.rs` dan harus IDENTIK untuk
//!    backend mana pun — kalau tidak, ganti backend berarti ganti perilaku
//!    pipeline. Jadi modul ini berhenti di "ini buffer RGBA rapat".
//! 2. **Bisa diverifikasi.** `screen.rs` menarik webrtc, openh264, dan NVENC,
//!    sehingga tidak bisa dimasukkan ke `tool/wincheck` (crate pemeriksa tipe
//!    lintas-target yang membuat kode Win32 bisa di-type-check di Linux dalam
//!    hitungan detik). Modul ini hanya bergantung pada crate `windows` dan
//!    `pixfmt`, jadi seluruh kode `unsafe`-nya benar-benar terperiksa sebelum
//!    menghabiskan satu putaran CI Windows ~10 menit.
//!
//! ## Kenapa GDI ada sama sekali
//!
//! Lambat: satu `BitBlt` + `GetDIBits` per frame, tanpa notifikasi perubahan,
//! tanpa dirty-rect. Tapi ia **selalu ada** — tidak butuh Windows 10 1903+,
//! tidak butuh GPU, tidak menggambar border kuning, dan tetap bekerja di sesi
//! RDP, yang penting untuk PC/server sewaan tanpa monitor fisik. Perannya di
//! rantai capture adalah jaring pengaman terakhir: bila Windows Graphics
//! Capture membuka sesi (border terlihat, jadi dari luar tampak sehat) tetapi
//! `on_frame_arrived` tidak pernah dipanggil, hanya backend semacam ini yang
//! bisa membuat layar client tidak hitam.
//!
//! ## Yang TIDAK ditangani modul ini
//!
//! - Kondisi berhenti (pindah monitor, bitrate, keyframe, sesi selesai): milik
//!   pemanggil, supaya satu kebijakan untuk semua backend.
//! - Kecepatan: pemanggil yang mengatur pace dan melaporkan fps nyata.
//! - HDR: BitBlt menghasilkan SDR. Di desktop HDR hasilnya bisa terlihat pudar;
//!   itu keterbatasan yang diketahui, bukan kerusakan — dan tetap lebih baik
//!   daripada layar hitam.

// Alur fallback yang sama dipakai Win32 dan fixture regresi tanpa desktop.
#[cfg(any(target_os = "windows", test))]
trait PixelSource {
    fn rect(&self) -> crate::desktop_geometry::CaptureRect;
    fn is_fallback(&self) -> bool;
    fn read_rgba(&mut self, rgba: &mut Vec<u8>) -> Result<(), String>;
    /// Gagal membuka fallback tidak boleh mengubah sumber lama.
    fn switch_to_desktop(&mut self, name: &str) -> Result<(), String>;
}

#[cfg(any(target_os = "windows", test))]
fn sample_luma(rgba: &[u8]) -> u8 {
    let mut sum = 0u64;
    let mut count = 0u64;
    for i in (0..rgba.len()).step_by(4096 * 4) {
        if i + 2 >= rgba.len() {
            break;
        }
        sum += (u64::from(rgba[i]) + u64::from(rgba[i + 1]) + u64::from(rgba[i + 2])) / 3;
        count += 1;
    }
    sum.checked_div(count).unwrap_or(255) as u8
}

#[cfg(any(target_os = "windows", test))]
fn read_pixels(source: &mut impl PixelSource, rgba: &mut Vec<u8>) -> Result<(), String> {
    source.read_rgba(rgba)?;
    let rect = source.rect();
    crate::pixfmt::validate_rgba(rgba, rect.width as usize, rect.height as usize)
}

#[cfg(any(target_os = "windows", test))]
fn grab_pixels(
    source: &mut impl PixelSource,
    name: &str,
    rgba: &mut Vec<u8>,
    black_streak: &mut u8,
) -> Result<(), String> {
    let original_rect = source.rect();
    read_pixels(source, rgba)?;
    if sample_luma(rgba) < 8 {
        *black_streak = black_streak.saturating_add(1);
        if *black_streak == 15 && !source.is_fallback() {
            match source.switch_to_desktop(name) {
                Ok(()) => {
                    // Pemanggil telah membangun encoder dan input geometry
                    // dari rect lama. Jangan mengirim frame dengan kontrak baru
                    // sampai supervisor membuka ulang seluruh pipeline.
                    if source.rect() != original_rect {
                        return Err("geometri berubah saat fallback; buka ulang capture".into());
                    }
                    // Handle baru belum berisi piksel. Ambil frame sungguhan,
                    // bukan buffer kosong atau frame gelap dari sumber lama.
                    read_pixels(source, rgba)?;
                    if sample_luma(rgba) >= 8 {
                        *black_streak = 0;
                    }
                    eprintln!("[xydesk-host] GDI monitor-DC memberi frame hitam 15 kali; frame desktop-DC GetDC(0) sudah diambil");
                }
                Err(error) => {
                    eprintln!(
                        "[xydesk-host] GDI monitor-DC hitam; fallback desktop-DC gagal: {error}"
                    );
                }
            }
        }
    } else {
        *black_streak = 0;
    }
    Ok(())
}

/// Sumber frame GDI: memegang handle + buffer, menghasilkan RGBA rapat.
///
/// Hanya ada di Windows. Tidak ada stub untuk platform lain karena satu-satunya
/// pemanggil (`screen::windows`) juga hanya dikompilasi di Windows.
#[cfg(target_os = "windows")]
pub struct GdiCapture {
    dalam: Handle,
    /// Buffer RGBA yang dipakai ulang antar frame (satu alokasi per sesi).
    rgba: Vec<u8>,
    source_name: Option<String>,
    geometry_checked: std::time::Instant,
    black_streak: u8,
    last_warn: Option<std::time::Instant>,
}

#[cfg(target_os = "windows")]
impl GdiCapture {
    /// Buka capture untuk satu perangkat tampilan.
    ///
    /// `nama_perangkat` adalah nama GDI seperti `\\.\\DISPLAY1` — harus nama
    /// yang dikembalikan enumerasi monitor, karena `CreateDCW` menolak nama
    /// karangan dan mengembalikan DC null. Untuk VM headless / RDP tanpa
    /// monitor, fallback GetDC(0) otomatis dicoba.
    pub fn baru(nama_perangkat: &str, width: usize, height: usize) -> Result<Self, String> {
        crate::desktop_geometry::init_thread_dpi();
        // Kalau caller kasih 0 karena list_displays kosong (VM tanpa monitor),
        // biarin — Handle::baru akan pakai GetSystemMetrics untuk tentukan ukuran.
        let (w, h) = if width == 0 || height == 0 {
            // Coba baca ukuran virtual screen dulu
            (0, 0)
        } else {
            (width, height)
        };
        let dalam = Handle::baru(nama_perangkat, w, h)?;
        let width = dalam.width as usize;
        let height = dalam.height as usize;
        Ok(Self {
            dalam,
            rgba: Vec::with_capacity(width * height * 4),
            source_name: crate::desktop_geometry::monitor_rect(nama_perangkat)
                .map(|_| nama_perangkat.to_owned()),
            geometry_checked: std::time::Instant::now(),
            black_streak: 0,
            last_warn: None,
        })
    }
    pub fn capture_rect(&self) -> crate::desktop_geometry::CaptureRect {
        self.dalam.rect
    }

    /// Lebar frame saat sumber dibuka; perubahan geometri meminta restart.
    pub fn width(&self) -> usize {
        self.dalam.width as usize
    }

    /// Tinggi frame saat sumber dibuka.
    pub fn height(&self) -> usize {
        self.dalam.height as usize
    }

    /// Ambil satu frame; kembalikan `(rgba, width, height)`.
    ///
    /// Slice meminjam buffer internal dan sah sampai panggilan `grab`
    /// berikutnya — cukup untuk satu kali encode, dan menghindari salinan
    /// tambahan per frame.
    pub fn grab(&mut self) -> Result<(&[u8], usize, usize), String> {
        if self.geometry_checked.elapsed() >= std::time::Duration::from_millis(500) {
            self.geometry_checked = std::time::Instant::now();
            let current = match &self.source_name {
                Some(name) => crate::desktop_geometry::monitor_rect(name),
                None => crate::desktop_geometry::virtual_rect(),
            };
            if current != Some(self.dalam.rect) {
                return Err("geometri desktop berubah; buka ulang capture".into());
            }
        }
        grab_pixels(
            &mut self.dalam,
            self.source_name.as_deref().unwrap_or(""),
            &mut self.rgba,
            &mut self.black_streak,
        )?;
        // Sampel pojok bukan bukti seluruh desktop hitam atau sesi terkunci.
        if self.rgba.len() >= 4 && self.rgba.iter().take(100).all(|&b| b == 0) {
            // Cek 100 byte pertama saja — cepat, cukup untuk deteksi.
            // Log hanya sekali per 5 detik biar tidak spam.
            let now = std::time::Instant::now();
            if self
                .last_warn
                .is_none_or(|last| now.duration_since(last).as_secs() >= 5)
            {
                eprintln!("[xydesk-host] GDI: sampel pojok bernilai nol; bukan bukti seluruh frame hitam. Gunakan --capture-test untuk hitungan RGB seluruh frame");
                self.last_warn = Some(now);
            }
        }
        let w = self.width();
        let h = self.height();
        Ok((&self.rgba, w, h))
    }
}

/// Handle + buffer Win32 di balik [`GdiCapture`].
#[cfg(target_os = "windows")]
struct Handle {
    screen: windows::Win32::Graphics::Gdi::HDC,
    mem: windows::Win32::Graphics::Gdi::HDC,
    bmp: windows::Win32::Graphics::Gdi::HBITMAP,
    bmi: windows::Win32::Graphics::Gdi::BITMAPINFO,
    /// Buffer BGRA hasil `GetDIBits`, dipakai ulang antar frame.
    bgra: Vec<u8>,
    height: u32,
    width: u32,
    is_fallback: bool,
    rect: crate::desktop_geometry::CaptureRect,
}

#[cfg(target_os = "windows")]
impl Handle {
    fn baru(nama_perangkat: &str, width: usize, height: usize) -> Result<Self, String> {
        // Coba DISPLAY spesifik dulu, kalau gagal fallback ke GetDC(0)
        match Self::baru_display(nama_perangkat, width, height) {
            Ok(h) => Ok(h),
            Err(e) => {
                eprintln!(
                    "[xydesk-host] GDI CreateDCW {nama_perangkat} gagal: {e} — fallback GetDC(0) virtual screen"
                );
                Self::baru_fallback(nama_perangkat, width, height)
            }
        }
    }

    fn baru_display(nama_perangkat: &str, _width: usize, _height: usize) -> Result<Self, String> {
        use windows::core::PCWSTR;
        use windows::Win32::Graphics::Gdi::{
            CreateCompatibleBitmap, CreateCompatibleDC, CreateDCW, BITMAPINFO, BITMAPINFOHEADER,
        };

        // Kalau width/height 0 (list_displays kosong), fallback langsung
        if nama_perangkat.is_empty() {
            return Self::baru_fallback(nama_perangkat, 0, 0);
        }

        let rect = crate::desktop_geometry::monitor_rect(nama_perangkat)
            .ok_or("monitor GDI tidak ditemukan")?;
        let (width, height) = (rect.width as usize, rect.height as usize);
        let dev: Vec<u16> = nama_perangkat
            .encode_utf16()
            .chain(std::iter::once(0))
            .collect();
        let driver: Vec<u16> = "DISPLAY".encode_utf16().chain(std::iter::once(0)).collect();

        unsafe {
            let screen: windows::Win32::Graphics::Gdi::HDC = CreateDCW(
                PCWSTR(driver.as_ptr()),
                PCWSTR(dev.as_ptr()),
                PCWSTR::null(),
                None,
            );
            if screen.is_invalid() {
                return Err(format!("CreateDCW gagal untuk {nama_perangkat}"));
            }
            let mem: windows::Win32::Graphics::Gdi::HDC = CreateCompatibleDC(Some(screen));
            if mem.is_invalid() {
                let lepas = Self {
                    screen,
                    mem,
                    bmp: windows::Win32::Graphics::Gdi::HBITMAP::default(),
                    bmi: BITMAPINFO::default(),
                    bgra: Vec::new(),
                    height: 0,
                    width: 0,
                    is_fallback: false,
                    rect,
                };
                drop(lepas);
                return Err("CreateCompatibleDC gagal".to_string());
            }
            let bmp = CreateCompatibleBitmap(screen, width as i32, height as i32);
            if bmp.is_invalid() {
                let lepas = Self {
                    screen,
                    mem,
                    bmp,
                    bmi: BITMAPINFO::default(),
                    bgra: Vec::new(),
                    height: 0,
                    width: 0,
                    is_fallback: false,
                    rect,
                };
                drop(lepas);
                return Err(format!("CreateCompatibleBitmap {width}x{height} gagal"));
            }
            let _sebelumnya = SelectObject(mem, windows::Win32::Graphics::Gdi::HGDIOBJ(bmp.0));

            let mut bmi: BITMAPINFO = std::mem::zeroed();
            bmi.bmiHeader.biSize = std::mem::size_of::<BITMAPINFOHEADER>() as u32;
            bmi.bmiHeader.biWidth = width as i32;
            bmi.bmiHeader.biHeight = -(height as i32);
            bmi.bmiHeader.biPlanes = 1;
            bmi.bmiHeader.biBitCount = 32;
            bmi.bmiHeader.biCompression = 0;

            Ok(Self {
                screen,
                mem,
                bmp,
                bmi,
                bgra: vec![0u8; width * height * 4],
                height: height as u32,
                width: width as u32,
                is_fallback: false,
                rect,
            })
        }
    }

    fn baru_fallback(nama_perangkat: &str, _width: usize, _height: usize) -> Result<Self, String> {
        use windows::Win32::Foundation::HWND;
        use windows::Win32::Graphics::Gdi::{
            CreateCompatibleBitmap, CreateCompatibleDC, GetDC, BITMAPINFO, BITMAPINFOHEADER,
        };

        unsafe {
            let rect = crate::desktop_geometry::monitor_rect(nama_perangkat)
                .or_else(crate::desktop_geometry::virtual_rect)
                .ok_or("geometri desktop tidak tersedia")?;
            let (w, h) = (rect.width as usize, rect.height as usize);

            let screen = GetDC(Some(HWND::default()));
            if screen.is_invalid() {
                return Err("fallback GetDC(0) gagal — tidak ada desktop".to_string());
            }
            let mem = CreateCompatibleDC(Some(screen));
            if mem.is_invalid() {
                let _ = windows::Win32::Graphics::Gdi::ReleaseDC(Some(HWND::default()), screen);
                return Err("fallback CreateCompatibleDC gagal".to_string());
            }
            let bmp = CreateCompatibleBitmap(screen, w as i32, h as i32);
            if bmp.is_invalid() {
                let _ = windows::Win32::Graphics::Gdi::ReleaseDC(Some(HWND::default()), screen);
                let _ = windows::Win32::Graphics::Gdi::DeleteDC(mem);
                return Err(format!("fallback CreateCompatibleBitmap {w}x{h} gagal"));
            }
            let _ = SelectObject(mem, windows::Win32::Graphics::Gdi::HGDIOBJ(bmp.0));

            let mut bmi: BITMAPINFO = std::mem::zeroed();
            bmi.bmiHeader.biSize = std::mem::size_of::<BITMAPINFOHEADER>() as u32;
            bmi.bmiHeader.biWidth = w as i32;
            bmi.bmiHeader.biHeight = -(h as i32);
            bmi.bmiHeader.biPlanes = 1;
            bmi.bmiHeader.biBitCount = 32;
            bmi.bmiHeader.biCompression = 0;

            eprintln!("[xydesk-host] GDI fallback aktif: {w}x{h} via GetDC(0) — cocok untuk VM/RDP tanpa DISPLAY spesifik");
            Ok(Self {
                screen,
                mem,
                bmp,
                bmi,
                bgra: vec![0u8; w * h * 4],
                height: h as u32,
                width: w as u32,
                is_fallback: true,
                rect,
            })
        }
    }

    /// Satu putaran BitBlt + GetDIBits; kembalikan buffer BGRA rapat.
    fn ambil(&mut self) -> Result<&[u8], String> {
        use windows::Win32::Graphics::Gdi::{BitBlt, GetDIBits, DIB_RGB_COLORS, SRCCOPY};

        unsafe {
            if let Err(e) = BitBlt(
                self.mem,
                0,
                0,
                self.bmi.bmiHeader.biWidth,
                self.height as i32,
                Some(self.screen),
                if self.is_fallback { self.rect.left } else { 0 },
                if self.is_fallback { self.rect.top } else { 0 },
                SRCCOPY,
            ) {
                return Err(format!(
                    "BitBlt gagal (layar terkunci, secure desktop, RDP disconnected, atau VM tanpa console): {e}"
                ));
            }
            let baris = GetDIBits(
                self.mem,
                self.bmp,
                0,
                self.height,
                Some(self.bgra.as_mut_ptr().cast()),
                &mut self.bmi,
                DIB_RGB_COLORS,
            );
            if baris == 0 {
                return Err("GetDIBits gagal (format bitmap tidak didukung / DC lepas)".to_string());
            }
            if baris as u32 != self.height {
                return Err(format!(
                    "GetDIBits hanya mengisi {baris} dari {} baris — layar berubah ukuran?",
                    self.height
                ));
            }
        }
        let cursor_result = crate::native_cursor::draw_bgra(
            &mut self.bgra,
            self.width as usize,
            self.height as usize,
            self.rect,
        );
        static CURSOR_ERROR: std::sync::atomic::AtomicBool =
            std::sync::atomic::AtomicBool::new(false);
        if let Err(error) = cursor_result {
            if !CURSOR_ERROR.swap(true, std::sync::atomic::Ordering::Relaxed) {
                eprintln!("[cursor] gagal menggambar pointer Windows: {error}");
            }
        } else {
            CURSOR_ERROR.store(false, std::sync::atomic::Ordering::Relaxed);
        }

        Ok(&self.bgra)
    }
}

#[cfg(target_os = "windows")]
impl PixelSource for Handle {
    fn rect(&self) -> crate::desktop_geometry::CaptureRect {
        self.rect
    }

    fn is_fallback(&self) -> bool {
        self.is_fallback
    }

    fn read_rgba(&mut self, rgba: &mut Vec<u8>) -> Result<(), String> {
        crate::pixfmt::bgra_to_rgba(self.ambil()?, rgba);
        Ok(())
    }

    fn switch_to_desktop(&mut self, name: &str) -> Result<(), String> {
        let replacement = Self::baru_fallback(name, 0, 0)?;
        *self = replacement;
        Ok(())
    }
}

/// Pelepas handle GDI.
#[cfg(target_os = "windows")]
impl Drop for Handle {
    fn drop(&mut self) {
        use windows::Win32::Foundation::HWND;
        use windows::Win32::Graphics::Gdi::{DeleteDC, DeleteObject, ReleaseDC};
        unsafe {
            // Bitmap masih terseleksi pada mem DC. Lepaskan DC dahulu;
            // DeleteObject pada bitmap yang masih dipilih dapat gagal/bocor.
            if !self.mem.is_invalid() {
                let _ = DeleteDC(self.mem);
            }
            if !self.bmp.is_invalid() {
                let _ = DeleteObject(windows::Win32::Graphics::Gdi::HGDIOBJ(self.bmp.0));
            }
            if !self.screen.is_invalid() {
                if self.is_fallback {
                    let _ = ReleaseDC(Some(HWND::default()), self.screen);
                } else {
                    let _ = DeleteDC(self.screen);
                }
            }
        }
    }
}

#[cfg(target_os = "windows")]
use windows::Win32::Graphics::Gdi::SelectObject;

#[cfg(test)]
mod tests {
    use super::*;
    use crate::desktop_geometry::CaptureRect;

    struct Source {
        rect: CaptureRect,
        next_rect: CaptureRect,
        fallback: bool,
        fail_switch: bool,
        fail_read: bool,
        empty_read: bool,
        dark: bool,
        reads: usize,
        switches: usize,
    }

    impl Source {
        fn new() -> Self {
            let rect = CaptureRect {
                left: -2,
                top: 0,
                width: 2,
                height: 2,
            };
            Self {
                rect,
                next_rect: rect,
                fallback: false,
                fail_switch: false,
                fail_read: false,
                empty_read: false,
                dark: true,
                reads: 0,
                switches: 0,
            }
        }
    }

    impl PixelSource for Source {
        fn rect(&self) -> CaptureRect {
            self.rect
        }
        fn is_fallback(&self) -> bool {
            self.fallback
        }
        fn read_rgba(&mut self, rgba: &mut Vec<u8>) -> Result<(), String> {
            self.reads += 1;
            if self.fallback && self.fail_read {
                return Err("DC lepas".into());
            }
            if self.fallback && self.empty_read {
                rgba.clear();
                return Ok(());
            }
            let value = if self.fallback || !self.dark { 80 } else { 0 };
            *rgba = vec![value; self.rect.width as usize * self.rect.height as usize * 4];
            Ok(())
        }
        fn switch_to_desktop(&mut self, _name: &str) -> Result<(), String> {
            self.switches += 1;
            if self.fail_switch {
                return Err("desktop DC tidak tersedia".into());
            }
            self.fallback = true;
            self.rect = self.next_rect;
            Ok(())
        }
    }

    #[test]
    fn frame_ke_15_mengambil_piksel_baru_bukan_buffer_kosong() {
        let mut source = Source::new();
        let mut rgba = Vec::new();
        let mut streak = 0;
        for _ in 0..14 {
            grab_pixels(&mut source, "display", &mut rgba, &mut streak).unwrap();
            assert_eq!(rgba, vec![0; 16]);
            assert_eq!(source.switches, 0);
        }
        grab_pixels(&mut source, "display", &mut rgba, &mut streak).unwrap();
        assert_eq!(source.switches, 1);
        assert_eq!(source.reads, 16, "15 frame monitor + satu frame desktop DC");
        assert_eq!(rgba, vec![80; 16]);
        assert_eq!(streak, 0);
        // Frame transisi memenuhi kontrak RGBA dan konversi input NVENC.
        crate::pixfmt::validate_rgba(&rgba, 2, 2).unwrap();
        let mut nv12 = Vec::new();
        crate::pixfmt::rgba_to_nv12(&rgba, 2, 2, &mut nv12).unwrap();
        assert_eq!(nv12.len(), 6);
    }

    #[test]
    fn fallback_gagal_mempertahankan_frame_lama_yang_utuh() {
        let mut source = Source::new();
        source.fail_switch = true;
        let mut rgba = Vec::new();
        let mut streak = 14;
        grab_pixels(&mut source, "display", &mut rgba, &mut streak).unwrap();
        assert!(!source.fallback);
        assert_eq!(rgba, vec![0; 16]);
        assert_eq!(source.reads, 1);
        assert_eq!(source.switches, 1);
    }

    #[test]
    fn piksel_fallback_kosong_tidak_pernah_diteruskan_ke_encoder() {
        let mut source = Source::new();
        source.empty_read = true;
        let mut rgba = Vec::new();
        let mut streak = 14;
        assert!(grab_pixels(&mut source, "display", &mut rgba, &mut streak).is_err());
        assert_eq!(source.reads, 2);
    }

    #[test]
    fn baca_desktop_dc_gagal_bukan_sukses_dengan_piksel_monitor_lama() {
        let mut source = Source::new();
        source.fail_read = true;
        let mut rgba = Vec::new();
        let mut streak = 14;
        assert_eq!(
            grab_pixels(&mut source, "display", &mut rgba, &mut streak),
            Err("DC lepas".into())
        );
    }

    #[test]
    fn geometri_fallback_berubah_meminta_restart_pipeline() {
        for rect in [
            CaptureRect {
                left: 0,
                top: 0,
                width: 2,
                height: 2,
            },
            CaptureRect {
                left: -2,
                top: 0,
                width: 4,
                height: 2,
            },
        ] {
            let mut source = Source::new();
            source.next_rect = rect;
            let mut rgba = Vec::new();
            let mut streak = 14;
            let error = grab_pixels(&mut source, "display", &mut rgba, &mut streak).unwrap_err();
            assert!(error.contains("geometri berubah"));
            assert_eq!(source.reads, 1, "jangan encode memakai geometri input lama");
        }
    }

    #[test]
    fn frame_terang_memutus_streak_dan_desktop_dc_tidak_diulang() {
        let mut source = Source::new();
        source.dark = false;
        let mut rgba = Vec::new();
        let mut streak = 14;
        grab_pixels(&mut source, "display", &mut rgba, &mut streak).unwrap();
        assert_eq!(streak, 0);
        assert_eq!(source.switches, 0);
        source.fallback = true;
        streak = 14;
        grab_pixels(&mut source, "display", &mut rgba, &mut streak).unwrap();
        assert_eq!(source.switches, 0);
    }

    #[test]
    fn konversi_warna_dilakukan_di_primitif_bukan_di_pemanggil() {
        let src = include_str!("gdi.rs");
        assert!(
            src.contains("crate::pixfmt::bgra_to_rgba"),
            "penukaran BGRA→RGBA harus terjadi di dalam grab()"
        );
    }
}
