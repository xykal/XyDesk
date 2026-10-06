//! Loopback transfer berkas: client (offerer) ↔ host (answerer) memakai
//! **kode produksi** — `Session` yang sama, `file_dispatch::serve` yang sama,
//! dan penulisan ke disk yang sama.
//!
//! Protokol dan mesin keadaannya sudah punya uji unit sendiri, tetapi uji
//! unit tidak pernah membuktikan bahwa channel `"file"` benar-benar sampai ke
//! penerima: channel yang labelnya salah dibaca, atau yang hilang karena
//! antrean data channel berhenti setelah menemukan `"input"`, akan membuat
//! seluruh fitur diam tanpa satu uji pun berubah warna. Itu persis bentuk bug
//! yang pernah membuat chat global membalas 500 ke setiap koneksi.
//!
//! Dua hal yang dibuktikan di sini:
//!   1. berkas 300 KB berpindah dan isinya **byte-per-byte sama**;
//!   2. hash yang tidak cocok berakhir sebagai CANCEL dan **tidak ada berkas
//!      final** yang tertinggal di folder tujuan.

use std::sync::Arc;
use std::time::Duration;

use anyhow::Context;
use sha2::{Digest, Sha256};
use tokio::sync::mpsc;
use webrtc::api::interceptor_registry::register_default_interceptors;
use webrtc::api::media_engine::MediaEngine;
use webrtc::api::APIBuilder;
use webrtc::interceptor::registry::Registry;
use webrtc::peer_connection::configuration::RTCConfiguration;
use webrtc::peer_connection::sdp::session_description::RTCSessionDescription;
use webrtc::rtp_transceiver::rtp_codec::RTPCodecType;
use webrtc::rtp_transceiver::rtp_transceiver_direction::RTCRtpTransceiverDirection;
use webrtc::rtp_transceiver::RTCRtpTransceiverInit;

use xydesk_host::filetransfer::{decode, encode, FileMessage, Reason, FILE_CHANNEL};
use xydesk_host::session::Session;

/// Pengirim host memakai antrean proses-global (`file_dispatch::queue_outgoing`),
/// jadi tiga test di berkas ini tidak boleh punya sesi hidup bersamaan:
/// sesi kedua akan mengambil alih antrean milik sesi pertama. Lock ini yang
/// menjaganya, dan sekaligus membuat kegagalan test mudah dibaca.
static SERIAL: tokio::sync::Mutex<()> = tokio::sync::Mutex::const_new(());

/// Folder tujuan khusus per test, supaya dua test tidak saling menimpa dan
/// tidak pernah menyentuh folder unduhan asli mesin yang menjalankan CI.
fn folder_tujuan(nama: &str) -> std::path::PathBuf {
    let dir = std::env::temp_dir().join(format!("xydesk-loopback-{nama}"));
    let _ = std::fs::remove_dir_all(&dir);
    std::fs::create_dir_all(&dir).unwrap();
    dir
}

struct Loopback {
    _host: Arc<Session>,
    client: Arc<webrtc::peer_connection::RTCPeerConnection>,
    dc: Arc<webrtc::data_channel::RTCDataChannel>,
    masuk: mpsc::UnboundedReceiver<FileMessage>,
}

