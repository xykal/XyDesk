//! Melayani data channel `"file"`: menyambungkan protokol
//! ([`crate::filetransfer`]) dengan penulisan ke disk ([`crate::filesink`]).
//!
//! Pembagian tugasnya sengaja tegas. Modul protokol memutuskan **apakah**
//! sebuah pesan boleh diterima dan tidak pernah menyentuh disk; modul sink
//! menulis dan tidak pernah memutuskan apa pun. Berkas ini hanya menyambung
//! keduanya dan berbicara ke jaringan — bagian paling tipis, dan satu-satunya
//! yang tidak bisa diuji tanpa sesi nyata.
//!
//! ## Satu transfer dalam satu waktu
//!
//! Transfer kedua yang datang saat satu transfer masih berjalan **ditolak**,
//! bukan diantrekan. Mengantre berarti memegang berkas sementara dalam
//! jumlah yang ditentukan lawan; menolak membuat batasnya jelas bagi kedua
//! sisi dan membuat pembatalan selalu punya arti tunggal.
//!
//! ## Persetujuan
//!
//! Dua lapis, dan keduanya harus lolos. [`decide_offer`] memeriksa hal-hal
//! yang tidak ada hubungannya dengan selera pemilik PC — sedang sibuk,
//! ukuran nol, lebih besar dari batas — lalu [`crate::file_consent`] bertanya
//! kepada pemiliknya lewat panel. Pertanyaannya tidak bisa dijawab di sini,
//! jadi tawaran yang menunggu dititipkan ke tugas tersendiri; selama itu
//! **belum satu byte pun menyentuh disk**: berkas sementara baru dibuat
//! setelah jawabannya "ya".

use std::path::PathBuf;
use std::sync::Arc;

use tokio::sync::Mutex;
use webrtc::data_channel::RTCDataChannel;

use crate::filesink::Sink;
use crate::filetransfer::{
    decode, encode, is_risky_extension, Action, FileMessage, Phase, Reason, Receiver, SendState,
    Sender, MAX_FILE_BYTES,
};
use crate::session::Session;

/// Keputusan atas sebuah tawaran berkas.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Decision {
    Accept,
    Reject(Reason),
}

/// Kebijakan penerimaan. Dipisah dari alur jaringan supaya bisa diuji dan
/// diubah tanpa menyentuh soket.
pub fn decide_offer(name: &str, size: u64, busy: bool) -> Decision {
    if busy {
        return Decision::Reject(Reason::Protocol);
    }
    if size == 0 || size > MAX_FILE_BYTES {
        return Decision::Reject(Reason::TooLarge);
    }
    // Berkas yang langsung dijalankan Windows tetap diterima — pemblokiran
    // diam-diam membuat transfer sah gagal tanpa penjelasan — tetapi
    // dicatat supaya panel bisa menandainya kepada pemilik PC.
    if is_risky_extension(name) {
        println!("[xydesk-host] berkas masuk ditandai berisiko: {name}");
    }
    Decision::Accept
}

/// Nama variabel lingkungan untuk memindahkan folder tujuan.
pub const DOWNLOAD_DIR_ENV: &str = "XYDESK_DOWNLOAD_DIR";

/// Folder tujuan berkas masuk: `%USERPROFILE%\Downloads\XyDesk` di Windows,
/// `$HOME/Downloads/XyDesk` di tempat lain, dan folder sementara bila
/// keduanya tidak terbaca.
///
/// Subfolder `XyDesk` disengaja: berkas dari mesin lain tidak dicampur
/// dengan unduhan browser pengguna, sehingga satu folder bisa dibersihkan
/// tanpa menimbang-nimbang isinya.
///
/// `XYDESK_DOWNLOAD_DIR` menimpa semuanya. Itu yang dipakai test loopback,
/// dan sekaligus jalan keluar bagi PC yang drive C-nya penuh.
pub fn download_dir() -> PathBuf {
    if let Some(custom) = std::env::var_os(DOWNLOAD_DIR_ENV) {
        if !custom.is_empty() {
            return PathBuf::from(custom);
        }
    }
    let home = std::env::var_os("USERPROFILE")
        .or_else(|| std::env::var_os("HOME"))
        .map(PathBuf::from);
    match home {
        Some(h) => h.join("Downloads").join("XyDesk"),
        None => std::env::temp_dir().join("XyDesk"),
    }
}

