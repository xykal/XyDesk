# HANDOFF — Antrian Kerja Lintas Role

Hanya item yang **masih terbuka**. Log lama: `../archive/HANDOFF-2026-09.md`
(beku). Menutup item = hapus di sini + satu baris `CHANGELOG.md` `[Belum terbit]`.

Dibersihkan operator 2026-10-01: item Flutter/`desktop/` Electron yang sudah
diganti klien Kotlin + panel Win32 dicoret. Verifikasi perangkat nyata tetap
item terbuka (tidak bisa ditutup dari CI).

**Flutter (2026-10-02):** tidak ada `pubspec.yaml` / `*.dart`. Sisa `android/`
(GeneratedPluginRegistrant + local.properties) dihapus. CI tidak lagi
menyebut `FLUTTER_VERSION`. Changelog/docs lama boleh tetap menyebut Flutter
sebagai sejarah. **Jangan rilis publik** sampai host/web/APK lolos uji
lapangan.

**Status stack (bukan lulus QA):** host Rust 6.11.1 ada; web Vite live HTTP
200 di app.xydesk.my.id (bukan uji sesi); APK hanya `android-native/` (CI
Gradle, belum bukti HP).

## Untuk: Client Android native

- [ ] **Screenshot sesi Android untuk berita** — rail + panel pengaturan, dari
  APK rilis di perangkat/emulator dengan video host asli. Pola nama:
  `web/public/news/shots/<versi>-android-sesi-*.jpg`.
- [ ] **Bukti lapangan** — pairing, IME 0x06 TEXT, clipboard opt-in, mic ke
  host, presence online/offline. CI tidak menggantikan HP.

Tertutup 2026-10-01: error pra-welcome. `RtcSession` rotasi ID; `Presence`
rotasi ID (#40). Clipboard tarik `0x09` sudah ada (opt-in).

## Untuk: Desktop Shell (Win32, `packaging/native-host/`)

- [ ] **Uji Windows asli** — TURN relay, tray, capture RDP/GDI. Wine/CI bukan
  bukti lapangan. Lihat `docs/qa/relay-turn-native-2026-09-23.md`.
- [ ] Screenshot host Windows untuk berita: ID+QR + panel saat sesi.

Tertutup sebagai usang: shell Electron/`desktop/` (dihapus 2026-09-23).

## Untuk: CI / Release

- [ ] **Keystore Android dirotasi** — APK lama tidak update-in-place; artikel
  rilis berikutnya harus jujur: uninstall dulu. Verifikasi
  `apksigner verify --print-certs` pada APK pertama keystore baru.
- [x] Native Tentang → Cek pembaruan membaca
  `github.com/xykal/XyDesk/releases/latest/download/update.json` (bukan
  domain app). Draft = 404, ditampilkan jujur. Belum unduh/pasang APK
  (hanya buka `release_url`). Tertutup 2026-10-01.
- [ ] Versi, berita, `workflow_dispatch` Build/Release = keputusan operator.
  Jangan terbitkan ulang nomor build yang sama.

Usang: resep verifikasi Flutter Linux; permintaan dispatch Galih 3 Sep 2026.

## Untuk: Backend / Edge

- [x] HTTP DO Hub (`/kick`, `/stats`, `/hub/devices`) menuntut
  `x-internal-admin: 1` (sama seperti AuthStore). Worker publik tetap tidak
  merutekan path itu. Tertutup 2026-10-01, belum deploy signaling.
- [x] **ExpressTurn hidup di Worker** sebagai triplet `TURN_DIRECT_*`
  (bukan GitHub Secrets). Dicek 2026-10-02: nama secret ada di
  `xydesk-signaling`; nilai tidak di-log. GitHub Secrets TURN kosong =
  disengaja: deploy CI **melewati** secret kosong, tidak menimpa Worker.
  Cadangan Open Relay / REST **opsional**, bukan blocker. Satu penyedia
  tetap risiko kuota/outage — jangan deploy-signaling hanya untuk “isi
  cadangan” tanpa kunci baru.
- [ ] Billing sewa PC otomatis: butuh keputusan gateway + provisioning;
  bukan kerja sesi ini.

## Untuk: News & Konten

- [ ] Artikel berikutnya: apa+kenapa, changelog pengguna, screenshot native
  Android + host Windows (web sudah ada `web-sesi-*`). Penulis: Haekal Saputra.

## Untuk: Host Engine

- [ ] Preview wallpaper host: generator masih memaksa pratinjau 1920×1080/Lanczos dan bisa menolak wallpaper yang terlalu rinci. Bila kartu APK masih kosong setelah retry native, sederhanakan generator host supaya mempertahankan rasio asli, tanpa filter/HD paksa, dengan batas byte yang tetap aman.
- [ ] Encoder MFT: belum diuji GPU AMD/Intel nyata (hanya RDP).
- [ ] Mic klien → VB-CABLE: alasan `micInput.reason` ada di 6.11.1; bukti
  lapangan masih butuh PC + APK.

Tertutup: hello ditolak hub tidak lagi menggantung loop host.
