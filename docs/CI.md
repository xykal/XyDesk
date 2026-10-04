# CI/CD — XyDesk

Build dan rilis dijalankan melalui GitHub Actions agar perangkat pengguna tidak
perlu menyiapkan Android SDK, Rust MSVC, WiX/NSIS, atau Cloudflare Wrangler
secara lokal. Semua workflow produksi sekarang manual dan dijaga actor guard.

## Workflow

Semua workflow aktif hanya berjalan lewat `workflow_dispatch`. Tidak ada pemicu
`push`, `pull_request`, `workflow_run`, atau jadwal.

| Berkas | Pemicu | Hasil |
|---|---|---|
| `.github/workflows/build.yml` | manual | Build penuh: APK Android per ABI, host Rust, panel Win32, web, Worker checks, installer lint, meta/version check |
| `.github/workflows/release.yml` | manual via `release_sha` | GitHub Release: APK, EXE, MSI, `update.json`, checksum, push OneSignal |
| `.github/workflows/deploy-web.yml` | manual | deploy bundle web dari run Build yang dipilih ke Cloudflare |
| `.github/workflows/deploy-signaling.yml` | manual | deploy Cloudflare Worker API/signaling |
| `.github/workflows/deploy-news.yml` | manual | deploy Worker berita + migrasi D1 |
| `.github/workflows/android-native.yml` | manual | build APK native cepat |
| `.github/workflows/host-check.yml` | manual | format/clippy host Linux + Windows check |
| `.github/workflows/web-shots.yml` | manual | bukti screenshot layout web |
| `.github/workflows/android-keystore.yml` | manual | buat/rotasi keystore Android |
| `.github/workflows/cleanup.yml` | manual | bersihkan run/artifact/cache lama |

Setiap job memiliki langkah awal `Validasi operator workflow`. Jika `GITHUB_ACTOR`
bukan `xykal`, job berhenti sebelum checkout/build/deploy. Artinya Actions
praktis hanya bisa dijalankan pemilik repo atau token pemilik repo.

## Pengujian web + host tanpa runner interaktif

Fokus saat ini adalah web dan host; integrasi platform lain ditunda. Pengujian
Windows/RDP/audio/input dijalankan pemilik di mesin sendiri dengan
[`WEB-HOST-MANUAL-QA.md`](WEB-HOST-MANUAL-QA.md). Build/kompilasi tetap melalui
Actions setelah izin; unit test, gerbang build, dan workflow packaging manual
tidak dihapus.

Menghapus file workflow tidak membatalkan run yang sudah berjalan atau
mencabut secret/node Tailscale. Periksa dan batalkan run lama bila masih aktif;
review kredensial yang khusus lab secara terpisah. Penghapusan baru berlaku
di branch remote setelah perubahan dipush.

**Perhatian:** Build sukses tidak lagi menyalakan deploy atau release otomatis.
Setelah Build penuh hijau, operator masih harus menjalankan `deploy-web.yml`
atau `release.yml` secara manual dengan SHA yang disengaja.

## Kebijakan pemicu (sejak 3 Sep 2026): push TIDAK memicu actions

Push ke `main` **tidak memicu actions apa pun**. Dulu ada satu
pengecualian — gerbang audit `verify-push-auth.yml` — tetapi workflow itu
dihapus operator pada 5 Sep 2026 (commit `b4ce4a4`), jadi sekarang betul-betul
nihil: tidak ada workflow yang berjalan karena push.
Semua jalur — build penuh, APK native cepat, host check, screenshot web,
deploy web/news/signaling, cleanup, keystore, dan release — hanya lewat
`workflow_dispatch`. Alasan: build otomatis dari push/PR perantara membuat
"hijau palsu", run terbuang, dan update produksi berulang. Hasil yang
dianggap bukti hanya run manual yang memang diminta operator.

### Deploy cepat

Tidak ada workflow deploy otomatis. Deploy cepat masih boleh atas restu operator,
tetapi harus dijalankan manual oleh pemilik/token pemilik dan tetap dicatat di
papan + laporan sesi. Build/rilis penuh tetap tidak boleh digabung diam-diam
dengan PR fitur.

