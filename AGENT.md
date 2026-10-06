# AGENT.md — Aturan Kerja di XyDesk

> **WAJIB dibaca penuh di awal SETIAP sesi, sebelum menyentuh satu file pun.**
> Dokumen ini adalah kontrak kerja. Melanggarnya = kerja sesi itu dianggap
> tidak sah. Sumber kebenaran teknis tetap `docs/project/ROADMAP.md`; dokumen
> ini mengatur **cara kerja dan perilaku**.

> **Perubahan 6 Okt 2026 — sistem role dihapus.** Dulu repo ini dibagi ke
> sembilan role (Web, Host Engine, CI/Release, dan seterusnya), masing-masing
> dengan identitas git karangan, papan kunci area, dan antrean izin push.
> Pembagian itu sudah tidak dipakai: seluruh pekerjaan dikerjakan atas nama
> pemilik repo, `xykal`. Yang hilang hanya birokrasinya — kewajiban bukti,
> changelog, dan PR justru tetap penuh.

---

## 1. Ritual awal sesi

1. Baca `AGENT.md` (file ini) sampai habis.
2. Baca PR dan issue yang masih terbuka — itu yang memberi tahu kontrak apa
   yang sedang berubah dan apa yang sedang menunggu.
3. Baca `docs/project/ROADMAP.md` bagian fase yang aktif, lalu `CHANGELOG.md`
   bagian `[Belum terbit]` — supaya tahu posisi proyek hari ini, bukan
   menebak. Baca juga `docs/project/HANDOFF.md`: itu antrean kerja yang masih
   terbuka.
4. Sepakati dengan pemilik repo **apa yang dikerjakan sesi ini**, lalu
   kerjakan itu saja. Kalau di tengah jalan ketemu bug atau ide di luar
   kesepakatan: jangan diam-diam dikerjakan — catat di
   `docs/project/HANDOFF.md` dan laporkan di akhir sesi.

---

## 2. Identitas commit

Satu identitas untuk semua commit di repo ini:

```bash
git config user.name  "xykal"
git config user.email "xykal@users.noreply.github.com"
```

Tidak ada lagi nama karangan `Nama - XyVerse Team`, tidak ada lagi pemilihan
role, tidak ada lagi pendaftaran identitas baru. Nama-nama lama tetap
tersimpan di `docs/project/CONTRIBUTORS.md` sebagai catatan sejarah —
**daftar itu hanya dibaca, tidak ditambah dan tidak dihapus.**

---

## 3. Perilaku: manusiawi, jujur, teliti

### Jujur — sejujur-jujurnya

- **Dilarang keras mengaku sesuatu sudah dites/jalan kalau belum dibuktikan
  sendiri di sesi ini.** Kata yang boleh dipakai hanya yang sesuai fakta:
  - "sudah saya jalankan dan hijau" → hanya kalau benar-benar dijalankan.
  - "compile lolos, tapi belum diuji di perangkat nyata" → kalau memang itu.
  - "belum saya uji" → kalau belum. Ini bukan aib, ini kejujuran.
- Kalau tidak tahu atau tidak yakin, bilang tidak tahu. Jangan mengarang API,
  jangan mengarang hasil, jangan mengarang angka benchmark.
- Kalau kamu memperkenalkan bug atau merusak sesuatu, laporkan sendiri di
  laporan akhir. Jangan disembunyikan.
- Ini sejalan dengan aturan #1 repo: **bukti dulu, baru poles.**

### Teliti dan konsisten

- Ikuti gaya kode dan konvensi yang **sudah ada** di area yang kamu sentuh.
  Baca file tetangga dulu sebelum menulis file baru. Jangan bawa gaya sendiri.
- Kalau ragu antara dua cara, cari bukti di kode/dokumen repo
  (`docs/ARCHITECTURE.md`, `docs/PROTOCOL.md`, dan lainnya). Kalau tetap ragu,
  tanya pemilik repo — bertanya lebih murah daripada salah.
- Sebelum menganggap kerjaan selesai, jalankan pemeriksaan area yang disentuh:

  | Area | Pemeriksaan |
  |---|---|
  | `android-native/` | `gradle assembleDebug` (CI), Kotlin tanpa warning baru |
  | `host/` | `cargo fmt --check` + `cargo test` |
  | `cloudflare/`, `signaling/` | test Worker di `cloudflare/test/`, `gofmt` untuk Go |
  | `web/`, `admin/` | lint + build sesuai `package.json` masing-masing |
  | `packaging/native-host/` | kompilasi panel Win32 |
  | lintas berkas | `python3 tool/check_version.py`, `git diff --check` |

- Kalau environment tidak memungkinkan menjalankan salah satunya, **tulis
  jujur di laporan bahwa pemeriksaan itu belum dijalankan.**

### Manusiawi — tanpa jejak AI di produk

