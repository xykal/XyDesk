# Gerbang kehadiran: siapa boleh masuk saat tidak ada siapa-siapa

Host menjawab `pair` dengan satu pertanyaan sejak rilis pertama: apakah
passwordnya benar. Itu jawaban yang tepat untuk **siapa** yang menyambung,
dan jawaban yang tidak pernah diminta untuk pertanyaan kedua: **apakah
sekarang waktunya**.

Keduanya tidak sama. Password yang pernah dibagikan ke rekan kerja untuk satu
sore tetap berlaku pukul tiga pagi. Laptop yang menyala di rumah bisa
disambungkan oleh siapa pun yang pernah menyalin passwordnya, dan satu-satunya
jejaknya ada di log yang tidak dibaca siapa pun.

Aturan dan uji: `host/src/unattended.rs`. Penyambungan: handler `pair` di
`host/src/main.rs`.

## 1. Tiga kebijakan

| Nilai | Arti | Kapan dipakai |
| --- | --- | --- |
| `always` | Terima kapan saja. **Bawaan.** | PC di rumah sendiri; perilaku sejak rilis pertama. |
| `watched` | Hanya saat panel host terbuka, atau saat izin sementara berlaku. | Laptop kerja, PC bersama. |
| `off` | Tolak semua koneksi masuk. | Sedang tidak ingin diganggu sama sekali. |

Disimpan sebagai satu kata di `~/.xydesk/unattended`, supaya bisa dibaca dan
diperbaiki dengan Notepad saat panelnya justru tidak bisa dibuka. Isi yang
tidak dikenal jatuh ke `always` — kebijakan yang tidak terbaca tidak boleh
mengunci pemiliknya di luar PC-nya sendiri.

**Bawaannya sengaja yang paling longgar.** Bawaan yang lebih ketat akan
membuat setiap PC yang sudah terpasang mendadak tidak bisa disambungkan
setelah pembaruan — justru dari jauh, justru saat pemiliknya tidak ada di sana
untuk membetulkan. Pembaruan keamanan yang mengunci pemiliknya di luar
rumahnya sendiri tidak akan dipercaya untuk pembaruan berikutnya.

## 2. "Ada orang di depan PC"

Dinilai sama seperti pintu persetujuan berkas: panel memanggil `/status`
sekitar sekali per detik, jadi panggilan terakhir yang lebih baru dari **10
detik** berarti panel sedang terbuka.

Yang dijanjikan hanya sebatas itu, dan tidak lebih: **kehadiran panel, bukan
kehadiran orang.** Panel yang ditinggal terbuka tetap dianggap ditunggui.
Alternatif yang lebih kuat adalah membaca status kunci-layar Windows, yang
tidak tersedia di semua konfigurasi dan akan membuat kebijakan ini gagal
diam-diam di sebagian PC — lebih buruk daripada janji kecil yang jujur.

## 3. Izin sementara: untuk pergi, bukan untuk selamanya

Kasus nyatanya hampir selalu "saya akan keluar, nanti saya butuh masuk dari
HP". Karena itu `watched` punya katup: satu tombol sebelum pergi, akses
terbuka sampai batas waktu, maksimal **24 jam**.

```
POST /action {"action":"unattended-grant","hours":8}   # beri izin 8 jam
POST /action {"action":"unattended-grant","hours":0}   # cabut sekarang
```

Yang disimpan di `~/.xydesk/unattended-until` adalah **waktu berakhir** (unix
ms), bukan sisa durasi — host yang direstart tidak boleh memperpanjang izin
sendiri dengan memulai hitungan dari nol. Konsekuensinya izin bergantung pada
jam dinding yang bisa dimundurkan; itu diterima dengan sadar, karena yang bisa
memundurkan jam PC sudah memegang PC-nya. Yang tetap dijaga: izin yang
berakhir lebih dari 24 jam di masa depan (berkas rusak, disunting tangan, jam
melompat) dianggap **kedaluwarsa**, bukan dipercaya.

`off` tidak bisa dikalahkan izin sementara maupun panel yang terbuka. "Tolak
semua" yang punya pengecualian bukan "tolak semua".

## 4. Penolakan tidak menghukum pemiliknya

Koneksi yang ditolak gerbang ini **bukan** tebakan password yang gagal, jadi
ia tidak dihitung `pairguard`. Kalau dihitung, pemilik yang mencoba masuk tiga
kali ke PC-nya sendiri yang sedang terkunci akan terkena lockout lima menit —
dihukum karena kebijakan yang ia pasang sendiri bekerja.

Ke client, penolakan terlihat **persis sama** dengan password yang salah:
jawaban `accepted:false` yang sama, setelah penundaan tetap yang sama.
Membedakannya akan memberi tahu penebak bahwa ia menemukan PC nyata dengan
password benar, dan hanya perlu menunggu waktu yang tepat.

Token "ingat perangkat ini" tidak diterbitkan untuk koneksi yang ditolak di
sini. Token itu melewati password pada percobaan berikutnya; menerbitkannya
sama dengan membatalkan kebijakan yang baru saja menolaknya.

## 5. Panel

Halaman **Koneksi**, baris keempat:

- Tombol putar `Akses: kapan saja → hanya saat saya ada → tolak semua`. Nilai
  berikutnya dihitung dari yang dilaporkan host, bukan tebakan panel, supaya
  dua panel yang terbuka tidak saling menimpa.
- `Izinkan 8 jam tanpa saya` — berubah menjadi `Cabut izin (N jam)` selama
  izin berjalan. Hanya hidup pada kebijakan `watched`: pada `always` ia tidak
  menambah apa pun, dan pada `off` ia memang tidak boleh menambah apa pun.

Host lama tidak melaporkan `unattendedPolicy` sama sekali; panel membaca itu
sebagai "fitur tidak ada" dan mematikan tombolnya — bukan sebagai "akses
dimatikan".

## 6. Yang belum dikerjakan

- **PC di layar kunci sebelum ada yang login.** Host berjalan dari entri `Run`
  milik pengguna, jadi ia baru hidup setelah ada yang masuk ke Windows. PC
  yang baru di-restart dan berhenti di layar masuk belum bisa disambungkan
  sama sekali — itu butuh Windows service di sesi 0, bukan kebijakan.
- **Layar kunci Windows sebagai sinyal kehadiran.** Lihat bagian 2.
- **Riwayat siapa masuk kapan.** Log host mencatatnya baris per baris; belum
  ada daftar yang bisa dibaca di panel.