### Sebelum build/rilis: cek papan, lalu izin operator (sejak 3 Sep 2026)

Aturan operator — bukan saran, bukan kebiasaan:

1. **Versi & berita = keputusan operator.** Role build/rilis TIDAK
   menetapkan nomor versi, tidak memilih isi berita, dan tidak menaikkan
   `VERSION` atas inisiatif sendiri. Semua lewat arahan operator.
2. **Cek kerjaan agent lain dulu.** Sebelum mengajukan build penuh/rilis:
   baca `project/AGENT_BOARD.md` (sesi aktif) + `project/HANDOFF.md` dan pastikan sesi
   yang menyentuh area rilis (client, host, desktop, web) sudah `SELESAI`.
   Kalau masih ada yang berjalan: TAHAN, laporkan ke operator — jangan
   memaksakan rilis.
3. **Push wajib izin operator.** Termasuk bump versi dan push yang
   menyentuh `VERSION`/`release.yml`/`build.yml` — antre di
   `project/AGENT_BOARD.md`, tunggu `DISETUJUI`, baru push. Push sendiri tidak
   menjalankan build apa pun — hanya gerbang audit izin.
4. **Satu gerakan saat siap.** Rilis penuh dikerjakan SEKALIGUS ketika
   operator menyatakan siap: bump → Build → Release → deploy → berita
   (artikel = bahan yang disatukan dari tiap agent).

Untuk memicu Build penuh:

```bash
gh workflow run build.yml --ref main
```

**Catatan anti-race:** `deploy-web.yml` tetap mempercayai API dan artefak run
Build yang dipilih, bukan asumsi dari branch. Karena workflow-nya manual, SHA
yang dideploy harus eksplisit dan bisa diaudit.

## Filter area di Build (sejak 3 Sep 2026)

`build.yml` manual masih memakai `dorny/paths-filter` agar ringkasan area jelas.
Pada `workflow_dispatch`, Build penuh menjalankan seluruh rantai; peta filter
ini tetap menjadi dokumentasi area yang dijaga:

| Perubahan | Job yang jalan |
|---|---|
| `android-native/**`, `VERSION` | `android` |
| `host/**`, `packaging/native-host/**`, `packaging/tests/**`, `VERSION` | `host-test` → `windows` |
| `web/**` | `web` |
| `news/**` | `check-news` |
| `cloudflare/**`, `signaling/**` | `check-signaling` |
| `packaging/**` | `installer-lint` |
| `docs/`, `AGENT.md`, `project/AGENT_BOARD.md`, `project/HANDOFF.md`, `CHANGELOG.md`, `project/CONTRIBUTORS.md`, `README.md`, `project/ROADMAP.md`, `project/SETUP.md`, `.github/workflows/**`, manifest versi | `check-meta` (konsistensi versi) |
| `workflow_dispatch` | **semua** job (build penuh untuk pemulihan) |

Konsekuensi yang dijaga:

- **Rilis tetap utuh.** `VERSION` masuk filter Android dan host, sehingga bump
  versi membangun ulang rantai client + host — `release.yml` selalu menemukan
  artefak `XyDesk-Android-APK` dan `XyDesk-Windows-<arch>`.
- **PR tidak otomatis membakar Actions.** PR dipakai untuk review. Validasi
  Actions dijalankan manual saat operator/token pemilik memang memintanya.
- **Deploy tidak ikut salah jalan.** `deploy-web.yml` memeriksa keberadaan
  artefak `XyDesk-Web` pada run Build yang dipilih; kalau tidak ada, deploy
  berhenti dengan pesan jelas.
- **Job `skipped` = bukan areamu, bukan kegagalan.** Baca tabel *Ringkasan*
  pada run untuk melihat area yang terdeteksi.
- **Release hanya mau Build penuh.** `release.yml` memeriksa artefak
  `XyDesk-Android-APK` + `XyDesk-Windows-x64/arm64` pada run Build yang
  dipilih; kalau tidak ada (run terfilter), rilis dilewati dengan
  peringatan, bukan merah. Build penuh lewat `workflow_dispatch` berjalan
  di jalur `manual` (tidak dibatalkan push biasa).
