# XyDesk — link unduh dan panduan tes

27 September 2026. **Kandidat uji, bukan promosi ke stabil.**

Validasi CI, build Windows/Android/web, packaging installer, serta deploy signaling/news/web sudah sukses. Domain www dan remote sudah aktif. Pemeriksaan HTTP bukan pengganti tes browser interaktif, koneksi remote, atau instalasi di PC lu.

## 1. Download yang benar

| Paket | Link |
|---|---|
| Installer Windows x64, EXE + MSI + SHA-256 | [Unduh ZIP installer dari GitHub Actions](https://github.com/xykal/XyDesk/actions/runs/36294273373/artifacts/10923152661) |
| Bundle Windows x64 tanpa installer | [Unduh ZIP bundle](https://github.com/xykal/XyDesk/actions/runs/36293875678/artifacts/10923780530) |
| APK Android ARM64 dan ARMv7 | [Unduh ZIP APK](https://github.com/xykal/XyDesk/actions/runs/36293875678/artifacts/10923516361) |

Link artefak Actions membutuhkan login GitHub dan dijadwalkan kedaluwarsa **27 Oktober 2026 UTC**. Unduh dan simpan salinan lokal.

Installer juga tersedia di workspace: `deliverables/XyDesk-test-00ae1b8/XyDesk-x64.exe` dan `XyDesk-x64.msi`, masing-masing beserta `.sha256`.

- Untuk tes biasa, pilih **EXE**. MSI alternatif; jangan pasang keduanya sekaligus.
- Hentikan host lama sebelum mengganti berkas. Menekan X pada panel hanya menyembunyikan panel ke tray, bukan otomatis menghentikan host.
- Jangan menghapus folder identitas/profil host untuk mencoba update.
- Kandidat masih memakai versi manifest **6.8.5+59**, tetapi berasal dari commit **00ae1b8**, bukan paket lama di `releases/latest`. Sebutkan commit saat melapor.
- Windows/Android berasal dari Build `36293875678`. Hotfix web di `bece719` hanya mengubah konfigurasi Worker, regresi web, dan dokumentasi; tidak mengubah source host/installer/Android.
- Belum ada verifikasi instalasi/upgrade di PC nyata. Gunakan mesin uji terlebih dahulu. Jangan menonaktifkan antivirus/SmartScreen secara global.

### Checksum installer — sudah dicocokkan dengan artefak CI

```text
865e08d0be165c6d9168a925b0f5ab0f5b9913fec0ad57404576c97fc20faf44  XyDesk-x64.exe
71e5dd6f3e2f0d9f1adc1c6c31a63b59cf6756d365dafe48b1dcf11d753f9c12  XyDesk-x64.msi
```

Di PowerShell: `Get-FileHash .\XyDesk-x64.exe -Algorithm SHA256`.

## 2. Semua halaman utama untuk dites

| Halaman | Link | Yang dicek |
|---|---|---|
| Publik | https://www.xydesk.my.id/ | Landing dan navigasi publik |
| Informasi download | https://www.xydesk.my.id/download | Situs masih pra-beta; gunakan link artefak di atas untuk kandidat ini |
| Berita | https://www.xydesk.my.id/news | Daftar/detail berita bisa dibuka |
| Legal | https://www.xydesk.my.id/legal | Halaman legal tampil |
| Aplikasi / restore terakhir | https://remote.xydesk.my.id/ | Memulihkan tujuan sesi sesuai akun dan izin tersimpan |
| Perangkat | https://remote.xydesk.my.id/devices | Kartu riwayat perangkat, lalu masuk halaman detail |
| Riwayat sesi | https://remote.xydesk.my.id/history | Riwayat koneksi terpisah dari daftar perangkat |
| Koneksi baru | https://remote.xydesk.my.id/connect | Masukkan ID 9 digit dan password dari host lu |
| Kontrol custom | https://remote.xydesk.my.id/controls | Atur mouse, scroll dan kombinasi keyboard |

Detail perangkat dan sesi harus dibuka memakai **ID host lu sendiri**, bukan ID contoh. URL sesi dibentuk aplikasi ketika memulai koneksi. Password/token tidak boleh muncul dalam URL sesi.

**Pindah domain:** login dan pairing ulang sekali di remote. Storage/izin browser dari app lama tidak berpindah otomatis. Riwayat tamu origin lama tidak otomatis ikut pindah.

### Redirect dan header teknis

- https://app.xydesk.my.id/ → `https://www.xydesk.my.id/`
- https://app.xydesk.my.id/connect → `https://remote.xydesk.my.id/connect`
- https://www.xydesk.my.id/devices → `https://remote.xydesk.my.id/devices`
- https://remote.xydesk.my.id/news → `https://www.xydesk.my.id/news`
- https://remote.xydesk.my.id/robots.txt → `Disallow: /`
- https://www.xydesk.my.id/sitemap.xml → sitemap publik

Google login dites dari tombol **Masuk dengan Google**, bukan dengan membuka callback secara manual. Callback yang dipakai:

- `https://www.xydesk.my.id/auth/callback`
- `https://remote.xydesk.my.id/auth/callback`

Operator mengonfirmasi kedua origin/callback sudah ditambahkan di Google Console. HTTP callback terverifikasi tersedia, **login Google end-to-end belum dijalankan**.

## 3. Urutan tes perangkat nyata

Semua butir di bagian ini masih memerlukan hasil dari perangkat lu.

1. **Host Windows:** pasang satu installer, buka panel di akun Windows/RDP yang dipakai. Cek Ringkasan, Akses host, Kontrol host; ID/password tampil dan status bukan sekadar proses hidup. Panel default 1100×720, coba DPI 100/125/150% dan monitor kecil.
2. **Koneksi awal:** buka `/connect` di HP/laptop, pairing dengan host sendiri. Pastikan gambar benar-benar bergerak, bukan hanya label tersambung.
3. **Navigasi:** perangkat → detail → sesi. Coba Back dan buka riwayat. Preview wallpaper detail bukan klaim desktop live.
4. **Input:** klik kiri/kanan, tahan-drag lalu lepaskan, scroll kedua arah, keyboard, trackpad/direct, layout custom. Coba pindah aplikasi/blur saat menahan tombol; input tidak boleh tertinggal tertekan.
5. **Mobile:** portrait/landscape, dock kiri/kanan, ukuran besar/ringkas. Panel, dock, keyboard dan editor tidak boleh saling menutup kontrol utama.
6. **Refresh saat sesi aktif:** layar sesi tetap terbuka dan mencoba koneksi baru jika izin tersimpan sah. Browser tidak mempertahankan transport WebRTC lama.
7. **Tutup dan buka browser:** buka lagi root remote pada profil/akun yang sama, dengan storage tetap ada. Tujuan terakhir seharusnya dipulihkan.
8. **Izin tidak sah:** cabut izin/ganti password host lalu coba restore. Harus meminta pemulihan/password, bukan loop reconnect tanpa jalan keluar. Tes juga login Google dari layar pemulihan lalu kembali ke sesi.
9. **Putus eksplisit dan pergantian akun:** setelah Putus/Batalkan, kembali ke perangkat dan jangan otomatis membuka sesi lama. Logout/ganti akun tidak boleh memakai izin akun sebelumnya.
10. **Windows/RDP:** akun Windows yang sama di console/RDP tidak boleh menghasilkan dua leader aktif; akun Windows berbeda harus terisolasi. Uji lock/unlock dan RDP disconnect/reconnect. Catat kalau gambar hitam/takeover macet.
11. **Audio dan durasi:** speaker, mic hanya setelah diaktifkan dengan sengaja, lalu sesi 30 menit. Coba putus jaringan singkat dan retry. Catat frame macet, suara putus, input tertahan dan penggunaan resource.
12. **Android APK:** ARM64 untuk perangkat 64-bit yang mendukungnya; ARMv7 untuk perangkat 32-bit. Coba pairing ke host kandidat. Jika pemasangan ditolak karena tanda tangan/versi, catat pesannya; jangan langsung hapus data tanpa mempertimbangkan kehilangan sesi/profil.

Saat melapor: kirim langkah reproduksi, Windows/browser/Android + versi, jenis koneksi (LAN/internet/RDP), commit kandidat, waktu kejadian, hasil yang terlihat, dan screenshot yang sudah disensor. **Jangan kirim password, token, cookie atau berkas kredensial.**

## 4. Bukti yang sudah ada

| Tahap | Hasil / bukti |
|---|---|
| Validasi web + Linux + Windows | [PASS — 36293323607](https://github.com/xykal/XyDesk/actions/runs/36293323607) |
| Build executable dan APK | [PASS — 36293875678](https://github.com/xykal/XyDesk/actions/runs/36293875678) |
| Installer MSI/NSIS | [PASS — 36294273373](https://github.com/xykal/XyDesk/actions/runs/36294273373) |
| Deploy signaling/CORS | [PASS — 36293816856](https://github.com/xykal/XyDesk/actions/runs/36293816856) |
| Deploy news | [PASS — 36293822342](https://github.com/xykal/XyDesk/actions/runs/36293822342) |
| Build penuh hotfix routing web | [PASS — 36294341595](https://github.com/xykal/XyDesk/actions/runs/36294341595) |
| Deploy web final | [PASS — 36294668021](https://github.com/xykal/XyDesk/actions/runs/36294668021) |

Windows validation mencakup 202 unit test, startup identitas serentak, control IPC antarproses, compile panel, geometri/hit-test dan snapshot bentuk panel. Snapshot adalah fixture renderer, bukan bukti sesi desktop live.

HTTP produksi: 18 URL diperiksa, redirect 308 sesuai tujuan, root remote no-store/noindex, robots remote disallow, dan 3 aset JS/CSS merespons 200 dengan MIME sesuai. CORS www/remote/app masing-masing merespons 204 dengan origin yang tepat. Ini tidak menjalankan JavaScript dalam browser atau membuktikan restore/remote control/audio end-to-end.

Masalah yang ditemukan dan diperbaiki selama validasi: strict Clippy, fixture pipe backpressure, argumen CLI probe panel, dan Cloudflare asset-first yang melewati routing root/robots. Gerbang tidak dimatikan untuk meloloskan build.