/// Membangun sesi nyata: client menawarkan video recvonly (seperti produksi)
/// plus data channel `"file"`, host menjawab dengan `Session::answer` dan
/// melayani channel berkas dengan `file_dispatch::serve`.
async fn bangun(dir: std::path::PathBuf) -> anyhow::Result<Loopback> {
    let mut media = MediaEngine::default();
    media.register_default_codecs().context("codec client")?;
    let mut registry = Registry::new();
    registry = register_default_interceptors(registry, &mut media).context("interceptor")?;
    let api = APIBuilder::new()
        .with_media_engine(media)
        .with_interceptor_registry(registry)
        .build();
    let client = Arc::new(api.new_peer_connection(RTCConfiguration::default()).await?);
    client
        .add_transceiver_from_kind(
            RTPCodecType::Video,
            Some(RTCRtpTransceiverInit {
                direction: RTCRtpTransceiverDirection::Recvonly,
                send_encodings: vec![],
            }),
        )
        .await
        .context("transceiver video")?;
    // Channel input tetap dibuka seperti produksi: host memisahkan channel
    // berkas dari antrean input, dan test ini sekaligus membuktikan
    // pemisahan itu tidak menelan salah satunya.
    let _input = client.create_data_channel("input", None).await?;
    let dc = client.create_data_channel(FILE_CHANNEL, None).await?;

    let (tx, masuk) = mpsc::unbounded_channel();
    dc.on_message(Box::new(move |m| {
        if let Some(pesan) = decode(&m.data) {
            let _ = tx.send(pesan);
        }
        Box::pin(async {})
    }));
    let dibuka = Arc::new(tokio::sync::Notify::new());
    {
        let dibuka = dibuka.clone();
        dc.on_open(Box::new(move || {
            dibuka.notify_waiters();
            Box::pin(async {})
        }));
    }

    let offer = client.create_offer(None).await?;
    client.set_local_description(offer).await?;
    let mut gather = client.gathering_complete_promise().await;
    let _ = gather.recv().await;
    let offer_sdp = client
        .local_description()
        .await
        .ok_or_else(|| anyhow::anyhow!("offer tanpa local description"))?
        .sdp;

    let host = Arc::new(Session::new(vec![], vec![]).await?);
    let (answer_sdp, _track) = host.answer(&offer_sdp).await?;
    tokio::spawn(xydesk_host::file_dispatch::serve_in(host.clone(), dir));
    client
        .set_remote_description(RTCSessionDescription::answer(answer_sdp)?)
        .await?;

    tokio::time::timeout(Duration::from_secs(25), dibuka.notified())
        .await
        .context("data channel berkas tidak pernah terbuka")?;
    let mut masuk = masuk;
    // Tunggu tanda siap dari host (ACK id 0) sebelum menawarkan apa pun.
    tunggu(&mut masuk, |m| matches!(m, FileMessage::Ack { id: 0, .. }))
        .await
        .context("host tidak pernah mengirim tanda siap")?;
    Ok(Loopback {
        _host: host,
        client,
        dc,
        masuk,
    })
}

async fn tunggu(
    masuk: &mut mpsc::UnboundedReceiver<FileMessage>,
    cocok: impl Fn(&FileMessage) -> bool,
) -> anyhow::Result<FileMessage> {
    let batas = tokio::time::Instant::now() + Duration::from_secs(20);
    loop {
        let sisa = batas.saturating_duration_since(tokio::time::Instant::now());
        let pesan = tokio::time::timeout(sisa, masuk.recv())
            .await
            .context("tidak ada balasan dari host dalam 20 detik")?
            .ok_or_else(|| anyhow::anyhow!("channel tertutup"))?;
        if cocok(&pesan) {
            return Ok(pesan);
        }
    }
}

async fn kirim(
    dc: &Arc<webrtc::data_channel::RTCDataChannel>,
    pesan: &FileMessage,
) -> anyhow::Result<()> {
    dc.send(&bytes::Bytes::from(encode(pesan))).await?;
    Ok(())
}

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn berkas_berpindah_utuh_lewat_sesi_nyata() -> anyhow::Result<()> {
    let _serial = SERIAL.lock().await;
    let dir = folder_tujuan("utuh");
    let mut lb = bangun(dir.clone()).await?;
    // 300 KB, pola yang tidak bisa dipalsukan dengan buffer nol.
    let isi: Vec<u8> = (0..300_000u32).map(|i| (i % 253) as u8).collect();
    let sha: [u8; 32] = Sha256::digest(&isi).into();

    kirim(
        &lb.dc,
        &FileMessage::Offer {
            id: 42,
            size: isi.len() as u64,
            // Nama sengaja kotor: host yang harus membersihkannya.
            name: "..\\..\\laporan rahasia.pdf".into(),
        },
    )
    .await?;
    tunggu(&mut lb.masuk, |m| matches!(m, FileMessage::Accept { .. })).await?;

    for (i, bagian) in isi.chunks(16 * 1024).enumerate() {
        kirim(
            &lb.dc,
            &FileMessage::Chunk {
                id: 42,
                seq: i as u32,
                data: bagian.to_vec(),
            },
        )
        .await?;
        // Beri ruang pada buffer SCTP; pengirim produksi memakai ACK untuk
        // ini, test cukup menahan laju.
        if i % 4 == 3 {
            tokio::time::sleep(Duration::from_millis(5)).await;
        }
    }
    kirim(
        &lb.dc,
        &FileMessage::Done {
            id: 42,
            sha256: sha,
        },
    )
    .await?;

    // Berkas final muncul setelah host memverifikasi hash.
    let tujuan = dir.join("laporan rahasia.pdf");
    let batas = std::time::Instant::now() + Duration::from_secs(20);
    while !tujuan.exists() && std::time::Instant::now() < batas {
        tokio::time::sleep(Duration::from_millis(100)).await;
    }
    assert!(
        tujuan.exists(),
        "berkas final tidak pernah muncul di {}",
        dir.display()
    );
    assert_eq!(std::fs::read(&tujuan)?, isi, "isi berkas berubah di jalan");
    // Tidak ada sisa .xypart.
    let sisa: Vec<_> = std::fs::read_dir(&dir)?
        .filter_map(|e| e.ok())
        .map(|e| e.file_name().to_string_lossy().to_string())
        .filter(|n| n.ends_with(".xypart"))
        .collect();
    assert!(sisa.is_empty(), "berkas sementara tertinggal: {sisa:?}");

    lb.client.close().await?;
    let _ = std::fs::remove_dir_all(&dir);
    Ok(())
}

