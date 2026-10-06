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

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn hash_yang_tidak_cocok_tidak_meninggalkan_berkas() -> anyhow::Result<()> {
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