struct Transfer {
    receiver: Receiver,
    /// `None` selama tawaran masih menunggu persetujuan pemilik PC. Berkas
    /// sementara sengaja belum dibuat: tawaran yang ditolak tidak boleh
    /// meninggalkan jejak apa pun di disk.
    sink: Option<Sink>,
}

/// Antrean kirim milik sesi yang sedang berjalan.
///
/// Aksi control API (`file-send`) dijalankan di utas HTTP yang sama sekali
/// tidak tahu-menahu tentang sesi WebRTC, jadi ia hanya menitipkan path ke
/// sini; yang mengirim adalah tugas milik sesi. Bila tidak ada sesi, titipan
/// ditolak seketika — mengantre berkas untuk sesi yang mungkin tidak pernah
/// datang hanya membuat kiriman muncul mengejutkan nanti.
type Outbox = (
    tokio::sync::mpsc::UnboundedSender<PathBuf>,
    Arc<RTCDataChannel>,
);

static OUTBOX: std::sync::Mutex<Option<Outbox>> = std::sync::Mutex::new(None);

fn set_outbox(entry: Option<Outbox>) {
    if let Ok(mut slot) = OUTBOX.lock() {
        *slot = entry;
    }
}

/// Menitipkan satu berkas untuk dikirim ke perangkat yang sedang tersambung.
///
/// Mengembalikan pesan kesalahan bila tidak ada sesi, path bukan berkas,
/// kosong, atau melebihi batas — semuanya diperiksa **sebelum** pengguna
/// diberi tahu bahwa kiriman dimulai.
pub fn queue_outgoing(path: PathBuf) -> Result<(), String> {
    let meta = std::fs::metadata(&path).map_err(|e| format!("berkas tidak terbaca: {e}"))?;
    if !meta.is_file() {
        return Err("yang dipilih bukan berkas".to_string());
    }
    if meta.len() == 0 {
        return Err("berkas kosong, tidak ada yang dikirim".to_string());
    }
    if meta.len() > MAX_FILE_BYTES {
        return Err(format!(
            "berkas melebihi batas {}",
            crate::filetransfer::human_bytes(MAX_FILE_BYTES)
        ));
    }
    let mut slot = OUTBOX
        .lock()
        .map_err(|_| "antrean kirim rusak".to_string())?;
    // Channel milik sesi yang sudah mati tetap tersimpan di sini sampai
    // `on_close` sempat jalan. Menitipkan berkas ke sana akan "berhasil"
    // tanpa satu byte pun berangkat — keadaan terburuk: pengguna diberi tahu
    // kiriman dimulai padahal tidak ada penerimanya.
    let hidup = slot.as_ref().is_some_and(|(_, dc)| {
        dc.ready_state() == webrtc::data_channel::data_channel_state::RTCDataChannelState::Open
    });
    if !hidup {
        *slot = None;
        return Err("tidak ada perangkat yang tersambung".to_string());
    }
    let (tx, _) = slot.as_ref().expect("sudah diperiksa hidup");
    tx.send(path).map_err(|_| "sesi sudah berakhir".to_string())
}

/// Keadaan pengiriman host → client untuk satu sesi.
#[derive(Default)]
struct Outgoing {
    sender: Option<Sender>,
    /// Tanda siap dari client sudah tiba. Client mengirimnya ketika
    /// pendengarnya terpasang; mengirim OFFER sebelum itu berarti tawaran
    /// hilang tanpa jejak.
    peer_ready: bool,
}

