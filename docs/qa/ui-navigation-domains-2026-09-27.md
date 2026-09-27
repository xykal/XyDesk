# Kandidat UX, navigasi sesi dan domain — 27 September 2026

Sesi `SESI-20260927-OPERATOR-HARDEN`, lanjutan atas pilihan pemilik.
Baseline tetap `f5fd41f49ee0bd606cca8bd0bc0880c79c0a5318`.
**Status: source lokal, belum build/test Actions, belum observasi browser/Windows,
belum push/deploy/DNS/OAuth Console. Tidak ada bump versi.**

## Keputusan dan penilaian UX

Masalah utama sebelumnya bukan sekadar ukuran: pemilihan perangkat, riwayat,
detail dan sesi remote ditumpuk dalam satu alur/modal; hash dibuat setelah
connected tetapi tidak dibaca untuk membangun kembali layar sesi. Komponen
MouseHud ada, namun tidak dirender oleh layar sesi. Ikon tanpa label membuat
aksi penting kurang terlihat.

Arah yang dipilih: halaman kerja terpisah, preview tidak mengaku layar live,
kontrol mouse utama berlabel, editor custom tetap tersedia, dan halaman sesi
menjadi tujuan navigasi yang bisa di-refresh. Ini penilaian source/struktur,
bukan hasil usability test atau screenshot aplikasi yang sudah berjalan.

## Halaman web

| Origin / route | Fungsi |
|---|---|
| `https://www.xydesk.my.id/` | Website publik, landing |
| `www.../news`, `/download`, `/legal`, `/billing` | Halaman publik |
| `https://remote.xydesk.my.id/` | Pulihkan tujuan sesi tersimpan akun saat ini; jika tidak ada, tampilkan perangkat |
| `remote.../devices` | Kartu perangkat dari riwayat yang tersedia; bukan klaim inventaris perangkat online |
| `remote.../devices/:deviceId` | Halaman detail, wallpaper/spec terakhir dan tombol buka sesi |
| `remote.../history` | Catatan tiap koneksi, bukan daftar perangkat yang digabung ulang |
| `remote.../connect` | Koneksi baru, ID/password/QR |
| `remote.../session/:deviceId#session/<id-tampilan>` | Layar sesi/pemulihan; hash bukan tiket akses |
| `remote.../controls` | Halaman editor layout custom |
| `www.../auth/callback` dan `remote.../auth/callback` | Callback Google pada origin penyimpan nonce masing-masing |

`app.xydesk.my.id` dipertahankan dalam kandidat routes sebagai redirect legacy:
root/publik menuju www, halaman remote menuju remote. HTTPS dipaksakan oleh
kandidat helper redirect untuk host kanonik. Aset pada www/remote tidak saling
redirect. Origin API/signaling tetap `signal.xydesk.my.id`.

## Restore: kontrak perilaku

- URL sesi dibuat sejak mulai mencoba koneksi, tidak menunggu connected.
- `sessionOpen` dibangun dari route; refresh tidak kembali ke form connect.
- Metadata localStorage hanya version/deviceId/fragment/scope/savedAt. Tidak
  menyimpan password, JWT atau resume grant di metadata/URL sesi.
- Membuka ulang root remote dapat memilih tujuan terakhir **dengan scope akun
  yang cocok**. Tujuan tidak diberi masa berlaku buatan; izin host tetap harus
  sah pada setiap reconnect.
- Auto-reconnect hanya mencoba izin browser yang sudah tersimpan. Izin dicabut,
  storage hilang, atau belum pernah pairing: tetap di layar sesi, minta password
  atau masuk akun di sana. Host busy tidak diterobos.
- Gangguan jaringan tetap menampilkan state/error dan aksi retry di layar sesi.
  Tidak menjanjikan soket/WebRTC bertahan setelah browser ditutup: dibuat baru.
- Refresh/unmount/pagehide menutup transport tanpa menghapus tujuan. BFCache
  pageshow mencoba pemulihan bila scope/izin masih cocok. Cleanup StrictMode
  tidak mengubah layar sesi menjadi form connect.
- Tombol putus/batalkan eksplisit menghapus tujuan milik tampilan tersebut dan
  kembali ke daftar perangkat. Logout/pergantian akun menghentikan transport
  dan menahan auto-reconnect, tanpa menggunakan izin akun sebelumnya.
- Masuk Google dari layar sesi menyimpan return path/hash non-secret; callback
  tetap pada origin nonce lalu kembali ke halaman sesi.
- Fullscreen/audio dapat memerlukan gesture pengguna; restore otomatis tidak
  memaksa fullscreen dan tidak otomatis mengaktifkan mikrofon.

Batas browser: storage diblokir/dihapus, mode private setelah ditutup, atau
pengguna membuka www/URL lain tidak bisa dipaksa mempertahankan tujuan remote.
Deep link `/session/:id` tetap menyediakan layar pemulihan tanpa storage.

## Kontrol dan ukuran

### Remote web

- Dock nyata: klik kiri/kanan berlabel, tahan untuk drag, scroll tahan,
  Windows, switch trackpad/sentuh langsung, pusatkan kursor, layout custom.
- Posisi kiri/kanan dan ukuran besar/ringkas tersimpan sebagai preferensi UI.
- Tombol utama besar 64 px, mode ringkas 48 px; viewport landscape pendek
  memakai minimum 44 px. Aksi mouse tetap melalui owner/ordering yang sudah
  dipakai pointer engine, bukan kanal baru.
