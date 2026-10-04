# Arsitektur XyDesk

```
┌──────────────────────────────┐        ┌──────────────────────────────┐
│  HOST (PC yang dikontrol)     │        │  CLIENT                       │
│  Rust engine + panel Win32    │        │  Android native / Web PWA     │
│                               │        │                               │
│  DXGI Desktop Duplication     │        │  Surface/HTMLVideo render     │
│        │ (capture)            │        │        ▲                      │
│        ▼                      │        │        │ decode hardware       │
│  NVENC/AMF/QuickSync/MFT      │        │        │                      │
│        │ (encode)             │        │  WebRTC SDK / browser RTCPeer │
│        ▼                      │        │        ▲                      │
│  Rust `webrtc` crate          │        │  input (mouse/kb/HUD)         │
│        │                      │        │  → data channel               │
└────────┼──────────────────────┘        └────────┼──────────────────────┘
         │                                        │
         │          WebRTC (DTLS-SRTP)            │
         │        media + data channel            │
         │          END-TO-END                    │
         │                                        │
         ▼                                        ▼
   ┌───────────────────────────────────────────────────┐
   │  SIGNALING — hanya SDP/ICE, bukan media           │
   │  Cloudflare Worker + Durable Object (serverless)  │
   │  gratis, tanpa VM/VPS, tanpa kartu kredit          │
   └───────────────────────────────────────────────────┘
              │                    │
              ▼                    ▼
         STUN publik         TURN relay produksi
         NAT discovery       relay bila direct gagal
```

## Kenapa pemisahan ini

1. **Server tak bisa menyadap layar** — media end-to-end. Ini bukan sekadar
   performa, ini kepercayaan pengguna.
2. **Server ringan** — muat di Cloudflare Workers/free-tier serverless; biaya
   operasional tetap rendah.
3. **Protokol stabil** — penggantian backend atau provider TURN tidak mengubah
   kontrak host/client.

## Kenapa host pakai Rust

- **Desktop Duplication API (DXGI)** butuh akses Win32 yang cepat dan aman;
  Rust memberi kontrol penuh tanpa GC pause yang bikin frame jitter.
- **Pipeline encode** harus dekat dengan D3D11/MFT/NVENC agar target latency
  tidak habis di copy frame.
- **Panel Windows hanya shell**. Panel Win32 di `packaging/native-host/`
  membuka/menutup engine, menampilkan status, dan memberi aksi operator. Logika
  capture, encode, WebRTC, audio, dan input tetap di `host/`.

## Alur data host (path panas)

```
DXGI AcquireNextFrame (8 ms budget)     // d3d11 texture
   → RGBA -> NV12 (pixfmt.rs)           // zero-copy DXGI masih backlog
   → NVENC > MFT (AMD/Intel) > openh264 // host/src/screen.rs memilih encoder
   → frame ke WebRTC RTP                // pacing + NACK + FEC
   → kirim
```

Rule of thumb: **total waktu di path panas harus < 25 ms** untuk menyisakan
jaringan + decode di bawah 40 ms glass-to-glass.

## Alur input (path balik, latency juga penting)

```
client pointer/keys/HUD → data channel (reliable, ordered)
   → host inject (SendInput / raw input)   // < 5 ms target lokal
```

Input pakai data channel yang **reliable** karena kehilangan satu event klik
atau key-up bisa membuat sesi terasa rusak. Optimasi coalescing hanya boleh
menggabungkan posisi pointer berurutan; tombol down/up tetap berurutan.

## Klien aktif

- **Android native** (`android-native/`): Kotlin + Compose, WebRTC SDK Android,
  dan pustaka C++ kecil untuk protokol/HUD. Versi APK dibaca dari `VERSION`.
- **Web client** (`web/`): PWA/browser client untuk akses cepat, landing,
  login, dan verifikasi kontrak host lewat browser.

Flutter bukan stack aktif lagi: tidak ada `pubspec.yaml`, `lib/`, atau
`*.dart`. Shell Tauri/Electron lama juga sudah dihapus.

## Keputusan yang masih terbuka (tulis di sini, jangan di kepala)

- **Hardware encode nyata**: NVENC sudah jalur utama ketika tersedia; AMD/Intel
  lewat MFT masih butuh bukti perangkat nyata dan log yang jelas.
- **Codec default jangka panjang**: H.264 tetap baseline kompatibilitas. AV1
  baru dipertimbangkan setelah pipeline H.264 terbukti memenuhi target.
- **Transport input**: data channel dulu untuk PoC; UDP khusus hanya layak bila
  metrik jitter input menunjukkan kebutuhan nyata.

## Struktur repo

```
XyDesk/
├── VERSION              # sumber tunggal versi X.Y.Z+NN
├── android-native/      # Android Kotlin/Compose + C++ kecil
├── cloudflare/          # signaling + auth produksi (Worker + Durable Object)
├── signaling/           # Go — opsi self-host/LAN
├── host/                # Rust engine: capture, encode, WebRTC, input, audio
├── packaging/native-host/ # panel Windows native + payload installer
├── web/                 # web client/landing Vite + React
└── docs/                # PROTOCOL, ARCHITECTURE, FREE-STACK, QA, legal
```