/// Pintu persetujuan, dibuktikan lewat sesi WebRTC sungguhan.
///
/// Dua hal yang tidak bisa dibuktikan uji unit `file_consent`: bahwa gerbang
/// itu benar-benar tersambung ke jalur jaringan, dan bahwa tawaran yang
/// ditolak tidak meninggalkan apa pun di folder tujuan.
#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn kebijakan_never_menolak_berkas_sebelum_menyentuh_disk() -> anyhow::Result<()> {
    let _serial = SERIAL.lock().await;
    let dir = folder_tujuan("tolak");
    let mut lb = bangun(dir.clone()).await?;
    xydesk_host::file_consent::set_policy(xydesk_host::file_consent::Policy::Never);

    kirim(
        &lb.dc,
        &FileMessage::Offer {
            id: 77,
            size: 4096,
            name: "tidak-diminta.bin".into(),
        },
    )
    .await?;
    let balasan = tunggu(&mut lb.masuk, |m| {
        matches!(m, FileMessage::Reject { .. } | FileMessage::Accept { .. })
    })
    .await?;
    assert!(
        matches!(
            balasan,
            FileMessage::Reject {
                reason: Reason::User,
                ..
            }
        ),
        "tawaran harus ditolak dengan alasan User, bukan {balasan:?}"
    );
    // Tidak ada berkas sementara maupun final yang pernah dibuat.
    let isi: Vec<_> = std::fs::read_dir(&dir)
        .map(|d| {
            d.filter_map(|e| e.ok())
                .map(|e| e.file_name().to_string_lossy().to_string())
                .collect()
        })
        .unwrap_or_default();
    assert!(
        isi.is_empty(),
        "folder tujuan tidak boleh tersentuh: {isi:?}"
    );

    xydesk_host::file_consent::set_policy(xydesk_host::file_consent::Policy::Ask);
    lb.client.close().await?;
    let _ = std::fs::remove_dir_all(&dir);
    Ok(())
}

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn hash_yang_tidak_cocok_tidak_meninggalkan_berkas() -> anyhow::Result<()> {
    let _serial = SERIAL.lock().await;
    let dir = folder_tujuan("hash");
    let mut lb = bangun(dir.clone()).await?;
    let isi = vec![7u8; 4096];

    kirim(
        &lb.dc,
        &FileMessage::Offer {
            id: 9,
            size: isi.len() as u64,
            name: "palsu.bin".into(),
        },
    )
    .await?;
    tunggu(&mut lb.masuk, |m| matches!(m, FileMessage::Accept { .. })).await?;
    kirim(
        &lb.dc,
        &FileMessage::Chunk {
            id: 9,
            seq: 0,
            data: isi.clone(),
        },
    )
    .await?;
    // Hash milik isi yang lain — persis yang terjadi kalau berkas berubah di
    // jalan atau pengirim berbohong.
    kirim(
        &lb.dc,
        &FileMessage::Done {
            id: 9,
            sha256: Sha256::digest(b"isi lain").into(),
        },
    )
    .await?;

    let batal = tunggu(&mut lb.masuk, |m| matches!(m, FileMessage::Cancel { .. })).await?;
    assert_eq!(
        batal,
        FileMessage::Cancel {
            id: 9,
            reason: Reason::HashMismatch
        }
    );
    // Folder tujuan harus benar-benar kosong: tidak ada berkas final, tidak
    // ada .xypart yang menunggu dibuka pengguna besok pagi.
    tokio::time::sleep(Duration::from_millis(300)).await;
    let isi_folder: Vec<_> = std::fs::read_dir(&dir)?
        .filter_map(|e| e.ok())
        .map(|e| e.file_name().to_string_lossy().to_string())
        .collect();
    assert!(
        isi_folder.is_empty(),
        "folder tujuan tidak bersih: {isi_folder:?}"
    );

    lb.client.close().await?;
    let _ = std::fs::remove_dir_all(&dir);
    Ok(())
}

