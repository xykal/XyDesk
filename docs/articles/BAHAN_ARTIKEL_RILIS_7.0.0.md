# Bahan Artikel — Rilis XyDesk 7.0.0 (Splash Baru, Chat Global Dihapus)

Status bahan: siap edit. Pola sama dengan `BAHAN_ARTIKEL_RILIS_6.12.0.md`.
Rilis: https://github.com/xykal/XyDesk/releases/tag/v7.0.0 (terbit 1 November 2026).

## Ringkasan Perubahan

| Aspek | Versi 6.12.x | Versi 7.0.0 |
|---|---|---|
| Tampilan awal | Splash logo lama | Splash screen baru |
| Chat global | Ada di aplikasi | Dihapus total — aplikasi fokus remote desktop |
| Koneksi host | Bisa "hang" saat jaringan putus-sambung | Otomatis pulih: koneksi baru menggantikan yang lama, sesi tidak mati mendadak |
| Jendela kontrol Windows | Bug susulan saat ganti sesi | Masih dalam pengerjaan (rute di HANDOFF) |

## Narasi (Draft)

XyDesk 7.0.0 hadir dengan tampilan pembuka (splash screen) baru dan satu keputusan
besar: **chat global dihapus dari aplikasi**. Versi ini mempertajam fokus XyDesk
sebagai aplikasi remote desktop — bukan aplikasi sosial.

Di balik layar, versi ini memperbaiki bug yang paling menyebalkan belakangan ini:
host yang mendadak "hang" atau langsung mati saat jaringan putus-sambung. Penyebabnya
dua: (1) koneksi lama yang masih tercatat di server menolak koneksi baru dengan ID
yang sama, dan (2) koneksi yang tertutup langsung mematikan sesi tanpa sempat
mencoba ulang. Keduanya sudah diperbaiki: koneksi terbaru selalu menang, dan aplikasi
mencoba menyambung ulang sampai 3 kali sebelum menyerah. Tanda tangannya: koneksi
baru langsung "mengambil alih" sesi, jadi tidak ada lagi drama "host sibuk" karena
zombie koneksi.

## FAQ

**Kenapa chat global dihapus?**
Agar XyDesk fokus sebagai alat remote desktop. Tidak ada lagi gangguan chat yang
tidak berkaitan dengan sesi remote kamu.

**Apakah perangkat saya perlu di-update juga?**
Disarankan. APK baru tersedia untuk ARM64 dan ARMv7 di halaman rilis. Host Windows
juga ikut diperbarui.

**Sesi saya masih bisa putus?**
Putus sesaat karena jaringan tetap bisa terjadi, tapi sekarang pulihnya otomatis —
tidak perlu restart aplikasi atau host.

## Kontak & Sumber

- Halaman rilis: https://github.com/xykal/XyDesk/releases/tag/v7.0.0
- Unduhan APK & installer Windows: aset rilis + SHA256SUMS.txt
- Kanal berita: XyFeed (di aplikasi)

## Catatan Admin

- Token `NEWS_ADMIN_TOKEN` (env `admin_token_news`) belum ada di `.env` → publikasi
  otomatis via `/api/news/admin` masih terkunci; unggah manual lewat GitHub.
- Belum ada screenshot/aset visual rilis 7.0.0 — lampirkan sebelum tayang.
