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
//! Untuk sekarang host **menerima otomatis** berkas dari client yang sudah
//! lolos pairing dan masuk akun — pintu persetujuan ada di panel host dan
//! belum digambar. Kebijakan itu ditulis di satu tempat ([`decide_offer`])
//! supaya mengubahnya nanti menjadi "tanya pengguna" tidak perlu menyentuh
//! alur jaringan.

use std::path::PathBuf;
use std::sync::Arc;

use tokio::sync::Mutex;
use webrtc::data_channel::RTCDataChannel;

use crate::filesink::Sink;
use crate::filetransfer::{
    decode, encode, is_risky_extension, Action, FileMessage, Phase, Reason, Receiver,
    MAX_FILE_BYTES,
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
    sink: Sink,
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
        })
    }));

    let channel = Arc::clone(&dc);
    dc.on_message(Box::new(move |msg| {
        let state = Arc::clone(&state);
        let channel = Arc::clone(&channel);
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
            handle(&channel, &state, &dir, message).await;
        })
    }));

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
        *slot = Some(Transfer { receiver, sink });
        return;
    }

    let Some(transfer) = slot.as_mut() else {
        return;
    };
    let id = transfer.receiver.id();
    match transfer.receiver.handle(&message) {
        Action::Write { data, ack } => {
            if let Err(e) = transfer.sink.write(&data) {
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
            match done.sink.commit() {
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
