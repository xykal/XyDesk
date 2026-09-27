# Tes manual web + host — perangkat pemilik

Tujuan: membuktikan gambar, kontrol, audio, dan pemulihan sesi pada Windows nyata tanpa menahan runner Actions. Build/kompilasi tetap melalui CI setelah izin; menjalankan aplikasi hasil build di PC sendiri tidak memerlukan Rust/Visual Studio lokal.

**Status dokumen:** rencana tes, bukan hasil PASS. Semua baris mulai dari BELUM DIUJI.

## 1. Persiapan aman

1. Gunakan PC/VPS milik sendiri yang boleh diuji, bukan runner GitHub interaktif. Mulai dari akun uji/desktop non-sensitif; tutup dokumen pribadi dan aplikasi transaksi.
2. Pakai artefak CI yang jelas run ID + commit SHA-nya. Catat SHA256 paket. Jangan campur EXE lama dan engine baru. Nomor versi saja tidak cukup karena beberapa build dapat memakai versi sama.
3. Untuk web, catat URL dan identitas bundle/commit deployment. Jika memerlukan build-only atau deployment staging, minta secara terpisah; Build umum dapat memicu deploy/rilis lewat workflow turunan.
4. Pertahankan akses pemulihan independen ke VPS. Jangan logoff, mengganti driver, memakai `tscon`, atau memutus RDP satu-satunya tanpa jalur kembali.
5. Jangan memaksa capture lock screen/UAC/secure desktop. Hasil yang benar dapat berupa penolakan/pause dengan diagnosis yang jelas.
6. Jangan mengirim `host.log` mentah: source saat audit mencetak token control lokal. Sensor password pairing, `token=...`, header Authorization, host-refresh, resume grants, dan kredensial TURN. HAR/SDP dapat mengandung token/IP; sensor juga.
7. Untuk stress disconnect/audio, tunggu WH-01/03/07 diperbaiki atau perlakukan sebagai reproduksi bug, bukan tes kelulusan rilis.

## 2. Identitas satu run

Salin blok berikut saat melaporkan hasil:

```text
Run manual:
Tanggal/jam + zona waktu:
Commit source host:
Run CI / nama artefak:
SHA256 paket:
Web URL + commit/bundle:
Windows edition/build:
PC fisik / VM / VPS:
CPU / RAM / GPU / driver:
Console / RDP, user dan session ID (sensor nama pribadi):
Resolusi + scaling + jumlah monitor:
Browser/perangkat + versi:
Jaringan host/client: LAN / Wi-Fi / seluler / lainnya:
Jalur WebRTC aktual: direct-p2p / turn-relay / belum diketahui:
Perangkat audio output + mic:
```

Opsional, hitung hash paket di PowerShell:

```powershell
Get-FileHash -Algorithm SHA256 -LiteralPath 'C:\path\ke\paket-yang-diuji.zip'
```

Ganti path dengan file unduhan sendiri. Perintah itu hanya membaca file, bukan menjalankan installer.

## 3. Matriks minimum

| Lingkungan | Wajib sekarang | Catatan |
|---|---|---|
| Windows desktop console + browser Chrome/Edge desktop | Ya jika mesin tersedia | Baseline gambar/input tanpa RDP |
| VPS/VM RDP milik pemilik + web | Ya untuk klaim dukungan RDP | Uji di sesi RDP yang benar, jangan menyamakan dengan console |
| Browser Android Chrome, landscape/portrait | Ya untuk web mobile | Bukan APK Flutter |
| Firefox desktop | Sebelum klaim dukungan Firefox | Codec, audio, fullscreen dan input |
| Safari/iOS | Sebelum klaim dukungan Safari/iOS | Izin audio/mic dan fullscreen berbeda |
| Hardware GPU dan software fallback | Sebelum klaim kedua jalur matang | Verifikasi label encoder yang benar, bukan asumsi merek GPU |
| Direct LAN dan relay TURN nyata | Ya sebelum rilis remote internet | Berbeda jaringan tidak otomatis berarti TURN; baca candidate stats |

Jika lingkungan tidak tersedia, tulis **BELUM DIUJI**, bukan PASS atau “seharusnya bisa”.

## 4. Urutan tes dan kriteria lulus

Target waktu di bawah adalah acceptance yang diusulkan, bukan benchmark yang sudah tercapai. Bila produk memilih batas lain, tetapkan sebelum menjalankan tes.

