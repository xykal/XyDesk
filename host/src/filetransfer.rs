//! Transfer berkas antara klien dan host — protokol dan mesin keadaan
//! penerima.
//!
//! ## Kenapa data channel sendiri
//!
//! Berkas **tidak boleh** lewat channel `input`. Satu berkas 200 MB berarti
//! ribuan pesan beruntun; kalau dimasukkan ke antrean yang sama dengan gerak
//! mouse, setiap klik ikut mengantre di belakang potongan berkas dan sesi
//! terasa macet justru saat pengguna paling sibuk. Transfer memakai channel
//! `"file"` yang terpisah, boleh lambat, dan boleh tersendat tanpa menyentuh
//! jalur input.
//!
//! ## Protokol biner (data channel `"file"`, little-endian)
//!
//! ```text
//! byte 0 : tipe pesan
//!
//! 0x01 OFFER  id:u32  size:u64  name_len:u16  name:utf8
//! 0x02 ACCEPT id:u32
//! 0x03 REJECT id:u32  reason:u8
//! 0x04 CHUNK  id:u32  seq:u32  bytes (sisa pesan)
//! 0x05 DONE   id:u32  sha256:32 byte
//! 0x06 CANCEL id:u32  reason:u8
//! 0x07 ACK    id:u32  received:u64
//! ```
//!
//! `seq` dimulai dari 0 dan **wajib** naik satu per satu. SCTP di WebRTC
//! sudah memberi pengiriman terurut dan andal pada channel bawaan, jadi
//! nomor urut di sini bukan untuk menyusun ulang melainkan untuk menolak
//! aliran yang menyimpang: potongan ganda, potongan yang dilewati, atau
//! pengirim yang memulai di tengah.
//!
//! ## Yang sengaja tidak dipercaya dari lawan
//!
//! Nama berkas datang dari mesin lain dan **tidak pernah** dipakai apa
//! adanya. `sanitize_name` membuang komponen direktori, menolak `..`,
//! membuang karakter yang ilegal di Windows, memangkas titik dan spasi di
//! ujung, dan memberi awalan pada nama perangkat DOS (`CON`, `LPT1`, …) yang
//! kalau dibiarkan akan membuat penulisan berkas berubah menjadi menulis ke
//! perangkat. Ukuran yang dijanjikan dibatasi, total byte yang benar-benar
//! diterima dihitung sendiri, dan isinya baru diakui setelah SHA-256-nya
//! cocok dengan yang dijanjikan pada DONE.

use sha2::{Digest, Sha256};

/// Tipe pesan di channel `"file"`.
pub const MSG_OFFER: u8 = 0x01;
pub const MSG_ACCEPT: u8 = 0x02;
pub const MSG_REJECT: u8 = 0x03;
pub const MSG_CHUNK: u8 = 0x04;
pub const MSG_DONE: u8 = 0x05;
pub const MSG_CANCEL: u8 = 0x06;
pub const MSG_ACK: u8 = 0x07;

/// Nama data channel untuk transfer berkas.
pub const FILE_CHANNEL: &str = "file";

/// Batas ukuran satu berkas: 4 GiB.
///
/// Angkanya bukan selera: di bawah batas ini ukuran masih muat di `u32` bila
/// suatu saat ada sisi yang memakai tipe lebih sempit, dan 4 GiB sudah jauh
/// di atas kebutuhan wajar transfer lewat sesi remote.
pub const MAX_FILE_BYTES: u64 = 4 * 1024 * 1024 * 1024;

/// Batas isi satu potongan: 64 KiB.
///
/// SCTP memecah sendiri pesan besar, tetapi penerima harus punya batas atas
/// yang tegas supaya satu pesan jahat tidak bisa meminta alokasi sebesar
/// yang disebut pengirim.
pub const MAX_CHUNK_BYTES: usize = 64 * 1024;

/// Batas panjang nama berkas setelah dibersihkan (karakter, bukan byte).
pub const MAX_NAME_CHARS: usize = 120;

/// Nama cadangan bila nama kiriman tidak menyisakan apa pun yang aman.
pub const FALLBACK_NAME: &str = "berkas";

/// Alasan penolakan atau pembatalan. Dikirim sebagai satu byte supaya sisi
/// lain bisa menampilkan kalimat dalam bahasanya sendiri.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Reason {
    /// Pengguna menolak atau membatalkan.
    User,
    /// Ukuran melebihi batas yang diterima penerima.
    TooLarge,
    /// Aliran menyimpang dari protokol (urutan, id, atau bentuk pesan).
    Protocol,
    /// SHA-256 tidak cocok dengan yang dijanjikan.
    HashMismatch,
    /// Gagal menulis ke disk.
    Io,
    /// Sesi berakhir di tengah transfer.
    Disconnected,
}

