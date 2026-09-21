SATU PINTU: XYDESK CONTROL PANEL
Desktop hanya berisi satu shortcut: "XyDesk Control Panel" — aplikasi native C++
dengan surface gelap yang tenang untuk Start/Stop/status/ID/kode pairing. Tidak
ada terminal dan tidak ada PowerShell untuk pemakaian normal. Host berjalan
sebagai user Windows yang membuka panel; tidak ada pemindahan ke akun lain.
Status aktif tetap bukan bukti streaming; statistik live terlihat di web.

Start Menu hanya menyediakan Control Panel, panduan, dan uninstall. Untuk
pemakaian normal cukup Control Panel; skrip teknis internal tidak perlu dibuka.

MODE DRIVER / VIRTUAL DISPLAY — BELUM MENJADI ALUR NATIVE

Control Panel native tidak memasang, mengonfigurasi, atau mengaktifkan driver
virtual display. Installer hanya membawa berkas teknis dan license untuk
validasi engineering; tidak ada shortcut Virtual720 dan tidak ada setup driver
otomatis. Flow driver/Virtual Display harus divalidasi terpisah sebelum dapat diklaim sebagai fitur native. Jangan menjalankan skrip teknis itu sebagai alur
pemakaian normal, terutama saat terhubung melalui RDP.

Jika pengujian driver diperlukan, ikuti prosedur engineering yang disetujui
untuk mesin uji yang tepat. Jangan import certificate, disable Secure Boot,
aktifkan testsigning, reboot, tscon, memutus RDP, memindahkan user console, atau
memulai ulang host secara otomatis. Host uninstall tidak menghapus driver/config.

--- PETUNJUK MODE HOST BIASA ---

XyDesk Host Test — installer NSIS Windows x64

Engine uji 6.8.5 dengan perbaikan audio, antrean keyboard/mouse, serta
permintaan otomatis desktop16:9. Control Panel native C++ menjadi satu-satunya
entrypoint user. Bukan rilis bertanda tangan. Source SHA dan checksum ada di manifest.json;
installer-source.json mengikat installer ke engine yang dibangun/diuji.

PASANG DAN MULAI
1. Jalankan installer. Untuk instalasi baru pilih folder kosong; untuk versi
   uji sebelumnya gunakan lokasi yang sama. Identitas uji dipertahankan.
2. Buka "XyDesk Control Panel" dari Desktop atau Start Menu.
3. Tekan "Mulai host". Tidak ada jendela terminal yang dibuka.
4. Gunakan https://app.xydesk.my.id dan salin Device ID/kode pairing dari panel.
   Jangan membagikan kode pairing, token, atau file identitas.
5. Uji suara PC, keyboard, pointer/klik di tengah dan empat sudut, serta
   pelepasan tombol ketika sesi ditutup.

Panel native menjalankan xydesk-host.exe langsung sebagai user Windows aktif,
memakai pengawas proses Windows (Job Object), dan menghentikannya dengan tombol
Hentikan. Installer tidak mengubah execution policy atau membuka PowerShell.

DESKTOP16:9 — PERUBAHAN PERILAKU
Saat sesi yang terotorisasi dimulai, host meminta1920x1080 atau1280x720 sesuai
kemampuan H264 yang dinegosiasikan. Hanya mode terdaftar yang lolos CDS_TEST
Windows yang digunakan. Posisi monitor/scaling tidak diubah. Hasil dibaca
kembali; Windows/RDP dapat menolak atau kemudian menerapkan ulang resolusinya.
Tidak ada restart/reconnect RDP, instalasi driver, atau penyimpanan mode ke
registry. Host tidak otomatis mengembalikan resolusi sebelumnya saat berhenti.

Flag engineering `--keep-desktop-resolution` tidak diekspos oleh Control Panel
normal. Panel adalah alur yang didukung untuk menjalankan host; perubahan
resolusi tetap tunduk pada kebijakan Windows/RDP dan hasil aktual di mesin.

Panel Gambar web menampilkan desktop diminta, ukuran terbaca, serta hasil
permintaan. Ukuran gambar hasil decode dilaporkan terpisah. Jika perubahan
ditolak, sumber tetap mengikuti Windows/RDP; konten dijaga proporsional dalam
canvas720p/1080p. Desktop16:9 tidak bisa memenuhi seluruh viewport HP bukan16:9
sekaligus mempertahankan seluruh gambar tanpa crop atau distorsi.

SUARA PC DAN MIC HP
Suara PC direkam dari output default Windows melalui WASAPI loopback.
Di web, aktifkan Suara PC dan izinkan pemutaran bila browser menahannya.
Format perangkat44.1/48/96kHz dinormalisasi ke Opus48kHz dengan paket20ms.

Mic HP -> aplikasi Windows memerlukan virtual audio cable yang dipasang dan
disetujui sendiri oleh pengguna. Contoh: https://vb-audio.com/Cable/
Pilih recording endpoint-nya (misalnya CABLE Output) di Discord/Zoom/game.
Host merender mic ke pasangan render endpoint (misalnya CABLE Input).
Tanpa endpoint virtual, web menjelaskan input mic belum tersedia; tidak
memainkan mic HP melalui speaker sebagai pengganti. Tidak ada driver dibundel
atau dipasang otomatis. Izin mic browser tetap diperlukan.

KINERJA, KONTROL, DAN PREVIEW
Antrean injeksi penuh tidak lagi menghentikan penerimaan input. Posisi absolut
berurutan digabung, tetapi urutan tombol/klik/lepas dan delta relatif dijaga.
Ini bukan janji zero-lag. CPU/GPU, encode, jaringan, buffer dan decode tetap
menentukan kecepatan. Panel web memisahkan RTT, decode/frame, buffer video,
serta antrean input lokal. Aplikasi elevated/UAC dapat menolak injeksi.

Preview wallpaper HD otomatis saat diizinkan dan dapat dinonaktifkan. Hanya
wallpaper lokal, bukan aplikasi, ikon, atau taskbar. Tidak ada auto-minimize.
Jika sumber tidak tersedia, host tidak menggantinya dengan screenshot desktop.

UNINSTALL DAN BATAS
Gunakan Settings > Apps atau Start Menu > XyDesk Host Test > Uninstall.
Hentikan host sendiri dahulu. Uninstaller tidak membunuh proses dan menjaga
identitas uji serta file pribadi tambahan dalam folder instalasi.
Tidak ada service, autostart, firewall rule, atau konfigurasi RDP yang dipasang.
Installer tidak memperbarui APK/web/server; web diperbarui terpisah.

Installer/engine belum ditandatangani Authenticode. Verifikasi SHA-256 dan asal
paket; jangan menonaktifkan keamanan Windows secara global. Bila diblokir oleh
kebijakan mesin, gunakan proses persetujuan administrator yang berlaku.
Build, tes unit, roundtrip codec, dan uji installer bukan bukti suara, injeksi,
resolusi atau latensi pada RDP fisik Anda. Paket ini belum diuji pada PC Anda.

Lisensi: LICENSE-XyDesk.txt, THIRD-PARTY-LICENSES.md, dan folder licenses.

DISPLAY TERBUNDEL
Payload installer membawa arsip driver dan berkas konfigurasi hanya untuk alur
engineering yang terpisah. Halaman installer tidak menawarkan setup driver dan
Control Panel native tidak mengaktifkannya. Tidak ada reboot otomatis, pemutusan
RDP, perpindahan pengguna console, atau restart host. Driver/config yang sudah
ada tidak dihapus saat uninstall.