| ID | Langkah | Kriteria lulus | Bukti |
|---|---|---|---|
| M01 | Install paket uji, buka panel, jalankan host | EXE/panel/engine cocok; identitas konsisten; status membedakan proses hidup dan ready | SHA paket, screenshot panel tanpa password |
| M02 | Pair password salah lalu benar | Penolakan jelas; tombol aktif kembali; sesi salah tidak membuka kontrol | Waktu dan pesan, tanpa password |
| M03 | Sambung saat desktop diam, lalu gerakkan jendela | Frame pertama muncul; gerakan benar-benar berubah; tidak hanya status connected | Rekaman 15–30 detik, framesDecoded |
| M04 | Pakai wallpaper hitam/adegan gelap, lalu buka jendela putih | Tidak ada panic/putus akibat transisi GDI; peringatan tidak disamakan dengan bukti lock | Backend capture, log tersensor |
| M05 | RDP aktif, lock/unlock, reconnect RDP dengan jalur cadangan tersedia | Tidak mengambil layar user lain; secure desktop dihormati; pulih atau memberi langkah yang benar | Session ID tersensor, waktu pemulihan |
| M06 | Dua instance satu sesi; console/RDP akun sama; lalu dua akun Windows terpisah jika tersedia | Satu leader per profil akun; standby dapat promosi akun sama; akun lain tidak berbagi identitas/layer layar atau merebut resource | Daftar proses/session, status |
| M07 | Ganti monitor/resolusi/scaling, termasuk monitor dengan koordinat negatif | Seluruh desktop sesuai kebijakan; input tetap presisi; metadata tidak stale | Screenshot grid target + resolusi |
| M08 | Klik target kecil di sudut/tengah, drag, scroll, keyboard fisik/virtual | Klik tidak bergeser setelah resize; tidak ada tombol tertahan saat blur/disconnect | Video target grid + tombol yang diuji |
| M09 | Clipboard kecil, Unicode/emoji, dan ukuran sekitar batas 64 KiB | Hasil sama atau penolakan eksplisit; tidak hilang diam-diam; UTF-8 tidak rusak | Panjang byte dan checksum teks non-sensitif |
| M10 | Putar audio host, mute/unmute browser, izinkan playback lewat gesture | Audio terdengar di endpoint benar; izin ditolak diberi pesan; tidak double-play | Output device dan rekaman bila aman |
| M11 | Aktifkan mic browser setelah sesi berjalan >30 detik; disable/re-enable | Mic bekerja bila endpoint tersedia; indikator/track berhenti setelah disable/stop | Izin browser, indikator mic, endpoint Windows |
| M12 | Host tanpa perangkat audio, lalu hotplug bila didukung | Video/input tetap hidup; audio dilaporkan unavailable, tidak mengklaim aktif palsu | Status audio + hasil aktual |
| M13 | Putus jaringan singkat lalu pulihkan; jangan kehilangan akses VPS cadangan | Sesi pulih atau timeout actionable; satu attempt aktif; tidak ada sesi tersembunyi | Waktu, phase, jumlah koneksi |
| M14 | Saat auto-retry dijadwalkan, klik Coba lagi sebelum timer habis | Tidak ada attempt kedua dari timer lama; hanya satu peer/session | Screen recording + log frontend tersensor |
| M15 | Request token dibuat pending/offline, lalu Cancel dan konek lagi | Cancel responsif; deadline fase awal berlaku; respons lama tidak membuka sesi | Durasi, status UI |
| M16 | 30 connect/disconnect: sebagian audio diam, sebagian bersuara | Thread/handle/memori tidak naik terus; setelah idle kembali mendekati baseline | Task Manager/Process Explorer milik pemilik, tabel sampel |
| M17 | Sambung lewat jalur relay yang dikonfirmasi candidate stats | Gambar+input+audio berfungsi; panel menampilkan turn-relay; failure relay dibedakan | Candidate type/protocol tanpa kredensial |
| M18 | Fullscreen, rotate HP, buka/tutup keyboard/panel; Back dan route history | Tidak terjebak overlay; focus masuk/keluar dialog; sesi berhenti saat diminta | Video desktop + mobile |
| M19 | Logout/ganti akun saat `/auth/me` masih pending; simulasi offline/5xx di lingkungan uji | Akun baru tidak dihapus oleh respons lama; transient outage bukan logout otomatis | Timeline request tersensor |
| M20 | Simpan history/wallpaper; hapus perangkat; cek akses tersimpan | Wallpaper bukan frame aplikasi sensitif; perilaku hapus history vs cabut grant dijelaskan | Screenshot data uji tanpa token |
| M21 | Buka panel saat engine fixture gagal/macet, restart dan baca telemetry lama | Panel tidak freeze; status stale/error tidak tampil sebagai capture sehat | Respons UI, durasi, exit code |
| M22 | Streaming aktif 30 menit dengan video bergerak + input berkala | Tidak crash/freeze progresif; CPU/RAM/audio tetap stabil; error bisa didiagnosis | Sampel menit 0/5/15/30 |

