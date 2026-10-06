# Transfer berkas HP → PC

Channel data terpisah bernama `file`, di luar channel `input`. Satu berkas
200 MB berarti ribuan pesan; kalau ikut mengantre di jalur input, setiap klik
mouse tertahan di belakang berkas.

## 1. Pesan

Semua bilangan little-endian.

```
0x01 OFFER  id:u32  size:u64  name_len:u16  name:utf8
0x02 ACCEPT id:u32
0x03 REJECT id:u32  reason:u8
0x04 CHUNK  id:u32  seq:u32  bytes (sisa pesan)
0x05 DONE   id:u32  sha256:32 byte
0x06 CANCEL id:u32  reason:u8
0x07 ACK    id:u32  received:u64
```

Kode alasan: `0` pengguna, `1` terlalu besar, `2` pelanggaran protokol,
`3` hash tidak cocok, `4` gagal tulis, `5` sesi putus. Kode yang tidak
dikenal diperlakukan sebagai pelanggaran protokol — bukan diam-diam
dianggap "dibatalkan pengguna".

Batas: satu berkas ≤ 4 GiB, satu potongan ≤ 64 KiB, nama ≤ 120 karakter.

## 2. Urutan

```
host                                   client (HP)
  |  ACK id=0 (tanda siap)  ───────────────>|
  |<──────────────────────────  OFFER id,size,name
  |  ACCEPT id  ───────────────────────────>|
  |<──────────────────────────  CHUNK id,seq=0
  |  ACK id,received  ─────────────────────>|
  |                    … berulang …          |
  |<──────────────────────────  DONE id,sha256
  |  (diam = tersimpan; CANCEL = gagal)     |
```

**Tanda siap wajib ditunggu.** Channel sudah `OPEN` di sisi client beberapa
saat sebelum host sempat memasang pendengarnya; `OFFER` yang tiba di celah
itu hilang tanpa jejak dan transfer menggantung tanpa satu pun pesan
kesalahan. Celah ini pernah benar-benar terjadi dan ditangkap oleh uji
loopback pada percobaan pertama. Host karena itu mengirim `ACK` dengan id 0 —
id yang tidak pernah dipakai transfer sungguhan — setelah pendengarnya
terpasang.

## 3. Sisi host (penerima)

- `host/src/filetransfer.rs` — protokol dan mesin keadaan penerima. Tidak
  pernah menyentuh disk.
- `host/src/filesink.rs` — menulis; tidak pernah memutuskan apa pun.
- `host/src/file_dispatch.rs` — penyambungnya, satu-satunya bagian yang
  berbicara ke jaringan.

Penerima menolak: potongan sebelum `ACCEPT`, nomor urut yang melompat atau
terulang, potongan kosong atau di atas 64 KiB, total byte melebihi yang
dijanjikan, `DONE` sebelum semua byte tiba, `OFFER` kedua dengan id yang
sama, dan pesan yang menyebut id transfer lain.

Isi ditulis ke `.xypart` dan baru dipindahkan ke nama aslinya setelah SHA-256
cocok — transfer yang putus meninggalkan sampah yang jelas sampahnya, bukan
`laporan.pdf` rusak yang dibuka pengguna besok pagi. Tujuan:
`Downloads\XyDesk`. Nama yang bertabrakan dinomori `laporan (2).pdf`.

Persetujuan masih **otomatis** untuk client yang lolos pairing; kebijakannya
terkumpul di `decide_offer` supaya menjadikannya "tanya pengguna" nanti tidak
perlu menyentuh alur jaringan.

## 4. Sisi aplikasi (pengirim)

- `xyadapt/FileRules.kt` — pembersihan nama, batas, kemajuan, ETA. Cerminan
  aturan host, dengan contoh uji yang identik: dua sisi yang berbeda
  jawabannya untuk nama yang sama berarti pengguna menyetujui nama yang bukan
  nama yang ditulis ke disk.
- `xyadapt/FileWire.kt` — pengkodean pesan + `FileSender`, mesin keadaan
  pengirim. Murni, tanpa I/O.
- `app/ui/SessionFile.kt` — `ContentResolver`, SHA-256, utas pengirim.
- `app/ui/FileProgressView.kt` — satu kapsul kemajuan di atas video.

Dua rem dipasang agar berkas besar tidak memecahkan memori:

1. **Jendela ACK 512 KiB** di `FileSender` — byte yang benar-benar sampai di
   disk PC, bukan yang keluar dari HP.
2. **Antrean channel 1 MiB** di `SessionFile` — byte yang belum keluar dari
   HP sama sekali.

Kemajuan yang ditampilkan ke pengguna dihitung dari **ACK**. Angka yang
melompat ke 100% begitu berkas selesai dibaca dari penyimpanan HP akan
berbohong tentang sesuatu yang belum terjadi.

Nama dibersihkan sebelum ditawarkan: `..\..\Windows\System32\drivers\etc\hosts`
menjadi `hosts`.

## 5. Yang diuji

| Lapisan | Uji |
|---|---|
| Protokol + penerima (Rust) | 28 uji `filetransfer` |
| Pengirim + pengkodean (Kotlin) | 21 uji `FileWireTest` + `FileSenderTest` |
| Aturan nama/batas (Kotlin) | 12 uji `FileRulesTest` |
| Dua sisi bersama | `host/tests/file_transfer_loopback.rs` — 300 KB lewat WebRTC nyata, byte-per-byte sama; hash yang tidak cocok → folder tujuan kosong |
| Lintas bahasa | `vektor_dari_pengirim_kotlin_terbaca_sama` — byte yang dihasilkan Kotlin ditempel apa adanya di uji Rust |

Uji lintas bahasa itu ada karena dua sisi menulis pengkodeannya
sendiri-sendiri: satu sisi yang keliru urutan byte tidak akan ketahuan oleh
uji mana pun di satu bahasa saja — yang terlihat hanyalah transfer yang gagal
di perangkat pengguna.

## 6. Yang belum ada

- **Arah PC → HP.** Host belum punya pengirim dan aplikasi belum punya
  penerima.
- **Pintu persetujuan di PC.** Host menerima otomatis dari client yang sudah
  lolos pairing.
- **Konfirmasi sukses.** Host diam bila berkas tersimpan; pengirim
  menyimpulkan berhasil dari ketiadaan `CANCEL`. Pesan `DONE-OK` eksplisit
  akan lebih jujur.
- **Lanjut setelah putus.** Transfer yang terputus diulang dari nol.
- **Beberapa berkas sekaligus.** Satu transfer dalam satu waktu; yang kedua
  ditolak, bukan diantrekan.
- **Belum pernah dijalankan di perangkat Android sungguhan** — yang terbukti
  adalah protokolnya, mesin keadaannya, dan loopback dua sisi di host.
