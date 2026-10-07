//! Gerbang kehadiran: boleh atau tidak PC ini disambungkan saat tidak ada
//! siapa-siapa di depannya.
//!
//! ## Kenapa modul ini ada
//!
//! Sampai sekarang host menjawab `pair` dengan satu pertanyaan saja: apakah
//! passwordnya benar. Itu jawaban yang tepat untuk "siapa yang menyambung",
//! dan jawaban yang tidak pernah diminta untuk pertanyaan kedua: **apakah
//! sekarang waktunya**. Keduanya tidak sama. Password yang pernah dibagikan
//! ke rekan kerja untuk satu sore tetap berlaku pada pukul tiga pagi; laptop
//! kantor yang menyala di rumah bisa disambungkan oleh siapa pun yang pernah
//! menyalinnya, dan pemiliknya tidak akan pernah tahu — satu-satunya jejak
//! ada di log yang tidak dibaca siapa pun.
//!
//! Yang membuat pertanyaan ini pantas dijawab di sini, bukan di sisi client:
//! client mana pun bisa berbohong tentang keadaannya, sedangkan "ada orang
//! di depan PC ini" hanya bisa dinilai oleh PC itu sendiri.
//!
//! ## Tiga kebijakan, dan kenapa bawaannya yang paling longgar
//!
//! - [`Policy::Always`] — terima kapan saja. **Ini bawaannya**, dan itu
//!   disengaja: perilaku inilah yang sudah berjalan sejak rilis pertama.
//!   Memilih bawaan yang lebih ketat akan membuat setiap PC yang sudah
//!   terpasang mendadak tidak bisa disambungkan setelah pembaruan, justru
//!   dari jauh, justru saat pemiliknya tidak ada di sana untuk membetulkan.
//!   Pembaruan keamanan yang mengunci pemiliknya di luar rumahnya sendiri
//!   tidak akan dipercaya untuk pembaruan berikutnya.
//! - [`Policy::WhenWatched`] — hanya saat ada orang di depan PC, atau saat
//!   pemiliknya sudah memberi izin sementara sebelum pergi.
//! - [`Policy::Off`] — tolak semua, tanpa pengecualian. Saklar mati yang
//!   tidak bergantung pada siapa pun dan tidak bisa dilewati izin sementara;
//!   "tolak semua" yang punya pengecualian bukan "tolak semua".
//!
//! ## "Ada orang di depan PC"
//!
//! Dinilai dengan cara yang sama seperti [`crate::file_consent`]: panel host
//! memanggil `/status` sekitar sekali per detik, jadi panggilan terakhir yang
//! lebih baru dari [`WATCHER_FRESH_MS`] berarti panel sedang terbuka. Itu
//! bukan bukti ada manusia yang melihat layarnya — panel yang ditinggal
//! terbuka tetap dianggap ditunggui. Yang dijanjikan di sini memang hanya
//! sebatas itu, dan tidak lebih: kehadiran panel, bukan kehadiran orang.
//! Satu-satunya alternatif yang lebih kuat adalah mengandalkan status
//! kunci-layar Windows, yang tidak tersedia di semua konfigurasi dan akan
//! membuat kebijakan ini gagal diam-diam di sebagian PC — lebih buruk
//! daripada janji yang kecil tetapi jujur.
//!
//! ## Izin sementara: untuk pergi, bukan untuk selamanya
//!
//! Kasus nyatanya hampir selalu "saya akan keluar, nanti saya butuh masuk
//! dari HP". Karena itu [`Policy::WhenWatched`] punya katup: pemilik menekan
//! satu tombol sebelum pergi dan akses terbuka sampai batas waktu yang ia
//! pilih, maksimal [`MAX_GRANT_MS`]. Izin yang tidak pernah kedaluwarsa akan
//! dinyalakan sekali lalu dilupakan selamanya, dan kebijakannya kembali
//! menjadi [`Policy::Always`] dengan nama yang lebih menenangkan.
//!
//! Izin disimpan sebagai **waktu berakhir** (unix ms), bukan sisa durasi,
//! supaya host yang direstart tidak memperpanjangnya sendiri dengan memulai
//! hitungan dari nol. Konsekuensinya izin bergantung pada jam dinding yang
//! bisa dimundurkan; itu diterima dengan sadar, karena yang bisa memundurkan
//! jam PC sudah memegang PC-nya. Yang tetap dijaga: izin yang berakhir lebih
//! dari [`MAX_GRANT_MS`] di masa depan — berkas rusak, disunting tangan,
//! atau jam yang melompat jauh — dianggap **kedaluwarsa**, bukan dipercaya.
//!
//! ## Penolakan tidak boleh menghukum pemiliknya
//!
//! Koneksi yang ditolak di sini **bukan** tebakan password yang gagal, jadi
//! ia tidak dihitung oleh [`crate::pairguard`]. Kalau dihitung, pemilik yang
//! mencoba masuk tiga kali ke PC-nya sendiri yang sedang terkunci akan ikut
//! terkena lockout lima menit — dihukum karena kebijakan yang ia pasang
//! sendiri bekerja.
//!
//! Ke client, penolakan terlihat persis sama dengan password yang salah.
//! Membedakannya akan memberi tahu penebak bahwa ia menemukan PC yang nyata
//! dengan password yang benar dan hanya perlu menunggu waktu yang tepat.
//!
//! Keputusan dan penyimpanan dipisah: [`decide`] tidak menyentuh disk, jam,
//! maupun jaringan — seluruh perilakunya bisa diuji tanpa menunggu.

