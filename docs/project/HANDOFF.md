# HANDOFF — Antrean Kerja Terbuka

Hanya item yang **masih terbuka**, dikelompokkan per area kode. Log lama:
`../archive/HANDOFF-2026-09.md` (beku). Menutup item = hapus di sini + satu
baris `CHANGELOG.md` `[Belum terbit]`.

Dibersihkan 2026-10-01: item Flutter/`desktop/` Electron yang sudah diganti
klien Kotlin + panel Win32 dicoret. Verifikasi perangkat nyata tetap item
terbuka (tidak bisa ditutup dari CI).

Disusun ulang 2026-10-06: judul bagian tidak lagi menyebut role — sistem role
dihapus, antrean ini milik siapa pun yang mengerjakan area tersebut.

**Flutter (2026-10-02):** tidak ada `pubspec.yaml` / `*.dart`. Sisa `android/`
(GeneratedPluginRegistrant + local.properties) dihapus. CI tidak lagi
menyebut `FLUTTER_VERSION`. Changelog/docs lama boleh tetap menyebut Flutter
sebagai sejarah. **Jangan rilis publik** sampai host/web/APK lolos uji
lapangan.

**Status stack (bukan lulus QA):** host Rust 6.11.1 ada; web Vite live HTTP
200 di app.xydesk.my.id (bukan uji sesi); APK hanya `android-native/` (CI
Gradle, belum bukti HP).

## Klien Android native (`android-native/`)

- [ ] **Screenshot sesi Android untuk berita** — rail + panel pengaturan, dari
  APK rilis di perangkat/emulator dengan video host asli. Pola nama:
  `web/public/news/shots/<versi>-android-sesi-*.jpg`.
- [ ] **Bukti lapangan** — pairing, IME 0x06 TEXT, clipboard opt-in, mic ke
  host, presence online/offline. CI tidak menggantikan HP.

Tertutup 2026-10-01: error pra-welcome. `RtcSession` rotasi ID; `Presence`
rotasi ID (#40). Clipboard tarik `0x09` sudah ada (opt-in).

## Panel desktop Win32 (`packaging/native-host/`)

- [ ] **Uji Windows asli** — TURN relay, tray, capture RDP/GDI. Wine/CI bukan
  bukti lapangan. Lihat `docs/qa/relay-turn-native-2026-09-23.md`.
- [ ] Screenshot host Windows untuk berita: ID+QR + panel saat sesi.

Tertutup sebagai usang: shell Electron/`desktop/` (dihapus 2026-09-23).

## CI & rilis (`.github/`, `tool/`, `packaging/`)

- [ ] **Keystore Android dirotasi** — APK lama tidak update-in-place; artikel
  rilis berikutnya harus jujur: uninstall dulu. Verifikasi
  `apksigner verify --print-certs` pada APK pertama keystore baru.
- [x] Native Tentang → Cek pembaruan membaca
  `github.com/xykal/XyDesk/releases/latest/download/update.json` (bukan
  domain app). Draft = 404, ditampilkan jujur. Belum unduh/pasang APK
  (hanya buka `release_url`). Tertutup 2026-10-01.
- [ ] Versi, berita, `workflow_dispatch` Build/Release = keputusan pemilik repo.
  Jangan terbitkan ulang nomor build yang sama.

Usang: resep verifikasi Flutter Linux; permintaan dispatch Galih 3 Sep 2026.

## Backend & edge (`cloudflare/`, `signaling/`)

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

## Berita & konten (`news/`, `web/public/news/`)

- [ ] Artikel berikutnya: apa+kenapa, changelog pengguna, screenshot native
  Android + host Windows (web sudah ada `web-sesi-*`). Penulis: Haekal Saputra.

## Host engine (`host/`)

- [ ] Encoder MFT: belum diuji GPU AMD/Intel nyata (hanya RDP).
- [ ] Mic klien → VB-CABLE: alasan `micInput.reason` ada di 6.11.1; bukti
  lapangan masih butuh PC + APK.

Tertutup 2026-10-04: preview wallpaper host sudah diperkecil aman di PR #73 — mempertahankan rasio asli, tidak upscale, tidak memakai Lanczos, tidak memakai label HD, dan fallback JPEG lebih longgar.

Tertutup: hello ditolak hub tidak lagi menggantung loop host.

## Integrasi ilustrasi & bingkai VIP (host + web + APK)

Sumber: PR `sesi-20261004-agent-ilustrasi-vip-frame`. Panduan lengkap +
spec proporsi: [`docs/ILUSTRASI-ASSETS.md`](../ILUSTRASI-ASSETS.md).

- [ ] **Bingkai VIP (`frame_vip`) di semua UI profil pengguna berlangganan** —
  APK (layar akun/profil), web (akun), host panel bila menampilkan identitas
  pengguna. Foto = 48% lebar frame, ring di tengah — jangan `matchParentSize`.
  **TERHALANG (dicek 2026-10-06): belum ada konsep langganan di produk.**
  `UserProfile` (web) hanya punya `id`/`email`/`name`/`picture`; Worker tidak
  menyimpan status bayar per pengguna; APK dan host juga tidak mengenal
  tingkatan akun. `Billing.tsx` adalah sewa PC per jam yang pemesanannya masih
  manual lewat WhatsApp. Memasang bingkai sekarang berarti mengarang flag VIP
  yang tidak punya sumber data. Butuh keputusan produk lebih dulu: apa yang
  membuat seseorang VIP, dan field mana yang menyatakannya.
- [ ] **Ilustrasi melayang di onboarding & empty state** — APK + web:
  welcome `float_pc_mascot`, connect `float_phone`+`float_wifi`, gestur
  `float_cursor`+`float_keycap`, host `float_monitor`, riwayat kosong
  `float_pc_sleep`, OTP `float_mail`, low-latency `float_bolt`/`float_rocket`,
  keamanan `float_shield`/`float_padlock`.
  Progres 2026-10-04: web Devices/History empty state memakai
  `float-pc-sleep.webp` (PR #99); APK empty state perangkat/host memakai
  `R.drawable.float_pc_sleep` (batch ini).
  Progres 2026-10-06: web kartu fitur beranda memakai `float-cursor`,
  `float-shield`, `float-bolt`, `float-phone`; layar OTP memakai
  `float-mail`; blok "Cara main" memakai `float-phone`/`float-monitor`
  yang berganti mengikuti tab client/host.
  **Sisi web dianggap selesai.** Tiga aset sisanya tidak dipasang karena
  layarnya memang tidak ada di web, bukan karena terlewat:
  `float_pc_mascot` butuh layar welcome (web langsung mendarat di beranda,
  dan `hero-cartoon` tidak boleh diganti), `float_keycap` butuh tutorial
  gestur (`session_guidance.ts` isinya pesan kegagalan relay, bukan
  tutorial), dan `float_wifi` butuh indikator status signaling yang berdiri
  sendiri. Ketiganya tetap relevan untuk **APK**, yang belum tersentuh.
- [ ] **Dekorasi melayang** (`float_cloud`/`float_sparkle`/`float_globe`) di
  splash APK — animasi lembut, delay berbeda per elemen. Hero web selesai
  2026-10-06 (tiga dekorasi + `web/test/hero-deco-contract.test.mjs`); sisa
  yang terbuka hanya splash Android.
- [ ] Verifikasi ulang `tool/audit_assets.py` setelah integrasi; tema terang
  saja; `hero_login.webp` tidak diganti.