impl Reason {
    pub fn code(self) -> u8 {
        match self {
            Reason::User => 0,
            Reason::TooLarge => 1,
            Reason::Protocol => 2,
            Reason::HashMismatch => 3,
            Reason::Io => 4,
            Reason::Disconnected => 5,
        }
    }

    pub fn from_code(code: u8) -> Reason {
        match code {
            0 => Reason::User,
            1 => Reason::TooLarge,
            3 => Reason::HashMismatch,
            4 => Reason::Io,
            5 => Reason::Disconnected,
            // Kode yang tidak dikenal diperlakukan sebagai pelanggaran
            // protokol, bukan diam-diam dianggap "dibatalkan pengguna".
            _ => Reason::Protocol,
        }
    }
}

/// Pesan protokol hasil dekode.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum FileMessage {
    Offer { id: u32, size: u64, name: String },
    Accept { id: u32 },
    Reject { id: u32, reason: Reason },
    Chunk { id: u32, seq: u32, data: Vec<u8> },
    Done { id: u32, sha256: [u8; 32] },
    Cancel { id: u32, reason: Reason },
    Ack { id: u32, received: u64 },
}

/// Mendekode satu pesan channel `"file"`.
///
/// `None` berarti pesan cacat: terlalu pendek, tipe tak dikenal, nama bukan
/// UTF-8, atau panjang nama tidak cocok dengan isinya. Pemanggil wajib
/// memperlakukannya sebagai pelanggaran protokol, bukan mengabaikannya.
pub fn decode(bytes: &[u8]) -> Option<FileMessage> {
    let (&kind, rest) = bytes.split_first()?;
    let id = |r: &[u8]| -> Option<u32> { Some(u32::from_le_bytes(r.get(0..4)?.try_into().ok()?)) };
    match kind {
        MSG_OFFER => {
            let id = id(rest)?;
            let size = u64::from_le_bytes(rest.get(4..12)?.try_into().ok()?);
            let name_len = u16::from_le_bytes(rest.get(12..14)?.try_into().ok()?) as usize;
            let raw = rest.get(14..14 + name_len)?;
            // Nama yang panjangnya tidak sesuai header ditolak, bukan
            // dipotong: pengirim jujur tidak pernah salah hitung.
            if rest.len() != 14 + name_len {
                return None;
            }
            let name = String::from_utf8(raw.to_vec()).ok()?;
            Some(FileMessage::Offer { id, size, name })
        }
        MSG_ACCEPT => {
            if rest.len() != 4 {
                return None;
            }
            Some(FileMessage::Accept { id: id(rest)? })
        }
        MSG_REJECT | MSG_CANCEL => {
            if rest.len() != 5 {
                return None;
            }
            let reason = Reason::from_code(rest[4]);
            let id = id(rest)?;
            Some(if kind == MSG_REJECT {
                FileMessage::Reject { id, reason }
            } else {
                FileMessage::Cancel { id, reason }
            })
        }
        MSG_CHUNK => {
            let id = id(rest)?;
            let seq = u32::from_le_bytes(rest.get(4..8)?.try_into().ok()?);
            let data = rest.get(8..)?.to_vec();
            Some(FileMessage::Chunk { id, seq, data })
        }
        MSG_DONE => {
            if rest.len() != 36 {
                return None;
            }
            let mut sha256 = [0u8; 32];
            sha256.copy_from_slice(&rest[4..36]);
            Some(FileMessage::Done {
                id: id(rest)?,
                sha256,
            })
        }
        MSG_ACK => {
            if rest.len() != 12 {
                return None;
            }
            let received = u64::from_le_bytes(rest[4..12].try_into().ok()?);
            Some(FileMessage::Ack {
                id: id(rest)?,
                received,
            })
        }
        _ => None,
    }
}

