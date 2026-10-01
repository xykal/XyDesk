# Checklist manual Android Chrome (per rilis web)

Tes otomatis web hanya Node; hal-hal di bawah pernah lolos CI tetapi patah di
HP sungguhan. Jalankan di satu HP Android dengan Chrome, sesi ke host nyata,
5 menit. Tulis hasilnya di `changelogs/<versi>.md` bagian Verifikasi.

| # | Langkah | Lulus bila |
|---|---------|-----------|
| 1 | Statistik → Latensi → **Salin laporan** | Tulisan "Laporan disalin"; tempel di Notes menghasilkan JSON `xydesk-latency-report/1` |
| 2 | **Bagikan** | Share sheet Android terbuka dengan teks JSON |
| 3 | **Unduh** | File `xydesk-latency-*.json` ada di Files → Download (boleh gagal diam: catat) |
| 4 | Baris **Layar ke layar** tampil (bukan "Perkiraan") | Host 6.8.6+ dan Chrome; kalau "Perkiraan", catat versi host + jalur (P2P/relay) |
| 5 | Kolom **Video Mbps** > 0 saat gambar bergerak | Bila 0.0 dengan FPS > 0, bug pembacaan `bytesReceived` |
| 6 | Panel Video → Otomatis: ganti jaringan Wi-Fi ↔ seluler | Keputusan dan alasan berubah dalam ≤ 15 detik, tanpa putus sesi |
| 7 | Rotasi layar saat sesi | Video mengisi ulang tanpa hitam > 1 detik |
| 8 | Kunci layar 10 detik, buka | Sesi kembali sendiri atau tombol sambung ulang terlihat |
| 9 | Tombol dengan file/clipboard lain (QR, salin ID) | Hasil terlihat oleh pengguna (toast/teks), bukan diam |

Kenapa ada: 6.8.8, tombol "Unduh laporan latensi" tidak menghasilkan apa pun
di Chrome Android tanpa pesan; pengguna mengira laporannya "gada".
