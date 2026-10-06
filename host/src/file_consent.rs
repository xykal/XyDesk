//! Pintu persetujuan untuk berkas yang MASUK ke PC.
//!
//! ## Kenapa modul ini ada
//!
//! Arah HP → PC selama ini diterima otomatis: client yang sudah lolos pairing
//! boleh menulis berkas ke `Downloads\XyDesk` tanpa pemilik PC tahu, apalagi
//! menyetujui. Arah sebaliknya (PC → HP) **selalu** bertanya di HP. Asimetri
//! itu tidak punya pembenaran: kedua sisi sama-sama penyimpanan pribadi milik
//! orang yang sedang tidak memegang perangkat satunya.
//!
//! Pairing bukan pengganti persetujuan. Pairing menjawab "siapa yang boleh
//! menyambung", bukan "apa yang boleh ia tulis ke disk saya". Satu sesi yang
//! sah pun bisa dipakai orang lain yang kebetulan memegang HP itu.
//!
//! ## Kebijakan
//!
//! Tiga nilai, dan yang tengah adalah bawaan:
//!
//! - [`Policy::Always`] — terima otomatis (perilaku lama).
//! - [`Policy::Ask`] — **tanya bila ada yang bisa menjawab**, lihat di bawah.
//! - [`Policy::Never`] — tolak semua berkas masuk.
//!
//! ## "Bila ada yang bisa menjawab"
//!
//! Dialognya digambar panel host, dan panel tidak selalu berjalan: host juga
//! dipakai di PC tanpa siapa-siapa di depannya. Pertanyaan yang tidak bisa
//! dilihat siapa pun bukan perlindungan — ia hanya membuat setiap transfer
//! gagal setelah satu menit hening, dan pemiliknya tidak pernah tahu kenapa.
//!
//! Jadi host menganggap ada yang menonton bila panel memanggil `/status`
//! dalam [`WATCHER_FRESH_MS`] terakhir. Bila tidak ada, [`Policy::Ask`]
//! berperilaku seperti [`Policy::Always`] **dan mencatatnya di log** — bukan
//! diam-diam. Pemilik PC yang menginginkan penolakan dalam keadaan itu punya
//! [`Policy::Never`] yang tidak bergantung pada siapa pun.
//!
//! ## Diam bukan izin
//!
//! Tawaran yang tidak dijawab dalam [`TIMEOUT_MS`] **ditolak**. Dialog yang
//! berubah menjadi izin karena ditinggal makan siang adalah pintu yang hanya
//! tampak seperti pintu.
//!
//! ## Perangkat yang diingat
//!
//! Menjawab pertanyaan yang sama untuk perangkat yang sama setiap kali adalah
//! cara tercepat membuat orang berhenti membacanya. "Terima & ingat perangkat
//! ini" menyimpan id client ke `file-trusted.json`; tawaran berikutnya dari
//! perangkat itu lewat tanpa bertanya — **dan tetap dicatat di log**, karena
//! penerimaan yang tidak terlihat di mana pun tidak bisa diaudit siapa pun.
//!
//! Kepercayaan itu melekat pada id perangkat, bukan pada sesi, dan hanya bisa
//! dicabut semuanya sekaligus (`file-trust-clear`). Pencabutan satu per satu
//! butuh daftar di panel yang belum ada; menawarkan tombol yang tidak bisa
//! menunjuk perangkat mana pun hanya akan menipu.
//!
//! [`Policy::Never`] mengalahkan daftar ini: "tolak semua" yang punya
//! pengecualian bukan "tolak semua".
//!
//! Keputusan dan penyimpanan dipisah: fungsi inti tidak menyentuh jaringan
//! maupun disk dan waktunya selalu dioper dari luar, sehingga seluruh
//! perilakunya bisa diuji tanpa menunggu satu detik pun.

use std::collections::BTreeSet;
use std::path::{Path, PathBuf};

use crate::filetransfer::Reason;

/// Berapa lama tawaran menunggu jawaban sebelum ditolak.
pub const TIMEOUT_MS: u64 = 60_000;

/// Batas jumlah perangkat yang diingat. Daftar yang tumbuh tanpa batas
/// adalah daftar yang tidak pernah ditinjau siapa pun.
pub const MAX_TRUSTED: usize = 64;