- **Branch protection**: kalau ada, required check sebaiknya tidak memaksa
  workflow otomatis. PR dipakai untuk review; Build manual dipakai sebagai
  bukti sebelum merge/rilis.

Gerbang utama yang tetap dijaga: test Worker berita, test Worker signaling,
`gofmt`/`go vet`/`go test` signaling Go, `tool/check_version.py`, Gradle APK
native, host Rust, web build/test, dan lint installer.

## Verifikasi izin push — **sedang NONAKTIF**

> **Status 6 Sep 2026: workflow `verify-push-auth.yml` dihapus operator
> sendiri** (commit `b4ce4a4`, 5 Sep 2026) dan branch `main` tidak
> memakai branch protection. Jadi saat ini **tidak ada satu pun workflow
> yang berjalan karena push**, dan tidak ada pemeriksaan yang menolak
> push ke `main`. Bagian di bawah ini disimpan sebagai resep bila gerbang
> ingin dihidupkan kembali.

Aturan yang tetap berlaku sebagai kebiasaan tim (bukan sebagai gerbang
mesin): setiap commit non-merge pada push ke `main` WAJIB memuat penanda
`Izin: <ID-SESI>` di body, dan ID-nya harus punya baris di
`project/AGENT_BOARD.md`. Pengecualian: commit merge (tindakan operator), commit
yang ditulis operator (`OPERATOR_LOGIN` di repository variables), dan
commit dari `Operator - XyDesk Team` (role Operator, `AGENT.md` bagian
2.1) — ia mewakili operator, jadi penanda `Izin:`-nya tetap dicatat
sebagai jejak, tetapi tidak perlu status `DISETUJUI`.

Untuk menghidupkan kembali pengawasannya, pilih salah satu:

1. **Audit (pelanggaran tampil merah)** — pulihkan workflow
   `verify-push-auth.yml` dari riwayat git (`git show b4ce4a4^:.github/
   workflows/verify-push-auth.yml`). Tidak perlu pengaturan lain; hasilnya
   tampil di tab Actions, tetapi push tetap tidak ditolak.
2. **Gerbang keras (push tanpa izin ditolak)** — selain workflow di atas,
   pasang salah satu:
   - branch protection `main` → *Require status checks* → wajibkan
     `Periksa izin push`; atau
   - wajibkan PR (tidak ada push langsung) — persetujuan adalah review
     operator + merge-nya. Commit feature branch tetap diperiksa: tulis
     `Izin: <ID>` di body commit (biasa) atau — untuk squash merge — di
     deskripsi/isi PR, karena squash memakai isi PR sebagai body commit.
     Merge commit sendiri (tindakan operator) dikecualikan.

Keduanya keputusan operator — bukan sesuatu yang bisa dipasang agent
sendiri, karena menyangkut siapa yang boleh menulis ke `main`.

## Notifikasi push tanpa izin

Saat gerbang menolak push, langkah terakhir workflow mengirim peringatan ke
operator. Dua kanal didukung — salah satu saja sudah cukup, keduanya juga
boleh:

**ntfy (paling cepat dipasang, tanpa akun)** — buat topic di
<https://ntfy.sh> (mis. `xydesk-izin`), lalu tambahkan repository variable:
`NTFY_WEBHOOK = https://ntfy.sh/xydesk-izin`. Untuk produksi, sebaiknya
self-host ntfy di server sendiri (topic publik bisa dibaca siapa pun yang
tahu namanya).