/// Melayani channel berkas satu sesi sampai sesinya berakhir, menulis ke
/// [`download_dir`].
pub async fn serve(session: Arc<Session>) {
    let dir = download_dir();
    serve_in(session, dir).await
}

/// Seperti [`serve`], tetapi folder tujuannya ditentukan pemanggil.
///
/// Folder dioper sebagai argumen, bukan dibaca ulang dari lingkungan di
/// dalam handler: dua sesi yang berjalan bersamaan harus bisa punya tujuan
/// berbeda, dan test yang berjalan paralel tidak boleh saling menimpa lewat
/// satu variabel lingkungan global.
pub async fn serve_in(session: Arc<Session>, dir: PathBuf) {
    let dc = session.wait_file_channel().await;
    println!("[xydesk-host] data channel berkas terbuka");
    let state: Arc<Mutex<Option<Transfer>>> = Arc::new(Mutex::new(None));
    let out: Arc<Mutex<Outgoing>> = Arc::new(Mutex::new(Outgoing::default()));
    let (tx, mut rx) = tokio::sync::mpsc::unbounded_channel::<PathBuf>();
    set_outbox(Some((tx, Arc::clone(&dc))));

    let on_close_state = Arc::clone(&state);
    dc.on_close(Box::new(move || {
        let state = Arc::clone(&on_close_state);
        Box::pin(async move {
            // Transfer yang belum selesai dibuang di sini. Sink-nya akan
            // menghapus berkas .xypart saat jatuh, jadi sesi yang putus
            // tidak meninggalkan potongan di folder unduhan.
            if let Some(mut t) = state.lock().await.take() {
                t.receiver.disconnected();
                println!("[xydesk-host] transfer berkas dibatalkan: sesi berakhir");
            }
            set_outbox(None);
        })
    }));

    let channel = Arc::clone(&dc);
    let out_handler = Arc::clone(&out);
    dc.on_message(Box::new(move |msg| {
        let state = Arc::clone(&state);
        let channel = Arc::clone(&channel);
        let out_msg = Arc::clone(&out_handler);
        let dir = dir.clone();
        Box::pin(async move {
            let Some(message) = decode(&msg.data) else {
                // Pesan cacat tidak pernah ditebak isinya.
                let _ = send(
                    &channel,
                    &FileMessage::Cancel {
                        id: 0,
                        reason: Reason::Protocol,
                    },
                )
                .await;
                return;
            };
            // Pesan yang menyangkut kiriman keluar ditangani lebih dulu:
            // ACCEPT/REJECT/ACK adalah jawaban untuk kita, bukan tawaran
            // baru untuk penerima.
            if route_outgoing(&channel, &out_msg, &message).await {
                return;
            }
            handle(&channel, &state, &dir, message).await;
        })
    }));

    // Pengirim: satu berkas dalam satu waktu, dari antrean control API.
    let out_task = Arc::clone(&out);
    let send_channel = Arc::clone(&dc);
    tokio::spawn(async move {
        while let Some(path) = rx.recv().await {
            send_file(&send_channel, &out_task, path).await;
        }
    });

    // Tanda "siap": `ACK` dengan id 0. Handler `on_message` baru terpasang
    // beberapa baris di atas, sementara channel sudah OPEN di sisi client
    // sejak tadi — OFFER yang dikirim di detik pembukaan bisa tiba sebelum
    // ada yang mendengarkan dan hilang tanpa jejak, dan transfer menggantung
    // tanpa pesan kesalahan. Client wajib menunggu tanda ini dulu.
    let beacon = Arc::clone(&dc);
    tokio::spawn(async move {
        for _ in 0..50 {
            if beacon.ready_state()
                == webrtc::data_channel::data_channel_state::RTCDataChannelState::Open
            {
                let _ = send(&beacon, &FileMessage::Ack { id: 0, received: 0 }).await;
                return;
            }
            tokio::time::sleep(std::time::Duration::from_millis(100)).await;
        }
    });
}