/// Batas "panel masih menonton" sejak `/status` terakhir. Panel memanggilnya
/// sekitar sekali per detik; sepuluh detik memberi kelonggaran untuk PC yang
/// sibuk tanpa membuat panel yang sudah ditutup dianggap hidup.
pub const WATCHER_FRESH_MS: u64 = 10_000;

/// Kebijakan berkas masuk.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Default)]
pub enum Policy {
    /// Tanya pemilik PC bila panelnya sedang menonton.
    #[default]
    Ask,
    /// Terima otomatis dari client yang sudah lolos pairing.
    Always,
    /// Tolak semua berkas masuk.
    Never,
}

impl Policy {
    /// Nama yang dipakai control API dan panel.
    pub fn as_str(self) -> &'static str {
        match self {
            Policy::Ask => "ask",
            Policy::Always => "always",
            Policy::Never => "never",
        }
    }

    pub fn parse(text: &str) -> Option<Policy> {
        match text {
            "ask" => Some(Policy::Ask),
            "always" => Some(Policy::Always),
            "never" => Some(Policy::Never),
            _ => None,
        }
    }
}

/// Hasil penimbangan satu tawaran.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Verdict {
    Accept,
    Reject(Reason),
    /// Menunggu jawaban pemilik PC.
    Pending,
}

/// Tawaran yang sedang menunggu jawaban, sebagaimana dilihat panel.
#[derive(Clone, Debug, PartialEq, Eq, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PendingView {
    pub id: u32,
    pub name: String,
    pub size: u64,
    /// Ekstensi yang langsung dijalankan Windows (`.exe`, `.ps1`, …).
    pub risky: bool,
    /// Sisa waktu sebelum tawaran ditolak sendiri, dibulatkan ke bawah.
    pub seconds_left: u64,
}

#[derive(Clone, Debug)]
struct Pending {
    id: u32,
    /// Id perangkat pengirim, untuk "ingat perangkat ini".
    peer: String,
    name: String,
    size: u64,
    risky: bool,
    asked_ms: u64,
}

/// Pintu persetujuan. Satu per host; waktu selalu dioper dari pemanggil.
#[derive(Debug, Default)]
pub struct Gate {
    policy: Policy,
    pending: Option<Pending>,
    /// Jawaban yang sudah masuk tetapi belum dipanen oleh [`Gate::poll`]:
    /// (id, izinkan, ingat perangkatnya).
    answer: Option<(u32, bool, bool)>,
    last_watch_ms: Option<u64>,
    /// Id perangkat yang pernah diberi "ingat perangkat ini".
    trusted: BTreeSet<String>,
}

impl Gate {
    pub fn new(policy: Policy) -> Gate {
        Gate {
            policy,
            ..Gate::default()
        }
    }

    pub fn policy(&self) -> Policy {
        self.policy
    }

    /// Mengubah kebijakan. Tawaran yang sedang menunggu ikut diputuskan oleh
    /// nilai baru pada [`Gate::poll`] berikutnya — memilih "tolak semua"
    /// sementara satu dialog terbuka harus berarti dialog itu juga ditolak.
    pub fn set_policy(&mut self, policy: Policy) {
        self.policy = policy;
    }

    /// Dipanggil setiap kali panel membaca `/status`.
    pub fn touch_watcher(&mut self, now_ms: u64) {
        self.last_watch_ms = Some(now_ms);
    }

    /// Benar bila ada panel yang cukup baru untuk menjawab pertanyaan.
    pub fn watched(&self, now_ms: u64) -> bool {
        self.last_watch_ms
            .is_some_and(|t| now_ms.saturating_sub(t) <= WATCHER_FRESH_MS)
    }

    /// Timbang satu tawaran baru.
    ///
    /// Tawaran kedua saat satu tawaran masih menunggu ditolak: dua dialog
    /// sekaligus membuat pemilik PC menyetujui yang salah, dan protokolnya
    /// pun hanya mengizinkan satu transfer dalam satu waktu.
    pub fn offer(
        &mut self,
        peer: &str,
        id: u32,
        name: &str,
        size: u64,
        risky: bool,
        now_ms: u64,
    ) -> Verdict {
        if self.pending.is_some() {
            return Verdict::Reject(Reason::Protocol);
        }
        match self.policy {
            Policy::Never => Verdict::Reject(Reason::User),
            Policy::Always => Verdict::Accept,
            Policy::Ask if self.trusts(peer) => Verdict::Accept,
            Policy::Ask if !self.watched(now_ms) => Verdict::Accept,
            Policy::Ask => {
                self.answer = None;
                self.pending = Some(Pending {
                    id,
                    peer: peer.to_string(),
                    name: name.to_string(),
                    size,
                    risky,
                    asked_ms: now_ms,
                });
                Verdict::Pending
            }
        }
    }

