# Bahan Artikel Rilis — 6.12.0

**Tanggal:** 31 Okt 2026
**Penulis:** Haekal Saputra
**Kategori:** rilis
**Slug WAJIB:** `changelog-v6-12-0`
**Cover:** `https://app.xydesk.my.id/news/covers/changelog-6120.jpg`
(sumber: `web/public/news/covers/changelog-6120.jpg` — deploy web dulu supaya URL-nya hidup)

---

## Judul (tanpa nomor versi)

Kini Bisa Sambung di Jaringan Mana Pun: XyDesk Jalan Tanpa P2P

## Excerpt

Koneksi ke PC tidak lagi bergantung pada jalur langsung yang sering diblokir jaringan. Semua media kini bisa dialirkan lewat server relay — dan kalau tetap gagal, sebabnya kini terbaca jelas, bukan menghilang tanpa kabar.

## Isi

### Apa yang berubah

XyDesk untuk Android kini punya **Mode Tanpa P2P**: semua gambar dan suara dari PC kamu dialirkan lewat server relay kami, tanpa mencoba koneksi langsung antar perangkat. Mode ini kini menjadi bawaan — kamu bisa menemukan saklarnya di **Pengaturan > Koneksi** dan mematikannya kapan saja bila ingin mencoba koneksi langsung yang lebih hemat.

Selain itu, aplikasi kini tidak langsung menyerah saat koneksi gagal: ia akan mencoba menyambung ulang dua kali dengan jalur yang berbeda sebelum berhenti. Dan yang paling penting — bila koneksi tetap gagal, pesannya kini menyebut **kenapa** gagal, bukan sekadar "gagal ICE".

### Kenapa diubah

Banyak jaringan hari ini memblokir jalur koneksi langsung antar perangkat: VM cloud gaming, WiFi kantor, hotel, sampai jaringan seluler tertentu. Selama ini pengguna di jaringan-jaringan itu hanya melihat "gagal ICE" tanpa penjelasan, padahal masalahnya bukan di aplikasi — jalur langsungnya memang ditutup oleh jaringan.

Kami juga menemukan bahwa saat server relay tidak tersedia, aplikasi diam-diam tetap mencoba tanpa relay lalu gagal tanpa pesan. Praktik itu kami hapus: sekarang setiap kegagalan punya alasan yang terbaca.

### Apa dampaknya

- **Tidak perlu melakukan apa pun.** Mode Tanpa P2P sudah aktif secara bawaan; cukup perbarui aplikasi.
- Koneksi ke PC di jaringan yang dulu selalu gagal (misalnya VM cloud gaming) kini seharusnya langsung tersambung.
- Karena media lewat server relay, latensi bisa sedikit lebih tinggi dan pemakaian kuota server relay naik. Bila ingin hemat, matikan Mode Tanpa P2P untuk mencoba koneksi langsung.
- IP HP kamu tidak lagi terlihat oleh PC yang kamu kendalikan — sedikit lebih privat.

### Semua perubahan di versi 6.12.0

- Mode Tanpa P2P (semua lewat server relay) menjadi bawaan, dengan saklar di Pengaturan > Koneksi.
- Aplikasi mencoba menyambung ulang dua kali (kandidat baru, lalu memaksa relay) sebelum menyatakan gagal.
- Pesan kegagalan kini menyebut penyebabnya: koneksi langsung tidak ditembus, atau server relay sedang tidak tersedia — lengkap dengan petunjuk singkat dari server.
- Bila Mode Tanpa P2P aktif tetapi server relay sedang tidak siap, aplikasi langsung memberi tahu alasannya, tanpa menunggu lama.

### Yang masih belum

- Mode relay tetap bergantung pada ketersediaan server relay. Jaringan yang memblokir koneksi ke server relay pun masih butuh penanganan lanjutan (jalur TCP/TLS) — sedang kami siapkan.
- Pengujian lapangan berbagai provider cloud masih berlanjut; hasilnya akan kami bagikan di berita berikutnya.

*(Catatan untuk penerbit: lampirkan screenshot asli dari build 6.12.0 di HP — saklar "Mode Tanpa P2P" di Pengaturan > Koneksi — sesuai aturan NEWS_STYLE §3 sebelum artikel ini terbit.)*

---

## Perintah terbit (butuh ADMIN_TOKEN)

```bash
curl -X POST "https://news.xydesk.my.id/api/admin/publish" \
  -H "Content-Type: application/json" \
  -H "x-admin-token: <ADMIN_TOKEN>" \
  -d @artikel-6.12.0.json
```

Isi `artikel-6.12.0.json`:

```json
{
  "title": "Kini Bisa Sambung di Jaringan Mana Pun: XyDesk Jalan Tanpa P2P",
  "excerpt": "Koneksi ke PC tidak lagi bergantung pada jalur langsung yang sering diblokir jaringan. Semua media kini bisa dialirkan lewat server relay — dan kalau tetap gagal, sebabnya kini terbaca jelas.",
  "content": "<paragraf isi di atas, dipisah baris kosong>",
  "cover": "https://app.xydesk.my.id/news/covers/changelog-6120.jpg",
  "category": "rilis",
  "author": "Haekal Saputra",
  "slug": "changelog-v6-12-0"
}
```