M15/M19 yang memerlukan simulasi jaringan/respons sebaiknya dilakukan di browser/proxy uji milik sendiri atau harness staging, bukan dengan mengubah API produksi. M21 fixture engine adalah tugas regresi pengembang; pemilik tidak perlu mengganti binary produksi dengan executable sembarang.

## 5. Pengukuran performa yang jujur

Catat pada aktivitas **bergerak**, bukan wallpaper diam saja:

| Sampel | Resolusi decode | FPS | Mbps | RTT ms | Jitter ms | Jitter buffer ms | Decode ms | Loss interval % | Encoder/backend | CPU/RAM host |
|---|---|---|---|---|---|---|---|---|---|---|
| awal stabil | | | | | | | | | | |
| menit 5 | | | | | | | | | | |
| menit 15 | | | | | | | | | | |
| menit 30 | | | | | | | | | | |

- Angka tidak tersedia ditulis N/A, bukan nol.
- RTT bukan latency gambar. Encode time bukan latency glass-to-glass.
- Untuk glass-to-glass, rekam tampilan host dan client bersamaan dengan kamera ber-fps diketahui, menggunakan konten timestamp/frame counter yang sama. Selisih tampilan adalah pengukuran dengan ketidakpastian sesuai resolusi waktu kamera.
- Kamera 60 fps mempunyai interval frame sekitar 16,7 ms; jangan mengklaim presisi 1 ms dari itu.
- Catat median/p95 jika jumlah sampel memadai, resolusi, fps, rute jaringan, hardware dan metode. Target roadmap <40 ms LAN belum dianggap lulus hanya karena RTT <40 ms.

## 6. Format laporan bug

```text
ID kasus: Mxx / WH-xx
Hasil: PASS / FAIL / BELUM DIUJI / TERBLOKIR
Lingkungan dan SHA:
Langkah reproduksi:
Harapan:
Hasil sebenarnya:
Frekuensi: ... dari ... percobaan
Durasi sampai gejala:
Video/screenshot:
Log tersensor:
Workaround jika ada:
```

Satu bukti harus menyebut artefak yang benar. Jika ada perbaikan source, jalankan ulang kasus terkait pada build baru; hasil dari build lama tidak dipindahkan otomatis.

## 7. Stop condition dan release gate

Hentikan pengujian dan catat FAIL jika input tertahan, kontrol bergerak ke sesi/user lain, CPU/memori/thread terus meningkat, atau akses pemulihan VPS terancam. Jangan terus menekan Connect untuk menutupi gejala.

Web + host boleh disebut kandidat stabil setelah:

- P1 dalam audit ditutup dengan regresi dan bukti yang sesuai;
- M01–M22 yang berlaku pada platform yang diklaim sudah terisi hasil, dengan pengecualian diterima eksplisit;
- tidak ada rahasia pada log dukungan;
- direct/relay, capture bergerak, input, audio dan teardown terbukti;
- tidak ada deploy/rilis otomatis tak disengaja dari langkah validasi;
- hasil tersimpan dengan SHA yang sama dengan artefak rilis.

Lab Actions tidak diperlukan untuk menjalankan checklist ini. Penghapusan workflow tidak menghapus run lama atau membatalkan run yang sudah aktif; tindakan remote tersebut terpisah dan belum dilakukan oleh sesi audit ini.


## Tambahan navigasi/domain dan panel

Jalankan UX01–UX12 pada `qa/ui-navigation-domains-2026-09-27.md` untuk kandidat
pemisahan domain/halaman, restore sesi, dock mouse dan panel 1100×720.
Seluruhnya BELUM DIUJI; jangan samakan snapshot renderer fixture dengan
screenshot host live atau unit metadata dengan browser end-to-end.
