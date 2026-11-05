# XyDesk untuk macOS — Host & Klien Native (Swift)

Aplikasi Mac native untuk XyDesk: **host** (menyiarakan layar Mac agar bisa
dikendalikan dari Android/Web) dan **klien** (mengendalikan PC Windows/Mac lain
dari Mac). Protokol **sama persis** dengan Android, Web, dan host Windows —
signaling `hello/pair/offer/answer/ice/bye`, kanal data `input`/`pointer`,
dan paket input biner `StreamXy` (tag `0x01`–`0x10`, little-endian, seperti
`host/src/input.rs`).

## Struktur

```
XyDeskMac/
  Package.swift            — SPM: 2 executable + 1 lib bersama
  Sources/XyDeskCore/      — StreamXy (paket), Signaling (WS+API), PeerLink (WebRTC)
  Sources/XyDeskHost/      — menu-bar host: CaptureEngine (ScreenCaptureKit),
                             InputInjector (CGEvent), HostSession
  Sources/XyDeskClient/    — SwiftUI klien: sesi, render video, InputCapture
```

## Build (di Mac)

```sh
cd macos/XyDeskMac
swift build            # atau buka Package.swift di Xcode 15+
swift run XyDeskMacHost
swift run XyDeskMacClient
```

Dependensi WebRTC: `GoogleWebRTC` (distribusi biner xcframework via SPM).
Bila repo dependensi berpindah/berhenti: pasang `WebRTC.xcframework` manual
lalu ganti `dependencies` pada `Package.swift` — API yang dipakai hanya
`RTCPeerConnectionFactory`, `RTCPeerConnection`, `RTCDataChannel`,
`RTCVideoSource/Track`, `RTCMTLVideoView` (stabil sejak 2020).

## Izin (WAJIB saat runtime)

| Izin | Untuk | Info.plist |
|---|---|---|
| Screen Recording | Host: tangkap layar | `NSScreenCaptureUsageDescription` |
| Accessibility | Host: injeksi mouse/keyboard | `NSAppleEventsUsageDescription` (prompt AX muncul otomatis) |
| Microphone | Opsional, sesi audio menyusul | `NSMicrophoneUsageDescription` |

Untuk aplikasi tanpa dock icon (host), set `LSUIElement = true` di
Info.plist saat membungkus app bundle.

## Cara pakai

**Host:** jalankan `XyDeskMacHost` → menu bar → "Atur kode perangkat…" (9
digit dari akun XyDesk, halaman Perangkat di web) → salin kode → bagikan ke
perangkat lain. "Atur password pairing" untuk membatasi siapa yang boleh
menyambung (kosongkan = semua diterima).

**Klien:** jalankan `XyDeskMacClient` → masukkan kode perangkat host + password
+ JWT akun → "Sambung". Gerakkan mouse/ketik di jendela sesi untuk mengendalikan
host.

## Akun (JWT)

Sementara JWT ditempel manual (salin dari sesi web — panel Developer atau
endpoint login). Poin integrasi OAuth/OTP ada di `TokenStore`
(`Sources/XyDeskHost/HostSession.swift`): ganti dengan alur
`ASWebAuthenticationSession` (Google) atau OTP email sesuai
`web/src/google.ts` / `web/src/api.ts` saat pembungkusan app bundle.

## Batas jujur versi pertama

- Audio host (system/mic) belum disiarkan — video + input + clipboard dulu.
- Gamepad diterima protokolnya tapi belum diinjeksi (IOKit menyusul).
- **Layar virtual (super screen)** macOS butuh `CGVirtualDisplay`
  (API privat, dipakai UU Remote juga) — titik integrasi ada di
  `InputInjector.handle(.virtualDisplay)`; dipasang sebagai modul terpisah
  supaya penolakan App Store bisa dibuang tanpa menyentuh inti.
- Peta keyboard Windows-VK → CGKeyCode mencakup huruf, angka, F1–F12, panah,
  dan modifier; tombol eksotis diabaikan sampai peta dilengkapi.
- Klien memakai `moveAbs` ternormalisasi (sama dengan web); host Windows tetap
  menerima `moveRel` dari Android tanpa perubahan.

## Kecocokan protokol

| Asal | Rujukan |
|---|---|
| Tag & tata letak paket | `host/src/input.rs` (`mod tag`) |
| Builder setara | `android-native/app/src/main/cpp/streamxy/input.cpp` |
| Alur signaling klien | `web/src/rtc.ts` (hello/pair/offer/answer/ice/bye) |
| Endpoint API | `web/src/api.ts` (`/signal-token`, `/turn-ice`, `wss://…/ws`) |