    /// Perangkat yang pernah diingat. Id kosong tidak pernah dipercaya:
    /// sesi tanpa identitas tidak boleh mewarisi izin sesi lain.
    pub fn trusts(&self, peer: &str) -> bool {
        !peer.is_empty() && self.trusted.contains(peer)
    }

    pub fn trusted_count(&self) -> usize {
        self.trusted.len()
    }

    pub fn forget_all(&mut self) -> usize {
        let n = self.trusted.len();
        self.trusted.clear();
        n
    }

    /// Jawaban pemilik PC. Mengembalikan false bila tidak ada tawaran dengan
    /// id itu — jawaban yang terlambat satu detik tidak boleh menyetujui
    /// tawaran berikutnya yang kebetulan sudah menggantikannya.
    pub fn answer(&mut self, id: u32, allow: bool, remember: bool) -> bool {
        match &self.pending {
            Some(p) if p.id == id => {
                self.answer = Some((id, allow, remember));
                true
            }
            _ => false,
        }
    }

    /// Keadaan tawaran yang sedang menunggu.
    ///
    /// Memanen jawaban dan kedaluwarsa. Selama hasilnya [`Verdict::Pending`],
    /// pemanggil harus menanyakannya lagi nanti.
    pub fn poll(&mut self, id: u32, now_ms: u64) -> Verdict {
        let Some(p) = &self.pending else {
            return Verdict::Reject(Reason::Protocol);
        };
        if p.id != id {
            return Verdict::Reject(Reason::Protocol);
        }
        if self.policy == Policy::Never {
            self.clear(id);
            return Verdict::Reject(Reason::User);
        }
        if self.policy == Policy::Always {
            self.clear(id);
            return Verdict::Accept;
        }
        if now_ms.saturating_sub(p.asked_ms) >= TIMEOUT_MS {
            self.clear(id);
            return Verdict::Reject(Reason::User);
        }
        match self.answer {
            Some((answered, allow, remember)) if answered == id => {
                let peer = p.peer.clone();
                self.clear(id);
                if allow {
                    // Hanya penerimaan yang bisa diingat. "Tolak & ingat"
                    // terdengar masuk akal sampai pengirimnya adalah orang
                    // yang sama yang besok benar-benar perlu mengirim.
                    if remember && !peer.is_empty() && self.trusted.len() < MAX_TRUSTED {
                        self.trusted.insert(peer);
                    }
                    Verdict::Accept
                } else {
                    Verdict::Reject(Reason::User)
                }
            }
            _ => Verdict::Pending,
        }
    }

    /// Lupakan tawaran ini (dijawab, kedaluwarsa, atau dibatalkan pengirim).
    pub fn clear(&mut self, id: u32) {
        if self.pending.as_ref().is_some_and(|p| p.id == id) {
            self.pending = None;
            self.answer = None;
        }
    }

    /// Yang perlu digambar panel, atau `None` bila tidak ada yang menunggu.
    pub fn view(&self, now_ms: u64) -> Option<PendingView> {
        let p = self.pending.as_ref()?;
        let waited = now_ms.saturating_sub(p.asked_ms);
        Some(PendingView {
            id: p.id,
            name: p.name.clone(),
            size: p.size,
            risky: p.risky,
            seconds_left: TIMEOUT_MS.saturating_sub(waited) / 1000,
        })
    }
}

// ── Penyimpanan ─────────────────────────────────────────────────────────

/// Berkas kebijakan: satu kata, supaya bisa dibaca dan diperbaiki dengan
/// Notepad saat panel sedang tidak bisa dibuka.
pub fn policy_path(home: &Path) -> PathBuf {
    home.join("file-policy")
}

/// Berkas perangkat tepercaya: satu id per baris, alasan yang sama.
pub fn trusted_path(home: &Path) -> PathBuf {
    home.join("file-trusted")
}

