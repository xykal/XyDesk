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
//! 0x08 DONE_OK id:u32
//! ```
//!
//! ## Konfirmasi, bukan kesimpulan
//!
//! Sampai 6.11.11 keberhasilan disimpulkan dari ketiadaan `CANCEL`: penerima
//! diam kalau berkasnya tersimpan. Diam adalah jawaban yang sama persis
//! dengan "koneksi mati sebelum sempat mengeluh", jadi pengirim mengaku
//! berhasil untuk berkas yang tidak pernah ada di disk. `DONE_OK` dikirim
//! penerima **setelah** hash cocok dan berkas dipindahkan ke tempat
//! permanennya — bukan saat byte terakhir tiba. Penerima lama tidak
//! mengenalnya dan tetap diam; pengirim memperlakukan itu seperti dulu
//! ("terkirim, belum dikonfirmasi") dan tidak pernah menampilkannya sebagai
//! kegagalan.
//!
//! `seq` dimulai dari 0 dan **wajib** naik satu per satu. SCTP di WebRTC
//! sudah memberi pengiriman terurut dan andal pada channel bawaan, jadi
//! nomor urut di sini bukan untuk menyusun ulang melainkan untuk menolak
//! aliran yang menyimpang: potongan ganda, potongan yang dilewati, atau
//! pengirim yang memulai di tengah.
//!
//! ## Tanda siap
//!
//! Begitu host selesai memasang pendengarnya, ia mengirim `ACK` dengan
//! `id = 0` — id yang tidak pernah dipakai transfer sungguhan. Pengirim
//! wajib menunggu tanda ini sebelum mengirim `OFFER`: channel sudah OPEN di
//! sisi pengirim beberapa saat sebelum penerima sempat memasang handler,
//! dan `OFFER` yang tiba di celah itu hilang tanpa jejak — transfer lalu
//! menggantung tanpa satu pun pesan kesalahan.
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
/// Penerima: berkas sudah terverifikasi dan tersimpan permanen.
pub const MSG_DONE_OK: u8 = 0x08;

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

