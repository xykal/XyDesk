//! Virtual Xbox 360 (XInput) di host Windows.
//!
//! Pad fisik yang sudah terpasang (XInputGetState sukses) **tidak** ditimpa
//! virtual. Tanpa pad fisik, plugin ViGEmBus (`ViGEmClient.dll`) bila ada.
//! Laporan dari klien: opcode 0x0E.

use std::sync::Mutex;

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct GamepadReport {
    pub buttons: u16,
    pub lt: u8,
    pub rt: u8,
    pub lx: i16,
    pub ly: i16,
    pub rx: i16,
    pub ry: i16,
}

static PAD: Mutex<Option<Pad>> = Mutex::new(None);

/// Status pad virtual untuk ditampilkan ke pengguna.
///
/// Tanpa ini, host yang tidak punya ViGEmBus **menelan diam-diam** setiap
/// laporan gamepad: pengguna menekan tombol A di layar HP-nya dan tidak ada
/// apa pun yang terjadi di PC, tanpa satu pun penjelasan di aplikasi. Satu
/// baris di konsol host tidak terlihat oleh orang yang sedang memegang HP.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Status {
    /// Laporan gamepad benar-benar sampai ke Windows.
    pub available: bool,
    /// Kenapa tidak, dalam kalimat yang boleh dibaca pengguna. Kosong bila
    /// tersedia.
    pub reason: &'static str,
}

/// Alasan baku. Dipisah sebagai fungsi murni supaya bisa diuji di Linux —
/// kombinasi driver yang ada/tidak ada mustahil dirangkai di sandbox.
pub fn reason_text(windows: bool, physical: bool, virt: bool) -> Status {
    if !windows {
        return Status {
            available: false,
            reason: "host bukan Windows",
        };
    }
    if physical {
        // Pad fisik yang sudah menancap di PC tidak ditimpa: dua pad di slot
        // yang sama membuat game membaca dua perangkat yang saling melawan.
        return Status {
            available: false,
            reason: "ada gamepad fisik di PC — pad virtual dilewati",
        };
    }
    if virt {
        return Status {
            available: true,
            reason: "",
        };
    }
    Status {
        available: false,
        reason: "ViGEmBus belum terpasang di PC",
    }
}

/// Status pad saat ini. Membuka pad bila belum pernah dibuka, supaya
/// jawabannya benar sejak laporan pertama belum dikirim.
pub fn status() -> Status {
    let mut g = match PAD.lock() {
        Ok(g) => g,
        Err(_) => {
            return Status {
                available: false,
                reason: "status gamepad tidak terbaca",
            }
        }
    };
    if g.is_none() {
        *g = Some(Pad::open());
    }
    g.as_ref()
        .map(|p| p.status())
        .unwrap_or(reason_text(cfg!(target_os = "windows"), false, false))
}

/// Status gamepad untuk blok META yang dikirim ke klien.
pub fn telemetry() -> serde_json::Value {
    let s = status();
    serde_json::json!({ "available": s.available, "reason": s.reason })
}

struct Pad {
    #[cfg(target_os = "windows")]
    virt: Option<Vigem>,
    physical: bool,
}

pub fn submit(r: GamepadReport) {
    let mut g = match PAD.lock() {
        Ok(g) => g,
        Err(_) => return,
    };
    if g.is_none() {
        *g = Some(Pad::open());
    }
    if let Some(p) = g.as_mut() {
        p.apply(r);
    }
}

impl Pad {
    fn open() -> Self {
        let physical = physical_present();
        #[cfg(target_os = "windows")]
        {
            let virt = if physical { None } else { Vigem::connect() };
            if virt.is_some() {
                eprintln!("[xydesk-host] virtual XInput (ViGEm) aktif");
            } else if physical {
                eprintln!("[xydesk-host] XInput fisik ada — virtual dilewati");
            } else {
                eprintln!(
                    "[xydesk-host] ViGEmClient.dll tidak ada — pasang ViGEmBus untuk pad virtual"
                );
            }
            Self { virt, physical }
        }
        #[cfg(not(target_os = "windows"))]
        {
            let _ = physical;
            Self { physical: false }
        }
    }

    fn status(&self) -> Status {
        #[cfg(target_os = "windows")]
        {
            reason_text(true, self.physical, self.virt.is_some())
        }
        #[cfg(not(target_os = "windows"))]
        {
            let _ = self.physical;
            reason_text(false, false, false)
        }
    }

    fn apply(&mut self, r: GamepadReport) {
        #[cfg(target_os = "windows")]
        if let Some(v) = &self.virt {
            v.update(r);
        }
        let _ = r;
        let _ = self.physical;
    }
}

fn physical_present() -> bool {
    #[cfg(target_os = "windows")]
    {
        xinput_any_slot()
    }
    #[cfg(not(target_os = "windows"))]
    {
        false
    }
}

