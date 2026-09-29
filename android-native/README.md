# XyDesk Native (Android, Kotlin) — Fase 1

Client sesi native tanpa Flutter: signaling + WebRTC (libwebrtc) + decode
MediaCodec langsung ke `SurfaceViewRenderer`, kanal `input` biner yang sama
dengan host (`host/src/input.rs`). Login OTP email, ID + password pairing,
mode trackpad (geser, ketuk, dua jari = klik kanan/scroll).

Build hanya di CI: workflow **Android Native** → artefak `XyDesk-Native-APK`.
Package `id.xyverse.xydesk.native` (bisa dipasang berdampingan dengan APK Flutter).

Belum ada: keyboard, gamepad, audio kontrol, pilih monitor, statistik latensi.