use std::path::{Path, PathBuf};

/// Batas "panel masih menonton" sejak `/status` terakhir. Sama dengan
/// [`crate::file_consent::WATCHER_FRESH_MS`] dan memang harus sama: dua
/// definisi berbeda untuk "ada yang menonton" di satu program hanya akan
/// membuat dua gerbangnya berbeda pendapat pada detik yang sama.
pub const WATCHER_FRESH_MS: u64 = 10_000;

/// Izin sementara terpanjang yang bisa diberikan sekaligus.
pub const MAX_GRANT_MS: u64 = 24 * 60 * 60 * 1000;

/// Kebijakan kehadiran.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Default)]
pub enum Policy {
    /// Terima koneksi kapan saja — perilaku sejak rilis pertama.
    #[default]
    Always,
    /// Terima hanya saat panel terbuka, atau saat izin sementara berlaku.
    WhenWatched,
    /// Tolak semua koneksi masuk.
    Off,
}

impl Policy {
    /// Satu kata, supaya berkasnya bisa dibaca dan diperbaiki dengan Notepad
    /// saat panel justru sedang tidak bisa dibuka.
    pub fn as_str(self) -> &'static str {
        match self {
            Policy::Always => "always",
            Policy::WhenWatched => "watched",
            Policy::Off => "off",
        }
    }

    /// `None` untuk kata yang tidak dikenal — pemanggil yang memutuskan apa
    /// artinya, dan di [`load_policy`] artinya "kembali ke bawaan".
    pub fn parse(text: &str) -> Option<Policy> {
        match text.trim().to_ascii_lowercase().as_str() {
            "always" => Some(Policy::Always),
            "watched" => Some(Policy::WhenWatched),
            "off" => Some(Policy::Off),
            _ => None,
        }
    }

    /// Nilai berikutnya untuk tombol putar di panel. Urutannya dari longgar
    /// ke ketat lalu kembali, supaya menekan tiga kali mengembalikan keadaan
    /// semula — tombol yang tidak bisa dibatalkan dengan tombol yang sama
    /// memaksa orang menebak.
    pub fn next(self) -> Policy {
        match self {
            Policy::Always => Policy::WhenWatched,
            Policy::WhenWatched => Policy::Off,
            Policy::Off => Policy::Always,
        }
    }

    /// Kalimat untuk panel dan log. Bahasa manusia, bukan nama varian.
    pub fn label(self) -> &'static str {
        match self {
            Policy::Always => "Akses kapan saja",
            Policy::WhenWatched => "Hanya saat saya ada",
            Policy::Off => "Tolak semua koneksi",
        }
    }
}

/// Kenapa sebuah koneksi ditolak. Dipakai untuk log di sisi host; client
/// tidak pernah melihat perbedaannya.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Denial {
    /// Kebijakan [`Policy::Off`].
    Off,
    /// [`Policy::WhenWatched`] dan tidak ada panel yang menonton, tidak ada
    /// izin sementara yang berlaku.
    NoOneWatching,
}

