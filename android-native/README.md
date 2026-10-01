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

- `res/raw/xydesk_vo.mp3` — master stereo intro sinematik + VO "XyDesk" (line-draw shimmer, sub-bass, kilau kaca, hall reverb), diputar saat first launch dan putar ulang intro.
- `res/raw/sfx_confirm.mp3` — chime kristal konfirmasi login (geser-ke-Google dan verifikasi OTP).
- `res/font/manrope.ttf` — Manrope (SIL OFL 1.1, © The Manrope Project Authors), font variabel dibundel; tidak ada font dari jaringan.
- `res/drawable/ic_social_*.xml` — logo resmi dari Simple Icons (CC0 1.0); `ic_google_g.xml` — logo "G" Google (pedoman brand Google Sign-In).
