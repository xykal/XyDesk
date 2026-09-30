//! Kanal data input dari client: menerima event (keyboard/mouse/pointer,
//! papan klip, pilihan monitor, kualitas video, wallpaper), memisahkan
//! injeksi `SendInput` ke thread blocking sendiri, dan mengirim umpan balik
//! kursor + META berkala ke client.
//!
//! Dipindah dari `main.rs` agar loop signaling tidak memuat detail protokol
//! data channel. Perilaku identik dengan versi sebelumnya.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

use webrtc::data_channel::data_channel_state::RTCDataChannelState;
use webrtc::data_channel::RTCDataChannel;

use crate::input::{Injector, InputEvent, InputLease};
use crate::session::Session;
use crate::{input_queue, screen, video_policy, xyadapt};

/// Snapshot META yang dikirim ke client saat data channel terbuka dan setiap
/// kali keadaan host berubah (pindah monitor, mode video, fps).
pub fn meta_json() -> serde_json::Value {
    serde_json::json!({
        "type": "meta",
        "displays": crate::screen::list_displays(),
        "wanted": crate::screen::wanted_display(),
        "desktopMode": crate::desktop_mode::telemetry(),
        "cursorEmbedded": crate::screen::cursor_embedded(),
        "video": crate::video_policy::telemetry(),
        "capture": crate::screen::capture_telemetry(),
        "encoder": crate::screen::encoder_label(),
        "inputGeometry": crate::desktop_geometry::active(),
        "audio": {
            "available": crate::audio::capture_available(),
            "pipeline": crate::audio::capture_status(),
        },
        "micInput": { "available": crate::audio::mic_input_available(), "route": "virtual-cable" },
        "mic": {
            "available": crate::audio::mic_capture_available(),
            "pipeline": crate::audio::mic_capture_status(),
        },
        // Spesifikasi mesin ini. Client sudah menunggu blok ini sejak lama
        // (`HostMeta.fromJson` → `hardware.*`); sebelumnya selalu null karena
        // host tidak pernah membacanya. Nilai yang gagal dibaca dikirim null
        // supaya UI menulis "Tidak terdeteksi" — bukan angka karangan.
        "hardware": crate::hwinfo::hardware_json()
    })
}

/// Melayani kanal input satu sesi sampai kanal tersebut ditutup. Dipanggil
/// dari task terpisah oleh loop signaling.
pub async fn serve(session: Arc<Session>) {
    match session.receive_input_channels().await {
        Ok((dc, pointer_dc)) => {
            println!(
                "[xydesk-host] data channel input terbuka{}",
                if pointer_dc.is_some() {
                    " + pointer lossy"
                } else {
                    " (legacy pointer fallback)"
                }
            );
            let _ = dc.send_text(meta_json().to_string()).await;
            spawn_feedback(dc.clone(), meta_json());
            dispatch(dc, pointer_dc).await;
        }
        Err(e) => eprintln!("[xydesk-host] input channel gagal: {e:#}"),
    }
}

/// Umpan balik kursor tiap 50 ms + META telemetri tiap detik, berhenti
/// sendiri saat kanal tutup atau buffer penuh.
fn spawn_feedback(feedback_dc: Arc<RTCDataChannel>, base_meta: serde_json::Value) {
    tokio::spawn(async move {
        let mut tick = tokio::time::interval(std::time::Duration::from_millis(50));
        tick.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);
        let mut ticks = 0u32;
        loop {
            tick.tick().await;
            if feedback_dc.ready_state() != RTCDataChannelState::Open {
                break;
            }
            let cursor = crate::desktop_geometry::cursor_feedback().unwrap_or_else(
                || serde_json::json!({"type":"cursor","x":0.5,"y":0.5,"visible":false}),
            );
            if feedback_dc.buffered_amount().await < 32768
                && feedback_dc.send_text(cursor.to_string()).await.is_err()
            {
                break;
            }
            ticks += 1;
            if ticks.is_multiple_of(20) {
                if let Some(bps) = xyadapt::step() {
                    screen::set_target_bitrate_bps(bps);
                }
                let mut meta = base_meta.clone();
                meta["video"] = video_policy::telemetry();
                meta["capture"] = screen::capture_telemetry();
                meta["inputGeometry"] = serde_json::json!(crate::desktop_geometry::active());
                meta["cursorEmbedded"] = serde_json::json!(screen::cursor_embedded());
                meta["wanted"] = serde_json::json!(screen::wanted_display());
                meta["displays"] = serde_json::json!(screen::list_displays());
                if feedback_dc.send_text(meta.to_string()).await.is_err() {
                    break;
                }
            }
        }
    });
}