#[cfg(target_os = "windows")]
fn xinput_any_slot() -> bool {
    use windows::core::w;
    use windows::Win32::Foundation::HMODULE;
    use windows::Win32::System::LibraryLoader::{GetProcAddress, LoadLibraryW};
    #[repr(C)]
    struct State {
        packet: u32,
        report: [u8; 12],
    }
    type GetState = unsafe extern "system" fn(u32, *mut State) -> u32;
    unsafe {
        let lib: HMODULE = match LoadLibraryW(w!("xinput1_4.dll"))
            .or_else(|_| LoadLibraryW(w!("xinput1_3.dll")))
        {
            Ok(h) => h,
            Err(_) => return false,
        };
        if lib.is_invalid() {
            return false;
        }
        let proc = GetProcAddress(lib, windows::core::s!("XInputGetState"));
        let Some(proc) = proc else { return false };
        let get: GetState = std::mem::transmute(proc);
        let mut st = State {
            packet: 0,
            report: [0; 12],
        };
        (0..4).any(|i| get(i, &mut st) == 0)
    }
}

#[cfg(target_os = "windows")]
struct Vigem {
    client: *mut std::ffi::c_void,
    target: *mut std::ffi::c_void,
    update: UpdateFn,
}

// ViGEm handle hanya disentuh dari thread injeksi (Mutex).
#[cfg(target_os = "windows")]
unsafe impl Send for Vigem {}

#[cfg(target_os = "windows")]
type UpdateFn =
    unsafe extern "C" fn(*mut std::ffi::c_void, *mut std::ffi::c_void, *const u8) -> u32;

#[cfg(target_os = "windows")]
impl Vigem {
    fn connect() -> Option<Self> {
        use windows::core::w;
        use windows::Win32::Foundation::HMODULE;
        use windows::Win32::System::LibraryLoader::{GetProcAddress, LoadLibraryW};
        unsafe {
            let lib: HMODULE = LoadLibraryW(w!("ViGEmClient.dll")).ok()?;
            if lib.is_invalid() {
                return None;
            }
            let gpa = |n: windows::core::PCSTR| GetProcAddress(lib, n);
            let alloc: unsafe extern "C" fn() -> *mut std::ffi::c_void =
                std::mem::transmute(gpa(windows::core::s!("vigem_alloc"))?);
            let connect: unsafe extern "C" fn(*mut std::ffi::c_void) -> u32 =
                std::mem::transmute(gpa(windows::core::s!("vigem_connect"))?);
            let talloc: unsafe extern "C" fn() -> *mut std::ffi::c_void =
                std::mem::transmute(gpa(windows::core::s!("vigem_target_x360_alloc"))?);
            let add: unsafe extern "C" fn(*mut std::ffi::c_void, *mut std::ffi::c_void) -> u32 =
                std::mem::transmute(gpa(windows::core::s!("vigem_target_add"))?);
            let update: UpdateFn =
                std::mem::transmute(gpa(windows::core::s!("vigem_target_x360_update"))?);
            let client = alloc();
            if client.is_null() {
                return None;
            }
            if connect(client) & 0x8000_0000 != 0 {
                return None;
            }
            let target = talloc();
            if target.is_null() {
                return None;
            }
            if add(client, target) & 0x8000_0000 != 0 {
                return None;
            }
            Some(Self {
                client,
                target,
                update,
            })
        }
    }

    fn update(&self, r: GamepadReport) {
        let mut buf = [0u8; 12];
        buf[0] = (r.buttons & 0xff) as u8;
        buf[1] = (r.buttons >> 8) as u8;
        buf[2] = r.lt;
        buf[3] = r.rt;
        buf[4..6].copy_from_slice(&r.lx.to_le_bytes());
        buf[6..8].copy_from_slice(&r.ly.to_le_bytes());
        buf[8..10].copy_from_slice(&r.rx.to_le_bytes());
        buf[10..12].copy_from_slice(&r.ry.to_le_bytes());
        unsafe {
            let _ = (self.update)(self.client, self.target, buf.as_ptr());
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_report_idle() {
        assert_eq!(GamepadReport::default().buttons, 0);
    }

    #[test]
    fn tanpa_windows_gamepad_tidak_pernah_tersedia() {
        let s = reason_text(false, false, false);
        assert!(!s.available);
        assert_eq!(s.reason, "host bukan Windows");
    }

    #[test]
    fn pad_fisik_mengalahkan_pad_virtual_dan_alasannya_dijelaskan() {
        let s = reason_text(true, true, true);
        assert!(!s.available);
        assert!(s.reason.contains("fisik"));
    }

    #[test]
    fn tanpa_vigem_alasannya_menyebut_apa_yang_harus_dipasang() {
        let s = reason_text(true, false, false);
        assert!(!s.available);
        assert!(s.reason.contains("ViGEmBus"));
    }

    #[test]
    fn dengan_vigem_tersedia_dan_tanpa_alasan() {
        let s = reason_text(true, false, true);
        assert!(s.available);
        assert_eq!(s.reason, "");
    }

    #[test]
    fn telemetri_selalu_membawa_dua_kunci() {
        let t = telemetry();
        assert!(t.get("available").is_some());
        assert!(t.get("reason").is_some());
        // Di Linux (CI dan sandbox) jawabannya pasti tidak tersedia.
        assert_eq!(t["available"], serde_json::json!(false));
    }
}