/// Menyuapkan pesan yang menyangkut kiriman keluar. `true` berarti pesan ini
/// sudah selesai ditangani dan tidak boleh ikut dibaca penerima.
async fn route_outgoing(
    channel: &Arc<RTCDataChannel>,
    out: &Arc<Mutex<Outgoing>>,
    message: &FileMessage,
) -> bool {
    let mut guard = out.lock().await;
    if let FileMessage::Ack { id: 0, .. } = message {
        guard.peer_ready = true;
        if let Some(sender) = guard.sender.as_mut() {
            if let Some(offer) = sender.on_message(message) {
                let _ = send(channel, &offer).await;
            }
        }
        return true;
    }
    let Some(sender) = guard.sender.as_mut() else {
        return false;
    };
    let mine = match message {
        FileMessage::Accept { id }
        | FileMessage::Reject { id, .. }
        | FileMessage::Ack { id, .. } => *id == sender.id(),
        _ => false,
    };
    // CANCEL dengan id kiriman kita juga milik kita; CANCEL dengan id lain
    // adalah urusan penerima.
    let mine = mine || matches!(message, FileMessage::Cancel { id, .. } if *id == sender.id());
    if !mine {
        return false;
    }
    if let Some(reply) = sender.on_message(message) {
        let _ = send(channel, &reply).await;
    }
    true
}

/// Mengirim satu berkas ke client. Bagian paling tipis dan satu-satunya yang
/// tidak bisa diuji tanpa sesi nyata: semua aturannya ada di
/// [`crate::filetransfer::Sender`], di sini hanya membaca disk, menahan laju,
/// dan berbicara ke channel.
async fn send_file(channel: &Arc<RTCDataChannel>, out: &Arc<Mutex<Outgoing>>, path: PathBuf) {
    use sha2::{Digest, Sha256};
    use tokio::io::AsyncReadExt;

    let name = path
        .file_name()
        .map(|n| n.to_string_lossy().to_string())
        .unwrap_or_else(|| "berkas".to_string());
    let Ok(meta) = tokio::fs::metadata(&path).await else {
        eprintln!(
            "[xydesk-host] kirim berkas gagal: {} tidak terbaca",
            path.display()
        );
        return;
    };
    let Ok(mut file) = tokio::fs::File::open(&path).await else {
        eprintln!(
            "[xydesk-host] kirim berkas gagal: {} tidak bisa dibuka",
            path.display()
        );
        return;
    };

    {
        let mut guard = out.lock().await;
        if guard.sender.as_ref().is_some_and(|s| s.active()) {
            eprintln!("[xydesk-host] kiriman ditolak: masih ada berkas berjalan");
            return;
        }
        guard.sender = Some(Sender::new(new_transfer_id(), &name, meta.len()));
    }

    // Tunggu tanda siap dari client, maksimal 15 detik.
    let mut siap = false;
    for _ in 0..150 {
        let mut guard = out.lock().await;
        if guard.peer_ready {
            let ready = FileMessage::Ack { id: 0, received: 0 };
            if let Some(sender) = guard.sender.as_mut() {
                if let Some(offer) = sender.on_message(&ready) {
                    let _ = send(channel, &offer).await;
                }
            }
            siap = true;
            break;
        }
        drop(guard);
        tokio::time::sleep(std::time::Duration::from_millis(100)).await;
    }
    if !siap {
        // Client lama tidak mengirim tanda siap sama sekali. Menunggu
        // selamanya berarti kiriman menggantung diam-diam; lebih baik
        // berhenti dengan pesan yang bisa dibaca di log panel.
        eprintln!("[xydesk-host] kirim {name} dibatalkan: perangkat tidak mengirim tanda siap");
        out.lock().await.sender = None;
        return;
    }

    let mut hasher = Sha256::new();
    let mut buf = vec![0u8; crate::filetransfer::SEND_CHUNK_BYTES];
    loop {
        let allowance = {
            let guard = out.lock().await;
            match guard.sender.as_ref() {
                Some(s) if s.active() => {
                    if s.ready() {
                        0
                    } else {
                        s.allowance()
                    }
                }
                _ => break,
            }
        };
        let finished = {
            let guard = out.lock().await;
            guard.sender.as_ref().is_some_and(|s| s.ready())
        };
        if finished {
            let mut guard = out.lock().await;
            if let Some(sender) = guard.sender.as_mut() {
                if let Some(done) = sender.finish(hasher.clone().finalize().into()) {
                    let _ = send(channel, &done).await;
                    println!("[xydesk-host] berkas terkirim: {name}");
                }
            }
            break;
        }
        // Antrean channel juga direm: byte yang belum keluar dari sini tetap
        // memakai memori proses, berapa pun cepatnya disk dibaca.
        if allowance == 0 || channel.buffered_amount().await > BUFFER_CAP {
            tokio::time::sleep(std::time::Duration::from_millis(8)).await;
            continue;
        }
        let read = match file.read(&mut buf[..allowance]).await {
            Ok(0) | Err(_) => {
                let mut guard = out.lock().await;
                if let Some(sender) = guard.sender.as_mut() {
                    if let Some(cancel) = sender.cancel(Reason::Io) {
                        let _ = send(channel, &cancel).await;
                    }
                }
                eprintln!("[xydesk-host] kirim berkas gagal: isi {name} berubah saat dikirim");
                break;
            }
            Ok(n) => n,
        };
        let part = buf[..read].to_vec();
        hasher.update(&part);
        let message = {
            let mut guard = out.lock().await;
            match guard.sender.as_mut() {
                Some(sender) => sender.chunk(part),
                None => break,
            }
        };
        let Some(message) = message else { break };
        if send(channel, &message).await.is_err() {
            break;
        }
    }

    let mut guard = out.lock().await;
    if let Some(sender) = guard.sender.as_ref() {
        if let SendState::Failed(reason) = sender.state() {
            eprintln!("[xydesk-host] kiriman {name} gagal: {reason:?}");
        }
    }
    guard.sender = None;
}