impl Denial {
    pub fn as_str(self) -> &'static str {
        match self {
            Denial::Off => "akses jarak jauh dimatikan",
            Denial::NoOneWatching => "tidak ada yang menunggui PC dan tidak ada izin sementara",
        }
    }
}

/// Hasil penilaian satu percobaan koneksi.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Decision {
    /// Diizinkan oleh kebijakan [`Policy::Always`].
    Allow,
    /// Diizinkan karena panel sedang terbuka.
    AllowWatched,
    /// Diizinkan oleh izin sementara; sisa waktunya ikut dibawa supaya bisa
    /// ditulis ke log apa adanya.
    AllowGranted {
        remaining_ms: u64,
    },
    Deny(Denial),
}

impl Decision {
    pub fn allowed(self) -> bool {
        !matches!(self, Decision::Deny(_))
    }
}

/// Keputusan murni. `grant_until_ms` adalah waktu berakhir izin sementara
/// dalam unix ms, atau `None` bila tidak ada.
///
/// Urutan pemeriksaannya penting: [`Policy::Off`] dinilai **sebelum** izin
/// sementara, karena saklar mati yang bisa dikalahkan izin yang kemarin
/// diberikan bukan saklar mati.
pub fn decide(policy: Policy, watched: bool, grant_until_ms: Option<u64>, now_ms: u64) -> Decision {
    match policy {
        Policy::Off => Decision::Deny(Denial::Off),
        Policy::Always => Decision::Allow,
        Policy::WhenWatched => {
            if watched {
                return Decision::AllowWatched;
            }
            match remaining_grant(grant_until_ms, now_ms) {
                Some(remaining_ms) => Decision::AllowGranted { remaining_ms },
                None => Decision::Deny(Denial::NoOneWatching),
            }
        }
    }
}

/// Sisa izin sementara dalam ms, atau `None` bila tidak ada/kedaluwarsa.
///
/// Izin yang berakhir lebih dari [`MAX_GRANT_MS`] di masa depan tidak pernah
/// bisa lahir dari [`grant_until`], jadi kalau terlihat di sini berkasnya
/// rusak, disunting tangan, atau jam dinding melompat jauh ke belakang.
/// Ketiganya dijawab sama: anggap habis.
pub fn remaining_grant(grant_until_ms: Option<u64>, now_ms: u64) -> Option<u64> {
    let until = grant_until_ms?;
    let remaining = until.checked_sub(now_ms)?;
    if remaining == 0 || remaining > MAX_GRANT_MS {
        return None;
    }
    Some(remaining)
}

/// Waktu berakhir untuk izin selama `hours` jam, dipotong di [`MAX_GRANT_MS`].
/// Nol jam berarti tidak ada izin sama sekali — bukan izin yang langsung
/// habis, supaya pemanggil tidak perlu membedakan keduanya.
pub fn grant_until(now_ms: u64, hours: u64) -> Option<u64> {
    if hours == 0 {
        return None;
    }
    let span = hours.saturating_mul(60 * 60 * 1000).min(MAX_GRANT_MS);
    Some(now_ms.saturating_add(span))
}

/// Dibulatkan ke atas: sisa 30 detik ditampilkan "1 menit", bukan "0 menit".
/// Angka nol di sebelah izin yang masih berlaku akan dibaca sebagai habis.
pub fn human_remaining(remaining_ms: u64) -> String {
    let minutes = remaining_ms.div_ceil(60_000);
    if minutes < 60 {
        return format!("{minutes} menit");
    }
    let hours = minutes / 60;
    let sisa = minutes % 60;
    if sisa == 0 {
        format!("{hours} jam")
    } else {
        format!("{hours} jam {sisa} menit")
    }
}

// ── Penyimpanan ─────────────────────────────────────────────────────────

/// Berkas kebijakan: satu kata.
pub fn policy_path(home: &Path) -> PathBuf {
    home.join("unattended")
}

/// Berkas izin sementara: satu angka unix ms.
pub fn grant_path(home: &Path) -> PathBuf {
    home.join("unattended-until")
}