/// Mengkode satu pesan channel `"file"`.
pub fn encode(message: &FileMessage) -> Vec<u8> {
    let mut out = Vec::new();
    match message {
        FileMessage::Offer { id, size, name } => {
            let raw = name.as_bytes();
            out.push(MSG_OFFER);
            out.extend_from_slice(&id.to_le_bytes());
            out.extend_from_slice(&size.to_le_bytes());
            out.extend_from_slice(&(raw.len() as u16).to_le_bytes());
            out.extend_from_slice(raw);
        }
        FileMessage::Accept { id } => {
            out.push(MSG_ACCEPT);
            out.extend_from_slice(&id.to_le_bytes());
        }
        FileMessage::Reject { id, reason } => {
            out.push(MSG_REJECT);
            out.extend_from_slice(&id.to_le_bytes());
            out.push(reason.code());
        }
        FileMessage::Chunk { id, seq, data } => {
            out.push(MSG_CHUNK);
            out.extend_from_slice(&id.to_le_bytes());
            out.extend_from_slice(&seq.to_le_bytes());
            out.extend_from_slice(data);
        }
        FileMessage::Done { id, sha256 } => {
            out.push(MSG_DONE);
            out.extend_from_slice(&id.to_le_bytes());
            out.extend_from_slice(sha256);
        }
        FileMessage::Cancel { id, reason } => {
            out.push(MSG_CANCEL);
            out.extend_from_slice(&id.to_le_bytes());
            out.push(reason.code());
        }
        FileMessage::Ack { id, received } => {
            out.push(MSG_ACK);
            out.extend_from_slice(&id.to_le_bytes());
            out.extend_from_slice(&received.to_le_bytes());
        }
    }
    out
}

/// Nama perangkat DOS yang masih istimewa di Windows modern. Membuat berkas
/// bernama `CON` atau `LPT1` tidak membuat berkas — ia berbicara dengan
/// perangkat.
const DOS_DEVICES: [&str; 22] = [
    "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8",
    "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
];

/// Membersihkan nama berkas kiriman menjadi nama yang aman ditulis di
/// Windows. Selalu mengembalikan nama yang tidak kosong.
pub fn sanitize_name(raw: &str) -> String {
    // Komponen direktori dibuang lebih dulu, baik gaya Windows maupun POSIX,
    // supaya "..\\..\\Windows\\System32\\drivers\\etc\\hosts" menjadi
    // "hosts" dan bukan jalan keluar dari folder unduhan.
    let base = raw
        .rsplit(['/', '\\'])
        .next()
        .unwrap_or("")
        .trim_matches(|c: char| c.is_whitespace());
    let mut cleaned: String = base
        .chars()
        .filter(|c| {
            // Karakter kontrol, karakter ilegal NTFS, dan titik dua yang
            // membuka alternate data stream semuanya dibuang.
            !c.is_control() && !matches!(c, '<' | '>' | ':' | '"' | '|' | '?' | '*')
        })
        .collect();
    // Titik dan spasi di ujung dihapus diam-diam oleh Windows, sehingga
    // "laporan.exe " bisa berakhir sebagai "laporan.exe" setelah pengguna
    // melihat nama yang lain. Dipangkas di sini supaya yang dilihat pengguna
    // sama dengan yang ditulis ke disk.
    while cleaned.ends_with('.') || cleaned.ends_with(' ') {
        cleaned.pop();
    }
    let trimmed = cleaned.trim_start().to_string();
    if trimmed.is_empty() || trimmed == ".." || trimmed == "." {
        return FALLBACK_NAME.to_string();
    }
    let stem_upper = trimmed.split('.').next().unwrap_or("").to_ascii_uppercase();
    let mut name = if DOS_DEVICES.contains(&stem_upper.as_str()) {
        format!("_{trimmed}")
    } else {
        trimmed
    };
    if name.chars().count() > MAX_NAME_CHARS {
        // Pemotongan menjaga ekstensi: nama panjang tetap bisa dibuka dengan
        // aplikasi yang benar, dan ekstensi yang hilang justru membuat
        // berkas tampak lebih tidak berbahaya dari aslinya.
        let ext: String = match name.rsplit_once('.') {
            Some((_, ext)) if !ext.is_empty() && ext.chars().count() <= 10 => format!(".{ext}"),
            _ => String::new(),
        };
        let keep = MAX_NAME_CHARS - ext.chars().count();
        name = name.chars().take(keep).collect::<String>() + &ext;
    }
    name
}

/// Apakah ekstensinya termasuk yang dijalankan Windows begitu diklik dua
/// kali. Dipakai antarmuka untuk memberi peringatan — **bukan** untuk
/// memblokir: memblokir diam-diam akan membuat transfer sah gagal tanpa
/// penjelasan, sedangkan peringatan menaruh keputusan di tangan pemilik PC.
pub fn is_risky_extension(name: &str) -> bool {
    const RISKY: [&str; 17] = [
        "exe", "msi", "bat", "cmd", "com", "scr", "pif", "ps1", "vbs", "vbe", "js", "jse", "wsf",
        "wsh", "hta", "cpl", "lnk",
    ];
    match name.rsplit_once('.') {
        Some((_, ext)) => RISKY.contains(&ext.to_ascii_lowercase().as_str()),
        None => false,
    }
}