/// Id transfer baru. Tidak pernah 0: id itu milik tanda siap.
fn new_transfer_id() -> u32 {
    use std::time::{SystemTime, UNIX_EPOCH};
    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.subsec_nanos())
        .unwrap_or(1);
    let id = nanos | 0x4000_0000;
    if id == 0 {
        1
    } else {
        id
    }
}

/// Antrean channel maksimum sebelum pengirim menahan diri (1 MiB).
const BUFFER_CAP: usize = 1024 * 1024;

async fn handle(
    channel: &Arc<RTCDataChannel>,
    state: &Arc<Mutex<Option<Transfer>>>,
    dir: &std::path::Path,
    message: FileMessage,
) {
    let mut slot = state.lock().await;
    if let FileMessage::Offer { id, size, name } = &message {
        let busy = slot
            .as_ref()
            .is_some_and(|t| matches!(t.receiver.phase(), Phase::Offered | Phase::Receiving));
        let mut receiver = Receiver::from_offer(*id, *size, name, MAX_FILE_BYTES);
        let decision = match receiver.phase() {
            Phase::Failed(reason) => Decision::Reject(*reason),
            _ => decide_offer(receiver.name(), *size, busy),
        };
        match decision {
            Decision::Reject(reason) => {
                let _ = send(channel, &FileMessage::Reject { id: *id, reason }).await;
                return;
            }
            Decision::Accept => {}
        }
        // Lapis kedua: pemilik PC. `Pending` berarti dialog panel terbuka;
        // yang menjawabnya adalah tugas di bawah, bukan utas pesan ini —
        // menunggu di sini akan menahan CANCEL dari pengirim selama satu
        // menit penuh.
        match crate::file_consent::offer(
            *id,
            receiver.name(),
            *size,
            is_risky_extension(receiver.name()),
        ) {
            crate::file_consent::Verdict::Accept => {}
            crate::file_consent::Verdict::Reject(reason) => {
                let _ = send(channel, &FileMessage::Reject { id: *id, reason }).await;
                return;
            }
            crate::file_consent::Verdict::Pending => {
                println!(
                    "[xydesk-host] menunggu persetujuan untuk \"{}\" ({})",
                    receiver.name(),
                    crate::filetransfer::human_bytes(*size)
                );
                *slot = Some(Transfer {
                    receiver,
                    sink: None,
                });
                drop(slot);
                spawn_consent_wait(channel.clone(), state.clone(), dir.to_path_buf(), *id);
                return;
            }
        }
        let sink = match Sink::create(dir, receiver.name()) {
            Ok(sink) => sink,
            Err(e) => {
                eprintln!("[xydesk-host] berkas masuk ditolak: {e}");
                let _ = send(
                    channel,
                    &FileMessage::Reject {
                        id: *id,
                        reason: Reason::Io,
                    },
                )
                .await;
                return;
            }
        };
        if let Some(accept) = receiver.accept() {
            let _ = send(channel, &accept).await;
        }
        println!(
            "[xydesk-host] menerima berkas \"{}\" ({})",
            receiver.name(),
            crate::filetransfer::human_bytes(*size)
        );
        *slot = Some(Transfer {
            receiver,
            sink: Some(sink),
        });
        return;
    }

    let Some(transfer) = slot.as_mut() else {
        return;
    };
    let id = transfer.receiver.id();
    match transfer.receiver.handle(&message) {
        Action::Write { data, ack } => {
            let Some(sink) = transfer.sink.as_mut() else {
                // Potongan sebelum persetujuan: pengirim melanggar urutan.
                crate::file_consent::clear(id);
                let _ = send(
                    channel,
                    &FileMessage::Cancel {
                        id,
                        reason: Reason::Protocol,
                    },
                )
                .await;
                *slot = None;
                return;
            };
            if let Err(e) = sink.write(&data) {
                eprintln!("[xydesk-host] tulis berkas gagal: {e}");
                let _ = send(
                    channel,
                    &FileMessage::Cancel {
                        id,
                        reason: Reason::Io,
                    },
                )
                .await;
                *slot = None;
                return;
            }
            // ACK dikirim per potongan supaya pengirim bisa menahan laju;
            // tanpa itu client hanya tahu bahwa byte-nya sudah keluar dari
            // dirinya sendiri, bukan bahwa byte itu sampai di disk.
            let _ = send(channel, &FileMessage::Ack { id, received: ack }).await;
        }
        Action::Finish => {
            let Some(done) = slot.take() else { return };
            let Some(sink) = done.sink else { return };
            match sink.commit() {
                Ok(path) => println!("[xydesk-host] berkas tersimpan: {}", path.display()),
                Err(e) => {
                    eprintln!("[xydesk-host] simpan berkas gagal: {e}");
                    let _ = send(
                        channel,
                        &FileMessage::Cancel {
                            id,
                            reason: Reason::Io,
                        },
                    )
                    .await;
                }
            }
        }
        Action::Abort(reason) => {
            eprintln!("[xydesk-host] transfer berkas dihentikan: {reason:?}");
            crate::file_consent::clear(id);
            let _ = send(channel, &FileMessage::Cancel { id, reason }).await;
            *slot = None;
        }
        Action::AskUser { .. } | Action::None => {
            if let Phase::Failed(_) = transfer.receiver.phase() {
                *slot = None;
            }
        }
    }
}