/// Baca kebijakan tersimpan. Isi yang tidak dikenal (berkas rusak, editor
/// yang menambah BOM, pengguna yang salah ketik) jatuh ke bawaan: kebijakan
/// yang tidak terbaca tidak boleh diam-diam menjadi "terima semua".
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

/// Baca daftar perangkat tepercaya. Baris kosong dibuang, duplikat lebur
/// sendiri, dan daftar yang kepanjangan dipotong di [`MAX_TRUSTED`].
pub fn load_trusted(home: &Path) -> BTreeSet<String> {
    std::fs::read_to_string(trusted_path(home))
        .unwrap_or_default()
        .lines()
        .map(str::trim)
        .filter(|l| !l.is_empty() && l.len() <= 128)
        .take(MAX_TRUSTED)
        .map(str::to_string)
        .collect()
}

pub fn save_trusted(home: &Path, trusted: &BTreeSet<String>) -> std::io::Result<()> {
    std::fs::create_dir_all(home)?;
    let body: String = trusted
        .iter()
        .map(|id| format!("{id}\n"))
        .collect::<Vec<_>>()
        .concat();
    std::fs::write(trusted_path(home), body)
}

// ── Pintu milik proses ───────────────────────────────────────────────────

static GATE: std::sync::Mutex<Option<Gate>> = std::sync::Mutex::new(None);

fn home() -> PathBuf {
    crate::identity::config_dir()
}

fn with<T>(f: impl FnOnce(&mut Gate) -> T) -> T {
    let mut slot = GATE
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    let gate = slot.get_or_insert_with(|| {
        // Dibaca sekali, saat pertama dibutuhkan: kebijakan yang hilang
        // setiap host restart bukan kebijakan.
        let dir = crate::identity::config_dir();
        Gate {
            policy: load_policy(&dir),
            trusted: load_trusted(&dir),
            ..Gate::default()
        }
    });
    f(gate)
}

/// Milidetik monotonik sejak proses mulai. Tidak memakai jam dinding: jam
/// yang mundur karena NTP tidak boleh memperpanjang atau memutus dialog.
pub fn now_ms() -> u64 {
    static START: std::sync::OnceLock<std::time::Instant> = std::sync::OnceLock::new();
    START
        .get_or_init(std::time::Instant::now)
        .elapsed()
        .as_millis() as u64
}

pub fn policy() -> Policy {
    with(|g| g.policy())
}

pub fn set_policy(policy: Policy) {
    with(|g| g.set_policy(policy));
    if let Err(e) = save_policy(&home(), policy) {
        eprintln!("[xydesk-host] kebijakan berkas masuk gagal disimpan: {e}");
    }
    println!("[xydesk-host] kebijakan berkas masuk: {}", policy.as_str());
}

pub fn touch_watcher() {
    let t = now_ms();
    with(|g| g.touch_watcher(t));
}

pub fn trusted_count() -> usize {
    with(|g| g.trusted_count())
}

/// Lupakan semua perangkat tepercaya. Mengembalikan jumlah yang dilupakan.
pub fn forget_all() -> usize {
    let n = with(|g| g.forget_all());
    if let Err(e) = save_trusted(&home(), &BTreeSet::new()) {
        eprintln!("[xydesk-host] daftar perangkat tepercaya gagal ditulis: {e}");
    }
    println!("[xydesk-host] {n} perangkat tepercaya dilupakan");
    n
}

pub fn offer(peer: &str, id: u32, name: &str, size: u64, risky: bool) -> Verdict {
    let t = now_ms();
    let (watched, trusted, policy) = with(|g| (g.watched(t), g.trusts(peer), g.policy()));
    let verdict = with(|g| g.offer(peer, id, name, size, risky, t));
    if verdict == Verdict::Accept && policy == Policy::Ask {
        if trusted {
            println!(
                "[xydesk-host] berkas masuk \"{name}\" diterima tanpa ditanyakan: perangkat {peer} diingat"
            );
        } else if !watched {
            println!(
                "[xydesk-host] berkas masuk \"{name}\" diterima tanpa ditanyakan: panel host tidak sedang berjalan"
            );
        }
    }
    verdict
}

pub fn answer(id: u32, allow: bool, remember: bool) -> bool {
    with(|g| g.answer(id, allow, remember))
}

