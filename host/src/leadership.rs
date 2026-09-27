//! Satu leader per akun Windows, lintas sesi console/RDP akun yang sama.
//!
//! File lock eksklusif di profil identitas memakai share_mode(0), sehingga
//! berlaku lintas sesi tanpa namespace Global atau ACL lintas user. Direktori
//! XYDESK_HOME bila disetel harus privat untuk akun pemilik, seperti password
//! dan grant yang sudah disimpan di direktori itu.

/// Keputusan startup murni: instance boleh langsung jadi leader hanya bila
/// ia hidup di sesi yang memegang layar aktif.
pub fn langsung_leader(sessiku: u32, sesi_aktif: u32) -> bool {
    sessiku == sesi_aktif
}

/// Jangan mengevaluasi akuisisi yang memiliki efek samping sebelum eligibility.
/// Guard generik membuat urutan akuisisi/pelepasan dapat diuji tanpa Win32.
fn acquire_if_eligible<T>(
    sessiku: u32,
    sesi_aktif: u32,
    acquire: impl FnOnce() -> Option<T>,
) -> Option<T> {
    if langsung_leader(sessiku, sesi_aktif) {
        acquire()
    } else {
        None
    }
}

/// File membuka slot sampai guard dilepas, termasuk jalur keluar dengan error.
pub struct LeaderGuard {
    #[cfg(target_os = "windows")]
    _lock: std::fs::File,
}

/// Identitas berasal dari WTS, bukan label jaringan yang dikirim client.
pub fn same_account(user: &str, domain: &str, owner: &str, owner_domain: &str) -> bool {
    !user.is_empty()
        && !owner.is_empty()
        && !domain.is_empty()
        && !owner_domain.is_empty()
        && user.eq_ignore_ascii_case(owner)
        && domain.eq_ignore_ascii_case(owner_domain)
}

/// Ambil slot hanya untuk sesi yang berhak. `None` juga berarti slot sibuk
/// atau Win32 menolak pembuatan objek; pemanggil tetap standby.
pub fn try_acquire(sessiku: u32, sesi_aktif: u32) -> Option<LeaderGuard> {
    #[cfg(target_os = "windows")]
    if sessiku == u32::MAX || sesi_aktif == u32::MAX {
        return None;
    }
    acquire_if_eligible(sessiku, sesi_aktif, || {
        #[cfg(target_os = "windows")]
        {
            mech::try_acquire()
        }
        #[cfg(not(target_os = "windows"))]
        {
            Some(LeaderGuard {})
        }
    })
}

/// Membatalkan auth/connect yang macet ketika sesi akun yang aktif berubah.
pub async fn until_session_changes() {
    loop {
        tokio::time::sleep(std::time::Duration::from_secs(2)).await;
        crate::screen::evaluate_sessions_now();
        if crate::screen::proc_session() != crate::screen::active_session() {
            return;
        }
    }
}

#[cfg(target_os = "windows")]
mod mech {
    use super::LeaderGuard;
    pub fn try_acquire() -> Option<LeaderGuard> {
        let dir = crate::identity::config_dir();
        if let Err(error) = std::fs::create_dir_all(&dir) {
            eprintln!("[leader] direktori identitas tidak tersedia: {error}");
            return None;
        }
        match try_acquire_path(&dir.join("leader.lock")) {
            Ok(guard) => guard,
            Err(error) => {
                eprintln!("[leader] file lock tidak dapat dibuka: {error}");
                None
            }
        }
    }

    pub(super) fn try_acquire_path(path: &std::path::Path) -> std::io::Result<Option<LeaderGuard>> {
        use std::os::windows::fs::OpenOptionsExt;
        match std::fs::OpenOptions::new()
            .read(true)
            .write(true)
            .create(true)
            .truncate(false)
            .share_mode(0)
            .open(path)
        {
            Ok(file) => Ok(Some(LeaderGuard { _lock: file })),
            Err(error) if matches!(error.raw_os_error(), Some(32 | 33)) => Ok(None),
            Err(error) => Err(error),
        }
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
    fn standby_tidak_memanggil_akuisisi_mutex() {
        let guard = acquire_if_eligible(1, 2, || -> Option<()> {
            panic!("standby tidak boleh menyentuh mutex");
        });
        assert!(guard.is_none());
    }

    #[test]
    fn sesi_aktif_mencoba_sekali_dan_menghormati_slot_sibuk() {
        let mut calls = 0;
        let guard = acquire_if_eligible(2, 2, || -> Option<()> {
            calls += 1;
            None
        });
        assert!(guard.is_none());
        assert_eq!(calls, 1);
    }

    #[test]
    fn standby_dapat_promosi_setelah_sesi_berubah_dan_guard_dilepas() {
        use std::cell::Cell;
        struct Slot<'a>(&'a Cell<bool>);
        impl Drop for Slot<'_> {
            fn drop(&mut self) {
                self.0.set(false);
            }
        }
        let held = Cell::new(false);
        let acquire = || {
            if held.replace(true) {
                None
            } else {
                Some(Slot(&held))
            }
        };
        assert!(acquire_if_eligible(1, 2, acquire).is_none());
        assert!(
            !held.get(),
            "standby tidak boleh meninggalkan slot terambil"
        );
        let first = acquire_if_eligible(1, 1, acquire).expect("promosi pertama");
        assert!(held.get());
        assert!(acquire_if_eligible(1, 1, acquire).is_none());
        drop(first);
        assert!(!held.get());
        let second = acquire_if_eligible(1, 1, acquire).expect("slot bisa dipakai ulang");
        drop(second);
        assert!(!held.get());
    }

    #[test]
    fn guard_dilepas_saat_pemilik_keluar_dengan_error() {
        use std::cell::Cell;
        struct Guard<'a>(&'a Cell<usize>);
        impl Drop for Guard<'_> {
            fn drop(&mut self) {
                self.0.set(self.0.get() + 1);
            }
        }
        fn run(drops: &Cell<usize>) -> Result<(), &'static str> {
            let _leader = acquire_if_eligible(2, 2, || Some(Guard(drops))).ok_or("slot sibuk")?;
            Err("startup gagal")
        }
        let drops = Cell::new(0);
        assert_eq!(run(&drops), Err("startup gagal"));
        assert_eq!(drops.get(), 1);
    }

    #[cfg(target_os = "windows")]
    #[test]
    fn guard_file_win32_eksklusif_dan_dapat_diambil_ulang() {
        let path = std::env::temp_dir().join(format!(
            "xydesk-leader-test-{}-{}.lock",
            std::process::id(),
            rand::random::<u64>()
        ));
        let first = mech::try_acquire_path(&path).unwrap().unwrap();
        assert!(mech::try_acquire_path(&path).unwrap().is_none());
        drop(first);
        let second = mech::try_acquire_path(&path).unwrap().unwrap();
        drop(second);
        std::fs::remove_file(path).unwrap();
    }

    #[test]
    fn pemilik_sesi_harus_akun_dan_domain_yang_sama() {
        assert!(same_account("Alice", "PC", "alice", "pc"));
        assert!(!same_account("alice", "domain-a", "alice", "domain-b"));
        assert!(!same_account("bob", "PC", "alice", "PC"));
        assert!(!same_account("", "PC", "", "PC"));
        assert!(!same_account("alice", "", "alice", ""));
    }
}