/// Thread injeksi: `SendInput` sinkron, jangan blokir runtime async yang juga
/// melayani video/ICE. Saat kanal tutup, tombol yang masih ditekan dilepas.
fn spawn_injector(
    mut inj_rx: tokio::sync::mpsc::Receiver<InputEvent>,
    inject_closed: tokio::sync::watch::Receiver<bool>,
) {
    std::thread::spawn(move || {
        let injector = Injector::new();
        let mut lease = InputLease::default();
        'inject: while let Some(first) = inj_rx.blocking_recv() {
            for ev in input_queue::ready_batch(&mut inj_rx, first) {
                // Discard stale queued actions after disconnect;
                // release only keys/buttons actually injected.
                if *inject_closed.borrow() {
                    break 'inject;
                }
                if injector.inject(&ev) {
                    lease.applied(&ev);
                } else {
                    eprintln!("[xydesk-host] injeksi input ditolak");
                }
            }
        }
        for event in lease.releases() {
            if !injector.inject(&event) {
                eprintln!("[xydesk-host] pelepasan input ditolak Windows");
            }
        }
    });
}

fn wallpaper_error(id: u32) -> String {
    serde_json::json!({"type":"wallpaper-error","id":id}).to_string()
}

/// Kirim pratinjau wallpaper dalam potongan base64 sebagai lalu lintas latar,
/// tidak pernah mendahului balasan kursor/kontrol.
fn spawn_wallpaper_reply(reply: Arc<RTCDataChannel>, id: u32, busy: Arc<AtomicBool>) {
    tokio::spawn(async move {
        use base64::Engine;
        match tokio::task::spawn_blocking(crate::wallpaper::configured_preview).await {
            Ok(Ok(bytes)) => {
                let encoded = base64::engine::general_purpose::STANDARD.encode(bytes);
                let chunks: Vec<_> = encoded.as_bytes().chunks(16384).collect();
                // Wallpaper is background traffic, not an
                // unbounded burst ahead of cursor/control replies.
                let transfer = async {
                    for (index, chunk) in chunks.iter().enumerate() {
                        while reply.buffered_amount().await >= 32768 {
                            if reply.ready_state() != RTCDataChannelState::Open {
                                return;
                            }
                            tokio::time::sleep(std::time::Duration::from_millis(20)).await;
                        }
                        let message=serde_json::json!({"type":"wallpaper","id":id,"index":index,"total":chunks.len(),"data":std::str::from_utf8(chunk).unwrap()}).to_string();
                        if reply.send_text(message).await.is_err() {
                            return;
                        }
                        tokio::time::sleep(std::time::Duration::from_millis(80)).await;
                    }
                };
                let _ = tokio::time::timeout(std::time::Duration::from_secs(12), transfer).await;
            }
            _ => {
                let _ = reply
                    .send_text(serde_json::json!({"type":"wallpaper-error","id":id}).to_string())
                    .await;
            }
        }
        busy.store(false, Ordering::Release);
    });
}

