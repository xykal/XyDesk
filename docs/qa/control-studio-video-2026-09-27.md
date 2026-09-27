# Control Studio dan perbaikan pemutar video — 27 September 2026

## Status akhir

Source web `61e490e2c11cbc8db97aa06ae23ea1d4200bd0e2` sudah live di www/remote.
Run [36297653583](https://github.com/xykal/XyDesk/actions/runs/36297653583)
berhasil: unit tests, TypeScript/Vite build, Chromium smoke, upload bukti dan
Cloudflare deployment. Perubahan ini web-only; tidak mengganti installer host,
APK, protokol signaling, maupun nomor versi. Rilis stabil/latest tidak berubah.

## Layar hitam: bukti dan koreksi

Operator melaporkan 932 frame diterima, 926 didecode, capture GDI berjalan,
namun elemen video paused/readyState=0/0x0. Ini membedakan penerimaan/decode
WebRTC dari pemasangan stream pada elemen HTML video.

Source sebelumnya memeriksa `video.srcObject !== remoteVideoStream` dan
langsung return sebelum helper yang memasang `srcObject` dipanggil. Pada
mount pertama `srcObject` null, sehingga stream tidak pernah terpasang.

Controller baru memasang stream pertama, memanggil play muted/inline,
mengikat retry pada pemilik elemen, membatalkan callback/timer saat cleanup,
dan tidak menghentikan track milik peer. Jika autoplay ditolak, tersedia tombol
gesture untuk memutar. Revisi diagnostik: **pointer-v3-video-bind**.

Chromium Actions memakai canvas.captureStream() nyata untuk membuktikan video
awalnya kosong menjadi playing, readyState>=2 dan ukuran 160x90. Regresi lain:
StrictMode remount, stale owner, rejected play setelah teardown. Bukan bukti
sesi Windows/RDP operator telah sembuh; perlu sambungkan ulang dan periksa
ukuran pemutar/non-paused serta gambar desktop nyata.

## Kontrol dan menu

- Hamburger memakai dialog selayar browser, tanpa Fullscreen API. Escape,
  tombol tutup, focus containment native dan pemulihan overflow tersedia.
- Control Studio meminta fullscreen perangkat dari klik pengguna. Jika ditolak,
  editor memakai overlay viewport. Save/Batal keluar dari fullscreen yang
  diminta editor sendiri, tidak mengambil kepemilikan fullscreen sesi lama.
- Alur: kategori → tambah kontrol → canvas → pilih kontrol → inspector samping.
- Kategori keyboard (QWERTY, F1–F24, navigasi, numpad, modifier, media), stick,
  mouse, kombinasi, serta shortcut copy/paste/cut/undo/select-all pada host.
- Nama internal dan teks tombol terpisah. Label visual custom hanya untuk key
  dan chord; mouse/scroll tetap ikon. Ukuran, radius, posisi dan aksi bisa diatur.
- Inspector bisa dipindah kiri/kanan; sisi disimpan. Toolbar diukur dengan
  ResizeObserver agar inspector tidak menutupi Simpan/Batal.
- Draft portrait/landscape dipisah dan dipertahankan ketika orientasi berganti;
  Simpan menulis draft ke storage. Maksimal 24 kontrol per orientasi.
- Input pointer/keyboard host ditahan saat mengedit; input yang ditahan dilepas
  sebelum masuk editor. Stick direcenter/release pada cancel, blur, hide dan
  unmount. Stick keyboard berbagi ownership tombol dengan kontrol lain.
- Ikon klik kiri/kanan/tengah serta tombol samping dibedakan. Dock mouse diberi
  styling baru, tanpa membolehkan label custom menggantikan ikon mouse.

## Batas fitur: gamepad analog

**BELUM DIIMPLEMENTASIKAN:** gamepad analog sungguhan di host. Yang aktif
adalah stick keyboard (termasuk diagonal) dan stick gerak mouse relatif.
Pilihan gamepad analog ditandai disabled dengan penjelasan, bukan dimasukkan
ke daftar fitur selesai. Source/protokol host saat ini tidak memiliki virtual
gamepad; penambahan ini memerlukan desain protokol, dukungan host serta
pertimbangan driver bertanda tangan. Tidak ada driver dipasang diam-diam.

## Bukti browser

[Artefak screenshot dan hasil JSON](https://github.com/xykal/XyDesk/actions/runs/36297653583/artifacts/10925090322)
memuat editor desktop, editor mobile 390px, menu desktop serta hasil tes:

1. Stream video pertama terpasang dan diputar.
2. Menu memenuhi viewport tetapi tidak memakai fullscreen perangkat; Escape menutup.
3. Pilih kontrol membuka inspector; nama/label dan pilihan sisi bertahan.
4. Stick WASD bergerak dan recenter ketika dilepas; gamepad analog disabled.
5. Inspector tidak keluar viewport mobile; tidak ada pageerror Chromium.

Bug toolbar tertutup inspector ditemukan oleh klik Playwright biasa dan
screenshot, lalu diperbaiki. Tes tidak dipaksa memakai force-click. Kegagalan
awal async fixture dan JSX juga diperbaiki sebelum deploy; tidak ada gate
dinonaktifkan. Workflow manual web-only menghindari build ulang APK/host pada
iterasi yang tidak mengubahnya dan menolak deploy bila main sudah maju.

HTTP produksi setelah deploy: `/controls`, root remote dan root www 200;
remote no-store/noindex; bundle `/assets/index-Wq36Sz_w.js` berisi Control Studio
dan revisi video baru. HTTP/bundle verification bukan uji interaksi perangkat.

## Link dan tes operator

- https://remote.xydesk.my.id/controls → Atur kontrol · fullscreen.
- https://remote.xydesk.my.id/connect → reconnect host yang sama.
- https://www.xydesk.my.id/ → hamburger pada viewport mobile.
- Menu remote juga tersedia pada header perangkat/kontrol.

Reload tanpa menghapus storage, lalu sambungkan ulang. Jika masih hitam,
periksa revisi `pointer-v3-video-bind`, paused, readyState, ukuran pemutar,
frame pemutar dan frame decode. Kirim statistik yang telah disensor; jangan
kirim token/password/cookie. Kondisi RDP background/disconnect tetap perlu
dibedakan jika capture selanjutnya bermasalah, tetapi bukan pengganti diagnosis
pemutar kosong yang terbukti pada source sebelumnya.

Acceptance masih terbuka: Chrome Android operator, Safari/iOS fullscreen
fallback, login Google end-to-end, sesi RDP nyata, audio/mic, mouse-stick di
host nyata, dan ketahanan sesi panjang.
