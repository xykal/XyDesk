# HANDOFF — Antrian Kerja Lintas Role

Hanya item yang **masih terbuka**. Item selesai dan log pengiriman per sesi
ada di `../archive/HANDOFF-2026-09.md` (beku, jangan ditambah). Kalau menutup
item: hapus dari sini, tulis satu baris di `CHANGELOG.md` `[Belum terbit]`.
Status real-time siapa mengunci area apa: `AGENT_BOARD.md`.

Only open items live here. Completed items and per-session delivery logs are
frozen in `../archive/HANDOFF-2026-09.md`. Closing an item = delete it here and
add one line to `CHANGELOG.md`.

## Untuk: Client Flutter

- [ ] (Operator - XyDesk Team, 2026-09-25, sesi MATANGKAN) — **Temuan lintas
  role dari perbaikan host: error sebelum `welcome` harus memicu coba ulang,
  bukan menunggu selamanya.** Hub menolak `hello` dengan `id sudah online`
  bila soket lama ber-id sama belum ditutup server (zombie pasca putus
  jaringan; jendelanya ~30-60 detik sampai heartbeat hibernasi menutupnya).
  Host sudah diperbaiki (`host/src/main.rs`: `error` sebelum welcome → putus
  → sambung ulang; uji kontrak hub di `cloudflare/test/hub.test.js`). Web aman
  (id `web-<uuid>` selalu baru per sesi + jalur `fail()` yang actionable).
  Yang belum diperiksa: apakah APK memakai `deviceId` yang bisa dipakai ulang
  lintas restart aplikasi — kalau ya, `SignalingClient` perlu perlakuan sama:
  error pra-welcome = pendaftaran gagal → tutup dan sambung ulang, bukan diam.


- [ ] (dari Operator - XyDesk Team, 2026-09-06) — **Bukti sejati perbaikan boot
  tetap butuh perangkat nyata.** CI membuktikan kode itu diformat, dikompilasi,
  dianalisis, dan diuji — TIDAK membuktikan aplikasi terbuka di HP. Jalur yang
  gagal (`setMethodCallHandler` sebelum binding siap) adalah galat runtime yang
  tidak bisa dilihat `flutter analyze` maupun `flutter test`; simetri itu berlaku
  juga untuk pembuktiannya, jadi yang bisa memastikan hanya memasang APK dari run
  Build `34044474570` di HP yang sebelumnya terjebak di splash lalu melihat
  apakah aplikasi masuk. Commit `e358ab4` sudah menulis catatan jujur yang sama
  ("bukti sejati tetap perangkat nyata") dan itu masih benar.
- [ ] (dari Cakra - XySpace Team, 2026-09-03) — **Aturan rilis baru:** versi & berita = keputusan operator; saat menutup sesi fitur, tulis bahan artikel kerjamu sendiri (dampak pengguna + screenshot asli, gaya `docs/NEWS_STYLE.md`) — role CI/Release menyatukan semua bahan jadi SATU artikel saat rilis.

- [ ] (dari Danu - XySpace Team, 2026-09-03) — **Screenshot layar sesi
  Android untuk artikel berita** (permintaan operator: artikel harus punya
  screenshot asli semua platform). Yang dibutuhkan: layar sesi dengan rail
  kontrol terlihat + panel pengaturan terbuka, diambil dari **build rilis
  yang berjalan di perangkat/emulator nyata dengan video host asli**
  (bukan mockup). Simpan ke `web/public/news/shots/` dengan nama
  `<versi>-android-sesi-*.jpg` — pola dan aturan ada di README folder itu.
  Web sudah mengisi bagiannya (`web-sesi-*.jpg`, 11 lembar, hasil E2E
  lokal dengan video host pola uji yang benar-benar ter-decode).
- [ ] (dari Danu - XySpace Team, 2026-09-03) — Rail sesi web sekarang
  punya tombol **"Ambil dari papan klip PC"** (`0x09 CLIPBOARD_REQ` →
  jawaban `0x08` lewat data channel). Kalau aplikasi Android belum
  memakai model tarik ini (host Rust sudah menjawab sejak lama), ini
  saatnya menyamakan — protokolnya sudah stabil.

- [ ] (dari Danu - XySpace Team, 2026-09-02) — **Rebrand**: logo X baru sudah
  masuk, token ungu `lib/core/tokens.dart` sudah diselaraskan dengan web
  (accent `#7C3AED`, deep `#5B21B6`, lavender `#A78BFA`) oleh Laras. Yang
  tersisa: verifikasi hasil rebrand (ikon launcher, splash, aset, warna)
  di **build Android nyata** — belum ada perangkat di sesi ini.
- [ ] (dari Laras - XySpace Team, 2026-09-03) — **Verifikasi di perangkat
  nyata**: avatar & nama komentator (DiceBear `adventurer` SVG dari URL
  `api.dicebear.com`) dimuat melalui jaringan; pastikan `flutter_svg`
  merender SVG di Android tanpa placeholder permanen saat offline. Belum
  diuji di perangkat.