/// Keadaan penerima satu berkas.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Phase {
    /// Tawaran sudah masuk, menunggu keputusan pengguna.
    Offered,
    /// Diterima; potongan boleh masuk.
    Receiving,
    /// Selesai dan terverifikasi.
    Complete,
    /// Berakhir tanpa hasil.
    Failed(Reason),
}

/// Apa yang harus dilakukan pemanggil setelah menyuapkan satu pesan.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Action {
    /// Tidak ada yang perlu dikirim; tampilkan kemajuan saja.
    None,
    /// Tanyakan ke pengguna, lalu panggil [`Receiver::accept`] atau
    /// [`Receiver::reject`].
    AskUser {
        name: String,
        size: u64,
        risky: bool,
    },
    /// Tulis byte ini ke berkas sementara, lalu kirim `Ack`.
    Write { data: Vec<u8>, ack: u64 },
    /// Berkas utuh dan terverifikasi; pindahkan dari berkas sementara.
    Finish,
    /// Hentikan dan kirim `Cancel` dengan alasan ini.
    Abort(Reason),
}

/// Mesin keadaan penerima. Tidak menyentuh disk dan tidak menyentuh jaringan:
/// ia hanya memutuskan. Itu yang membuatnya bisa diuji tanpa Windows, tanpa
/// GPU, dan tanpa sesi nyata.
#[derive(Clone, Debug)]
pub struct Receiver {
    id: u32,
    name: String,
    declared: u64,
    received: u64,
    next_seq: u32,
    phase: Phase,
    hasher: Sha256,
    max_bytes: u64,
}

impl Receiver {
    /// Membuat penerima dari sebuah OFFER. Nama langsung dibersihkan di sini
    /// supaya tidak ada jalur mana pun yang sempat memegang nama mentah.
    pub fn from_offer(id: u32, size: u64, raw_name: &str, max_bytes: u64) -> Receiver {
        let name = sanitize_name(raw_name);
        let phase = if size == 0 || size > max_bytes.min(MAX_FILE_BYTES) {
            // Berkas nol byte ditolak bersama yang kebesaran: tidak ada
            // gunanya, dan ia adalah satu-satunya ukuran yang membuat DONE
            // sah tiba sebelum satu potongan pun dikirim.
            Phase::Failed(Reason::TooLarge)
        } else {
            Phase::Offered
        };
        Receiver {
            id,
            name,
            declared: size,
            received: 0,
            next_seq: 0,
            phase,
            hasher: Sha256::new(),
            max_bytes,
        }
    }

    pub fn id(&self) -> u32 {
        self.id
    }
    pub fn name(&self) -> &str {
        &self.name
    }
    pub fn declared_size(&self) -> u64 {
        self.declared
    }
    pub fn received(&self) -> u64 {
        self.received
    }
    pub fn phase(&self) -> &Phase {
        &self.phase
    }

    /// Kemajuan 0..=100. Dibulatkan ke bawah supaya tidak pernah menampilkan
    /// 100% sebelum byte terakhir benar-benar masuk.
    pub fn percent(&self) -> u8 {
        if self.declared == 0 {
            return 0;
        }
        ((self.received.min(self.declared) * 100) / self.declared) as u8
    }

    /// Pengguna menyetujui. Mengembalikan pesan ACCEPT yang harus dikirim.
    pub fn accept(&mut self) -> Option<FileMessage> {
        if self.phase != Phase::Offered {
            return None;
        }
        self.phase = Phase::Receiving;
        Some(FileMessage::Accept { id: self.id })
    }

    /// Pengguna menolak.
    pub fn reject(&mut self, reason: Reason) -> FileMessage {
        self.phase = Phase::Failed(reason);
        FileMessage::Reject {
            id: self.id,
            reason,
        }
    }

