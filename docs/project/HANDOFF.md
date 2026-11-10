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
- [ ] **Poles UI host lanjutan (Tugas 26 fase 2)** — permintaan pemilik
  2026-11-03. SELESAI digabung: (a) isian gerbang → kulit custom transparan
  (teks mengambang, cincin fokus, placeholder) + logo piringan gradien;
  (b) chrome dialog custom untuk Pengaturan/Profil/Panduan/QR (tanpa caption
  Windows, bar judul + tutup sendiri, sudut membulat, bisa digeser);
  (c) pemilih berkas custom menggantikan GetOpenFileName; (d) pil bitrate
  menggantikan COMBOBOX bawaan. Sisi APK juga selesai: anti-lag jalur input
  (antrean async + gabung gerakan; video TIDAK disentuh), toolbar sesi kaca
  gelap, Customize overlay lengkap (opasitas, snap grid, duplikat, rename,
  editor gelap). Fase 1 host (splash, modal custom, blok password Akses host,
  hapus password dari Pengaturan, hardening DLL) sudah digabung.
  Menu tray juga sudah diganti popup custom. Sisa kecil yang belum: halaman
  Koneksi/Profil/Panduan versi embedded masih memakai resource dialog + statis
  Windows sebagai dasar gambar (tampilan sudah owner-draw). (Koreksi dua catatan
  keliru sebelumnya: "pemilih huruf (ChooseFont)" dan "tooltip bawaan" tidak
  pernah ada di host — tidak perlu dikerjakan.)