- [ ] (dari Laras - XySpace Team, 2026-09-03) — **Verifikasi ikon nav bawah &
  rail yang baru** (AI 3D ungu glossy, `assets/img/nav/*.png`, dua mode
  off=abu / on=warna): pastikan di build Android nyata ikon tampil tajam
  (tidak terpotong) di `NavigationBar` dan `NavigationRail`, dan
  transparansi latar bersih di atas tema terang. Belum diuji di perangkat.
- [ ] (dari Laras - XySpace Team, 2026-09-03) — **Verifikasi papan ketik
  "Sistem" (IME)** di perangkat nyata: ketik teks di sesi → host menerima
  sebagai 0x06 TEXT; perbaiki jika IME Android mengirim teks dengan urutan
  berbeda (delta di `_SystemKeyboard` hanya mengasumsikan menambah/menghapus
  di akhir). Belum diuji di perangkat.
- [ ] (dari Laras - XySpace Team, 2026-09-03) — **Verifikasi identitas komentar
  di perangkat nyata**: pengguna yang login memakai nama akun (bukan nama acak)
  dan tamu memakai nama deterministik; keduanya menampilkan avatar. Belum diuji.

## Untuk: Desktop Shell

- [ ] (Operator - XyDesk Team, 2026-09-23) — **Relay TURN host native: sesi gagal
  walaupun kredensialnya sah — sudah diperbaiki, belum diuji di Windows asli.**
  Rincian, bukti, dan batas jujurnya: `docs/qa/relay-turn-native-2026-09-23.md`.
  Yang perlu ditindak role lain:
  1. **SELESAI 2026-09-23 (Operator, keamanan):** kredensial ExpressTurn sudah
     dirotasi dan papan/HANDOFF diredaksi — nilai hanya ada di secret Worker.
     Catatan 2026-09-25 (sesi MATANGKAN): saat diagnosis gerbang CI, kredensial
     relay sempat tercetak di log sesi kerja (di luar repo) — rotasi berikut
     di ExpressTurn disarankan saat pemilik sempat, sekalian simpan `turn.txt`
     karena vault kerja tidak menyimpan salinannya.
  2. **Backend/Edge:** relay produksi masih **satu penyedia** (`direct`,
     ExpressTurn free). Langkah baru di `deploy-signaling.yml` ("Buktikan
     kredensial TURN bisa allocate") sengaja MENGGAGALKAN deploy bila relay
     tidak bisa allocate — siapkan cadangan (`OPENRELAY_API_KEY` atau
     `TURN_REST_URL` + `TURN_REST_API_KEY`) supaya tidak terjepit.
  3. **SELESAI 2026-09-25 (sesi SESI-20260925-OPERATOR-MATANGKAN):**
     `tool/check_turn_auth.py` kini mengallocate `turns:` (TLS di atas TCP)
     sungguhan — verifikasi sertifikat terhadap CA sistem (atau `--tls-ca`),
     `--tls-insecure` hanya untuk lab, dan `--self-test` menguji jalur TLS
     dengan server tiruan bersertifikat sendiri (butuh CLI `openssl`; dilewati
     dengan jujur bila tidak ada). Sekalian menutup cacat lama parser: URL
     TURN tanpa port eksplisit dulu dibaca "bukan TURN" dan dilewati diam-diam.
  4. **Docs & Audit:** kalimat "Wine bukan Windows" di dokumen panel tetap
     berlaku sampai hasil uji lapangan Windows masuk (item di atas).

- [ ] (dari Galih - XySpace Team, 2026-09-03) — Host Windows kini kembali
  memakai **shell Electron + Next.js** (`desktop/`) sebagai UI utama, dan
  installer mengemasnya (bukan lagi jendela native `host/src/bin/gui.rs`).
  `desktop/electron/main.cjs` sudah diberi **tray + always-on**: tutup
  jendela = sembunyi ke tray, engine tetap hidup; keluar lewat menu tray.
  Silakan verifikasi di Windows nyata (tray icon, balloon, `npm run build`
  + `npx electron-builder --win --dir`), dan lanjutkan pemolesan UI panel
  (bitrate, latensi, encoder, mic) yang endpoint-nya sudah ada.
- [ ] (dari Danu - XySpace Team, 2026-09-03) — **Screenshot shell desktop +
  host Windows untuk artikel berita** (permintaan operator: lengkapi artikel
  dengan screenshot asli semua platform): jendela host di Windows (ID+QR
  terlihat) dan shell desktop saat sesi berjalan. Simpan ke
  `web/public/news/shots/` dengan nama `<versi>-desktop-*.jpg` /
  `<versi>-host-*.jpg` (lihat README folder itu).

  **Catatan Galih:** di lingkungan agent TIDAK ada Windows, jadi screenshot
  yang bisa dibuat di sini adalah render Chromium (Linux) dari `desktop/out`
  — cocok untuk dokumentasi teknis, BUKAN pengganti "screenshot asli semua
  platform" untuk artikel. Yang perlu diambil di lab Windows nanti: jendela
  host dengan ID+password terlihat, shell saat sesi berjalan (chip perangkat
  "… (HP · Android)" di topbar), dan kartu Pengaturan baru. Simpan sebagai
  `<versi>-desktop-*.jpg` di `web/public/news/shots/`.
- [ ] (dari Galih - XySpace Team, 2026-09-03) — Host kini mengirim **dua**
  stream audio: `audio` (loopback suara sistem) dan `mic` (mikrofon PC host,
  hanya bila ada perangkat capture). Client yang menyajikan track audio
  dengan `mid`/`stream_id` "mic" akan otomatis menerima suara mic. Bila mau
  mute/volume mic terpisah, butuh aksi control API baru (belum ada —
  `audio-volume` saat ini hanya menyentuh perangkat output default).
- [ ] (dari Danu - XySpace Team, 2026-09-02) — **Rebrand**: ikon Windows
  (`packaging/windows/xydesk.ico`) sudah lahir ulang dari logo baru —
  verifikasi installer CI berikutnya memakai ikon itu. Selaraskan juga
  warna aksen shell dan avatar penulis resmi berita (foto founder, lihat
  catatan Client Flutter).

  **Catatan Galih (2026-09-03, sesi SESI-20260903-GALIH-HOST-UIUX):** bagian
  shell sudah diselaraskan — `desktop/public/logo.png` (merek sidebar, dulu SVG
  gambar tangan) dan `desktop/electron/tray.ico` (tray + taskbar + `build.win.icon`)
  kini jadi target `tool/gen_logo.py` dan `docs/BRAND_ASSETS.md` mencatat barisnya.
  `packaging/windows/xydesk.ico` memang sudah hasil generator, jadi installer CI
  berikutnya otomatis memakai ikon itu. YANG BELUM disentuh dan bukan
  keputusan teknis sepihak: (a) **warna aksen shell** — `--accent: #6142d6` di
  `desktop/app/globals.css` bukan tebak-tebakan, itu token bersama yang
  identik dengan palet terang `lib/core/tokens.dart` (sumber kebenaran desain),
  sedangkan ungu #7C3AED→#A78BFA di logo adalah warna ASET brand; menyamakan
  keduanya = memutuskan ulang token lintas platform, butuh keputusan desain,
  bukan commit shell; (b) avatar penulis resmi berita di halaman Berita shell.

- [ ] (dari Danu - XySpace Team, 2026-09-02) — Waktu komentar berita di web
  kini relatif ("5 menit lalu"); samakan di Flutter dan Desktop
  (`formatRelativeTime` di `web/src/news.ts` sebagai acuan). Avatar
  penulis resmi juga kini bulat penuh.
- [ ] (dari Danu - XySpace Team, 2026-09-02) — Form komentar berita: pindah
  ke BAWAH daftar komentar + auto-scroll saat "Balas" (web sudah; alasan:
  input di atas menyulitkan setelah membaca komentar).
- [ ] (dari Danu - XySpace Team, 2026-09-02) — Avatar + nama manusia
  komentar: ikuti pola web (`web/src/news.ts` — `newsDisplayName`,
  `newsAvatarUrl`). Flutter sudah; shell desktop menyusul.

## Untuk: CI / Release

- [ ] (Operator - XyDesk Team, 2026-09-25, sesi PULIHKAN) — **Keystore signing
  Android dirotasi — keystore lama hilang saat pindah akun GitHub.** Keystore
  baru dibuat atas keputusan operator (JKS, RSA 2048, alias `xydesk`, valid
  10000 hari); `KEYSTORE_BASE64`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD`
  sudah terpasang di GitHub Secrets, dan berkas `.keystore` + kata sandinya
  dipegang operator di brankas lokal (TIDAK di repo). Yang wajib diketahui
  sebelum rilis APK berikutnya: `MainActivity.verifyApk` membandingkan signer
  APK terpasang vs APK unduhan, jadi **APK rilisan lama tidak bisa
  update-in-place ke APK ber-signer baru** — pengguna lama harus install ulang
  (uninstall + pasang baru), dan ini layak disebut jujur di artikel rilis
  mendatang. Build APK pertama dengan keystore baru harus diverifikasi:
  `apksigner verify --print-certs` cocok dengan sertifikat keystore baru.

- [ ] (Operator - XyDesk Team, 2026-09-18) — **AUDIO-REPAIR belum dikemas/dideploy**. Source Linux156/web76 + local web build + Windows WASAPI ABI check PASS, bukan native Windows runtime proof. Tunggu pekerjaan input/16:9 sesuai instruksi pengguna implement/test sebelum packaging, lalu izin Build/NSIS/web rollout tersendiri. Existing37c5eea installer dan live740345c3 tidak mengandung patch ini. Pertahankan OAuth/bindings/version. `docs/qa/audio-repair-2026-09-18.md`.

- [ ] (dari Operator - XyDesk Team, 2026-09-06) — **`update.json` tidak
  disajikan dari domain mana pun.** Sesi ini sempat menyimpulkan salah bahwa ia
  404 di `signal.xydesk.my.id/update.json` dan `app.xydesk.my.id/update.json` —
  keduanya memang menjawab "not found", dan itu BUKAN bug: `release.yml` hanya
  mengunggahnya sebagai aset GitHub Release (`releases/download/v6.6.0/
  update.json`). Perlu dipastikan klien membaca dari URL yang benar; kalau ada
  klien yang mengharapkan ia di domain sendiri, itu akan gagal diam-diam dan
  pembaruan tidak pernah ditawarkan.
- [ ] (dari Operator - XyDesk Team, 2026-09-06) — **INFO: Dart/Flutter bisa
  diverifikasi di mesin Linux biasa, tanpa Android Studio.** Resepnya: unduh
  Flutter persis versi `env.FLUTTER_VERSION` di `build.yml` (saat ini 3.44.9
  → Dart 3.12.2) dari
  `https://storage.googleapis.com/flutter_infra_release/releases/stable/linux/flutter_linux_<versi>-stable.tar.xz`,
  ekstrak, lalu `flutter pub get` → `flutter analyze --fatal-infos
  --fatal-warnings` → `flutter test`. Empat gerbang yang paling sering
  membuat merah dan bisa dicek lokal sebelum dispatch: `git diff --exit-code
  -- pubspec.lock`, `dart format --output=none --set-exit-if-changed lib
  tool`, larangan `// ignore:` dan `exclude:` di `analysis_options.yaml`,
  serta `node tool/gen-licenses.mjs --check`. Butuh ruang disk ±5 GB (arsip
  1,5 GB + hasil ekstrak 2,5 GB) — jangan diekstrak di `/tmp` yang kecil.
  Alasan catatan ini: selama berhari-hari laporan sesi Client Flutter
  bertulis "DART tidak bisa diverifikasi di sini", padahal kodenya sejak
  3 Sep bahkan tidak bisa dikompilasi.


- [ ] (dari Galih - XySpace Team, 2026-09-03) — **Perubahan perilaku yang tidak
  boleh lewat tanpa diberitahu:** password pairing host kini PEKA-KASUS (lihat
  item Client Flutter & Web di file ini). Build APK/web/shell berikutnya berbeda
  di mata pengguna: HP lama + password campuran = pairing ditolak. Tolong
  masukkan ke bahan artikel rilis (aturan papan #3) beserta kalimat pemulihannya:
  buka XyDesk di PC → "Password acak baru" atau `xydesk-host --new-password`,
  lalu pairing ulang. Jangan ditulis sebagai "perbaikan keamanan" tanpa efek
  samping — efeknya ada dan menimpa pengguna yang tidak menyentuh apa pun.

- [ ] (dari Galih - XySpace Team, 2026-09-03) — **Permintaan dispatch Build**
  atas restu operator di chat (3 Sep 2026, "gas + minta dispatch build"): push
  `SESI-20260903-GALIH-HOST-AUDIT` (host engine: masa tenggang disconnect,
  penutupan peer connection, inject teks per batch) sudah di `main` dan
  `verify-push-auth` hijau. Mohon dijalankan saat sesi rilis lain sudah
  `SELESAI` — **nomor versi tetap keputusan operator**; saya tidak menyentuh
  `pubspec.yaml`/`package.json`/`Cargo.toml` (hanya `host/Cargo.lock` yang
  disinkronkan ke 6.4.0 karena tertinggal dari bump 4cbbc22). Bahan artikel
  untuk peranmu sudah ada di blok "Untuk: Host Engine" (tanpa screenshot —
  tidak ada perubahan visual di host).
- [ ] (dari Cakra - XySpace Team, 2026-09-03) — **Tugas rilis kini:** menyatukan bahan artikel dari tiap agent menjadi SATU artikel rilis (bukan mengarang semuanya); versi & terbitnya berita = keputusan operator; jangan build/rilis sebelum semua sesi area rilis `SELESAI`.

- [ ] (dari Cakra - XySpace Team, 2026-09-03) — **JANGAN terbitkan rilis
  ulang dengan build number yang sama.** 6.3.0 build 25 sempat terbit
  belum lengkap lalu ditarik; diterbitkan ulang dengan build 25 juga →
  perangkat yang sempat memasangnya menolak pembaruan selamanya ("Anda
  memakai versi terbaru") karena aplikasi membandingkan
  `manifest.build > installed`. Rilis ulang resmi kini **build 26**
  (tag `v6.3.0` sama, aset ditimpa, idempotency OneSignal menyertakan
  build). Build number = satu-satunya sumber kebenaran naik-rilis.
- [ ] (dari Cakra - XySpace Team, 2026-09-03) — **Screenshot artikel
  6.3.0 diambil dari stack lokal** (web dev + host pola uji): UI = commit
  rilis yang sama, tapi bukan build yang diunduh pengguna dan video =
  test pattern, bukan desktop asli. Sesuai NEWS_STYLE §3 idealnya dari
  build rilis di perangkat nyata — item Danu "screenshot Android" adalah
  langkah serupa untuk Android.

## Untuk: Backend / Edge

- [ ] (Operator - XyDesk Team, 2026-09-25, sesi MATANGKAN) — **Audit hub.js:
  inti sudah matang, dua catatan kecil.** (1) Kontrak pendaftaran kini dikunci
  uji (hello duplikat ditolak tanpa mengganggu pemegang id, hello pertama
  diterima, hello tanpa id ditolak — `cloudflare/test/hub.test.js` 21/21).
  (2) Defense-in-depth (belum dikerjakan, bukan lubang keamanan aktif): endpoint HTTP DO Hub
  (`/kick`, `/stats`, `/hub/devices`) hanya terlindungi karena tidak dirutekan
  `worker.js` dan hanya dipanggil `admin.js` ber-JWT; pertimbangkan header
  `x-internal-admin` seperti AuthStore supaya dua lapis, bukan satu.

- [ ] (Operator - XyDesk Team, 2026-09-25, sesi PULIHKAN) — **Pasca-migrasi
  akun: tiga secret signaling DIROTASI, triplet TURN sengaja tidak ditaruh di
  GitHub.** (1) `XYDESK_SECRET`, `ADMIN_SECRET`, `AUTH_SECRET` lama hilang
  bersama akun GitHub lama dan nilainya tidak bisa dibaca balik dari Worker —
  atas restu operator di chat, ketiganya dirotasi (64-hex baru; nilai lama
  hangus). Dampak yang sudah terjadi dan normal: token perangkat lama ditolak
  (client ambil token baru lewat `/signal-token`) dan sesi login JWT lama mati
  (login ulang). Nilai baru dipegang operator di brankas lokal, tidak di repo.
  (2) `TURN_DIRECT_URLS/USERNAME/CREDENTIAL` **tidak** diisi di GitHub Secrets
  (keputusan operator 25 Sep): kredensial ExpressTurn produksi tetap hidup di
  secret Worker hasil rotasi 23 Sep, dan `deploy-signaling.yml` memang
  merancang TURN kosong = sah (Worker tidak ditimpa). Konsekuensinya wajib
  diketahui role ini: **deploy CI berikutnya tidak akan memperbarui secret TURN
  Worker** — kalau suatu hari gerbang allocate merah, artinya kredensial
  ExpressTurn mati dan pemilik harus menyediakan penggantinya (vault kerja
  TIDAK menyimpan salinannya; simpan `turn.txt`!). Mengisi separuh triplet di
  GitHub tetap ditolak keras workflow — itu disengaja. (3) Provider cadangan
  (`OPENRELAY_API_KEY` / `TURN_REST_*`) masih kosong — item 23 Sep soal relay
  satu penyedia tetap terbuka.

- [ ] (dari Danu - XySpace Team, 2026-09-03) — **Billing sewa PC otomatis**:
  halaman `/billing` web sudah tayang (paket, durasi, total, pesan via WA;
  operator konfirmasi manual). Otomasi penuh butuh: (1) gateway pembayaran
  QRIS (Midtrans/Xendit — perlu keputusan operator + akun), (2) endpoint
  provisioning yang mengirim ID+password XyDesk + kode billing setelah
  webhook pembayaran, verifikasi penebusan = 4 digit akhir nomor WA pembeli.
  Catatan CyberIndo: TIDAK ada API publik/dokumentasi developer (sistem
  tertutup, terikat GCA) — integrasi langsung tidak mungkin tanpa
  reverse-engineering. Jalur realistis: (a) helper kecil di PC server warnet
  yang menerima perintah dari backend kita lalu membuat member/top-up, atau
  (b) lepas dari billing CyberIndo untuk sesi remote — host XyDesk sendiri
  yang membatasi durasi sesi.

## Untuk: News & Konten

- [ ] (dari Danu - XySpace Team, 2026-09-03) — **PENGINGAT MANDAT OPERATOR
  (Xyckal, chat 3 Sep 2026): artikel berita HARUS lengkap** — detail apa +
  kenapa, changelog versi pengguna, dan **screenshot setiap perubahan
  visual** dari build rilis (bukan cuma sampul/banner). Artikel tanpa
  screenshot perubahan = belum layak terbit. Ini penegakan ulang aturan
  yang sudah ada di `docs/NEWS_STYLE.md` + gerbang `check-news`; mohon
  jadikan checklist eksplisit sebelum publish berikutnya.
- [ ] (dari Sena - XySpace Team, 2026-09-02) — Setelah Flutter selesai
  merender gambar inline (terbit 3 Sep 2026 di rilis 6.3.0): artikel
  rilis berikutnya dipastikan memakai format baru `docs/NEWS_STYLE.md`
  (apa+kenapa, changelog pengguna, screenshot di `web/public/news/shots/`).
- [ ] (dari Danu - XySpace Team, 2026-09-03) — **Lengkapi artikel dengan
  screenshot semua platform** (mandat operator). Bagian web sudah tersedia:
  11 screenshot sesi web baru di `web/public/news/shots/web-sesi-*`
  (rail kontrol, rail disembunyikan, panel 4 tab, keyboard, gaming,
  trackpad, papan klip, mik — semuanya dari build rilis via E2E lokal,
  video host pola uji yang benar-benar ter-decode, bukan mockup).
  Menunggu: Android (Client Flutter), desktop/host Windows (Desktop
  Shell). Artikel `changelog-v6-3-0` juga masih menampilkan screenshot
  sesi web versi LAMA (`6.3.0-*.jpg`) yang tidak lagi match dengan UI
  live setelah sesi WEB3 — pertimbangkan pasang ulang bagian web artikel
  itu dengan `web-sesi-*` atau terbitkan artikel baru saat semua
  screenshot terkumpul. Penulis: `Haekal Saputra` (baru, lihat
  NEWS_STYLE yang sudah dikoreksi).

## Untuk: Host Engine

- [ ] (Operator - XyDesk Team, 2026-09-25, sesi MATANGKAN) — **Host tidak lagi
  macet saat hello ditolak hub.** `error` sebelum `welcome` (kasus nyata:
  `id sudah online` setelah putus jaringan singkat) kini memutus koneksi dan
  membiarkan loop luar menyambung ulang (jeda 2 dtk); sebelumnya host menunggu
  welcome yang tak pernah datang di soket yang tidak diakui hub. Bukti:
  `cargo fmt --check` + `clippy -D warnings` + 189 uji hijau di lingkungan
  sesi (Linux); kontrak hub-nya dikunci 3 uji baru `cloudflare/test/hub.test.js`.
  Batas jujur: jalur nyata (zombie socket di produksi) belum bisa direproduksi
  di Linux — verifikasi lapangan berikutnya: cabut jaringan PC host ~10 detik,
  sambungkan lagi, host harus terdaftar ulang sendiri dalam ~1 menit tanpa
  restart manual.


- [ ] (Operator - XyDesk Team, 2026-09-19) — **VDD3010 FIELD REGRESSION**: pengguna membuktikan catalog/hash PASS, stagingoem13.inf, install3010, lalu skrip691dd09 keliru menghapus ROOT\MTTVDD\0000. Probe setelah removal hanya membuktikan tiada monitor saat itu, bukan penolakan RDP terhadap monitor yang aktif. Hotfix preserves accepted device, pending reboot state + verified resume. Native driver install/720p acceptance masih perlu pembuktian di host pengguna; jangan sarankan reboot hosted runner tanpa recovery plan.

- [ ] (Operator - XyDesk Team, 2026-09-18) — **VDISPLAY720**: user meminta virtual source1280×720 setelah audio56317a2 berhasil tetapi RDP tetap2336×1080. Implementasi profil ketat, adapter identity, same-session visibility, no wrong-screen fallback, explicit signed/pinned provisioning. Gate Windows35408521780 dan NSIS35408977237 sukses; source691dd09. Driver activation/virtual capture/user RDP tetap belum diuji. Batas pokok: virtual console monitor tidak otomatis menjadi monitor RDP. Jangan klaim driver install melewati batas sesi/lock. Host lama tetap entrypoint biasa; shortcut Virtual720 terpisah.

- [ ] (Operator - XyDesk Team, 2026-09-18) — **Status terbaru setelah HOST-FINISH**: auto16:9 supported-mode/test/readback sudah diimplementasikan, audio/input sudah dikemas dan web live. Windows153 termasuk real Opus PASS. Bukan bukti userRDP: tetap perlu suara nyata/recording endpoint/Windows mode acceptance/physical controls/latency lapangan. Forward-audio idle cancellation/backpressure, COM balancing dan hotplug tetap follow-up; jangan mengklaim sudah diimplementasikan. Tidak perlu build ulang source yang sama kecuali ada perubahan. Bukti `docs/qa/host-finish-2026-09-18.md`.

- [ ] (Operator - XyDesk Team, 2026-09-18) — **INPUT-LATENCY**: overflow yang menghentikan input sudah diperbaiki; bounded async backpressure + adjacent absolute coalescing, Linux161 PASS. Masih perlu native Windows/physical responsiveness, RTT/encode/decode measurement, otomatis16:9 supported/applied verification, audio field acceptance. Jangan klaim zero-lag atau seluruh keluhan selesai. Bukti `docs/qa/input-queue-2026-09-18.md`; belum packaging/deploy.

- [ ] (Operator - XyDesk Team, 2026-09-18) — **AUDIO-REPAIR follow-up**: source WASAPI/packetizer/render/late mic diperbaiki, Linux156 + ABI Windows checker PASS, belum physical Windows/RDP. Lanjut host input overflow/backlog dan pengukuran encode/decode/network, source auto16:9 supported-mode + verified applied state, forward-audio freshness/idle-cancel, COM balancing/hotplug. Bukan janji zero-lag. `docs/qa/audio-repair-2026-09-18.md` memuat acceptance gate dan keterbatasan. Jangan ubah driver/RDP/scaling/restart diam-diam.

- [ ] (dari Galih - XySpace Team, 2026-09-03) — **Permintaan "siapkan driver
  mic/audio/display/GPU" dijawab: tidak ada driver yang perlu/pantas dikirim.**
  Host sudah lewat jalur tercepat user-mode: DXGI Desktop Duplication (capture),
  NVENC D3D11+NV12 (`nvenc.rs`, fallback openh264), WASAPI loopback (audio PC →
  HP), track WebRTC (mic HP → PC), `SendInput` (keyboard/mouse). Driver kernel
  hanya perlu untuk HAL lain dan semuanya butuh EV code-signing + INF + test
  signing, yang bertentangan dengan aturan ROADMAP "semua gratis, tanpa kartu
  kredit": HidHide/ViGEmBus (menyembunyikan perangkat fisik), IddCx (display
  virtual/headless). Tiga Upgrade nyata yang masih murni user-mode dan belum
  dikerjakan — kerjakan kalau mau "gacor" beneran: (1) zero-copy penuh
  DXGI→NVENC lewat shared NT handle (buang salin BGRA→NV12 di CPU), (2) pilih
  adapter/L0 (`DXGI_SWAP_CHAIN_FLAG`/`CreateDXGIFactory1` + prefer GPU diskrit)
  sebelum encoder dibuat, (3) capture audio PER APLIKASI
  (`AUDIOCLIENT_ACTIVATION_PARAMS` + `PROCESS_LOOPBACK_MODE_TARGET`, Win10 2004+)
  supaya hanya suara app target yang ikut ke HP. Uji semuanya di lab Windows
  (`host/TEST-LAB-WINDOWS.md`) — runner CI tidak punya GPU.
- [ ] (dari Galih - XySpace Team, 2026-09-03) — **Keyboard & mouse FISIK di PC
  host sudah aman dan memang begitu desainnya**: `SendInput` MENAMBAH event ke
  antrean input sistem, tidak mengambil alih perangkat, jadi orang di depan PC
  tetap bisa mengetik/gerakin mouse selama sesi (input campur — bukan bug,
  belum ada permintaan mode eksklusif). Yang BELUM ada dan butuh keputusan
  produk sebelum dikoding: "kunci PC lokal selama sesi". Dua bentuk murah:
  (a) `LockWorkStation()` (user32, satu panggilan, tanpa driver) dipicu
  tombol/aksi `POST /action {action:"lock-now"}` — praktis, tapi local user
  harus login ulang; (b) `WH_KEYBOARD_LL`/`WH_MOUSE_LL` yang menelan event lokal
  dengan opsi "lepas" + watchdog — terasa lebih rapi TAPI tidak bisa diuji dari
  CI Linux dan hook salah tulis = pengguna kehilangan papan ketiknya sendiri.
  Jangan pasang (b) tanpa lab Windows + tombol darurat di UI.

- [ ] (dari Galih - XySpace Team, 2026-09-03) — **PENTING untuk verifikasi lab
  Windows**: host dulu "hidup-mati-hidup-mati" karena server signaling
  menendang koneksi yang tidak balas ping dalam 90 dtk. Sudah diperbaiki
  (engine balas ping + sambung-ulang dalam proses). Saat menguji di Windows
  nyata, pastikan host dibiarkan idle > 5 menit tanpa sesi dan tetap
  "siap" (tidak restart). Bila masih restart, lihat log shell (`[engine]`,
  `[shell] engine keluar (kode ...)`) — itu kunci diagnosis berikutnya.
- [ ] (dari Galih - XySpace Team, 2026-09-03) — **Verifikasi lab Windows
  untuk hardening stabilitas**: (1) tutup sesi client, lalu cek Task Manager
  — penggunaan GPU/CPU harus turun (capture DXGI berhenti; sebelumnya
  capture+encode terus jalan tanpa penonton); (2) lepas/tukar monitor atau
  picu UAC saat sesi — gambar harus pulih otomatis (capture di-retry), bukan
  membeku; (3) putuskan koneksi ke signaling (matikan Wi-Fi) lalu nyalakan
  lagi — engine menyambung ulang dalam proses; (4) biarkan server signaling
  tidak responsif (mis. blokir DNS) — supervisor harus mencoba ulang token
  dengan timeout, tidak menggantung di `engineStarting`.
- [ ] (dari Cakra - XySpace Team, 2026-09-03) — Push `f12dace` (bitrate
  live via control API) membuat run `verify-push-auth.yml` MERAH: commit
  tidak memuat penanda `Izin: <ID-SESI>` di body. Aturan baru: klaim
  sesi → persetujuan operator di `AGENT_BOARD.md` → push dengan penanda
  `Izin: ...` di body commit.

- [ ] (dari Galih - XySpace Team, 2026-09-03) — **Verifikasi lab Windows untuk
  SESI-20260903-GALIH-HOST-AUDIT** (semuanya lolos di unit test Linux, tapi
  perilaku nyata hanya terbukti di Windows). **Langkah uji siap jalan ditulis di
  `host/TEST-LAB-WINDOWS.md`** (resep token + control API, 5 blok pengujian,
  apa yang harus terlihat di log dan di `/status`): (1) saat sesi aktif, putuskan Wi-Fi
  ±5 detik lalu nyalakan — sesi HARUS lanjut tanpa pairing ulang dan log
  menampilkan "pulih sendiri — sesi lanjut"; (2) putuskan > 15 detik — slot
  dilepas DAN capture berhenti (cek Task Manager: GPU/CPU turun); (3) tempel 3–5
  baris + emoji dari HP ke Notepad — semua karakter masuk, tanpa karakter rusak;
  (4) reload client di tengah sesi (renegosiasi) — sesi lama harus ditutup,
  tidak ada dua engine capture berjalan.
- [ ] (dari Galih - XySpace Team, 2026-09-03) — **Butuh keputusan operator, jadi
  tidak kukerjakan:** kalau WebSocket signaling putus-nyambung, sesi media yang
  sedang berjalan TIDAK ditutup (kode menuliskan "Sesi media mati bersama
  koneksi signaling", padahal Arc-nya masih dipegang task video/input). Sisi
  baiknya: video kebal terhadap deploy signaling. Sisi buruknya: host tidak lagi
  mengenalinya sebagai sesi aktif, jadi `bye` dari client tidak menutupnya —
  hanya control API `stop-session` yang bisa. Pilih: biarkan (dan perbaiki
  komentar) atau tutup setelah masa tenggang.
- [ ] (dari Galih - XySpace Team, 2026-09-03) — Task input (arm "offer" di
  `main.rs`) keluar saat `rx.recv()` berakhir, tapi menutup data channel dari
  lawan TIDAK menutup channel mpsc di sisinya — thread injeksi
  (`std::thread::spawn`) bisa bertahan satu per sesi. Belum diukur (butuh sesi
  Windows nyata). Kandidat perbaikan: `dc.on_close` → drop `inj_tx`.
- [ ] (dari Galih - XySpace Team, 2026-09-03) — **Bahan artikel** (aturan papan
  #3; untuk disatukan CI/Release saat rilis berikutnya): "Sesi XyDesk tidak lagi
  putus karena jaringan sebentar. Dulu koneksi yang terlepas dua detik dianggap
  mati total — perangkat harus memasukkan password ulang, dan proses di PC
  kadang terus merekam layar tanpa penonton. Sekarang host memberi masa
  tenggang 15 detik untuk pulih sendiri, dan kalau memang tidak pulih,
  peralatannya dibebaskan dengan benar. Ketikan panjang dari HP juga tidak lagi
  hilang sebagian di tengah jalan." **Tanpa screenshot** — tidak ada perubahan
  visual di sisi host, dan screenshot sesi Windows masih jadi utang Desktop Shell.

## Untuk: Docs & Audit

- [ ] (dari Operator - XyDesk Team, 2026-09-06, diperbarui 2026-09-09) — **Sisa dari `docs/DESIGN.md`: navigasi
  tidak sama jumlahnya** (HP 4 item dengan "Akun", desktop 5 item dengan "Profil"+"Pengaturan" terpisah, web 2).
  Dua keputusan lain SUDAH dieksekusi 2026-09-09 atas suara operator (web = acuan): (1) radius disatukan ke
  8/12/16/20 di semua platform (kartu = 16); (2) garis pemisah dihapus di desktop + web (~120 situs web,
  ~60 desktop) — kartu→bayangan, baris→ubin overlay, tabel→zebra/ubin, chip→isi lembut. Tercatat di
  `docs/DESIGN.md` ("Garis yang disengaja" + "Yang sudah paritas" #4).
- [ ] (dari Operator - XyDesk Team, 2026-09-06) — **Laporan audit lengkap ada di
  `../archive/AUDIT-2026-09-06.md`** (paritas UI/UX tiga platform, diagnosis stuck,
  dan keputusan C++ dengan bukti). Kesimpulan bagian C: **jangan menambah C++.**
  C sudah ada dan jalan (`host/build.rs` mengompilasi libopus 1.5.2 dari
  `host/vendor/opus/` lewat crate `cc`); NVENC sudah selesai di Rust murni via
  FFI dinamis; satu-satunya kebutuhan C++ nyata (driver IddCx) sudah diputuskan
  untuk tidak ditulis karena butuh EV code-signing berbayar — installer memakai
  `ge9/IddSampleDriver` (MIT+CC0). Yang kurang bukan bahasa baru melainkan
  angka latency glass-to-glass yang belum pernah diukur.
- [ ] (dari Galih - XySpace Team, 2026-09-03) — **Catat batas gerbang host di
  `docs/CI.md`.** `host-test` menjalankan `cargo fmt/clippy/test` di ubuntu, jadi
  SEMUA kode di balik `cfg(target_os = "windows")` (DXGI, WASAPI, SendInput,
  papan klip) tidak pernah di-lint sebelum rilis. Buktinya nyata: `main` hari ini
  MERAH pada `tool/check-host-windows.sh --clippy` karena dua lint Windows-only —
  `screen.rs` (doc comment menggantung, `empty_line_after_doc_comments`) dan
  `gui.rs` (manual `Iterator::find`) — keduanya lolos CI. Sudah kuperbaiki, tapi
  strukturnya tetap: usulkan step `clippy --target x86_64-pc-windows-gnu` di
  `host-test` (mingw-w64 tersedia di runner ubuntu) ATAU tulis terang-terangan
  bahwa "CI host hijau" tidak berarti kode Windows bersih.