    /// Menyuapkan satu pesan yang sudah didekode.
    pub fn handle(&mut self, message: &FileMessage) -> Action {
        // Pesan untuk transfer lain tidak boleh menyentuh keadaan ini. Tanpa
        // penjagaan ini, pengirim bisa membatalkan transfer yang sedang
        // berjalan dengan menyebut id yang salah.
        let other_id = match message {
            FileMessage::Offer { id, .. }
            | FileMessage::Accept { id }
            | FileMessage::Reject { id, .. }
            | FileMessage::Chunk { id, .. }
            | FileMessage::Done { id, .. }
            | FileMessage::Cancel { id, .. }
            | FileMessage::Ack { id, .. } => *id,
        };
        if other_id != self.id {
            return Action::None;
        }
        if let Phase::Failed(_) | Phase::Complete = self.phase {
            return Action::None;
        }
        match message {
            FileMessage::Cancel { reason, .. } => {
                self.phase = Phase::Failed(*reason);
                Action::None
            }
            FileMessage::Chunk { seq, data, .. } => {
                if self.phase != Phase::Receiving {
                    // Potongan sebelum ACCEPT berarti pengirim memaksa:
                    // menulisnya akan membuat penolakan pengguna tidak ada
                    // artinya.
                    return self.fail(Reason::Protocol);
                }
                if *seq != self.next_seq
                    || data.is_empty()
                    || data.len() > MAX_CHUNK_BYTES
                    || self.received + data.len() as u64 > self.declared
                {
                    return self.fail(Reason::Protocol);
                }
                self.hasher.update(data);
                self.received += data.len() as u64;
                self.next_seq += 1;
                Action::Write {
                    data: data.clone(),
                    ack: self.received,
                }
            }
            FileMessage::Done { sha256, .. } => {
                if self.phase != Phase::Receiving || self.received != self.declared {
                    return self.fail(Reason::Protocol);
                }
                let digest: [u8; 32] = self.hasher.clone().finalize().into();
                if &digest != sha256 {
                    return self.fail(Reason::HashMismatch);
                }
                self.phase = Phase::Complete;
                Action::Finish
            }
            FileMessage::Offer { size, name, .. } => {
                // OFFER kedua dengan id yang sama bukan percobaan ulang yang
                // sah: id dipilih pengirim dan harus baru setiap berkas.
                let _ = (size, name);
                self.fail(Reason::Protocol)
            }
            // ACCEPT/REJECT/ACK adalah pesan untuk pengirim; menerimanya di
            // sisi penerima berarti ada yang salah membaca arah.
            _ => self.fail(Reason::Protocol),
        }
    }

    fn fail(&mut self, reason: Reason) -> Action {
        self.phase = Phase::Failed(reason);
        Action::Abort(reason)
    }

    /// Panggilan ketika transfer belum selesai tetapi sesi putus.
    pub fn disconnected(&mut self) {
        if self.phase != Phase::Complete {
            self.phase = Phase::Failed(Reason::Disconnected);
        }
    }

    /// Batas yang dipakai penerima ini, untuk ditampilkan di antarmuka.
    pub fn limit(&self) -> u64 {
        self.max_bytes.min(MAX_FILE_BYTES)
    }
}

/// Memecah berkas menjadi daftar nomor potongan. Dipakai sisi pengirim untuk
/// tahu berapa potongan yang akan dikirim sebelum membaca apa pun.
pub fn chunk_count(size: u64, chunk: usize) -> u64 {
    if chunk == 0 || size == 0 {
        return 0;
    }
    size.div_ceil(chunk as u64)
}