pub fn poll(id: u32) -> Verdict {
    let t = now_ms();
    let (verdict, trusted) = with(|g| (g.poll(id, t), g.trusted.clone()));
    if verdict == Verdict::Accept {
        // Ditulis setelah keputusan, bukan sebelum: daftar yang tersimpan
        // harus mencerminkan apa yang benar-benar diputuskan.
        if let Err(e) = save_trusted(&home(), &trusted) {
            eprintln!("[xydesk-host] daftar perangkat tepercaya gagal ditulis: {e}");
        }
    }
    verdict
}

pub fn clear(id: u32) {
    with(|g| g.clear(id));
}

pub fn view() -> Option<PendingView> {
    let t = now_ms();
    with(|g| g.view(t))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn ditonton(now: u64) -> Gate {
        let mut g = Gate::new(Policy::Ask);
        g.touch_watcher(now);
        g
    }

    #[test]
    fn panel_menonton_maka_tawaran_menunggu_jawaban() {
        let mut g = ditonton(1_000);
        assert_eq!(
            g.offer("hp", 7, "a.txt", 10, false, 1_000),
            Verdict::Pending
        );
        assert_eq!(g.poll(7, 1_100), Verdict::Pending);
        assert!(g.answer(7, true, false));
        assert_eq!(g.poll(7, 1_200), Verdict::Accept);
        // Setelah dijawab, tidak ada lagi yang menunggu.
        assert_eq!(g.view(1_200), None);
    }

    #[test]
    fn penolakan_pengguna_memakai_alasan_user() {
        let mut g = ditonton(0);
        assert_eq!(g.offer("hp", 1, "a.txt", 10, false, 0), Verdict::Pending);
        assert!(g.answer(1, false, false));
        assert_eq!(g.poll(1, 10), Verdict::Reject(Reason::User));
    }

    #[test]
    fn diam_bukan_izin() {
        let mut g = ditonton(0);
        g.offer("hp", 1, "a.txt", 10, false, 0);
        assert_eq!(g.poll(1, TIMEOUT_MS - 1), Verdict::Pending);
        assert_eq!(g.poll(1, TIMEOUT_MS), Verdict::Reject(Reason::User));
        assert_eq!(g.view(TIMEOUT_MS), None);
    }

    #[test]
    fn tanpa_panel_yang_menonton_perilaku_lama_dipertahankan() {
        // Tidak ada yang bisa menjawab: menolak berarti setiap transfer gagal
        // setelah satu menit hening di PC tanpa penjaga.
        let mut g = Gate::new(Policy::Ask);
        assert_eq!(g.offer("hp", 1, "a.txt", 10, false, 0), Verdict::Accept);
    }

    #[test]
    fn panel_yang_sudah_lama_diam_dianggap_mati() {
        let mut g = Gate::new(Policy::Ask);
        g.touch_watcher(0);
        assert!(g.watched(WATCHER_FRESH_MS));
        assert!(!g.watched(WATCHER_FRESH_MS + 1));
        assert_eq!(
            g.offer("hp", 1, "a.txt", 10, false, WATCHER_FRESH_MS + 1),
            Verdict::Accept
        );
    }

    #[test]
    fn kebijakan_never_menolak_tanpa_bertanya() {
        let mut g = ditonton(0);
        g.set_policy(Policy::Never);
        assert_eq!(
            g.offer("hp", 1, "a.txt", 10, false, 0),
            Verdict::Reject(Reason::User)
        );
        assert_eq!(g.view(0), None);
    }

    #[test]
    fn kebijakan_always_menerima_walau_panel_menonton() {
        let mut g = ditonton(0);
        g.set_policy(Policy::Always);
        assert_eq!(g.offer("hp", 1, "a.txt", 10, false, 0), Verdict::Accept);
    }

    #[test]
    fn mengubah_kebijakan_ikut_memutuskan_dialog_yang_terbuka() {
        let mut g = ditonton(0);
        g.offer("hp", 1, "a.txt", 10, false, 0);
        g.set_policy(Policy::Never);
        assert_eq!(g.poll(1, 5), Verdict::Reject(Reason::User));

        let mut g = ditonton(0);
        g.offer("hp", 2, "b.txt", 10, false, 0);
        g.set_policy(Policy::Always);
        assert_eq!(g.poll(2, 5), Verdict::Accept);
    }

    #[test]
    fn tawaran_kedua_saat_dialog_terbuka_ditolak() {
        let mut g = ditonton(0);
        assert_eq!(g.offer("hp", 1, "a.txt", 10, false, 0), Verdict::Pending);
        assert_eq!(
            g.offer("hp", 2, "b.txt", 10, false, 1),
            Verdict::Reject(Reason::Protocol)
        );
        // Dialog pertama tidak tergusur oleh tawaran kedua.
        assert_eq!(g.view(1).map(|v| v.id), Some(1));
    }

    #[test]
    fn jawaban_untuk_id_lain_tidak_menyetujui_apa_pun() {
        let mut g = ditonton(0);
        g.offer("hp", 1, "a.txt", 10, false, 0);
        assert!(!g.answer(2, true, false));
        assert_eq!(g.poll(1, 5), Verdict::Pending);
    }

    #[test]
    fn jawaban_terlambat_tidak_menyetujui_tawaran_berikutnya() {
        let mut g = ditonton(0);
        g.offer("hp", 1, "a.txt", 10, false, 0);
        assert_eq!(g.poll(1, TIMEOUT_MS), Verdict::Reject(Reason::User));
        // Pemilik PC menekan "Terima" sepersekian detik setelah kedaluwarsa,
        // lalu pengirim menawarkan berkas lain.
        assert!(!g.answer(1, true, false));
        // Panelnya masih hidup: ia tetap membaca /status selama menunggu.
        g.touch_watcher(TIMEOUT_MS);
        assert_eq!(
            g.offer("hp", 2, "b.txt", 10, false, TIMEOUT_MS + 1),
            Verdict::Pending
        );
        assert_eq!(g.poll(2, TIMEOUT_MS + 2), Verdict::Pending);
    }

    #[test]
    fn polling_id_yang_bukan_miliknya_tidak_membocorkan_keputusan() {
        let mut g = ditonton(0);
        g.offer("hp", 1, "a.txt", 10, false, 0);
        g.answer(1, true, false);
        assert_eq!(g.poll(9, 5), Verdict::Reject(Reason::Protocol));
        // Tawaran asli tetap utuh dan tetap bisa dipanen pemiliknya.
        assert_eq!(g.poll(1, 6), Verdict::Accept);
    }

    #[test]
    fn pembatalan_pengirim_menutup_dialog() {
        let mut g = ditonton(0);
        g.offer("hp", 1, "a.txt", 10, false, 0);
        g.clear(1);
        assert_eq!(g.view(0), None);
        // Dan membuka jalan bagi tawaran berikutnya.
        assert_eq!(g.offer("hp", 2, "b.txt", 10, false, 1), Verdict::Pending);
    }

    #[test]
    fn tampilan_panel_memuat_nama_ukuran_risiko_dan_sisa_waktu() {
        let mut g = ditonton(0);
        g.offer("hp", 42, "pasang.exe", 2048, true, 0);
        let v = g.view(1_500).expect("ada yang menunggu");
        assert_eq!(v.id, 42);
        assert_eq!(v.name, "pasang.exe");
        assert_eq!(v.size, 2048);
        assert!(v.risky);
        assert_eq!(v.seconds_left, (TIMEOUT_MS - 1_500) / 1000);
        // Sisa waktu tidak pernah negatif atau membungkus.
        assert_eq!(g.view(TIMEOUT_MS * 2).map(|v| v.seconds_left), Some(0));
    }

    #[test]
    fn perangkat_yang_diingat_lewat_tanpa_bertanya() {
        let mut g = ditonton(0);
        g.offer("hp-rumah", 1, "a.txt", 10, false, 0);
        assert!(g.answer(1, true, true));
        assert_eq!(g.poll(1, 5), Verdict::Accept);
        assert_eq!(g.trusted_count(), 1);
        // Berkas kedua dari perangkat yang sama: tidak ada dialog lagi.
        assert_eq!(
            g.offer("hp-rumah", 2, "b.txt", 10, false, 10),
            Verdict::Accept
        );
        // Perangkat lain tetap ditanya.
        assert_eq!(
            g.offer("hp-asing", 3, "c.txt", 10, false, 10),
            Verdict::Pending
        );
    }

    #[test]
    fn menerima_tanpa_mengingat_tidak_menambah_daftar() {
        let mut g = ditonton(0);
        g.offer("hp", 1, "a.txt", 10, false, 0);
        g.answer(1, true, false);
        assert_eq!(g.poll(1, 5), Verdict::Accept);
        assert_eq!(g.trusted_count(), 0);
        assert_eq!(g.offer("hp", 2, "b.txt", 10, false, 10), Verdict::Pending);
    }

    #[test]
    fn penolakan_tidak_pernah_diingat() {
        // "Tolak & ingat" tidak ditawarkan; kalaupun dipaksa lewat API,
        // daftar kepercayaan tidak boleh tumbuh dari sebuah penolakan.
        let mut g = ditonton(0);
        g.offer("hp", 1, "a.txt", 10, false, 0);
        g.answer(1, false, true);
        assert_eq!(g.poll(1, 5), Verdict::Reject(Reason::User));
        assert_eq!(g.trusted_count(), 0);
    }

    #[test]
    fn kebijakan_never_mengalahkan_perangkat_yang_diingat() {
        let mut g = ditonton(0);
        g.offer("hp", 1, "a.txt", 10, false, 0);
        g.answer(1, true, true);
        g.poll(1, 1);
        g.set_policy(Policy::Never);
        assert_eq!(
            g.offer("hp", 2, "b.txt", 10, false, 2),
            Verdict::Reject(Reason::User)
        );
    }

    #[test]
    fn sesi_tanpa_identitas_tidak_pernah_dipercaya() {
        let mut g = ditonton(0);
        g.offer("", 1, "a.txt", 10, false, 0);
        g.answer(1, true, true);
        assert_eq!(g.poll(1, 5), Verdict::Accept);
        assert_eq!(g.trusted_count(), 0, "id kosong tidak boleh masuk daftar");
        assert!(!g.trusts(""));
    }

    #[test]
    fn melupakan_mengosongkan_daftar() {
        let mut g = ditonton(0);
        for (i, peer) in ["a", "b"].iter().enumerate() {
            let id = i as u32 + 1;
            g.offer(peer, id, "x.txt", 10, false, 0);
            g.answer(id, true, true);
            g.poll(id, 1);
        }
        assert_eq!(g.trusted_count(), 2);
        assert_eq!(g.forget_all(), 2);
        assert_eq!(g.offer("a", 9, "x.txt", 10, false, 2), Verdict::Pending);
    }

    #[test]
    fn kebijakan_bertahan_di_disk_dan_yang_rusak_jatuh_ke_bawaan() {
        let dir = std::env::temp_dir().join(format!("xydesk-consent-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        // Belum pernah ditulis: bawaan, bukan "terima semua".
        assert_eq!(load_policy(&dir), Policy::Ask);
        save_policy(&dir, Policy::Never).expect("tulis kebijakan");
        assert_eq!(load_policy(&dir), Policy::Never);
        std::fs::write(policy_path(&dir), "kadang-kadang").expect("tulis berkas rusak");
        assert_eq!(load_policy(&dir), Policy::Ask, "isi rusak jatuh ke bawaan");
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn daftar_tepercaya_bertahan_dan_dibersihkan_saat_dibaca() {
        let dir = std::env::temp_dir().join(format!("xydesk-trust-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        assert!(load_trusted(&dir).is_empty());
        let mut set = BTreeSet::new();
        set.insert("hp-rumah".to_string());
        set.insert("hp-kantor".to_string());
        save_trusted(&dir, &set).expect("tulis daftar");
        assert_eq!(load_trusted(&dir), set);
        // Baris kosong, spasi, dan id tak masuk akal dibuang saat dibaca.
        std::fs::write(
            trusted_path(&dir),
            format!("  hp-rumah  \n\n{}\n", "x".repeat(200)),
        )
        .expect("tulis berkas kotor");
        let dibaca = load_trusted(&dir);
        assert_eq!(dibaca.len(), 1);
        assert!(dibaca.contains("hp-rumah"));
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn nama_kebijakan_bolak_balik() {
        for p in [Policy::Ask, Policy::Always, Policy::Never] {
            assert_eq!(Policy::parse(p.as_str()), Some(p));
        }
        assert_eq!(Policy::parse("kadang"), None);
        assert_eq!(Policy::default(), Policy::Ask);
    }
}