/// Menunggu jawaban pemilik PC di luar jalur pesan.
///
/// Polling 200 ms, bukan notifikasi: jawabannya datang dari utas HTTP control
/// API yang tidak mengenal runtime sesi, dan satu dialog per sesi tidak layak
/// dibayar dengan kanal tambahan lintas utas. Tugas ini berhenti sendiri
/// begitu tawaran hilang — dijawab, kedaluwarsa, atau dibatalkan pengirim.
fn spawn_consent_wait(
    channel: Arc<RTCDataChannel>,
    state: Arc<Mutex<Option<Transfer>>>,
    dir: PathBuf,
    id: u32,
) {
    tokio::spawn(async move {
        loop {
            tokio::time::sleep(std::time::Duration::from_millis(200)).await;
            let verdict = crate::file_consent::poll(id);
            let mut slot = state.lock().await;
            // Transfer sudah lenyap (sesi tutup / pengirim membatalkan):
            // tidak ada lagi yang perlu dijawab.
            let masih_menunggu = slot
                .as_ref()
                .is_some_and(|t| t.sink.is_none() && t.receiver.id() == id);
            if !masih_menunggu {
                crate::file_consent::clear(id);
                return;
            }
            match verdict {
                crate::file_consent::Verdict::Pending => continue,
                crate::file_consent::Verdict::Reject(reason) => {
                    *slot = None;
                    drop(slot);
                    println!("[xydesk-host] berkas masuk ditolak pemilik PC");
                    let _ = send(&channel, &FileMessage::Reject { id, reason }).await;
                    return;
                }
                crate::file_consent::Verdict::Accept => {
                    let Some(transfer) = slot.as_mut() else {
                        return;
                    };
                    let sink = match Sink::create(&dir, transfer.receiver.name()) {
                        Ok(sink) => sink,
                        Err(e) => {
                            eprintln!("[xydesk-host] berkas masuk ditolak: {e}");
                            *slot = None;
                            drop(slot);
                            let _ = send(
                                &channel,
                                &FileMessage::Reject {
                                    id,
                                    reason: Reason::Io,
                                },
                            )
                            .await;
                            return;
                        }
                    };
                    let accept = transfer.receiver.accept();
                    transfer.sink = Some(sink);
                    let nama = transfer.receiver.name().to_string();
                    drop(slot);
                    if let Some(accept) = accept {
                        let _ = send(&channel, &accept).await;
                    }
                    println!("[xydesk-host] berkas masuk disetujui: \"{nama}\"");
                    return;
                }
            }
        }
    });
}