- Input dilepas ketika blur, hide, cancel atau dock dilepas dari DOM.
- Dock, keyboard, panel setelan dan layout custom diatur agar tidak semua
  memenuhi layar bersamaan. Target custom tetap dapat diubah menjadi klik
  kanan/tengah, tombol samping, scroll, keyboard atau chord.
- Panel setelan desktop ditambah hingga 560 px; editor custom hingga 440 px;
  viewport mobile memakai lebar adaptif. Background navigasi dibuat inert
  ketika layar sesi terbuka.

### Host Windows

- Default panel menjadi **1100 × 720 logical px**, sebelumnya 900 × 560.
- Sidebar diperlebar; label Ringkasan / Akses host / Kontrol host memperjelas
  pembagian tugas. Host tidak dijadikan browser/inventaris remote kedua.
- Penyesuaian DPI/work area membatasi ukuran awal pada monitor yang digunakan;
  minimum geometri lama tetap menjadi batas. Perilaku monitor kecil/DPI tinggi
  masih perlu diuji nyata, bukan disimpulkan dari angka saja.
- Shortcut web host menuju halaman perangkat remote. Identitas dan readiness
  tetap memakai hasil engine, bukan status UI palsu.

## Konfigurasi layanan — belum diterapkan

1. Periksa kepemilikan/konflik DNS dan custom domains www/remote/app sebelum
   deploy frontend. Tidak mengubah DNS atau service lain pada sesi ini.
2. Deploy kandidat API CORS secara berizin: allowlist www dan remote ditambah,
   app/admin lama tetap dipertahankan; tidak menggunakan wildcard baru.
3. Daftarkan authorized JavaScript origins www dan remote pada OAuth Web client,
   serta redirect URI lengkap:
   - `https://www.xydesk.my.id/auth/callback`
   - `https://remote.xydesk.my.id/auth/callback`
4. Migrasikan frontend/Worker dan news redirect menuju www setelah gate hijau.
   `news/wrangler.toml`, renderer OG, sitemap/canonical, URL installer dan host
   sudah mendapat kandidat perubahan source. Deploy masing-masing tetap manual.
5. Verifikasi TLS, callback, CSP, SPA deep links, noindex remote dan redirect
   tanpa loop. Jangan menggunakan Build umum hanya untuk validasi: dapat
   memicu deploy/release yang berbeda dari workflow validation-only.

**Migrasi storage:** localStorage/JWT/grant origin app tidak tersedia di remote.
Perlu login/pair ulang sekali pada domain baru. Tidak ada jembatan credential
via query/hash dan tidak ada klaim SSO lintas-origin. Metadata/izin tamu lama
juga tidak dipindahkan otomatis. Pengguna akun dapat mengambil riwayat akun
setelah login; riwayat tamu tetap pada browser/origin asal.

## Pemeriksaan dan gerbang

Dilakukan lokal: review source/call-site, pemeriksaan sintaks TS/TSX/C++
(dibanding baseline), Python AST, YAML/TOML, CSS parser tingkat stylesheet,
`node --check` pada source regresi baru, serta `git diff --check`.
**Bukan** type-check, compile, unit test, render review atau uji end-to-end.

Source regresi baru: metadata restore/scope/ownership tab/path yang aman,
origin canonical/HTTPS/legacy redirect/OAuth callback, CORS allowlist serta
callback nonce-origin. Regresi geometri panel dan checker probe/shape diubah
sesuai ukuran baru. Workflow validasi menambahkan unit API/news dan snapshot
render panel fixture. Snapshot fixture kelak bukan bukti sesi host live.

Semua acceptance berikut **BELUM DIUJI**:

| ID | Acceptance |
|---|---|
| UX01 | Perangkat → detail → sesi, Back/Forward dan buka detail di tab baru; tidak ada sesi dalam modal |
| UX02 | Refresh saat pairing, negotiating, connected, error: tetap halaman sesi device yang sama |
| UX03 | Tutup/buka browser lalu root remote: auto-reconnect dengan grant sah; tetap minta password bila grant tidak ada |
| UX04 | Izin dicabut/password host berubah, JWT 401, host busy: tidak bypass; recovery tetap di sesi |
| UX05 | Putus eksplisit menghapus tujuan; refresh/cleanup StrictMode tidak menghapusnya; BFCache tidak menggandakan peer |
| UX06 | Akun berganti, dua tab dan metadata rusak/blocked storage: tidak memakai izin scope lain |
| UX07 | Klik kiri/kanan, double click, tahan-drag, scroll tahan, switch mode, blur/cancel: tidak ada tombol tertahan |
| UX08 | Dock kiri/kanan/besar/ringkas; custom mouse/key/chord; keyboard dan panel tidak menutup semua aksi keluar |
| UX09 | HP portrait/landscape, 320 px, laptop, desktop; focus/Tab/inert, virtual keyboard, safe area dan fullscreen |
| UX10 | Panel Windows 100/125/150/200% DPI, layar kecil, multi-monitor dan resize; tidak terpotong/keluar work area |
| UX11 | www/remote/app HTTPS, redirect, deep-link refresh, OAuth dua origin, CSP/CORS dan news canonical |
| UX12 | Domain lama ke baru: pemberitahuan re-login/re-pair; tidak ada credential pada URL/log/metadata restore |
