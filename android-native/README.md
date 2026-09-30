# XyDesk Native (Android, Kotlin) — Fase 1

Client sesi native tanpa Flutter: signaling + WebRTC (libwebrtc) + decode
MediaCodec langsung ke `SurfaceViewRenderer`, kanal `input` biner yang sama
dengan host (`host/src/input.rs`). Login OTP email, ID + password pairing,
mode trackpad (geser, ketuk, dua jari = klik kanan/scroll).

Build hanya di CI: workflow **Android Native** → artefak `XyDesk-Native-APK`.
Package `id.xyverse.xydesk` (bisa dipasang berdampingan dengan APK Flutter).

Belum ada: keyboard, gamepad, audio kontrol, pilih monitor, statistik latensi.

## libstreamxy.so (C++)

`app/src/main/cpp/streamxy/` — API C murni (`streamxy.h`) agar sama-sama bisa
dipanggil dari JNI, Dart FFI, maupun Rust host. Saat ini: enkoder protokol
input dan ring telemetri latensi (persentil). Rencana: jalur decode
AMediaCodec low-latency langsung ke Surface dan transport UDP khusus game.

## Aset suara

- `res/raw/xydesk_vo.mp3` — VO "XyDesk", dibuat sendiri (TTS), diputar sekali saat first launch.
- `res/raw/sfx_confirm.mp3` — efek konfirmasi geser-ke-Google, sumber: myinstants.com (`/en/instant/apple-pay-45496/`).