/// Ukuran berkas dalam bentuk yang dibaca manusia. Satu desimal untuk
/// MB/GB, tanpa desimal untuk byte dan KB — angka seperti "1,0 KB" lebih
/// berisik daripada informatif.
pub fn human_bytes(bytes: u64) -> String {
    const KB: f64 = 1024.0;
    let b = bytes as f64;
    if bytes < 1024 {
        format!("{bytes} B")
    } else if b < KB * KB {
        format!("{:.0} KB", b / KB)
    } else if b < KB * KB * KB {
        format!("{:.1} MB", b / (KB * KB))
    } else {
        format!("{:.1} GB", b / (KB * KB * KB))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sha_of(data: &[u8]) -> [u8; 32] {
        Sha256::digest(data).into()
    }

    fn jalankan(data: &[u8], potongan: usize) -> (Receiver, Vec<u8>) {
        let mut r = Receiver::from_offer(7, data.len() as u64, "catatan.txt", MAX_FILE_BYTES);
        assert!(r.accept().is_some());
        let mut tulisan = Vec::new();
        for (i, bagian) in data.chunks(potongan).enumerate() {
            let aksi = r.handle(&FileMessage::Chunk {
                id: 7,
                seq: i as u32,
                data: bagian.to_vec(),
            });
            match aksi {
                Action::Write { data, ack } => {
                    tulisan.extend_from_slice(&data);
                    assert_eq!(ack, tulisan.len() as u64);
                }
                lain => panic!("potongan {i} ditolak: {lain:?}"),
            }
        }
        let aksi = r.handle(&FileMessage::Done {
            id: 7,
            sha256: sha_of(data),
        });
        assert_eq!(aksi, Action::Finish);
        (r, tulisan)
    }

    #[test]
    fn transfer_utuh_menghasilkan_byte_yang_sama() {
        let data: Vec<u8> = (0..5000u32).map(|i| (i % 251) as u8).collect();
        let (r, hasil) = jalankan(&data, 1024);
        assert_eq!(hasil, data);
        assert_eq!(*r.phase(), Phase::Complete);
        assert_eq!(r.percent(), 100);
    }

    #[test]
    fn potongan_sebelum_accept_membatalkan_transfer() {
        let mut r = Receiver::from_offer(1, 10, "a.bin", MAX_FILE_BYTES);
        let aksi = r.handle(&FileMessage::Chunk {
            id: 1,
            seq: 0,
            data: vec![1, 2, 3],
        });
        assert_eq!(aksi, Action::Abort(Reason::Protocol));
        assert_eq!(*r.phase(), Phase::Failed(Reason::Protocol));
    }

    #[test]
    fn nomor_urut_yang_melompat_ditolak() {
        let mut r = Receiver::from_offer(1, 10, "a.bin", MAX_FILE_BYTES);
        r.accept();
        assert!(matches!(
            r.handle(&FileMessage::Chunk {
                id: 1,
                seq: 0,
                data: vec![1, 2]
            }),
            Action::Write { .. }
        ));
        let aksi = r.handle(&FileMessage::Chunk {
            id: 1,
            seq: 2,
            data: vec![3, 4],
        });
        assert_eq!(aksi, Action::Abort(Reason::Protocol));
    }

    #[test]
    fn potongan_ganda_ditolak_bukan_ditulis_dua_kali() {
        let mut r = Receiver::from_offer(1, 10, "a.bin", MAX_FILE_BYTES);
        r.accept();
        r.handle(&FileMessage::Chunk {
            id: 1,
            seq: 0,
            data: vec![1, 2],
        });
        let aksi = r.handle(&FileMessage::Chunk {
            id: 1,
            seq: 0,
            data: vec![1, 2],
        });
        assert_eq!(aksi, Action::Abort(Reason::Protocol));
        assert_eq!(r.received(), 2);
    }

    #[test]
    fn byte_melebihi_ukuran_yang_dijanjikan_ditolak() {
        let mut r = Receiver::from_offer(1, 4, "a.bin", MAX_FILE_BYTES);
        r.accept();
        let aksi = r.handle(&FileMessage::Chunk {
            id: 1,
            seq: 0,
            data: vec![0; 5],
        });
        assert_eq!(aksi, Action::Abort(Reason::Protocol));
    }

    #[test]
    fn potongan_lebih_besar_dari_batas_ditolak() {
        let mut r = Receiver::from_offer(1, 10_000_000, "a.bin", MAX_FILE_BYTES);
        r.accept();
        let aksi = r.handle(&FileMessage::Chunk {
            id: 1,
            seq: 0,
            data: vec![0; MAX_CHUNK_BYTES + 1],
        });
        assert_eq!(aksi, Action::Abort(Reason::Protocol));
    }

    #[test]
    fn done_dengan_hash_salah_tidak_pernah_jadi_finish() {
        let data = b"isi berkas".to_vec();
        let mut r = Receiver::from_offer(1, data.len() as u64, "a.bin", MAX_FILE_BYTES);
        r.accept();
        r.handle(&FileMessage::Chunk {
            id: 1,
            seq: 0,
            data: data.clone(),
        });
        let aksi = r.handle(&FileMessage::Done {
            id: 1,
            sha256: sha_of(b"isi lain"),
        });
        assert_eq!(aksi, Action::Abort(Reason::HashMismatch));
        assert_eq!(*r.phase(), Phase::Failed(Reason::HashMismatch));
    }

    #[test]
    fn done_sebelum_semua_byte_tiba_ditolak() {
        let mut r = Receiver::from_offer(1, 100, "a.bin", MAX_FILE_BYTES);
        r.accept();
        r.handle(&FileMessage::Chunk {
            id: 1,
            seq: 0,
            data: vec![9; 10],
        });
        let aksi = r.handle(&FileMessage::Done {
            id: 1,
            sha256: [0; 32],
        });
        assert_eq!(aksi, Action::Abort(Reason::Protocol));
    }

    #[test]
    fn pesan_dengan_id_lain_tidak_menyentuh_transfer_ini() {
        let mut r = Receiver::from_offer(1, 10, "a.bin", MAX_FILE_BYTES);
        r.accept();
        let aksi = r.handle(&FileMessage::Cancel {
            id: 2,
            reason: Reason::User,
        });
        assert_eq!(aksi, Action::None);
        assert_eq!(*r.phase(), Phase::Receiving);
    }

    #[test]
    fn berkas_kebesaran_dan_nol_byte_ditolak_sejak_tawaran() {
        let besar = Receiver::from_offer(1, MAX_FILE_BYTES + 1, "a.bin", MAX_FILE_BYTES);
        assert_eq!(*besar.phase(), Phase::Failed(Reason::TooLarge));
        let kosong = Receiver::from_offer(1, 0, "a.bin", MAX_FILE_BYTES);
        assert_eq!(*kosong.phase(), Phase::Failed(Reason::TooLarge));
        let batas_sendiri = Receiver::from_offer(1, 2_000_000, "a.bin", 1_000_000);
        assert_eq!(*batas_sendiri.phase(), Phase::Failed(Reason::TooLarge));
    }

    #[test]
    fn sesi_putus_menggagalkan_transfer_yang_belum_selesai() {
        let mut r = Receiver::from_offer(1, 10, "a.bin", MAX_FILE_BYTES);
        r.accept();
        r.disconnected();
        assert_eq!(*r.phase(), Phase::Failed(Reason::Disconnected));
        let data = b"halo".to_vec();
        let (mut selesai, _) = jalankan(&data, 2);
        selesai.disconnected();
        assert_eq!(*selesai.phase(), Phase::Complete);
    }

    #[test]
    fn nama_dengan_jalur_dibuang_sampai_nama_berkasnya_saja() {
        assert_eq!(
            sanitize_name("..\\..\\Windows\\System32\\drivers\\etc\\hosts"),
            "hosts"
        );
        assert_eq!(sanitize_name("/etc/passwd"), "passwd");
        assert_eq!(
            sanitize_name("C:\\Users\\xykal\\rahasia.txt"),
            "rahasia.txt"
        );
    }

    #[test]
    fn nama_kosong_atau_hanya_titik_jadi_nama_cadangan() {
        assert_eq!(sanitize_name(""), FALLBACK_NAME);
        assert_eq!(sanitize_name(".."), FALLBACK_NAME);
        assert_eq!(sanitize_name("   "), FALLBACK_NAME);
        assert_eq!(sanitize_name("///"), FALLBACK_NAME);
    }

    #[test]
    fn karakter_ilegal_dan_kontrol_dibuang() {
        assert_eq!(sanitize_name("lap\u{0}or*an?.txt"), "laporan.txt");
        assert_eq!(sanitize_name("data:stream.txt"), "datastream.txt");
        assert_eq!(sanitize_name("baris\nbaru.txt"), "barisbaru.txt");
    }

    #[test]
    fn titik_dan_spasi_di_ujung_dipangkas() {
        assert_eq!(sanitize_name("laporan.exe "), "laporan.exe");
        assert_eq!(sanitize_name("laporan.txt..."), "laporan.txt");
    }

    #[test]
    fn nama_perangkat_dos_diberi_awalan() {
        assert_eq!(sanitize_name("CON"), "_CON");
        assert_eq!(sanitize_name("lpt1.txt"), "_lpt1.txt");
        assert_eq!(sanitize_name("nul.log"), "_nul.log");
        // Nama yang hanya mirip tidak ikut diubah.
        assert_eq!(sanitize_name("console.txt"), "console.txt");
    }

    #[test]
    fn nama_panjang_dipotong_tapi_ekstensinya_bertahan() {
        let panjang = "a".repeat(300) + ".tar.gz";
        let hasil = sanitize_name(&panjang);
        assert_eq!(hasil.chars().count(), MAX_NAME_CHARS);
        assert!(hasil.ends_with(".gz"));
    }

    #[test]
    fn ekstensi_berbahaya_dikenali_untuk_peringatan() {
        assert!(is_risky_extension("pasang.EXE"));
        assert!(is_risky_extension("skrip.ps1"));
        assert!(is_risky_extension("pintasan.lnk"));
        assert!(!is_risky_extension("foto.png"));
        assert!(!is_risky_extension("tanpa-ekstensi"));
    }

    #[test]
    fn nama_di_tawaran_sudah_bersih_sebelum_ditanyakan_ke_pengguna() {
        let r = Receiver::from_offer(1, 10, "../../rahasia.txt", MAX_FILE_BYTES);
        assert_eq!(r.name(), "rahasia.txt");
    }

    #[test]
    fn kode_perjalanan_pulang_pergi_untuk_semua_pesan() {
        let pesan = [
            FileMessage::Offer {
                id: 9,
                size: 1234,
                name: "foto besar.png".into(),
            },
            FileMessage::Accept { id: 9 },
            FileMessage::Reject {
                id: 9,
                reason: Reason::User,
            },
            FileMessage::Chunk {
                id: 9,
                seq: 3,
                data: vec![1, 2, 3, 4],
            },
            FileMessage::Done {
                id: 9,
                sha256: [7; 32],
            },
            FileMessage::Cancel {
                id: 9,
                reason: Reason::HashMismatch,
            },
            FileMessage::Ack {
                id: 9,
                received: 4096,
            },
        ];
        for p in pesan {
            assert_eq!(decode(&encode(&p)), Some(p.clone()), "gagal untuk {p:?}");
        }
    }

    #[test]
    fn pesan_cacat_ditolak_bukan_ditebak() {
        assert_eq!(decode(&[]), None);
        assert_eq!(decode(&[0xFF, 1, 2, 3]), None);
        assert_eq!(decode(&[MSG_ACCEPT, 1, 2]), None);
        assert_eq!(decode(&[MSG_DONE, 1, 0, 0, 0]), None);
        // OFFER dengan name_len yang tidak cocok isinya.
        let mut cacat = vec![MSG_OFFER];
        cacat.extend_from_slice(&1u32.to_le_bytes());
        cacat.extend_from_slice(&10u64.to_le_bytes());
        cacat.extend_from_slice(&9u16.to_le_bytes());
        cacat.extend_from_slice(b"pendek");
        assert_eq!(decode(&cacat), None);
    }

    #[test]
    fn nama_bukan_utf8_ditolak() {
        let mut pesan = vec![MSG_OFFER];
        pesan.extend_from_slice(&1u32.to_le_bytes());
        pesan.extend_from_slice(&10u64.to_le_bytes());
        pesan.extend_from_slice(&2u16.to_le_bytes());
        pesan.extend_from_slice(&[0xFF, 0xFE]);
        assert_eq!(decode(&pesan), None);
    }

    #[test]
    fn alasan_tak_dikenal_jadi_pelanggaran_protokol() {
        assert_eq!(Reason::from_code(99), Reason::Protocol);
        for r in [
            Reason::User,
            Reason::TooLarge,
            Reason::Protocol,
            Reason::HashMismatch,
            Reason::Io,
            Reason::Disconnected,
        ] {
            assert_eq!(Reason::from_code(r.code()), r);
        }
    }

    #[test]
    fn kemajuan_tidak_pernah_100_sebelum_byte_terakhir() {
        let mut r = Receiver::from_offer(1, 1000, "a.bin", MAX_FILE_BYTES);
        r.accept();
        r.handle(&FileMessage::Chunk {
            id: 1,
            seq: 0,
            data: vec![0; 999],
        });
        assert_eq!(r.percent(), 99);
        r.handle(&FileMessage::Chunk {
            id: 1,
            seq: 1,
            data: vec![0; 1],
        });
        assert_eq!(r.percent(), 100);
    }

    #[test]
    fn jumlah_potongan_dihitung_membulat_ke_atas() {
        assert_eq!(chunk_count(0, 1024), 0);
        assert_eq!(chunk_count(1, 1024), 1);
        assert_eq!(chunk_count(1024, 1024), 1);
        assert_eq!(chunk_count(1025, 1024), 2);
        assert_eq!(chunk_count(100, 0), 0);
    }

    #[test]
    fn ukuran_dibaca_manusia() {
        assert_eq!(human_bytes(0), "0 B");
        assert_eq!(human_bytes(999), "999 B");
        assert_eq!(human_bytes(2048), "2 KB");
        assert_eq!(human_bytes(5 * 1024 * 1024), "5.0 MB");
        assert_eq!(human_bytes(3 * 1024 * 1024 * 1024), "3.0 GB");
    }

    #[test]
    fn transfer_dengan_potongan_sebesar_batas_tetap_jalan() {
        let data: Vec<u8> = (0..(MAX_CHUNK_BYTES * 2 + 17))
            .map(|i| (i % 256) as u8)
            .collect();
        let (_, hasil) = jalankan(&data, MAX_CHUNK_BYTES);
        assert_eq!(hasil, data);
    }
}
