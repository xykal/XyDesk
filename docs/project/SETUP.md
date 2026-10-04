# SETUP — dari workspace ke produksi (tanpa VM/VPS, tanpa kartu kredit)

Panduan menjalankan seluruh sistem XyDesk lewat GitHub Actions dan layanan
serverless. Build/compile rilis tidak dilakukan dari laptop pribadi, kecuali
untuk uji lokal area tertentu.

## Status terkini

| Item | Status |
|---|---|
| Sumber versi | `VERSION` (`X.Y.Z+NN`) |
| Signaling Worker live | `https://signal.xydesk.my.id` (custom domain) |
| Worker secrets inti | terpasang di Cloudflare Worker, nilai tidak disimpan di repo |
| Custom domain signaling | `signal.xydesk.my.id` -> worker |
| Endpoint TURN `/turn-ice` | jadi; ExpressTurn aktif lewat secret Worker `TURN_DIRECT_*` |
| Endpoint `/signal-token` | jadi; JWT pengguna -> token signaling |
| Host WebRTC | engine Rust di `host/` |
| Panel host Windows | Win32 native di `packaging/native-host/` |
| Android client | Kotlin/Compose native di `android-native/` |
| Web client | Vite/React di `web/` |
| CI Build penuh | `build.yml` manual (`workflow_dispatch`) |
| CI Android cepat | `android-native.yml` untuk APK native |
| CI Host cepat | `host-check.yml` untuk format/clippy/check host |
| Deploy Signaling/Web/News | workflow manual setelah izin operator |

## Tindakan yang butuh operator (di luar sandbox)

1. **Deploy produksi** (`deploy-signaling`, `deploy-web`, `deploy-news`,
   `release.yml`) hanya dijalankan setelah izin operator. Agent boleh menyiapkan
   patch dan bukti build/test, tetapi tidak menerbitkan produksi sendiri.
2. **TURN relay** sudah memiliki triplet `TURN_DIRECT_*` di Worker. GitHub
   Secrets TURN boleh kosong agar deploy CI tidak menimpa secret Worker yang
   benar. Tambahan provider TURN cadangan bersifat opsional, bukan syarat rilis.
3. **Keystore Android** dikelola sebagai secret Actions. Bila keystore dirotasi,
   rilis berikutnya wajib jujur bahwa update-in-place dari APK lama tidak bisa.
4. **Uji lapangan Windows/Android** tetap di perangkat nyata pemilik: DXGI,
   hardware encode, relay TURN, audio/mic, input, clipboard opt-in, dan latency
   glass-to-glass.

## 1. Struktur repo aktif

```
XyDesk/
├── VERSION              # sumber tunggal versi X.Y.Z+NN
├── android-native/      # client Android Kotlin + Compose + C++ kecil
├── cloudflare/          # signaling + auth produksi (Worker + Durable Object)
├── signaling/           # opsi self-host/LAN (Go)
├── host/                # engine Rust: capture, encode, WebRTC, control API
├── packaging/native-host/ # panel Windows native + installer payload
├── web/                 # web client/landing Vite + React
├── web_deploy/          # konfigurasi deploy web
├── news/                # worker berita + D1
├── admin/               # panel admin worker
├── docs/                # dokumentasi teknis, QA, legal, roadmap
└── .github/workflows/   # build, release, deploy manual/terarah
```

Tidak ada stack Flutter aktif: tidak ada `pubspec.yaml`, `lib/`, atau `*.dart`.
Shell desktop Tauri/Electron lama di `desktop/` juga sudah dihapus.

## 2. Setting GitHub Secrets dan Variables

Nilai secret dikelola operator secara privat di luar repo. Jangan pernah
menulis nilai secret ke file yang di-commit.

### Secrets Actions utama