async fn dispatch(dc: Arc<RTCDataChannel>, pointer_dc: Option<Arc<RTCDataChannel>>) {
    let wallpaper_busy = Arc::new(AtomicBool::new(false));
    let (tx, mut rx) = tokio::sync::mpsc::channel(128);
    let (closed_tx, mut closed_rx) = tokio::sync::watch::channel(false);
    dc.on_close(Box::new({
        let closed_tx = closed_tx.clone();
        move || {
            let _ = closed_tx.send(true);
            Box::pin(async {})
        }
    }));
    dc.on_message(Box::new({
        let reliable_tx = tx.clone();
        move |m| {
            let tx = reliable_tx.clone();
            Box::pin(async move {
                if !m.is_string && m.data.len() <= 65536 {
                    let _ = tx.send(m.data.to_vec()).await;
                }
            })
        }
    }));
    if let Some(pointer_dc) = pointer_dc {
        pointer_dc.on_close(Box::new({
            let closed_tx = closed_tx.clone();
            move || {
                let _ = closed_tx.send(true);
                Box::pin(async {})
            }
        }));
        pointer_dc.on_message(Box::new(move |m| {
            let tx = tx.clone();
            Box::pin(async move {
                // Pointer events are already lossy at
                // SCTP; keep only valid small binary
                // packets and never block video/ICE.
                if !m.is_string && m.data.len() <= 64 {
                    let _ = tx.send(m.data.to_vec()).await;
                }
            })
        }));
    }
    // Injeksi di thread blocking terpisah: SendInput
    // adalah syscall sinkron — jangan blokir runtime
    // async yang juga melayani video/ICE.
    let (inj_tx, inj_rx) = input_queue::channel();
    spawn_injector(inj_rx, closed_rx.clone());
    let mut last_wallpaper = std::time::Instant::now()
        .checked_sub(std::time::Duration::from_secs(15))
        .unwrap();
    while let Some(data) =
        tokio::select! {biased; _=closed_rx.changed()=>None, data=rx.recv()=>data}
    {
        if data.len() == 2 && data[0] == 0x0f {
            if video_policy::request_fps(data[1]) {
                screen::set_target_bitrate_bps(screen::target_bitrate_bps());
                let _ = dc.send_text(meta_json().to_string()).await;
            }
            continue;
        }
        if data.len() == 2 && data[0] == 0x0c {
            let mode = data[1];
            if video_policy::request(mode) {
                // Terapkan target desktop setelah preferensi
                // client benar-benar diketahui. Sebelumnya host
                // selalu meminta ukuran berdasarkan SDP saja, lalu
                // RDP dapat bertahan di 940x529 walau UI memilih
                // 720p. Mode HD meminta 1280x720; bila Windows/
                // RDP menolak, encoder software tetap menjamin
                // output 1280x720 lewat resize.
                let level = if mode == 0 {
                    31
                } else {
                    video_policy::level().max(40)
                };
                let wanted = screen::wanted_display();
                if let Some(display) = screen::list_displays()
                    .into_iter()
                    .find(|d| d.index == wanted)
                {
                    let report = tokio::task::spawn_blocking(move || {
                        crate::desktop_mode::request(display.name, level)
                    })
                    .await;
                    if let Ok(report) = report {
                        eprintln!("[xydesk-host] resolusi client {mode}: {report:?}");
                    }
                }
                screen::set_target_bitrate_bps(screen::target_bitrate_bps());
                let _ = dc.send_text(meta_json().to_string()).await;
            }
            continue;
        }
        if data.len() == 5 && data[0] == 0x0d {
            let id = u32::from_le_bytes(data[1..5].try_into().unwrap());
            if last_wallpaper.elapsed() < std::time::Duration::from_secs(10)
                || wallpaper_busy.swap(true, Ordering::AcqRel)
            {
                let _ = dc.send_text(wallpaper_error(id)).await;
                continue;
            }
            last_wallpaper = std::time::Instant::now();
            spawn_wallpaper_reply(dc.clone(), id, wallpaper_busy.clone());
            continue;
        }
        // Pesan rusak dibuang diam-diam (decode → None):
        // input korup tidak boleh mematikan sesi.
        if let Some(ev) = crate::input::decode(&data) {
            // Papan klip = bukan injeksi SendInput.
            // CLIPBOARD_SET menulis ke papan klip
            // PC; CLIPBOARD_REQ meminta isinya
            // dikirim balik ke klien (model tarik —
            // lihat modul `clipboard`).
            match ev {
                InputEvent::ClipboardSet(text) => {
                    match tokio::task::spawn_blocking(move || crate::clipboard::set_text(&text))
                        .await
                    {
                        Ok(Ok(())) => println!("[xydesk-host] papan klip PC diisi dari client"),
                        Ok(Err(e)) => eprintln!("[xydesk-host] papan klip gagal diisi: {e:#}"),
                        Err(e) => eprintln!("[xydesk-host] task papan klip gagal: {e}"),
                    }
                    continue;
                }
                InputEvent::ClipboardRequest => {
                    match tokio::task::spawn_blocking(crate::clipboard::get_text).await {
                        Ok(Ok(text)) => {
                            let out = crate::input::encode_clipboard_set(&text);
                            let _ = dc.send(&bytes::Bytes::from(out)).await;
                        }
                        Ok(Err(e)) => eprintln!("[xydesk-host] papan klip PC gagal dibaca: {e:#}"),
                        Err(e) => eprintln!("[xydesk-host] task papan klip gagal: {e}"),
                    }
                    continue;
                }
                _ => {}
            }

            // Pindah monitor / quality / bitrate = bukan injeksi.
            match ev {
                InputEvent::DisplaySelect(i) => {
                    screen::select_display(i);
                    let _ = dc.send_text(meta_json().to_string()).await;
                    continue;
                }
                InputEvent::VideoQuality(q) => {
                    // 0=auto 1=medium 2=high 3=ultra → map ke bitrate preset host
                    let bps = match q {
                        1 => 8_000_000,
                        2 => 15_000_000,
                        3 => 25_000_000,
                        _ => screen::DEFAULT_TARGET_BPS,
                    };
                    if q == 0 {
                        screen::set_target_bitrate_bps(screen::DEFAULT_TARGET_BPS);
                    } else {
                        screen::set_target_bitrate_bps(bps);
                    }
                    println!("[xydesk-host] quality dari client: {} -> {} bps", q, bps);
                    continue;
                }
                InputEvent::VideoBitrate(mbps) => {
                    if mbps == 0 {
                        screen::set_target_bitrate_bps(screen::DEFAULT_TARGET_BPS);
                        println!("[xydesk-host] bitrate auto dari client");
                    } else {
                        let wanted = (mbps as u32).clamp(1, 50) * 1_000_000;
                        let applied = xyadapt::request(screen::target_bitrate_bps(), wanted);
                        if let Some(bps) = applied {
                            if screen::set_target_bitrate_bps(bps) {
                                let line = format!("bitrate client {mbps} Mbps -> {bps} bps");
                                println!("[xydesk-host] {line}");
                            }
                        }
                    }
                    continue;
                }
                _ => {}
            }
            if !input_queue::send(&inj_tx, &mut closed_rx, ev).await {
                break;
            }
        }
    }
}
