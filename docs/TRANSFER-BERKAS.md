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

**Potongan yang dikirim 16 KiB, bukan 64 KiB.** Batas protokol tetap 64 KiB
supaya pengirim lama tetap diterima, tetapi satu pesan SCTP dibatasi 64 KiB
*termasuk* 9 byte header `CHUNK` — potongan 65536 byte menghasilkan pesan
65545 byte yang **tidak pernah berangkat**, dan pengiriman menggantung tanpa
satu pun pesan kesalahan. Ini ditemukan oleh uji loopback arah host → client;
tidak ada uji unit di dua bahasa yang bisa melihatnya, karena keduanya benar
menurut protokolnya sendiri.

## 2. Urutan

```
host                                   client (HP)
  |  ACK id=0 (tanda siap)  ───────────────>|
  |<───────────────────────  ACK id=0 (balasan siap)
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
terpasang. Sejak arah PC → HP ada, **tanda siap itu dua arah**: aplikasi
membalas tanda siap host dengan tanda siapnya sendiri, dan host baru
menawarkan berkas setelah menerimanya. Tanpa balasan itu, celah yang sama
muncul dalam arah sebaliknya — dan memang muncul: percobaan pertama uji
loopback arah baru menggantung persis karena ini.

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

## 3b. Host sebagai pengirim (PC → HP)

- `filetransfer::Sender` — mesin keadaan pengirim, kembaran `FileSender` di
  Kotlin: tanda siap wajib ditunggu, jendela ACK 512 KiB, kemajuan dari ACK,
  penolakan potongan yang menyimpang.
- `file_dispatch::queue_outgoing(path)` — antrean proses-global yang dipakai
  control API. Ia memeriksa **sebelum** menjawab: ada sesi hidup, path adalah
  berkas, tidak kosong, tidak melebihi 4 GiB. Channel milik sesi yang sudah
  mati ikut diperiksa (`ready_state`), karena menitipkan berkas ke sana akan
  "berhasil" tanpa satu byte pun berangkat.
- Control API: `POST /action {"action":"file-send","path":"C:\\...\\a.pdf"}`.
  Jawaban `ok:false` selalu membawa alasan yang bisa ditampilkan apa adanya.
- Satu kiriman dalam satu waktu; yang kedua ditolak, bukan diantrekan.
- Bila perangkat tidak pernah mengirim tanda siap dalam 15 detik, kiriman
  dibatalkan dengan catatan di log — menggantung diam-diam lebih buruk.

## 3c. Aplikasi sebagai penerima (PC → HP)

- `xyadapt/FileReceive.kt` — `FileReceiver`, cermin `Receiver` di Rust,
  dengan 17 uji JVM.
- `SessionFile` menulis ke berkas sementara di cache aplikasi dan baru
  memindahkannya ke **Unduhan/XyDesk** lewat MediaStore (`IS_PENDING=1`
  sampai isinya lengkap) setelah SHA-256 cocok.
- **Selalu bertanya.** Setiap tawaran dari PC memunculkan dialog berisi nama,
  ukuran, dan peringatan bila ekstensinya langsung dijalankan sistem. PC yang
  memilih nama dan isinya, sementara yang terisi adalah penyimpanan pribadi
  pemilik HP — menerima diam-diam berarti pemilik HP tidak pernah punya
  kesempatan berkata tidak.

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
| Pengirim host (Rust) | 10 uji `Sender`, termasuk pengirim ↔ penerima saling bicara tanpa jaringan |
| Penerima aplikasi (Kotlin) | 17 uji `FileReceiveTest`, termasuk `FileSender` ↔ `FileReceiver` saling bicara |
| Arah PC → HP di sesi nyata | `host_mengirim_berkas_ke_client` — 300 KB lewat WebRTC nyata, urutan diperiksa, SHA-256 dicocokkan |
| Control API | `aksi_file_send_menolak_dengan_alasan_yang_jelas` — tanpa path, path tidak ada, berkas kosong, tanpa sesi |
| Lintas bahasa | `vektor_dari_pengirim_kotlin_terbaca_sama` — byte yang dihasilkan Kotlin ditempel apa adanya di uji Rust |

Uji lintas bahasa itu ada karena dua sisi menulis pengkodeannya
sendiri-sendiri: satu sisi yang keliru urutan byte tidak akan ketahuan oleh
uji mana pun di satu bahasa saja — yang terlihat hanyalah transfer yang gagal
di perangkat pengguna.

## 5b. Tombol di panel host

Halaman **Koneksi** panel native menggambar "Kirim berkas…" di sebelah
"Putus sesi". Tombolnya hanya hidup bila host berjalan **dan** ada sesi
aktif: tanpa perangkat tersambung tidak ada tujuan kiriman, dan tombol yang
selalu gagal lebih buruk daripada tombol mati.

Klik membuka dialog "Buka" bawaan Windows (`GetOpenFileNameW`, dengan
`OFN_NOCHANGEDIR` supaya direktori kerja proses host tidak ikut bergeser),
lalu path-nya dititipkan ke control API di thread terpisah — `action()`
bersifat sinkron, dan memanggilnya di thread UI akan membekukan panel.

Panel hanya menunggu jawaban **"kiriman diterima antrean"**, bukan "berkas
sampai": perjalanan berkas besar bisa menit-menit. Jawaban `{"ok":false}`
ditampilkan apa adanya dari field `error` host, karena host sudah menulisnya
sebagai kalimat untuk manusia dan menerjemahkannya ulang di panel hanya
membuat dua sumber kebenaran.

Yang diuji di Linux (`packaging/tests/native-panel-layout-test.cpp`) hanya
yang murni angka: tombol tidak menimpa "Putus sesi", tidak keluar dari kartu
kerja pada 4 DPI × 3 lebar × 2 keadaan sidebar, dan hit-test-nya hanya
mengembalikan `SendFile` di halaman Koneksi. Sintaks `main.cpp` disaring
MinGW `-fsyntax-only`; kompilasi penuhnya tetap job Windows CI.

## 5c. Pintu persetujuan di PC

Arah HP → PC dulu diterima otomatis: client yang lolos pairing boleh menulis
ke `Downloads\XyDesk` tanpa pemilik PC tahu. Pairing menjawab "siapa yang
boleh menyambung", bukan "apa yang boleh ia tulis ke disk saya".

Kebijakan (`crate::file_consent`), bawaan di tengah:

| Nilai | Arti |
|---|---|
| `always` | terima otomatis (perilaku lama) |
| **`ask`** | tanya pemilik PC **bila ada yang bisa menjawab** |
| `never` | tolak semua berkas masuk |

Diubah lewat `POST /action {"action":"file-policy","value":"ask"}`.

**"Bila ada yang bisa menjawab".** Dialognya digambar panel host, dan panel
tidak selalu berjalan. Pertanyaan yang tidak dilihat siapa pun bukan
perlindungan — ia hanya membuat setiap transfer gagal setelah satu menit
hening. Host menganggap ada yang menonton bila panel memanggil `/status`
dalam 10 detik terakhir; bila tidak, `ask` berperilaku seperti `always`
**dan menulisnya ke log**. Yang menginginkan penolakan tanpa syarat memakai
`never`.

**Diam bukan izin.** Tawaran yang tidak dijawab dalam 60 detik ditolak
(`Reason::User`). Dialog yang berubah menjadi izin karena ditinggal makan
siang adalah pintu yang hanya tampak seperti pintu.

**Belum satu byte pun menyentuh disk selama menunggu.** Berkas sementara
(`.xypart`) baru dibuat setelah jawabannya "ya", jadi tawaran yang ditolak
tidak meninggalkan jejak. Potongan yang datang sebelum persetujuan dijawab
`CANCEL Protocol`.

Panel menampilkan nama, ukuran, peringatan bila ekstensinya langsung
dijalankan Windows, dan sisa waktu; jawabannya dikirim lewat
`POST /action {"action":"file-consent","id":42,"allow":true}`. Tawaran yang
sudah kedaluwarsa dijawab apa adanya ("sudah tidak menunggu jawaban"), bukan
"gagal" — jawaban yang terlambat satu detik tidak boleh menyetujui tawaran
berikutnya.

## 6. Yang belum ada

- **Konfirmasi sukses.** Host diam bila berkas tersimpan; pengirim
  menyimpulkan berhasil dari ketiadaan `CANCEL`. Pesan `DONE-OK` eksplisit
  akan lebih jujur.
- **Lanjut setelah putus.** Transfer yang terputus diulang dari nol.
- **Beberapa berkas sekaligus.** Satu transfer dalam satu waktu; yang kedua
  ditolak, bukan diantrekan.
- **Belum pernah dijalankan di perangkat Android sungguhan** — yang terbukti
  adalah protokolnya, kedua mesin keadaan, dan loopback dua arah di host.
  Penulisan lewat MediaStore khususnya belum pernah menyentuh penyimpanan
  Android betulan.