| Secret | Fungsi |
|---|---|
| `CLOUDFLARE_API_TOKEN` | Deploy Worker/Web/News ke Cloudflare |
| `CLOUDFLARE_ACCOUNT_ID` | Akun Cloudflare untuk workflow deploy |
| `XYDESK_SECRET` | Secret internal signaling/token |
| `ADMIN_SECRET` | Secret endpoint admin internal |
| `AUTH_SECRET` | Kunci JWT/OTP |
| `RESEND_API_KEY` | Email OTP/notifikasi |
| `ANDROID_KEYSTORE_B64` | Keystore APK release |
| `ANDROID_KEYSTORE_PASS` | Password keystore APK release |
| `RELEASE_TOKEN` | Upload APK cepat ke draft release bila workflow Android native dijalankan di `main` |
| `ONESIGNAL_REST_API_KEY` | Push update saat Release |

### Variables repo

| Variable | Fungsi |
|---|---|
| `GOOGLE_WEB_CLIENT_ID` | Google Sign-In Android native dan web build |
| `GOOGLE_CLIENT_ID` | OAuth/signaling Worker |
| `RESEND_FROM` | Alamat pengirim email |

TURN produksi saat ini dipertahankan sebagai secret Worker (`TURN_DIRECT_*`).
Kalau workflow deploy menerima secret TURN kosong, workflow harus melewatinya,
bukan menimpa nilai Worker yang sudah hidup.

## 3. Trigger build dan pemeriksaan

- **Build penuh**: workflow `Build` (`build.yml`) manual. Ini membangun APK
  native per ABI, host Rust, panel Win32, web, Worker, installer lint, dan
  gerbang lintas-dokumen.
- **Android native cepat**: workflow `Android Native` (`android-native.yml`)
  dari perubahan `android-native/**` atau manual; menghasilkan APK native.
- **Host cepat**: workflow `Host Check` (`host-check.yml`) manual; menjalankan
  format/clippy Linux dan clippy target Windows.
- **Web shots**: workflow `Web Layout Shots` untuk perubahan web dan screenshot
  layout.
- **Deploy**: `deploy-signaling.yml`, `deploy-web.yml`, dan `deploy-news.yml`
  hanya manual setelah izin operator.
- **Release**: `release.yml` manual memakai SHA yang sudah punya Build penuh
  sukses. Workflow menolak duplikasi tag/release.

## 4. Setelah deploy signaling jalan

Host tidak memakai endpoint admin publik untuk pengguna biasa. Token host bisa
diterbitkan oleh operator memakai secret admin:

```bash
curl -H "X-Admin: <ADMIN_SECRET>" \
  "https://signal.xydesk.my.id/issue?purpose=gaming-pc-01"
```

Client app tidak memakai `/issue`: setelah pengguna login, app menukar JWT
sesi menjadi token signaling lewat `GET /signal-token?id=<deviceId>` dengan
header `Authorization: Bearer <jwt>`.

## 5. Urutan kerja selanjutnya (dari ROADMAP.md)

1. Uji host Windows nyata: DXGI, hardware encode, audio, input, control API,
   dan TURN relay.
2. Uji Android native + web melawan host yang sama: pairing, render, input,
   clipboard opt-in, mic/audio, presence, reconnect.
3. Ukur latency glass-to-glass sesuai `docs/LATENCY.md` dan catat bukti di
   `docs/qa/`.
4. Tahan rilis publik sampai bukti perangkat nyata tersedia.

## Troubleshooting umum

- **wrangler gagal auth**: pastikan token punya scope Worker/Pages/D1 yang
  dibutuhkan workflow terkait.
- **Durable Object error saat local dev**: pastikan `Hub` di-export dari Worker
  signaling.
- **`npm ci` gagal**: pastikan `package-lock.json` ikut ter-commit di folder
  worker/web yang sedang diuji.
- **TURN 503 `turn-not-configured`**: cek nama secret Worker `TURN_DIRECT_*` di
  Cloudflare; jangan menulis nilai secret ke log.
- **APK release unsigned**: cek secret keystore Actions. Untuk build lokal,
  Gradle akan membuat APK debug tanpa keystore release.