/// Kebijakan tersimpan. Isi yang tidak dikenal jatuh ke bawaan — kebijakan
/// yang tidak terbaca tidak boleh diam-diam menjadi sesuatu yang lain, dan
/// di sini "sesuatu yang lain" akan berarti PC yang tidak bisa disambungkan.
pub fn load_policy(home: &Path) -> Policy {
    std::fs::read_to_string(policy_path(home))
        .ok()
        .and_then(|t| Policy::parse(t.trim()))
        .unwrap_or_default()
}

pub fn save_policy(home: &Path, policy: Policy) -> std::io::Result<()> {
    std::fs::create_dir_all(home)?;
    std::fs::write(policy_path(home), policy.as_str())
}

/// Izin tersimpan. Angka yang tidak terbaca berarti tidak ada izin: izin
/// adalah pernyataan positif, dan berkas rusak bukan pernyataan.
pub fn load_grant(home: &Path) -> Option<u64> {
    let text = std::fs::read_to_string(grant_path(home)).ok()?;
    if text.len() > 64 {
        return None;
    }
    text.trim().parse::<u64>().ok()
}

pub fn save_grant(home: &Path, until_ms: Option<u64>) -> std::io::Result<()> {
    std::fs::create_dir_all(home)?;
    match until_ms {
        Some(until) => std::fs::write(grant_path(home), until.to_string()),
        // Mencabut izin berarti berkasnya hilang, bukan berisi nol: berkas
        // berisi nol yang tertinggal akan terbaca sebagai izin kedaluwarsa
        // oleh pembaca yang kurang teliti, dan keduanya harus satu bentuk.
        None => match std::fs::remove_file(grant_path(home)) {
            Ok(()) => Ok(()),
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(()),
            Err(e) => Err(e),
        },
    }
}

// ── Keadaan proses ──────────────────────────────────────────────────────

#[derive(Default)]
struct Gate {
    policy: Policy,
    grant_until_ms: Option<u64>,
    last_watch_ms: Option<u64>,
}

static GATE: std::sync::Mutex<Option<Gate>> = std::sync::Mutex::new(None);

fn home() -> PathBuf {
    crate::identity::config_dir()
}

fn with<T>(f: impl FnOnce(&mut Gate) -> T) -> T {
    let mut slot = GATE
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    let gate = slot.get_or_insert_with(|| {
        let dir = home();
        Gate {
            policy: load_policy(&dir),
            grant_until_ms: load_grant(&dir),
            last_watch_ms: None,
        }
    });
    f(gate)
}

/// Jam dinding dalam unix ms. Dipakai hanya untuk izin sementara, yang
/// memang harus bertahan melewati restart host.
pub fn wall_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

pub fn policy() -> Policy {
    with(|g| g.policy)
}

pub fn set_policy(p: Policy) {
    with(|g| g.policy = p);
    if let Err(e) = save_policy(&home(), p) {
        eprintln!("[xydesk-host] kebijakan kehadiran gagal disimpan: {e}");
    }
    println!("[xydesk-host] kebijakan kehadiran: {}", p.label());
}

/// Panel memanggil `/status`; dipakai sebagai tanda ada yang menonton.
pub fn touch_watcher() {
    let t = wall_ms();
    with(|g| g.last_watch_ms = Some(t));
}

/// Sisa izin sementara sekarang, untuk ditampilkan panel. 0 = tidak ada.
pub fn grant_remaining_ms() -> u64 {
    let now = wall_ms();
    with(|g| remaining_grant(g.grant_until_ms, now).unwrap_or(0))
}

/// Beri izin sementara `hours` jam. `0` mencabutnya.
pub fn set_grant(hours: u64) -> u64 {
    let now = wall_ms();
    let until = grant_until(now, hours);
    with(|g| g.grant_until_ms = until);
    if let Err(e) = save_grant(&home(), until) {
        eprintln!("[xydesk-host] izin sementara gagal disimpan: {e}");
    }
    match remaining_grant(until, now) {
        Some(sisa) => {
            println!(
                "[xydesk-host] izin akses tanpa pendamping aktif {}",
                human_remaining(sisa)
            );
            sisa
        }
        None => {
            println!("[xydesk-host] izin akses tanpa pendamping dicabut");
            0
        }
    }
}

