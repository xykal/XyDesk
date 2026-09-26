//! Kepemimpinan host lintas sesi: satu LEADER per mesin, sisanya STANDBY.
//!
//! Kasus lapangan (VPS GitHub Actions): host auto-login di sesi konsol
//! (akun layanan runneradmin) sementara pemilik bekerja di sesi RDP-nya
//! sendiri. Windows melarang proses menangkap layar sesi user lain, jadi
//! satu-satunya jalan yang benar: host jalan di TIAP sesi interaktif
//! (installer mendaftarkan HKLM Run) dan instance di sesi pemegang layar
//! aktif menjadi leader — memegang capture, signaling, dan control API —
//! sementara instance lain standby menunggu takeover. Serah terima lewat
//! mutex bernama + berkas permintaan stepdown: senyap, tanpa kedip pindah
//! sesi, tanpa schtasks.
//!
//! Logika keputusan sengaja fungsi murni biar bisa diuji unit di Linux;
//! mekanisme Windows (mutex + berkas) ada di belakang cfg.

/// Keputusan startup murni: instance boleh langsung jadi leader hanya bila
/// ia hidup di sesi yang memegang layar aktif.
pub fn langsung_leader(sessiku: u32, sesi_aktif: u32) -> bool {
    sessiku == sesi_aktif
}

/// Promosi dari standby: boleh hanya bila sesi ini pemegang layar DAN mutex
/// kepemimpinan berhasil direbut (leader lama sudah turun).
pub fn boleh_promosi(sessiku: u32, sesi_aktif: u32, mutex_dapat: bool) -> bool {
    sessiku == sesi_aktif && mutex_dapat
}

/// Leader wajib turun bila ada permintaan stepdown dari sesi pemegang layar
/// aktif sementara leader sendiri tidak di sesi itu.
pub fn harus_turun(sessiku: u32, sesi_aktif: u32, peminta: Option<u32>) -> bool {
    peminta.is_some_and(|p| p == sesi_aktif && sessiku != sesi_aktif)
}

/// Rebut mutex kepemimpinan mesin ini. `false` = instance lain sedang leader.
pub fn coba_mutex() -> bool {
    #[cfg(target_os = "windows")]
    {
        return mech::coba_mutex();
    }
    #[cfg(not(target_os = "windows"))]
    true
}

/// Lepaskan mutex kepemimpinan (saat turun jabatan / keluar).
pub fn lepas_mutex() {
    #[cfg(target_os = "windows")]
    mech::lepas_mutex();
}

/// Tulis permintaan stepdown (dibaca leader di tick loop-nya).
pub fn minta_stepdown(sesi_peminta: u32) {
    #[cfg(target_os = "windows")]
    mech::tulis_stepdown(sesi_peminta);
    #[cfg(not(target_os = "windows"))]
    let _ = sesi_peminta;
}

/// Sesi yang meminta stepdown, bila ada permintaan tertulis.
pub fn baca_stepdown() -> Option<u32> {
    #[cfg(target_os = "windows")]
    {
        return mech::baca_stepdown();
    }
    #[cfg(not(target_os = "windows"))]
    None
}

/// Bersihkan berkas permintaan setelah kepemimpinan pindah tangan.
pub fn hapus_stepdown() {
    #[cfg(target_os = "windows")]
    mech::hapus_stepdown();
}

#[cfg(target_os = "windows")]
mod mech {
    use std::sync::atomic::{AtomicIsize, Ordering};

    static MUTEX: AtomicIsize = AtomicIsize::new(0);

    pub fn coba_mutex() -> bool {
        use windows::core::w;
        use windows::Win32::Foundation::{CloseHandle, GetLastError, ERROR_ALREADY_EXISTS};
        use windows::Win32::System::Threading::CreateMutexW;
        unsafe {
            match CreateMutexW(None, false, w!("Local\\XyDesk-Host-Leader")) {
                Ok(h) => {
                    if GetLastError() == ERROR_ALREADY_EXISTS {
                        let _ = CloseHandle(h);
                        false
                    } else {
                        MUTEX.store(h.0 as isize, Ordering::SeqCst);
                        true
                    }
                }
                Err(_) => false,
            }
        }
    }

    pub fn lepas_mutex() {
        use windows::Win32::Foundation::{CloseHandle, HANDLE};
        let h = MUTEX.swap(0, Ordering::SeqCst);
        if h != 0 {
            unsafe {
                let _ = CloseHandle(HANDLE(h as _));
            }
        }
    }

    fn stepdown_path() -> std::path::PathBuf {
        crate::identity::config_dir().join("stepdown.json")
    }

    pub fn tulis_stepdown(sesi: u32) {
        let _ = std::fs::write(stepdown_path(), format!("{{\"sesi\":{sesi}}}"));
    }

    pub fn baca_stepdown() -> Option<u32> {
        let body = std::fs::read_to_string(stepdown_path()).ok()?;
        let v: serde_json::Value = serde_json::from_str(&body).ok()?;
        v.get("sesi").and_then(|s| s.as_u64()).map(|s| s as u32)
    }

    pub fn hapus_stepdown() {
        let _ = std::fs::remove_file(stepdown_path());
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn leader_hanya_di_sesi_pemegang_layar() {
        assert!(langsung_leader(2, 2));
        assert!(!langsung_leader(1, 2));
        // Di luar Windows keduanya u32::MAX — tetap leader (tidak ada sesi).
        assert!(langsung_leader(u32::MAX, u32::MAX));
    }

    #[test]
    fn promosi_butuh_sesi_benar_dan_mutex() {
        assert!(boleh_promosi(2, 2, true));
        assert!(!boleh_promosi(2, 2, false)); // leader lama belum turun
        assert!(!boleh_promosi(1, 2, true)); // sesi salah: jangan rampas
    }

    #[test]
    fn turun_hanya_untuk_peminta_dari_layar_aktif() {
        // Leader di konsol (1), layar aktif RDP (2), permintaan dari 2 → turun.
        assert!(harus_turun(1, 2, Some(2)));
        // Permintaan dari sesi yang BUKAN pemegang layar → diabaikan.
        assert!(!harus_turun(1, 2, Some(3)));
        // Leader sudah di sesi aktif → tidak ada yang boleh menggusur.
        assert!(!harus_turun(2, 2, Some(3)));
        assert!(!harus_turun(1, 2, None));
    }
}
