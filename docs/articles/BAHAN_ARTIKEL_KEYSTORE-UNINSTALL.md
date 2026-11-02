# Bahan Artikel — Kunci Baru Sejak Versi 2.0.0: Uninstall Dulu Sebelum Memasang

Status bahan: siap edit. Bahan ini **bukan artikel terpisah** — gabungkan ke
artikel rilis berikutnya (satu rilis = satu artikel, lihat `docs/NEWS_STYLE.md`).
Bagian yang paling penting dibawa ke artikel gabungan: paragraf "Yang perlu kamu
lakukan" + FAQ pertama.

Sumber fakta (untuk editor rilis, jangan masukkan ke berita):

- Sidik SHA-256 sertifikat semua rilis publik sejak v2.0.0 identik
  (`8429f689e7…`, nilai penuh tercatat di sesi forensik APK). Artinya kunci
  penandatanganan memang SAMA untuk seluruh rilis v2.0.0 ke atas.
- Halaman unduh resmi sudah mencatat: "Ditandatangani dengan kunci baru sejak
  v2.0.0".
- HANDOFF: "Keystore Android dirotasi — APK lama tidak update-in-place".
- Verifikasi `apksigner verify --print-certs` pada APK pertama keystore baru
  (rilis v2.0.0) sudah terpenuhi lewat forensik sertifikat di atas.
- Application ID tidak berubah antar era (`id.xydesk.remote`) — jadi ini
  benar-benar "tidak bisa menimpa", bukan dua aplikasi berbeda.

## Ringkasan Perubahan

| Aspek | Aplikasi rilisan lama (sebelum 2.0.0) | Sejak versi 2.0.0 |
|---|---|---|
| Kunci penandatanganan | Kunci lama | Kunci baru — satu kunci untuk semua rilis |
| Memasang versi baru di atas yang lama | — | Tidak bisa menimpa; harus uninstall dulu |
| Pembaruan antar versi 2.0.0 ke atas | — | Berjalan normal, cukup pasang menimpa |

## Narasi (Draft)

Ada satu hal yang perlu kami sampaikan dengan jujur, terutama buat kamu yang
sudah lama memakai XyDesk-Remote: **aplikasi terbaru tidak bisa dipasang
menimpa aplikasi lama.** Android akan menolaknya — kadang dengan pesan singkat
yang tidak menjelaskan apa-apa.

Ini bukan karena aplikasinya rusak. Penyebabnya: kunci penandatanganan aplikasi
kami ganti sejak versi 2.0.0. Android memperlakukan aplikasi dengan tanda
tangan berbeda sebagai aplikasi yang berbeda, dan tidak boleh saling menimpa.
Itu aturan keamanan Android yang bekerja untuk melindungi kamu dari aplikasi
yang menyamar sebagai aplikasi lain.

**Yang perlu kamu lakukan mudah:** hapus (uninstall) aplikasi lama dari HP,
lalu pasang yang baru. Masuk dengan akun yang sama, dan kamu siap menyambung
lagi seperti biasa.

Yang tersimpan di HP — sesi masuk dan setelan aplikasi — ikut terhapus saat
di-uninstall, jadi kamu perlu masuk lagi sekali. Akun kamu dan data di cloud
tidak ikut terhapus.

Satu kabar baiknya: sejak versi 2.0.0, semua rilis memakai kunci yang sama.
Jadi ini hanya sekali di awal. Pembaruan berikutnya berjalan seperti biasa —
cukup pasang menimpa, tidak perlu uninstall lagi.

## FAQ

**Kenapa tidak bisa update langsung, harus uninstall dulu?**
Karena kunci penandatanganan aplikasi berbeda dari rilisan lama. Android
menolak pembaruan yang tanda tangannya tidak cocok — itu fitur keamanan, bukan
kerusakan.

**Apakah akun dan data saya hilang setelah uninstall?**
Tidak. Akun kamu tetap ada. Yang hilang hanya sesi masuk dan setelan di HP,
dan kamu tinggal mengaturnya sekali lagi setelah memasang versi baru.

**Kenapa kuncinya diganti?**
Penggantian ini disengaja. Saat kami membangun ulang XyDesk-Remote dengan
pondasi baru di versi 2.0.0, kami sekaligus mengganti kunci penandatanganan
aplikasinya. Konsekuensinya memang satu: pengguna rilisan lama harus memasang
ulang dari nol sekali ini saja.

**Saya melihat "paket tidak valid" — apa karena tanda tangan juga?**
Bukan. "Paket tidak valid" biasanya soal lain: berkas APK yang terunduh rusak
atau belum lengkap, penyimpanan HP hampir penuh, atau arsitektur HP tidak
sesuai dengan paket yang dipilih (HP 32-bit harus memakai paket 32-bit).
Gejala tanda tangan beda berbeda: Android menolak dengan pesan "aplikasi tidak
terpasang" dan tidak ada opsi "timpa".