/// Arah sebaliknya: **host yang mengirim**, client yang menerima.
///
/// Jalur ini melewati antrean control API (`queue_outgoing`), tugas pengirim
/// milik sesi, dan `Sender` — potongan yang tidak pernah tersentuh uji unit
/// mana pun karena semuanya butuh channel sungguhan. Yang dibuktikan:
/// tawaran keluar hanya setelah tanda siap, semua byte tiba berurutan, dan
/// SHA-256 di `DONE` cocok dengan isi berkas aslinya.
#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn host_mengirim_berkas_ke_client() -> anyhow::Result<()> {
    let _serial = SERIAL.lock().await;
    let dir = folder_tujuan("kirim");
    let mut lb = bangun(dir.clone()).await?;

    let isi: Vec<u8> = (0..300_000u32).map(|i| (i % 241) as u8).collect();
    let sumber = dir.join("kiriman dari pc.bin");
    std::fs::write(&sumber, &isi)?;

    // Host mengirim tanda siapnya lebih dulu; aplikasi membalas dengan tanda
    // siapnya sendiri. Balasan itu yang dipakai host sebelum menawarkan —
    // mengirimnya lebih awal tidak aman, karena pendengar di sisi host baru
    // terpasang beberapa saat setelah channel terbuka.
    kirim(&lb.dc, &FileMessage::Ack { id: 0, received: 0 }).await?;

    // Antrean baru terdaftar setelah `serve` memegang channel.
    let batas = std::time::Instant::now() + Duration::from_secs(10);
    loop {
        match xydesk_host::file_dispatch::queue_outgoing(sumber.clone()) {
            Ok(()) => break,
            Err(e) if std::time::Instant::now() < batas => {
                if !e.contains("tersambung") {
                    anyhow::bail!("titip kiriman ditolak: {e}");
                }
                tokio::time::sleep(Duration::from_millis(100)).await;
            }
            Err(e) => anyhow::bail!("titip kiriman tidak pernah diterima: {e}"),
        }
    }

    let tawaran = tunggu(&mut lb.masuk, |m| matches!(m, FileMessage::Offer { .. })).await?;
    let (id, size, name) = match tawaran {
        FileMessage::Offer { id, size, name } => (id, size, name),
        lain => anyhow::bail!("bukan OFFER: {lain:?}"),
    };
    assert_eq!(size, isi.len() as u64);
    assert_eq!(name, "kiriman dari pc.bin");
    kirim(&lb.dc, &FileMessage::Accept { id }).await?;

    let mut diterima: Vec<u8> = Vec::with_capacity(isi.len());
    let mut urut = 0u32;
    let sha_akhir = loop {
        let pesan = tunggu(&mut lb.masuk, |_| true).await?;
        match pesan {
            FileMessage::Chunk { id: cid, seq, data } => {
                assert_eq!(cid, id, "potongan untuk transfer lain");
                assert_eq!(seq, urut, "nomor urut melompat");
                urut += 1;
                diterima.extend_from_slice(&data);
                // ACK adalah rem produksi: tanpa ini pengirim berhenti di
                // 512 KiB dan test menggantung — itu justru yang diuji.
                kirim(
                    &lb.dc,
                    &FileMessage::Ack {
                        id,
                        received: diterima.len() as u64,
                    },
                )
                .await?;
            }
            FileMessage::Done { id: did, sha256 } => {
                assert_eq!(did, id);
                break sha256;
            }
            FileMessage::Cancel { reason, .. } => anyhow::bail!("kiriman dibatalkan: {reason:?}"),
            _ => {}
        }
    };

    assert_eq!(diterima, isi, "isi berkas berubah di jalan");
    let sha_asli: [u8; 32] = Sha256::digest(&isi).into();
    assert_eq!(sha_akhir, sha_asli, "SHA-256 di DONE tidak cocok");

    lb.client.close().await?;
    let _ = std::fs::remove_dir_all(&dir);
    Ok(())
}