**Telegram** — buat bot lewat [@BotFather](https://t.me/BotFather)
(perintah `/newbot`, simpan token-nya), cari `chat_id` (kirim pesan ke bot,
lalu GET `https://api.telegram.org/bot<TOKEN>/getUpdates`), lalu:
- secret: `TELEGRAM_BOT_TOKEN` (Settings → Secrets and variables → Actions → Secrets)
- variable: `TELEGRAM_CHAT_ID`

Keduanya tidak wajib tersedia: kalau kosong, workflow hanya memberi
peringatan di log dan pelanggaran tetap terlihat merah. Tidak ada
dependensi baru — pengiriman memakai `curl` bawaan runner.



Sejak 1 Sep 2026 seluruh gerbang host berada di dalam `build.yml`. Sebelumnya
fmt/clippy/`cargo test` host tinggal di `build-host.yml` yang berdiri sendiri
dan tidak menjadi syarat rilis apa pun; akibatnya v6.1.0 sempat terbit pada
16:21 sementara gerbang itu merah pada commit yang sama pukul 16:15. Job
`windows` sekarang `needs: [check, host-test]`, dan `release.yml` hanya jalan
setelah run **Build** sukses — jadi tidak ada jalan memutar: gerbang merah =
tidak ada `.exe`, tidak ada Release.

Sebagai bonus, kompilasi host Windows tidak lagi dobel di dua workflow.

Test otomatis yang wajib hijau: `cargo test` host (loopback WebRTC +
pairguard) dan `node --test` Worker. Pengujian perilaku perangkat keras
(capture DXGI, audio WASAPI) tetap manual setelah EXE dipasang.

## Cross-check Windows sebelum push (`tool/check-host-windows.sh`)

Seluruh jalur WASAPI, DXGI, dan GDI berada di balik `cfg(target_os =
"windows")`. `cargo check` di Linux tidak menyentuh satu baris pun dari kode
itu — yang dikompilasi hanyalah stub non-Windows. Konsekuensinya nyata: salah
ketik dan salah tanda tangan API windows-rs baru ketahuan di GitHub Actions,
dengan siklus umpan balik ~8 menit per percobaan. Pada 31 Agu 2026 pola itu
menghasilkan tujuh commit `fix(host)` beruntun dalam satu jam di `main`,
semuanya error kompilasi sepele.

```bash
rustup target add x86_64-pc-windows-gnu
sudo apt-get install -y mingw-w64      # brew install mingw-w64 di macOS

tool/check-host-windows.sh             # detik, bukan menit
tool/check-host-windows.sh --clippy    # + lint
```

Target `-gnu` dipilih karena `-msvc` menuntut Windows SDK + CRT Microsoft
(~1 GB lewat cargo-xwin). Untuk MEMERIKSA kode keduanya setara — parser, type
checker, dan binding windows-rs identik; yang berbeda cuma ABI dan linker, dan
itu tetap diverifikasi runner Windows asli di `build.yml`.

Yang skrip ini TIDAK buktikan: linking MSVC, perilaku runtime, dan apakah
audionya benar-benar terdengar. Itu tetap tugas lab Windows.

## Build biasa

Push ke `main` menjalankan:

1. `flutter pub get`;
2. `dart format lib`;
3. `flutter analyze --fatal-infos`;
4. pemeriksaan aturan desain seamless;
5. build Android, Windows, dan Web;
6. upload hasil ke **Actions → run → Artifacts**.

Artefak build biasa disimpan 30 hari. Artefak Actions bukan GitHub Release dan
tidak otomatis tampil di halaman Releases.

## Deployment Web

Saat operator menjalankan `deploy-web.yml`, workflow mengambil artefak
`XyDesk-Web` dari run Build yang dipilih dan memublikasikannya tanpa build ulang
ke Cloudflare Workers Static Assets. Produksi menggunakan
`https://app.xydesk.my.id`; API, autentikasi, dan signaling tetap terpisah di
`https://signal.xydesk.my.id`.

Konfigurasi publik berada di `web_deploy/wrangler.toml`. Deployment membutuhkan
GitHub Actions Secrets `CLOUDFLARE_ACCOUNT_ID` dan `CLOUDFLARE_API_TOKEN`.
Artefak Web tidak boleh di-commit ke repository.

## Menerbitkan GitHub Release

Versi aplikasi adalah syarat rilis, **bukan lagi pemicunya**. Sejak kebijakan manual penuh, push tidak menjalankan Build; alurnya: operator
menetapkan versi → naikkan `VERSION` (`X.Y.Z+NN`) → push/PR dengan izin →
operator men-*dispatch* `build.yml`. Setelah run Build itu sukses, operator
men-*dispatch* `release.yml` dengan SHA yang sama. Workflow Release lalu:

1. memastikan tag `v<versi>` belum ada (tag menandai versi yang sudah dirilis);
2. memastikan Build sukses berasal dari commit yang sama;
3. memakai APK, Windows client, dan Web dari Build yang sudah lulus;
4. membangun host Rust Windows dari commit yang sama;
5. menerbitkan aset stabil, checksum, dan `update.json`;
6. mengirim OneSignal ke Android dengan `app_version` lebih kecil dari build
   baru.

Build yang sukses tanpa perubahan nilai versi tidak membuat Release. Trigger
manual (`workflow_dispatch` + `release_sha`) disediakan untuk pemulihan.

**Pengawal SHA tertinggal (sejak 3 Sep 2026).** `prepare` menolak merilis SHA
yang sudah dilewati `main`. Alasannya kejadian nyata: manifest versi ikut
berubah di sebuah commit fitur, Build jalan, dan Release langsung menandai
`v6.3.0` di SHA itu — padahal perbaikan layar hitam baru masuk empat commit
setelahnya, sehingga tag menunjuk isi setengah jadi dan rilisnya harus
dianulir paksa. Kini:

- SHA rilis == HEAD `main` → lanjut seperti biasa;
- `main` sudah maju dan Release dijalankan untuk SHA lama → **berhenti merah**,
  dengan pesan berapa commit tertinggal;
- `main` sudah maju tetapi operator mengisi `release_sha` sendiri → lanjut
  dengan peringatan, karena SHA itu memang disengaja.

Aset Release:

- `XyDesk-Android-arm64-v8a.apk` — client Android 64-bit;
- `XyDesk-Android-armeabi-v7a.apk` — client Android 32-bit;
- `XyDesk-x64.msi` — installer WiX MSI Windows x64;
- `XyDesk-x64.exe` — installer NSIS consumer Windows x64 (link direct `.exe`);
- `XyDesk-Windows-x64` — bundle portable native C++ + engine Rust sebelum kedua installer;
- `XyDesk-Web.zip` — client Web;
- `SHA256SUMS.txt` — checksum unduhan;
- `update.json` — manifest update resmi untuk perbandingan build dan verifikasi
  APK;
- `xydesk_update_banner_1024x512.jpg` — gambar push update.

Rilis hanya terbit jika workflow Build untuk commit yang sama sukses dan GitHub
Secret OneSignal tersedia. APK kemudian diverifikasi aplikasi melalui checksum
SHA-256, package ID, nomor build, dan sertifikat signing sebelum installer
Android ditampilkan.

## Signing Android

Secrets berikut harus tersedia di **Settings → Secrets and variables →
Actions**:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Keystore dan `android/key.properties` tidak boleh di-commit. Workflow membuatnya
sementara di runner dan menghapus runner setelah job selesai.

Push update otomatis juga memerlukan GitHub Actions Secret
`ONESIGNAL_REST_API_KEY`. Nilainya hanya dimasukkan langsung melalui GitHub dan
tidak boleh disimpan di source, log, issue, atau percakapan. Jika Secret ini
belum tersedia, workflow berhenti sebelum GitHub Release dipublikasikan.

## Konfigurasi publik build

Repository variables `GOOGLE_WEB_CLIENT_ID`, `GOOGLE_CLIENT_ID`, dan
`RESEND_FROM` diteruskan ke build/deploy sesuai area. Nilai OAuth client ID
bukan secret, tetapi tetap dikelola di GitHub agar konfigurasi build konsisten.

## Checklist manual setelah install

- buka aplikasi dan pastikan splash selesai;
- login OTP dan Google pada perangkat Android nyata;
- tutup/buka aplikasi untuk memeriksa pemulihan sesi;
- logout dan pastikan sesi terhapus;
- periksa keyboard virtual, HUD, rotasi landscape, dan panel sesi;
- hubungkan host Windows dan periksa video, mouse, keyboard, serta reconnect.