/// Gerbang untuk satu percobaan koneksi. Memakai jam dinding dan keadaan
/// panel yang tersimpan; seluruh aturannya ada di [`decide`].
pub fn check() -> Decision {
    let now = wall_ms();
    let (policy, grant, last_watch) = with(|g| (g.policy, g.grant_until_ms, g.last_watch_ms));
    let watched = last_watch.is_some_and(|t| now.saturating_sub(t) <= WATCHER_FRESH_MS);
    decide(policy, watched, grant, now)
}

#[cfg(test)]
mod tests {
    use super::*;

    const NOW: u64 = 1_800_000_000_000;

    #[test]
    fn bawaan_adalah_perilaku_lama() {
        // Yang paling penting di modul ini: PC yang tidak pernah diatur
        // siapa pun harus tetap bisa disambungkan setelah pembaruan.
        assert_eq!(Policy::default(), Policy::Always);
        assert_eq!(decide(Policy::default(), false, None, NOW), Decision::Allow);
        assert!(decide(Policy::default(), false, None, NOW).allowed());
    }

    #[test]
    fn watched_butuh_panel_atau_izin() {
        assert_eq!(
            decide(Policy::WhenWatched, true, None, NOW),
            Decision::AllowWatched
        );
        assert_eq!(
            decide(Policy::WhenWatched, false, None, NOW),
            Decision::Deny(Denial::NoOneWatching)
        );
        assert_eq!(
            decide(Policy::WhenWatched, false, Some(NOW + 60_000), NOW),
            Decision::AllowGranted {
                remaining_ms: 60_000
            }
        );
    }

    #[test]
    fn off_tidak_bisa_dikalahkan_izin_maupun_panel() {
        // Saklar mati yang punya pengecualian bukan saklar mati.
        assert_eq!(
            decide(Policy::Off, true, Some(NOW + MAX_GRANT_MS), NOW),
            Decision::Deny(Denial::Off)
        );
        assert!(!decide(Policy::Off, true, Some(NOW + 1000), NOW).allowed());
    }

    #[test]
    fn izin_yang_habis_tidak_membuka_apa_pun() {
        assert_eq!(remaining_grant(Some(NOW), NOW), None);
        assert_eq!(remaining_grant(Some(NOW - 1), NOW), None);
        assert_eq!(remaining_grant(None, NOW), None);
        assert_eq!(
            decide(Policy::WhenWatched, false, Some(NOW - 1), NOW),
            Decision::Deny(Denial::NoOneWatching)
        );
    }

    #[test]
    fn izin_yang_terlalu_jauh_di_masa_depan_dianggap_habis() {
        // Berkas disunting tangan, berkas rusak, atau jam yang melompat jauh
        // ke belakang. Ketiganya tidak boleh menjadi izin seumur hidup.
        assert_eq!(remaining_grant(Some(NOW + MAX_GRANT_MS + 1), NOW), None);
        assert_eq!(remaining_grant(Some(u64::MAX), NOW), None);
        // Tepat di batas masih sah — itu izin terpanjang yang bisa diberikan.
        assert_eq!(
            remaining_grant(Some(NOW + MAX_GRANT_MS), NOW),
            Some(MAX_GRANT_MS)
        );
    }

    #[test]
    fn izin_dipotong_di_batas_dan_nol_jam_berarti_tidak_ada() {
        assert_eq!(grant_until(NOW, 0), None);
        assert_eq!(grant_until(NOW, 1), Some(NOW + 3_600_000));
        assert_eq!(grant_until(NOW, 24), Some(NOW + MAX_GRANT_MS));
        assert_eq!(grant_until(NOW, 999), Some(NOW + MAX_GRANT_MS));
        // Tidak panik di ujung rentang.
        assert_eq!(grant_until(u64::MAX, 5), Some(u64::MAX));
    }

    #[test]
    fn kata_kebijakan_bolak_balik_dan_yang_asing_jatuh_ke_bawaan() {
        for p in [Policy::Always, Policy::WhenWatched, Policy::Off] {
            assert_eq!(Policy::parse(p.as_str()), Some(p));
        }
        assert_eq!(Policy::parse("  WATCHED \n"), Some(Policy::WhenWatched));
        assert_eq!(Policy::parse("kadang"), None);
        assert_eq!(Policy::parse(""), None);
    }