/// Isi satu potongan yang **dikirim**: 16 KiB.
///
/// Batas protokol tetap 64 KiB supaya pengirim lama tetap diterima, tetapi
/// mengirim sebesar itu sendiri tidak aman: satu pesan SCTP dibatasi 64 KiB
/// **termasuk** 9 byte header `CHUNK`, jadi potongan 65536 byte menghasilkan
/// pesan 65545 byte yang tidak pernah berangkat — `send` menggantung tanpa
/// satu pun pesan kesalahan. Itu ditemukan oleh uji loopback arah host →
/// client, bukan oleh uji unit mana pun.
pub const SEND_CHUNK_BYTES: usize = 16 * 1024;

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
    Offer {
        id: u32,
        size: u64,
        name: String,
    },
    Accept {
        id: u32,
    },
    Reject {
        id: u32,
        reason: Reason,
    },
    Chunk {
        id: u32,
        seq: u32,
        data: Vec<u8>,
    },
    Done {
        id: u32,
        sha256: [u8; 32],
    },
    Cancel {
        id: u32,
        reason: Reason,
    },
    Ack {
        id: u32,
        received: u64,
    },
    /// Konfirmasi akhir dari penerima: hash cocok, berkas sudah di tempatnya.
    DoneOk {
        id: u32,
    },
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
        MSG_DONE_OK => {
            if rest.len() != 4 {
                return None;
            }
            Some(FileMessage::DoneOk { id: id(rest)? })
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
        FileMessage::DoneOk { id } => {
            out.push(MSG_DONE_OK);
            out.extend_from_slice(&id.to_le_bytes());
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

/// Keadaan pengirim satu berkas (arah host → client).
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum SendState {
    /// Menunggu tanda siap dari penerima.
    WaitReady,
    /// `OFFER` sudah dikirim, menunggu `ACCEPT`.
    Offered,
    /// Boleh mengirim potongan.
    Sending,
    /// Semua byte keluar; tinggal `DONE`.
    Finishing,
    /// `DONE` terkirim; penerima belum (atau tidak akan) mengonfirmasi.
    Done,
    /// `DONE_OK` diterima: berkas benar-benar tersimpan di seberang. Penerima
    /// lama tidak pernah mengirimnya, jadi keadaan ini **tidak** wajib
    /// dicapai untuk disebut berhasil.
    Confirmed,
    Failed(Reason),
}

/// Mesin keadaan pengirim — cermin dari [`Receiver`], dan kembaran
/// `FileSender` di `android-native/xyadapt/.../FileWire.kt`.
///
/// Seperti penerima, ia tidak menyentuh disk dan tidak menyentuh jaringan:
/// byte dibaca pemanggil, di sini hanya diputuskan **boleh atau tidak** dan
/// **berapa banyak**. Tiga aturan yang membuatnya bukan sekadar perulangan:
///
/// - **Tanda siap wajib ditunggu.** Channel sudah OPEN di sisi kita jauh
///   sebelum lawan memasang pendengarnya; `OFFER` yang tiba di celah itu
///   hilang tanpa jejak dan transfer menggantung tanpa pesan kesalahan.
/// - **Laju ditahan ACK**, bukan oleh kecepatan membaca disk. Tanpa jendela,
///   `CHUNK` menumpuk di antrean WebRTC sampai memori habis pada berkas
///   besar.
/// - **Kemajuan dihitung dari ACK**, yaitu byte yang benar-benar tertulis di
///   perangkat lawan — bukan byte yang baru keluar dari sini.
#[derive(Clone, Debug)]
pub struct Sender {
    id: u32,
    name: String,
    size: u64,
    chunk_size: usize,
    state: SendState,
    sent: u64,
    acked: u64,
    seq: u32,
}

impl Sender {
    /// Byte yang boleh "di udara" sebelum menunggu ACK.
    pub const WINDOW_BYTES: u64 = 512 * 1024;

    /// Nama dibersihkan di sini juga: yang ditawarkan harus sama dengan yang
    /// akan ditulis lawan, kalau tidak pengguna menyetujui nama yang lain.
    pub fn new(id: u32, raw_name: &str, size: u64) -> Sender {
        Sender {
            id,
            name: sanitize_name(raw_name),
            size,
            chunk_size: SEND_CHUNK_BYTES,
            state: SendState::WaitReady,
            sent: 0,
            acked: 0,
            seq: 0,
        }
    }

    pub fn id(&self) -> u32 {
        self.id
    }
    pub fn name(&self) -> &str {
        &self.name
    }
    pub fn size(&self) -> u64 {
        self.size
    }
    pub fn state(&self) -> SendState {
        self.state
    }
    pub fn acked(&self) -> u64 {
        self.acked
    }
    pub fn active(&self) -> bool {
        !matches!(
            self.state,
            SendState::Done | SendState::Confirmed | SendState::Failed(_)
        )
    }

    /// Benar bila penerima sudah menyatakan berkasnya tersimpan.
    pub fn confirmed(&self) -> bool {
        self.state == SendState::Confirmed
    }
    pub fn ready(&self) -> bool {
        self.state == SendState::Finishing
    }

    /// Kemajuan yang ditunjukkan ke pengguna: byte yang sampai di disk lawan.
    pub fn percent(&self) -> u8 {
        if self.size == 0 {
            return 0;
        }
        ((self.acked.min(self.size) * 100) / self.size) as u8
    }

    /// Menyuapkan satu pesan dari penerima. Mengembalikan pesan yang harus
    /// dikirim — hanya `OFFER`, saat tanda siap tiba.
    pub fn on_message(&mut self, message: &FileMessage) -> Option<FileMessage> {
        // Konfirmasi datang justru setelah pengirim berhenti aktif, jadi ia
        // diperiksa sebelum penjagaan di bawah. Konfirmasi untuk transfer
        // lain, atau yang datang sebelum DONE, diabaikan — bukan dipercaya.
        if let FileMessage::DoneOk { id } = message {
            if *id == self.id && self.state == SendState::Done {
                self.state = SendState::Confirmed;
            }
            return None;
        }
        if !self.active() {
            return None;
        }
        if let FileMessage::Ack { id: 0, .. } = message {
            if self.state != SendState::WaitReady {
                return None;
            }
            if self.size == 0 || self.size > MAX_FILE_BYTES {
                self.state = SendState::Failed(Reason::TooLarge);
                return None;
            }
            self.state = SendState::Offered;
            return Some(FileMessage::Offer {
                id: self.id,
                size: self.size,
                name: self.name.clone(),
            });
        }
        let other = match message {
            FileMessage::Offer { id, .. }
            | FileMessage::Accept { id }
            | FileMessage::Reject { id, .. }
            | FileMessage::Chunk { id, .. }
            | FileMessage::Done { id, .. }
            | FileMessage::Cancel { id, .. }
            | FileMessage::Ack { id, .. }
            | FileMessage::DoneOk { id } => *id,
        };
        if other != self.id {
            return None;
        }
        match message {
            FileMessage::Accept { .. } => {
                if self.state == SendState::Offered {
                    self.state = SendState::Sending;
                }
            }
            FileMessage::Reject { reason, .. } | FileMessage::Cancel { reason, .. } => {
                self.state = SendState::Failed(*reason);
            }
            FileMessage::Ack { received, .. } => {
                // ACK mundur atau melampaui ukuran berarti ada yang salah
                // membaca aliran; menampilkan kemajuan yang dikarang lebih
                // buruk daripada berhenti.
                if *received < self.acked || *received > self.size {
                    self.state = SendState::Failed(Reason::Protocol);
                } else {
                    self.acked = *received;
                }
            }
            // OFFER/CHUNK/DONE adalah pesan untuk penerima; menerimanya di
            // sini berarti ada yang salah membaca arah.
            _ => self.state = SendState::Failed(Reason::Protocol),
        }
        None
    }

    /// Berapa byte yang boleh dibaca dan dikirim sekarang; 0 berarti tunggu.
    pub fn allowance(&self) -> usize {
        if self.state != SendState::Sending {
            return 0;
        }
        let remaining = self.size.saturating_sub(self.sent);
        if remaining == 0 {
            return 0;
        }
        let in_flight = self.sent - self.acked;
        let room = Self::WINDOW_BYTES.saturating_sub(in_flight);
        if room < self.chunk_size as u64 && remaining > room {
            return 0;
        }
        remaining.min(self.chunk_size as u64).min(room) as usize
    }

    /// Membungkus potongan yang baru dibaca; `None` bila tidak boleh dikirim.
    pub fn chunk(&mut self, data: Vec<u8>) -> Option<FileMessage> {
        if self.state != SendState::Sending {
            return None;
        }
        if data.is_empty() || data.len() > self.chunk_size {
            self.state = SendState::Failed(Reason::Protocol);
            return None;
        }
        if self.sent + data.len() as u64 > self.size {
            self.state = SendState::Failed(Reason::Protocol);
            return None;
        }
        let message = FileMessage::Chunk {
            id: self.id,
            seq: self.seq,
            data,
        };
        self.seq += 1;
        if let FileMessage::Chunk { data, .. } = &message {
            self.sent += data.len() as u64;
        }
        if self.sent == self.size {
            self.state = SendState::Finishing;
        }
        Some(message)
    }

    /// `DONE` dengan SHA-256 berkas.
    pub fn finish(&mut self, sha256: [u8; 32]) -> Option<FileMessage> {
        if self.state != SendState::Finishing {
            return None;
        }
        self.state = SendState::Done;
        Some(FileMessage::Done {
            id: self.id,
            sha256,
        })
    }

    /// Dibatalkan dari sisi kita: pengguna, gagal baca, atau sesi berakhir.
    pub fn cancel(&mut self, reason: Reason) -> Option<FileMessage> {
        if !self.active() {
            return None;
        }
        self.state = SendState::Failed(reason);
        Some(FileMessage::Cancel {
            id: self.id,
            reason,
        })
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
            | FileMessage::Ack { id, .. }
            | FileMessage::DoneOk { id } => *id,
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
            // Konfirmasi milik pengirim di arah sebaliknya. Satu channel
            // dipakai dua arah, jadi ia boleh lewat tanpa dianggap
            // pelanggaran — tetapi tidak mengubah apa pun di sini.
            FileMessage::DoneOk { .. } => Action::None,
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

    /// Vektor byte yang **dihasilkan oleh kode Kotlin** di
    /// `android-native/xyadapt/.../FileWire.kt`, ditempel apa adanya.
    ///
    /// Dua sisi menulis pengkodeannya sendiri-sendiri; satu sisi yang keliru
    /// urutan byte (little-endian vs big-endian) atau keliru panjang nama
    /// tidak akan ketahuan oleh uji mana pun di satu bahasa saja — yang
    /// terlihat hanyalah transfer yang gagal di perangkat pengguna.
    #[test]
    fn vektor_dari_pengirim_kotlin_terbaca_sama() {
        let offer: Vec<u8> = vec![
            1, 68, 51, 34, 17, 203, 4, 251, 113, 31, 1, 0, 0, 15, 0, 108, 97, 112, 111, 114, 97,
            110, 32, 226, 154, 161, 46, 112, 100, 102,
        ];
        assert_eq!(
            decode(&offer),
            Some(FileMessage::Offer {
                id: 0x1122_3344,
                size: 1_234_567_890_123,
                name: "laporan ⚡.pdf".to_string(),
            })
        );
        assert_eq!(encode(&decode(&offer).unwrap()), offer);

        let chunk: Vec<u8> = vec![4, 7, 0, 0, 0, 4, 3, 2, 1, 1, 2, 3, 4, 5];
        assert_eq!(
            decode(&chunk),
            Some(FileMessage::Chunk {
                id: 7,
                seq: 0x0102_0304,
                data: vec![1, 2, 3, 4, 5],
            })
        );

        let mut done: Vec<u8> = vec![5, 7, 0, 0, 0];
        done.extend((1u8..=32).collect::<Vec<u8>>());
        let mut sha = [0u8; 32];
        sha.copy_from_slice(&done[5..37]);
        assert_eq!(
            decode(&done),
            Some(FileMessage::Done { id: 7, sha256: sha })
        );

        assert_eq!(
            decode(&[6, 7, 0, 0, 0, 3]),
            Some(FileMessage::Cancel {
                id: 7,
                reason: Reason::HashMismatch,
            })
        );
        assert_eq!(
            decode(&[2, 9, 0, 0, 0]),
            Some(FileMessage::Accept { id: 9 })
        );
        assert_eq!(
            decode(&[3, 9, 0, 0, 0, 1]),
            Some(FileMessage::Reject {
                id: 9,
                reason: Reason::TooLarge,
            })
        );
        assert_eq!(
            decode(&[7, 9, 0, 0, 0, 112, 17, 1, 0, 0, 0, 0, 0]),
            Some(FileMessage::Ack {
                id: 9,
                received: 70_000,
            })
        );
    }

    #[test]
    fn pengirim_menunggu_tanda_siap_sebelum_menawarkan() {
        let mut s = Sender::new(5, "catatan.txt", 100);
        assert_eq!(s.state(), SendState::WaitReady);
        assert_eq!(s.allowance(), 0);
        let offer = s.on_message(&FileMessage::Ack { id: 0, received: 0 });
        assert_eq!(
            offer,
            Some(FileMessage::Offer {
                id: 5,
                size: 100,
                name: "catatan.txt".to_string()
            })
        );
        assert_eq!(s.state(), SendState::Offered);
        // Potongan sebelum ACCEPT tetap tidak boleh keluar.
        assert_eq!(s.allowance(), 0);
        assert!(s.chunk(vec![1, 2, 3]).is_none());
    }

    #[test]
    fn pengirim_membersihkan_nama_sebelum_menawarkan() {
        let mut s = Sender::new(1, "C:\\Users\\x\\rahasia .txt ", 10);
        assert_eq!(s.name(), "rahasia .txt");
        let offer = s.on_message(&FileMessage::Ack { id: 0, received: 0 });
        assert!(
            matches!(offer, Some(FileMessage::Offer { ref name, .. }) if name == "rahasia .txt")
        );
    }

    #[test]
    fn pengirim_menolak_ukuran_nol_dan_kebesaran() {
        let mut kosong = Sender::new(1, "a.txt", 0);
        assert!(kosong
            .on_message(&FileMessage::Ack { id: 0, received: 0 })
            .is_none());
        assert_eq!(kosong.state(), SendState::Failed(Reason::TooLarge));

        let mut besar = Sender::new(1, "a.bin", MAX_FILE_BYTES + 1);
        assert!(besar
            .on_message(&FileMessage::Ack { id: 0, received: 0 })
            .is_none());
        assert_eq!(besar.state(), SendState::Failed(Reason::TooLarge));
    }

    #[test]
    fn jendela_pengirim_menahan_laju_sampai_ack_datang() {
        let mut s = Sender::new(5, "besar.bin", 10 * 1024 * 1024);
        s.on_message(&FileMessage::Ack { id: 0, received: 0 });
        s.on_message(&FileMessage::Accept { id: 5 });
        let mut keluar = 0u64;
        while s.allowance() > 0 {
            let n = s.allowance();
            assert!(s.chunk(vec![0u8; n]).is_some());
            keluar += n as u64;
        }
        assert_eq!(keluar, Sender::WINDOW_BYTES);
        assert_eq!(s.percent(), 0);
        s.on_message(&FileMessage::Ack {
            id: 5,
            received: Sender::WINDOW_BYTES,
        });
        assert!(s.allowance() > 0);
        assert_eq!(s.percent(), 5);
    }

    #[test]
    fn ack_mundur_atau_melampaui_ukuran_menggagalkan_pengirim() {
        let mut s = Sender::new(5, "a.bin", 1000);
        s.on_message(&FileMessage::Ack { id: 0, received: 0 });
        s.on_message(&FileMessage::Accept { id: 5 });
        s.on_message(&FileMessage::Ack {
            id: 5,
            received: 500,
        });
        s.on_message(&FileMessage::Ack {
            id: 5,
            received: 400,
        });
        assert_eq!(s.state(), SendState::Failed(Reason::Protocol));

        let mut t = Sender::new(5, "a.bin", 1000);
        t.on_message(&FileMessage::Ack { id: 0, received: 0 });
        t.on_message(&FileMessage::Accept { id: 5 });
        t.on_message(&FileMessage::Ack {
            id: 5,
            received: 1001,
        });
        assert_eq!(t.state(), SendState::Failed(Reason::Protocol));
    }

    #[test]
    fn pesan_untuk_transfer_lain_tidak_menyentuh_pengirim() {
        let mut s = Sender::new(5, "a.bin", 1000);
        s.on_message(&FileMessage::Ack { id: 0, received: 0 });
        s.on_message(&FileMessage::Accept { id: 5 });
        s.on_message(&FileMessage::Cancel {
            id: 99,
            reason: Reason::User,
        });
        s.on_message(&FileMessage::Ack {
            id: 99,
            received: 999,
        });
        assert_eq!(s.state(), SendState::Sending);
        assert_eq!(s.acked(), 0);
    }

    #[test]
    fn penolakan_penerima_menghentikan_pengirim() {
        let mut s = Sender::new(5, "a.bin", 1000);
        s.on_message(&FileMessage::Ack { id: 0, received: 0 });
        s.on_message(&FileMessage::Reject {
            id: 5,
            reason: Reason::User,
        });
        assert_eq!(s.state(), SendState::Failed(Reason::User));
        assert_eq!(s.allowance(), 0);
        assert!(s.cancel(Reason::User).is_none());
    }

    #[test]
    fn done_ditolak_sebelum_semua_byte_keluar() {
        let mut s = Sender::new(5, "a.bin", 1000);
        s.on_message(&FileMessage::Ack { id: 0, received: 0 });
        s.on_message(&FileMessage::Accept { id: 5 });
        s.chunk(vec![0u8; 10]);
        assert!(s.finish([0u8; 32]).is_none());
        assert_eq!(s.state(), SendState::Sending);
    }

    /// Dua mesin keadaan yang saling bicara, tanpa jaringan di antaranya.
    /// Inilah satu-satunya uji yang bisa membuktikan bahwa aturan pengirim
    /// dan aturan penerima benar-benar cocok, bukan dua tafsir berbeda atas
    /// dokumen yang sama.
    #[test]
    fn pengirim_dan_penerima_saling_bicara_sampai_berkas_utuh() {
        let data: Vec<u8> = (0..200_000u32).map(|i| (i % 251) as u8).collect();
        let mut s = Sender::new(77, "gambar besar.png", data.len() as u64);
        let mut r: Option<Receiver> = None;
        let mut tulisan: Vec<u8> = Vec::new();
        let mut offset = 0usize;

        // Penerima mengirim tanda siap lebih dulu.
        let mut antrean = vec![FileMessage::Ack { id: 0, received: 0 }];
        let mut putaran = 0;
        while s.active() && putaran < 100_000 {
            putaran += 1;
            // Pesan dari penerima ke pengirim.
            if let Some(m) = antrean.pop() {
                if let Some(FileMessage::Offer { id, size, name }) = s.on_message(&m).as_ref() {
                    let mut baru = Receiver::from_offer(*id, *size, name, MAX_FILE_BYTES);
                    antrean.push(baru.accept().expect("ACCEPT"));
                    r = Some(baru);
                }
                continue;
            }
            // Pengirim mengirim potongan.
            let n = s.allowance();
            if n > 0 {
                let bagian = data[offset..offset + n].to_vec();
                offset += n;
                let pesan = s.chunk(bagian).expect("CHUNK");
                match r.as_mut().expect("penerima").handle(&pesan) {
                    Action::Write { data, ack } => {
                        tulisan.extend_from_slice(&data);
                        antrean.push(FileMessage::Ack {
                            id: 77,
                            received: ack,
                        });
                    }
                    lain => panic!("tak terduga: {lain:?}"),
                }
                continue;
            }
            if s.ready() {
                let done = s.finish(Sha256::digest(&data).into()).expect("DONE");
                assert_eq!(r.as_mut().expect("penerima").handle(&done), Action::Finish);
                break;
            }
            panic!("buntu: tidak ada yang boleh dikirim dan belum selesai");
        }
        assert_eq!(s.state(), SendState::Done);
        assert_eq!(tulisan, data);
        assert_eq!(s.percent(), 100);
    }

    /// Hash yang tidak cocok harus ditangkap penerima, bukan diterima diam.
    #[test]
    fn hash_salah_dari_pengirim_ditolak_penerima() {
        let data = vec![9u8; 1000];
        let mut s = Sender::new(3, "a.bin", data.len() as u64);
        s.on_message(&FileMessage::Ack { id: 0, received: 0 });
        let offer = FileMessage::Offer {
            id: 3,
            size: data.len() as u64,
            name: "a.bin".into(),
        };
        let mut r = match &offer {
            FileMessage::Offer { id, size, name } => {
                Receiver::from_offer(*id, *size, name, MAX_FILE_BYTES)
            }
            _ => unreachable!(),
        };
        s.on_message(&r.accept().unwrap());
        let potongan = s.chunk(data.clone()).unwrap();
        assert!(matches!(r.handle(&potongan), Action::Write { .. }));
        let done = s.finish([0u8; 32]).unwrap();
        assert_eq!(r.handle(&done), Action::Abort(Reason::HashMismatch));
    }
    /// `DONE_OK` harus pulang-pergi lewat kawat tanpa berubah, dan panjang
    /// yang salah tidak boleh diterima sebagai konfirmasi.
    #[test]
    fn done_ok_bolak_balik_di_kawat() {
        let pesan = FileMessage::DoneOk { id: 0x0A0B0C0D };
        let byte = encode(&pesan);
        assert_eq!(byte[0], MSG_DONE_OK);
        assert_eq!(byte.len(), 5);
        assert_eq!(decode(&byte), Some(pesan));
        assert_eq!(decode(&[MSG_DONE_OK]), None);
        assert_eq!(decode(&[MSG_DONE_OK, 1, 2, 3]), None);
        assert_eq!(decode(&[MSG_DONE_OK, 1, 2, 3, 4, 5]), None);
    }

    /// Jalur bahagia: setelah DONE, konfirmasi penerima menaikkan keadaan
    /// pengirim dari "terkirim" menjadi "tersimpan".
    #[test]
    fn done_ok_mengonfirmasi_pengirim() {
        let mut s = Sender::new(7, "a.bin", 4);
        s.on_message(&FileMessage::Ack { id: 0, received: 0 });
        s.on_message(&FileMessage::Accept { id: 7 });
        let _ = s.chunk(vec![1, 2, 3, 4]);
        s.on_message(&FileMessage::Ack { id: 7, received: 4 });
        let _ = s.finish([0u8; 32]).unwrap();
        assert_eq!(s.state(), SendState::Done);
        assert!(!s.confirmed());
        assert!(s.on_message(&FileMessage::DoneOk { id: 7 }).is_none());
        assert!(s.confirmed());
        assert_eq!(s.state(), SendState::Confirmed);
        assert!(!s.active());
    }

    /// Konfirmasi hanya dipercaya kalau datang untuk transfer ini dan setelah
    /// DONE. Id asing atau konfirmasi yang mendahului DONE diabaikan — bukan
    /// dianggap bukti bahwa berkas tersimpan.
    #[test]
    fn done_ok_asing_atau_terlalu_cepat_diabaikan() {
        let mut s = Sender::new(7, "a.bin", 4);
        s.on_message(&FileMessage::Ack { id: 0, received: 0 });
        s.on_message(&FileMessage::DoneOk { id: 7 });
        assert!(!s.confirmed());
        s.on_message(&FileMessage::Accept { id: 7 });
        let _ = s.chunk(vec![1, 2, 3, 4]);
        s.on_message(&FileMessage::Ack { id: 7, received: 4 });
        s.on_message(&FileMessage::DoneOk { id: 7 });
        assert!(!s.confirmed(), "konfirmasi sebelum DONE tidak sah");
        let _ = s.finish([0u8; 32]).unwrap();
        s.on_message(&FileMessage::DoneOk { id: 8 });
        assert!(!s.confirmed(), "konfirmasi untuk id lain bukan milik kita");
        s.on_message(&FileMessage::DoneOk { id: 7 });
        assert!(s.confirmed());
    }

    /// Penerima lama tidak pernah membalas. Pengirim tetap boleh menyebut
    /// kirimannya selesai; yang hilang hanya konfirmasinya.
    #[test]
    fn tanpa_done_ok_pengirim_tetap_selesai() {
        let mut s = Sender::new(7, "a.bin", 4);
        s.on_message(&FileMessage::Ack { id: 0, received: 0 });
        s.on_message(&FileMessage::Accept { id: 7 });
        let _ = s.chunk(vec![1, 2, 3, 4]);
        s.on_message(&FileMessage::Ack { id: 7, received: 4 });
        let _ = s.finish([0u8; 32]).unwrap();
        assert_eq!(s.state(), SendState::Done);
        assert!(!s.active());
        assert!(!s.confirmed());
        assert_eq!(s.percent(), 100);
    }

    /// Satu channel dipakai dua arah, jadi konfirmasi milik arah sebaliknya
    /// akan ikut lewat di depan penerima. Itu bukan pelanggaran protokol dan
    /// tidak boleh menggugurkan transfer yang sedang berjalan.
    #[test]
    fn penerima_membiarkan_done_ok_lewat() {
        let mut r = Receiver::from_offer(5, 10, "a.bin", MAX_FILE_BYTES);
        let _ = r.accept().unwrap();
        assert_eq!(r.handle(&FileMessage::DoneOk { id: 5 }), Action::None);
        assert_eq!(r.handle(&FileMessage::DoneOk { id: 99 }), Action::None);
        assert_eq!(*r.phase(), Phase::Receiving);
    }
}