async fn send(
    channel: &Arc<RTCDataChannel>,
    message: &FileMessage,
) -> webrtc::error::Result<usize> {
    channel.send(&bytes::Bytes::from(encode(message))).await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn tawaran_kedua_saat_sibuk_ditolak() {
        assert_eq!(
            decide_offer("b.txt", 10, true),
            Decision::Reject(Reason::Protocol)
        );
        assert_eq!(decide_offer("b.txt", 10, false), Decision::Accept);
    }

    #[test]
    fn ukuran_nol_dan_kebesaran_ditolak() {
        assert_eq!(
            decide_offer("a.txt", 0, false),
            Decision::Reject(Reason::TooLarge)
        );
        assert_eq!(
            decide_offer("a.txt", MAX_FILE_BYTES + 1, false),
            Decision::Reject(Reason::TooLarge)
        );
    }

    #[test]
    fn berkas_berisiko_tetap_diterima_bukan_diblokir_diam_diam() {
        assert_eq!(decide_offer("pasang.exe", 1024, false), Decision::Accept);
    }

    #[test]
    fn folder_tujuan_berada_di_bawah_home_dan_bernama_xydesk() {
        let dir = download_dir();
        assert!(dir.ends_with("XyDesk"));
        assert!(
            dir.parent().is_some_and(|p| p.ends_with("Downloads"))
                || dir.starts_with(std::env::temp_dir())
        );
    }
}