- [ ] **Paket UI/UX ala StarDesk — sisa: build + verifikasi (izin pemilik).**
  Semua kode sudah termerge TANPA build: QR pairing Android (#224),
  device-quirks + capabilities (#225), super screen dari aplikasi (#226),
  Mode Klien unified host+client (#227; splash exe sudah ada sejak awal),
  macOS host+klien native Swift (#228/229), HUD sesi pill gaya pusat kendali.
  Menunggu izin build sekali: verifikasi lokal (cargo check + cargo fmt
  --check, gradlew :app:assembleDebug, npm run build) lalu workflow_dispatch.
  Web polish visual menyusul setelah build (butuh mata: preview + snapshot).
- [ ] **Konfirmasi tampilan baru APK (Tugas 27 lanjutan)** — pemilik minta
  2026-11-04: geser = gerak mouse/pointer (sudah dianti-lag lewat antrean +
  gabung gerakan), rapikan layout Home/detail PC/profil/settings/S&K/tentang,
  perbaiki input ID, riwayat perangkat/partner, login berilustrasi + gradasi.
  Semua sudah digabung; verifikasi visual di HP asli (Redmi A2 Go 32-bit)
  menyusul dari pemilik. Sisa polesan halus bila ada laporan: kartu detail
  sesi, sheet atur-host, onboarding.
- [ ] **Konfirmasi lapangan: sesi cloud PC tidak lagi mati mendadak** — bug
  2026-11-02 (HP → host cloud PC via RDP): "masuk sesi, hitam sebentar,
  menyambung ulang, host seperti offline" berulang. Akar: penjaga 2 detik
  mengakhiri sesi saat `me != act` polosan (deteksi `u32::MAX`/kedip RDP
  dianggap "beda"). Diperbaiki dengan `leadership::MismatchWatch` (unknown ≠
  mismatch; 3x berturut sebelum melepas leader). Menunggu uji di cloud PC
  pemilik dengan host hasil build sesudahnya.
- [ ] **Bukti lapangan Mode Tanpa P2P** — sesi HP ke host cloud VM berfirewall
  UDP ketat, dengan dan tanpa toggle; alasan `/turn-ice` harus terbaca di UI.
  Uji juga re-offer pemulihan saat sisi host masih `Connected`: host hanya
  mengambil alih sesi `Disconnected/Failed/Closed`, jadi kasus itu bisa
  berakhir `host-sibuk` dan butuh jalur cadangan bila terbukti.
  Kontras lapangan 2026-10-31: ThinkMay lolos, Limore Cloud
  (limorecloud.com) gagal di ICE dengan build yang sama — kemungkinan besar
  blok UDP provider. Mode Tanpa P2P (relay) adalah jalurnya; untuk host yang
  UDP keluarnya diblokir total, daftar TURN juga perlu varian transport
  TCP/TLS (`turn:...?transport=tcp` / `turns:`) di `TURN_DIRECT_URLS` /
  `TURN_STATIC_URLS`, bukan hanya URL UDP.
  Progres 2026-11-02: varian `?transport=tcp` kini diturunkan OTOMATIS oleh
  `withTransportVariants` di `cloudflare/src/turn.js` untuk provider
  `statis`/`direct` — URL UDP polos apa pun ikut menerbitkan kandidat TCP
  tanpa menyentuh secret. Bukti jalur TCP: probe STUN Allocate sungguhan ke
  free.expressturn.com:3478 dijawab server (0x0113). Varian `turns:` sempat
  diturunkan lalu DICABUT setelah terbukti TLS 5349 ExpressTurn tidak
  melayani TURN (jabat tangan sukses, tanpa balasan STUN, koneksi di-reset) —
  server ber-TURNS harus menulis `turns:` eksplisit di secret. Gerbang
  `check_turn_auth.py` kini menguji `?transport=tcp` sebagai TURN-over-TCP
  sungguhan (framing raw/RFC 4571), bukan salah potong jadi UDP. Sisa:
  bukti lapangan sesi ke host ber-UDP-ketat (Limore-type) masih menunggu
  uji perangkat.

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
  *Progres 2026-11-02: bahan artikel siap di
  `docs/articles/BAHAN_ARTIKEL_KEYSTORE-UNINSTALL.md` — menunggu digabung ke
  artikel rilis berikutnya; masih perlu satu kalimat alasan rotasi dari
  pemilik (slot sudah disediakan di bahan). Verifikasi sertifikat APK pertama
  kunci baru terpenuhi lewat forensik: sidik `8429f689e7…` sama di semua rilis
  publik v2.0.0+.*
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
- [ ] **Kuota Workers Cloudflare gratisan sering habis** — worker
  `xydesk-remote-web` (repo `xykal/XyDesk-Remote`) rata ±125 ribu request/hari
  vs batas 100 ribu/hari → Error 1027 menimpa SEMUA worker satu akun, termasuk
  `xydesk-signaling`. Biang utama (data PR #24): polling signaling PC-stream
  `GET /api/pc/rooms/:id/signal` tiap 1 detik (86.400/hari per sesi) + klien
  chat lama yang masih menembak jalur yang sudah 410 (chat pindah Firebase
  2026-10-24). Sudah dikerjakan 2026-10-31: PR #24 XyDesk-Remote di-merge
  (interval polling 1s→3s untuk app baru, counter unduhan KV→D1) + aturan
  rate-limit edge zone xydeskremote.biz.id (jalur chat 410 + POST
  /api/active-sessions diblokir >1 req/10 dtk per IP). Sisa terbuka:
  (a) klien app lama tetap polling 1 detik sampai update (perlu rilis app
  1.1.30 dengan interval baru); (b) ANOMALI: main menghapus kelas/binding
  `PcSignalRoom` (commit 201dcc4, "unused") tapi `PcSignalClient.kt` masih
  memanggil `/api/pc/rooms` — konfirmasi ke kall: sengaja atau perlu pulih;
  Diperiksa 2026-11-02: `PcSignalClient.kt` sudah TIDAK ADA di repo (ikut
  terhapus saat purge PC Stream, keputusan RDP-only). Pemanggil tersisa
  tinggal fosil `api/index.mjs` (repo XyDesk-Remote) yang mengimpor
  `PcSignalRoom` — bukan jalur deploy (wrangler memakai
  `cloudflare/worker.mjs` langsung) — plus klien app 1.1.x lawas yang masih
  polling. Menunggu putusan kall: hapus fosil + biarkan lama 410, atau
  pulihkan rute untuk klien lawas.
  DIPUTUSKAN kall 2026-11-02 ("bebas"): bersihkan. Fosil Vercel
  (`api/index.mjs` + `vercel.json`, impor rusak + pola rewrite boros
  tercatat di docs/CLOUDFLARE-1027.md) dihapus di XyDesk-Remote PR #38.
  Klien 1.1.x lawas dibiarkan menerima 404 murah dari worker — bila
  polling lawas masih menyedot kuota, solusinya rilis app 1.1.30 (item
  (a)), bukan memulihkan rute. Anomali ini TERTUTUP.
  (c) kalau masih tembus, opsi Workers Paid $5/bulan atau long-poll server.
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
- [ ] Verifikasi ulang `tool/audit_assets.py` setelah integrasi; tema terang
  saja; `hero_login.webp` tidak diganti.

## Antrean 2026-11-06 (analisis ulang + web/APK)

- [x] **SEO www dibuka** — `robots.txt` `Disallow: /` + `meta noindex` dihapus;
  halaman bot per rute + JSON-LD "XyDesk Remote". `remote.xydesk.my.id` tetap
  noindex. Sisa: pemilik mendaftarkan sitemap di Google Search Console supaya
  hasil pencarian "XyDesk Remote" benar-benar muncul.
- [ ] **Popup "cracked" pada unduhan resmi** — TIDAK ditemukan teks/deteksi
  "crack" apa pun di kode XyDesk (Android/web/host/worker sudah digrep
  menyeluruh). Menunggu info pemilik: tangkapan layar popup, teks persisnya,
  muncul di HP/PC/situs, dan sumber unduhan yang dipakai (web/APKPure/GitHub).
  Dugaan sementara: peringatan luar (Play Protect/MIUI/AV) atau versi
  repack pihak ketiga — jangan tambah deteksi sebelum penyebabnya jelas.
- [ ] **UI aplikasi belum responsif di semua layar** — pemilik melaporkan
  2026-11-06. Audit menemukan ~70 pemakaian dimensi dp tetap di Compose
  (8 di antaranya lebar ≥300dp rawan layar sempit). Perlu pass responsif
  (fillMaxWidth + batas maksimum, bukan lebar mati) di Android + web.
- [ ] **Percantik web** — permintaan pemilik 2026-11-06; menyusul setelah
  pass responsif (butuh preview + mata).
- [ ] **RDP via web & APK dengan IP aman** — permintaan pemilik 2026-11-06
  ("penting"). Menunggu keputusan arsitektur (gateway server vs klien
  RDP native di APK vs jalur WebRTC yang ada) sebelum diimplementasikan.