    #[test]
    fn tombol_putar_kembali_ke_awal_setelah_tiga_tekan() {
        let mut p = Policy::Always;
        for _ in 0..3 {
            p = p.next();
        }
        assert_eq!(p, Policy::Always);
        assert_eq!(Policy::Always.next(), Policy::WhenWatched);
        assert_eq!(Policy::WhenWatched.next(), Policy::Off);
    }

    #[test]
    fn sisa_waktu_dibulatkan_ke_atas() {
        assert_eq!(human_remaining(1), "1 menit");
        assert_eq!(human_remaining(30_000), "1 menit");
        assert_eq!(human_remaining(60_000), "1 menit");
        assert_eq!(human_remaining(61_000), "2 menit");
        assert_eq!(human_remaining(59 * 60_000), "59 menit");
        assert_eq!(human_remaining(60 * 60_000), "1 jam");
        assert_eq!(human_remaining(90 * 60_000), "1 jam 30 menit");
        assert_eq!(human_remaining(MAX_GRANT_MS), "24 jam");
        assert_eq!(human_remaining(0), "0 menit");
    }

    #[test]
    fn kebijakan_bertahan_melewati_restart_dan_berkas_rusak_jatuh_ke_bawaan() {
        let dir = std::env::temp_dir().join(format!(
            "xydesk-unattended-{}-{}",
            std::process::id(),
            rand::random::<u64>()
        ));
        assert_eq!(load_policy(&dir), Policy::Always, "belum ada berkas");
        save_policy(&dir, Policy::WhenWatched).unwrap();
        assert_eq!(load_policy(&dir), Policy::WhenWatched);
        save_policy(&dir, Policy::Off).unwrap();
        assert_eq!(load_policy(&dir), Policy::Off);
        std::fs::write(policy_path(&dir), "kadang-kadang").unwrap();
        assert_eq!(
            load_policy(&dir),
            Policy::Always,
            "isi tak dikenal harus jatuh ke bawaan, bukan mengunci pemiliknya"
        );
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn izin_bertahan_melewati_restart_dan_dicabut_dengan_menghapus_berkas() {
        let dir = std::env::temp_dir().join(format!(
            "xydesk-unattended-grant-{}-{}",
            std::process::id(),
            rand::random::<u64>()
        ));
        assert_eq!(load_grant(&dir), None);
        save_grant(&dir, Some(NOW + 3_600_000)).unwrap();
        assert_eq!(load_grant(&dir), Some(NOW + 3_600_000));
        // Restart host tidak boleh memulai hitungan dari nol: yang tersimpan
        // adalah waktu berakhir, jadi sisanya menyusut sendiri.
        assert_eq!(
            remaining_grant(load_grant(&dir), NOW + 3_000_000),
            Some(600_000)
        );
        save_grant(&dir, None).unwrap();
        assert_eq!(load_grant(&dir), None);
        assert!(!grant_path(&dir).exists(), "pencabutan menghapus berkasnya");
        // Mencabut dua kali bukan galat.
        save_grant(&dir, None).unwrap();
        std::fs::write(grant_path(&dir), "besok pagi").unwrap();
        assert_eq!(load_grant(&dir), None, "angka rusak bukan izin");
        std::fs::write(grant_path(&dir), "9".repeat(100)).unwrap();
        assert_eq!(load_grant(&dir), None, "berkas kepanjangan bukan izin");
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn panel_yang_sudah_ditutup_tidak_dianggap_menonton() {
        // Ini bukan uji `check()` (ia memakai jam nyata), melainkan aturan
        // kesegaran yang dipakainya.
        let segar = |last: u64, now: u64| now.saturating_sub(last) <= WATCHER_FRESH_MS;
        assert!(segar(NOW, NOW));
        assert!(segar(NOW, NOW + WATCHER_FRESH_MS));
        assert!(!segar(NOW, NOW + WATCHER_FRESH_MS + 1));
    }

    #[test]
    fn penolakan_punya_kalimat_yang_bisa_dibaca() {
        assert_eq!(Denial::Off.as_str(), "akses jarak jauh dimatikan");
        assert!(Denial::NoOneWatching.as_str().contains("izin sementara"));
        assert_eq!(Policy::WhenWatched.label(), "Hanya saat saya ada");
    }
}