- **Tidak boleh ada satu pun bahasa "AI-ish" yang bocor ke UI, konten,
  komentar kode, atau file proyek.** Dilarang muncul di produk:
  - "Sebagai AI...", "Berikut adalah...", "Tentu! ...", "Semoga membantu",
    "In this file we...", placeholder macam "Lorem ipsum" atau "TODO: ganti
    teks ini", emoji berlebihan, atau nada template.
  - Komentar kode yang menceritakan proses ("saya mengubah ini karena
    diminta...") — komentar hanya menjelaskan **kenapa kode begitu**, itu pun
    seperlunya.
- Teks yang menghadap pengguna (UI, web, berita) ditulis seperti manusia yang
  peduli produknya: bahasa Indonesia yang rapi, hangat tapi tidak lebay,
  konsisten dengan nada yang sudah ada di aplikasi dan `docs/NEWS_STYLE.md`.
- Commit message ditulis seperti engineer manusia: singkat, spesifik, bahasa
  Indonesia (mengikuti kebiasaan repo), tanpa menyebut AI/agent/prompt/sesi.
  Contoh baik: `perbaiki urutan add_video_track sebelum create_answer`.
  Contoh buruk: `AI update files as requested`.

---

## 4. Alur perubahan

- **`main` diproteksi: semua perubahan lewat PR**, tanpa force-push. Satu PR
  per putaran kerja, deskripsinya berisi apa yang berubah dan pemeriksaan apa
  yang membuktikannya.
- Merge memakai **merge commit** (bukan squash) supaya history tiap perubahan
  tetap terbaca.
- **Kontrak tetap dicatat** di `CHANGELOG.md` dan `changelogs/<versi>.md` —
  itu sumber kebenaran untuk sesi berikutnya.
- Bila `xykal/XyDesk-Remote` perlu kompatibel dengan XyDesk, perubahannya
  diusulkan di repo Remote dan diuji terhadap host versi rilis terbaru.

### Yang tetap butuh restu pemilik repo di chat

Birokrasi role dihapus, tiga gerbang ini tidak:

1. **Menaikkan nomor versi** (`VERSION`, `web/package.json`,
   `host/Cargo.toml`, `admin/package.json`).
2. **Menerbitkan artikel berita** atau mengubah isi berita yang sudah live.
3. **`workflow_dispatch` Build/Release dan deploy ke produksi.** Semua
   workflow manual-only dan dijaga actor guard `xykal`.

Dan yang **tidak pernah longgar**: perubahan berisiko produksi besar —
menghapus atau memigrasi data, mengubah auth/keamanan, mengubah
harga/lisensi, atau apa pun yang tidak bisa di-rollback dengan revert biasa —
wajib konfirmasi khusus lebih dulu.

---

## 5. Aturan repo yang tidak boleh dilanggar

- **Changelog wajib**: setiap perubahan berarti dicatat di `CHANGELOG.md`
  bagian `[Belum terbit]`, format Keep a Changelog.
- **Berita**: ditulis untuk pengguna tapi WAJIB lengkap — setiap perubahan
  dijelaskan *apa yang berubah* dan *kenapa*, ada bagian "Semua perubahan di
  versi X.Y.Z" (changelog bahasa pengguna, bukan salinan `CHANGELOG.md`), dan
  setiap perubahan visual disertai **screenshot asli dari build rilis**
  (bukan mockup/AI/stok) — banner saja tidak cukup. Tanpa nama berkas/fungsi,
  tanpa versi di judul, penulis `Haekal Saputra` (lihat `docs/NEWS_STYLE.md`).
- **Versi**: rilis wajib menaikkan `VERSION` (`X.Y.Z+NN`), `web/package.json`,
  dan `host/Cargo.toml` (lihat `docs/VERSIONING.md`).
- **Kredensial**: `.env` dan turunannya tidak pernah masuk git — hanya
  `.env.example` tanpa nilai asli. Kunci yang terlanjur bocor harus dirotasi,
  bukan sekadar dihapus dari working tree.
- **Logo/aset generate**: logo resmi XyDesk = X ungu glossy
  `design/logo-asli.png` — WAJIB dipakai di SEMUA platform (Android, web,
  desktop, host, email, materi rilis). JANGAN edit hasil generate dan jangan
  bikin varian sendiri. Satu sumber `design/logo-asli.png`, semua turunan
  lewat `tool/gen_logo.py` (rincian di `docs/BRAND_ASSETS.md`).
- **Tema Android**: terang (Paper) saja. Jangan menambahkan dark mode.
- **Desktop shell**: hanya shell — logika inti tetap di engine Rust.
- **Lisensi**: proprietary. Jangan menambah dependensi tanpa mencatatnya di
  `docs/THIRD-PARTY-LICENSES.md`, dan hanya yang gratis tanpa kartu kredit
  (aturan #2 ROADMAP).

---

## 6. Ritual akhir sesi

Sebelum pamit:

1. **Tutup jejak sesimu**: PR sudah merge, atau ditandai draft dengan catatan
   status. Hasil kerja dicatat di `CHANGELOG.md` `[Belum terbit]` — itu bukti
   jejaknya.
2. Tulis **laporan akhir sesi** (di chat, bukan ke file proyek) berisi:
   - apa yang dikerjakan — daftar file yang berubah;
   - **bukti**: pemeriksaan/test apa yang dijalankan dan hasil aslinya, atau
     pengakuan jujur bahwa belum dijalankan, plus tautan run CI bila ada;
   - apa yang BELUM selesai, diragukan, atau berisiko;
   - temuan di luar lingkup kerja — dan ini TIDAK cukup diucapkan di chat:
     **tulis ke `docs/project/HANDOFF.md`**, ikut di-commit bersama
     kerjaanmu. Item yang sudah selesai **dihapus** dari HANDOFF (buktinya
     ada di CHANGELOG), bukan ditandai `[x]` dan dibiarkan menumpuk.

Laporan yang jujur tapi hasilnya belum sempurna **lebih dihargai** daripada
laporan mulus yang ternyata bohong.
