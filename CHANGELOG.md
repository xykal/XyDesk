# Changelog XyDesk

Semua perubahan penting XyDesk dicatat di sini.
Format mengikuti [Keep a Changelog](https://keepachangelog.com/id/1.1.0/),
versi mengikuti [Semantic Versioning](https://semver.org/lang/id/).

> **Kebijakan baru 2026-09-07 (Founder Lock):** Changelog tidak digabung numpuk
> di satu file lagi. Setiap versi punya file sendiri di `changelogs/` — biar ga
> numpuk dan mudah dibaca. File ini hanya ringkasan + index. Detail lengkap ada
> di file per versi. Lihat `changelogs/README.md`.

Kebijakan rilis:
- Setiap update aplikasi **wajib menaikkan versi** (`VERSION` berisi `X.Y.Z+NN`,
  `web/package.json`, `host/Cargo.toml`).
- Setiap rilis **wajib punya artikel Berita** dengan changelog yang jelas dan
  panjang (lihat `news/README.md` untuk alur penerbitan).
- **Berita dan changelog adalah dua hal berbeda.** File ini untuk tim dan
  untuk catatan GitHub Release. Berita di `news.xydesk.my.id` ditulis untuk
  pengguna: tanpa nama berkas, tanpa nomor versi di judul, tanpa daftar
  commit. Panduan lengkap nadanya ada di [`docs/NEWS_STYLE.md`](docs/NEWS_STYLE.md).
- File ini otomatis dilampirkan ke GitHub Release oleh `release.yml`.
- **Banner artikel wajib 3D glossy morphing + floating motion blur** —
  lihat `docs/NEWS_STYLE.md` §11.

## [Belum terbit]

- **Satu keyboard Bluetooth tidak lagi menghapus gamepad virtual dari layar:** deteksi perangkat fisik dulu satu saklar untuk semuanya — begitu **satu** perangkat apa pun terpasang, **seluruh** lapisan kontrol disembunyikan. Menempel keyboard Bluetooth ikut menghilangkan stik dan tombol gamepad di layar padahal tidak ada gamepad fisik di mana pun, dan tidak ada cara mengembalikannya. Sekarang penyaringan dilakukan per keluarga: keyboard fisik hanya menyingkirkan tombol huruf dan kombinasi, mouse fisik hanya klik dan scroll, gamepad fisik hanya tombol dan stik XInput. Kontrol yang tidak punya padanan fisik tetap di tempatnya, dan jenis tombol yang belum dikenal aturan ini ikut tampil — lebih baik terlihat daripada hilang diam-diam karena tabelnya belum diperbarui.

- **Tombol "Tampil" di rail sesi:** kontrol di layar kini punya tiga mode — otomatis (mengikuti perangkat yang terdeteksi), selalu tampil (untuk yang memakai keyboard fisik tapi tetap ingin tombol makro di layar), dan disembunyikan. Pilihannya disimpan per perangkat. Saat mengatur tata letak, semua tombol terlihat apa pun modenya: tombol yang disembunyikan otomatis tetap harus bisa dipindah dan dihapus, kalau tidak ia mustahil diurus justru saat sedang diurus.

- **Host berhenti menelan laporan gamepad diam-diam:** pad virtual butuh ViGEmBus, dan PC yang sudah punya gamepad fisik sengaja tidak ditimpa — dua pad di slot yang sama membuat game membaca dua perangkat yang saling melawan. Sebelumnya kedua keadaan itu hanya dicatat satu baris di konsol host: orang yang memegang HP menekan tombol A dan tidak terjadi apa-apa, tanpa penjelasan di mana pun. Status itu kini ikut di blok `meta` (`gamepad.available` + alasan dalam kalimat), dan aplikasi memperingatkan sekali per sesi — hanya kalau tata letaknya memang punya tombol gamepad.

- **Dokumen kontrol:** `docs/KONTROL-OVERLAY.md` menjelaskan tiga jalur input (sentuhan, keyboard/mouse fisik, gamepad fisik) yang semuanya berakhir sebagai protokol biner yang sama, syarat deteksi tiap perangkat (keyboard wajib alfabetik — tanpa itu tombol volume HP ikut terhitung keyboard), tabel keluarga kontrol, dan daftar jujur yang belum ada: rumble, pemetaan tombol pad fisik ke aksi keyboard, dan pointer capture untuk mouse fisik.

- **Transfer berkas benar-benar berpindah sekarang (host, sisi penerima):** channel `"file"` disambungkan ke sesi, ditulis ke disk, dan dibuktikan oleh uji loopback yang memakai kode produksi — `Session` yang sama dengan sesi sungguhan, `file_dispatch::serve` yang sama, penulisan yang sama. Berkas 300 KB berpindah lewat WebRTC nyata dan tiba **byte-per-byte sama**; hash yang tidak cocok berakhir sebagai `CANCEL` dengan folder tujuan **benar-benar kosong**. Potongan ditulis ke berkas `.xypart` dan baru dipindahkan ke nama aslinya setelah SHA-256 cocok, jadi transfer yang putus meninggalkan sampah yang jelas sampahnya — bukan `laporan.pdf` rusak yang dibuka pengguna besok pagi dan disangka korup. Nama yang bertabrakan dinomori (`laporan (2).pdf`), tidak menimpa berkas pengguna. Berkas masuk mendarat di `Downloads\XyDesk`, bukan dicampur dengan unduhan browser.

- **Celah pembukaan channel yang sempat menelan tawaran pertama:** channel sudah `OPEN` di sisi pengirim beberapa saat sebelum penerima sempat memasang pendengarnya, dan `OFFER` yang tiba di celah itu hilang tanpa jejak — transfer menggantung tanpa satu pun pesan kesalahan. Uji loopback menangkapnya pada percobaan pertama. Host kini mengirim tanda siap (`ACK` dengan id 0, id yang tidak pernah dipakai transfer sungguhan) setelah pendengarnya terpasang, dan pengirim wajib menunggunya.

- **Pondasi transfer berkas:** protokol biner untuk data channel `"file"` yang terpisah dari `input` — satu berkas 200 MB berarti ribuan pesan, dan kalau ikut mengantre di jalur input setiap klik mouse ikut tertahan. Pesannya `OFFER`/`ACCEPT`/`REJECT`/`CHUNK`/`DONE`/`CANCEL`/`ACK`, dan mesin keadaan penerima menolak apa pun yang menyimpang: potongan yang datang sebelum pengguna menyetujui, nomor urut yang melompat atau terulang, potongan di atas 64 KiB, total byte yang melewati ukuran yang dijanjikan, `DONE` sebelum semua byte tiba, dan pesan yang menyebut id transfer lain. Isinya baru diakui setelah SHA-256-nya cocok; kalau tidak cocok berkasnya gagal, bukan "selesai dengan peringatan". Nama berkas tidak pernah dipakai apa adanya — komponen direktori dibuang (`..\..\Windows\System32\drivers\etc\hosts` menjadi `hosts`), karakter ilegal dan kontrol disaring, titik dan spasi di ujung dipangkas karena Windows menghapusnya diam-diam setelah pengguna melihat nama yang lain, dan nama perangkat DOS seperti `CON` atau `LPT1` diberi awalan supaya menulis berkas tidak berubah menjadi menulis ke perangkat. Ekstensi yang langsung dijalankan Windows ditandai untuk diperingatkan, bukan diblokir diam-diam. Aturan yang sama ditulis ulang di sisi Android (`FileRules`) dengan contoh uji yang identik, karena dua sisi yang berbeda jawabannya untuk nama yang sama berarti pengguna menyetujui nama yang bukan nama yang ditulis ke disk. 27 uji Rust dan 12 uji Kotlin. Penyambungan ke sesi nyata, penulisan ke disk, dan antarmukanya menyusul — yang ada sekarang adalah bagian yang bisa dibuktikan benar tanpa perangkat.

- **Empat harness runtime yang tidak pernah dipanggil kini jalan di CI:** `tool/runtime-check.mjs` sudah membusuk diam-diam — ia masih meminta rahasia TOTP dari `/admin/setup/start` berbulan-bulan setelah TOTP dicabut dari alur masuk admin, jadi ia gagal pada baris pertamanya dan tidak ada yang tahu. Harness itu ditulis ulang ke kontrak yang berlaku sekarang (pemasangan sekali pakai, token Google lama ditolak di endpoint pemasangan, kata sandi salah ditolak, cookie wajib `HttpOnly`+`Secure`+`SameSite`, logout mematikan sesi), dan tiga harness lain — anggota, riwayat, sesi terikat — ikut dipasang di CI supaya tidak bermain nasib yang sama.

- **Chat global ternyata rusak total sejak hari pertama — sudah diperbaiki:** setiap permintaan upgrade WebSocket ke `/chat/ws` dibalas **500**, bukan 101. Penyebabnya pembungkus CORS di Worker yang menyusun ulang setiap respons dengan `new Response(...)`; status 101 ada di luar rentang yang boleh dikonstruksi di runtime Workers, jadi konstruktornya melempar `RangeError` dan soketnya ikut hilang. 228 uji unit tidak menangkapnya karena tak satu pun pernah merangkai Worker dengan Durable Object sungguhan. Pembungkus CORS kini meneruskan respons apa adanya bila statusnya 101 atau respons itu membawa `webSocket`.

- **Chat diuji di runtime Workers yang sesungguhnya:** harness baru `cloudflare/tool/chat-check.mjs` (`npm run test:chat`, ikut jalan di CI) menyalakan Worker di workerd lewat Miniflare, mendaftarkan dua akun nyata lewat OTP, lalu membuka dua WebSocket betulan: riwayat, presensi, siaran, rem laju, sanitasi, dan jaminan bahwa alamat email tidak pernah ikut terkirim semuanya diperiksa dari ujung ke ujung. Bug 500 di atas ditemukan oleh harness ini pada jalannya yang pertama.

- **Gelembung chat memakai nama profil, lengkap dengan avatar:** nama pengirim dicetak tebal di atas kelompok pesan dan avatar bulat berisi inisial berdiri di kolomnya sendiri, sehingga pesan lanjutan dari orang yang sama tetap lurus dan tidak bergeser. Warna avatar tetap diambil dari `hue` yang ditentukan server, jadi satu orang selalu berwarna sama di layar semua peserta.

- **Jalur profil VIP disiapkan:** tingkat akun ikut mengalir dari penyimpanan akun → Worker → ruang chat → aplikasi sebagai `tier`, dan anggota VIP mendapat cincin emas pada avatarnya serta lencana "VIP" di sebelah namanya. Tingkat tidak pernah boleh dikirim oleh klien — aplikasi hanya membaca apa yang server sertakan. Fitur VIP-nya sendiri belum ada, jadi untuk saat ini setiap akun bernilai `free` dan tidak ada yang berubah di layar; yang dikerjakan di sini hanya pipanya supaya nanti tinggal menyalakan.

- **CI:** header versi host ditulis dengan perangkaian kutip tunggal. Bentuk sebelumnya memakai backslash sebagai escape di dalam string PowerShell — backslash bukan escape di sana — sehingga argumen `Set-Content` terpecah dan job Windows gagal di run 37469419917. Hasil tulisnya kini diperiksa ulang sebelum kompilasi dimulai.

- **Host Windows akhirnya bisa memperbarui dirinya:** menu tray punya item "Cek pembaruan" yang membaca `update.json` dari rilis terbaru, membandingkannya dengan versi yang terpasang, lalu — bila pengguna setuju — mengunduh installer resmi, mencocokkan sidik SHA-256-nya dengan yang tercatat di rilis, dan menjalankannya. Sebelum ini PC harus dipasang ulang dengan tangan sementara aplikasi Android sudah punya jalur pembaruan sejak lama. Unduhan dikunci ke alamat rilis resmi repo lewat HTTPS: manifes yang menunjuk ke tempat lain, memakai http, membawa hash yang bukan 64 heksadesimal, atau menjanjikan berkas di luar batas ukuran ditolak seluruhnya, bukan sekadar diberi peringatan. Berkas yang hash-nya tidak cocok dihapus tanpa pernah dijalankan.

- **Catatan rilis hanya memuat versinya sendiri:** `release.yml` dulu mengambil changelog mulai dari judul pertama lalu memotong di baris ke-120, sehingga catatan rilis satu versi ikut memuat dua versi sebelumnya; ditambah daftar commit otomatis sejak tag non-draft terakhir, badannya membengkak jadi 91 kB berisi seratusan PR dari sembilan rilis. Sekarang bagian versi diambil tepat sampai judul berikutnya, ringkasan pengguna di `changelogs/<versi>.md` dipasang sebagai pembuka, dan daftar commit otomatis dimatikan — tautan perbandingan ditulis sendiri.

## [6.11.11] — 2026-10-06

- **Obrolan di aplikasi Android:** tab baru "Obrolan" di bar bawah membuka ruang chat global. Gelembung berturut-turut dari orang yang sama dalam tiga menit digabung menjadi satu kelompok, avatar berwarna tetap per orang, jumlah orang yang sedang di ruang terlihat di kepala layar, dan sambungan yang putus menyambung ulang sendiri dengan jeda menanjak 1→15 detik. Tombol kirim mati sendiri saat rem laju menyala, jadi aplikasi tidak mengirim pesan yang sudah pasti ditolak server: batas 400 karakter, jeda 0,7 detik, dan 5 pesan per 10 detik dipakai bersama oleh klien dan server, dan diuji di kedua sisi.

- **Chat global (sisi server):** ruang obrolan tunggal untuk semua akun yang sudah masuk, berjalan di satu Durable Object dengan WebSocket hibernasi — tanpa server tambahan dan tanpa biaya tetap. Identitas diverifikasi di Worker sebelum soket sampai ke ruang, jadi token tidak pernah masuk ke ruang chat dan alamat email tidak pernah ikut tersiar: yang terlihat peserta lain hanya nama profil atau bagian sebelum `@`. Pesan dibatasi 400 karakter dan 6 baris, satu orang maksimal 5 pesan per 10 detik dengan jarak minimal 0,7 detik, dan 50 pesan terakhir disimpan sebagai riwayat untuk yang baru bergabung. Sanitasi membuang karakter kontrol, spasi nol-lebar, dan penanda arah teks yang bisa dipakai membalik tampilan nama orang lain. Tampilan di aplikasi menyusul.

- **Gerbang host menghormati bentuk jendela:** layar sambutan dan layar masuk kini melewati ritual penutup yang sama dengan panel, sehingga sudut jendela tetap membulat dan tembus pandang. Pemeriksa bentuk dan snapshot juga melewati gerbang, karena tugasnya memotret panel.

- **Panel host wajib masuk akun:** membuka panel kini melewati gerbang — sambutan singkat untuk pemasangan baru, lalu layar masuk lewat akun Google atau email. Sebelum masuk, panel tidak digambar sama sekali, bukan sekadar ditutupi: ID perangkat, kode pairing, dan status mesin tidak lagi terbaca oleh siapa pun yang menemukan PC ini menyala. Engine host juga baru dinyalakan setelah pemiliknya masuk, karena menyalakannya lebih awal berarti mesin siap menerima koneksi sementara layar masih menampilkan layar masuk. Keluar akun langsung mengembalikan panel ke layar masuk.

- **Tampilan panel host ala macOS:** tombol jendela menjadi tiga lampu lalu lintas di kiri atas — merah, kuning, hijau — dengan glyph yang baru muncul saat kursor mendekat dan warna yang meredup saat jendela tidak aktif. Layar gerbang memakai satu kartu fokus di tengah dengan isinya muncul berurutan, bukan serentak.

- **Panel Win32 diperiksa sintaksnya di Linux:** `main.cpp` sebelumnya hanya dikompilasi oleh runner Windows, sehingga kesalahan sepele seperti identifier yang belum dideklarasikan baru ketahuan setelah antre di belakang build Rust yang panjang. MinGW kini mengurai berkas yang sama di job Linux dalam hitungan detik. Ini bukan pengganti build MSVC, hanya penyaring cepat di depan.

- **Animasi berbasis waktu, bukan frame:** kemajuan animasi dihitung dari milidetik nyata. Sebelumnya gerakan dihitung per tick timer, sehingga animasi justru melambat tepat ketika sistem sibuk — yaitu saat sesi remote sedang berjalan.

- **Laju tinggi 120 dan 144 fps:** sesi tidak lagi berhenti di 60 fps. Batasnya kini dihitung dari MaxMBPS level H.264 hasil negosiasi, bukan dari daftar tetap: 720p pada level 5.1 sanggup 144 fps, 1080p pada level 5.1 sanggup 120 fps, sedangkan level 4.0 tetap maksimal 60 dan level 3.1 tetap 30. Hasilnya selalu dibulatkan ke anak tangga yang dikenal, tidak pernah ke angka sisa bagi seperti 68 fps yang akan merusak pacing capture dan VBV.

- **Opsi fps mengikuti layar HP:** panel 120/144 Hz kini mendapat pilihan 120 dan 144 di panel sesi dan di pengaturan bawaan, sementara layar 60 Hz tidak ditawari opsi yang mustahil ia tampilkan. Sesi juga meminta mode layar tercepat pada resolusi yang sama, karena tanpa permintaan itu banyak panel 120 Hz tetap berjalan 60 Hz dan separuh frame yang dikirim host terbuang di tahap tampil.

- **GOP mengikuti laju:** jarak antar-IDR dijaga sekitar dua detik di laju mana pun. Sebelumnya konstanta 120 frame berarti pemulihan packet loss baru datang empat detik sekali di 30 fps, dan sebaliknya membuang bit untuk IDR tiap 0,8 detik di 144 fps.

- **Uji unit Android ikut berjalan di CI:** job APK kini menjalankan `:xyadapt:test` sebelum merakit rilis. Modul itu sudah punya uji sejak lama, tetapi tidak pernah sekali pun dieksekusi oleh pipeline.

- **CI installer:** pemasangan NSIS kini diulang sampai tiga kali dan keberhasilannya diukur dari ada tidaknya `makensis.exe`, bukan dari kode keluar Chocolatey yang bisa nol walau pengunduhan paketnya ditolak server. Satu balasan 503 sesaat sebelumnya cukup untuk menjatuhkan seluruh Build yang menjadi syarat rilis.

- **Gerbang versi:** `tool/check_version.py` kini ikut memeriksa `host/Cargo.lock` dan `admin/package.json`. Bump versi yang melewatkan lock Rust sebelumnya lolos semua pemeriksaan lokal lalu menggagalkan job Windows di CI, setelah antre di belakang build Rust yang panjang.

## [6.11.10] — 2026-10-06

- **Panduan koneksi bergambar:** blok "Cara main" di halaman Connect kini menampilkan ilustrasi yang ikut berganti saat tab Sisi client dan Sisi host ditekan, sehingga sisi mana yang sedang dibaca langsung terlihat tanpa harus mengecek tab. Judul dan kalimat pembukanya diberi ruang supaya tidak pernah tertimpa ilustrasi, termasuk di layar sempit.

- **Ilustrasi beranda dan layar masuk:** empat kartu fitur di beranda kini punya ilustrasi yang sesuai isinya — kontrol, keamanan, kecepatan, dan klien browser — dipasang samar di pojok kartu supaya teksnya tetap penuh lebar dan kontrasnya tidak turun, lalu menguat sedikit saat kartu disentuh. Layar kode verifikasi mendapat ilustrasi amplop agar maksudnya terbaca tanpa menambah kalimat.

- **Dekorasi hero web:** beranda kini punya tiga ilustrasi melayang — awan, kilau, dan globe — yang mengambang pelan di sekitar ilustrasi utama dengan durasi berbeda-beda supaya tidak terlihat berayun serempak. Dekorasi ini murni hiasan: tidak dibacakan pembaca layar, tidak menghalangi tombol, dimuat belakangan agar tidak menunda tampilnya hero, dan disembunyikan di layar sempit saat hero menumpuk jadi satu kolom. Gaya orb dan kartu melayang lama yang markup-nya sudah lama dihapus ikut dibuang — berkas gaya web turun 118,1 kB menjadi 115,7 kB.

- **Panel admin naik ke Vite 8:** `admin/` ikut memakai Vite 8 dan `@vitejs/plugin-react` 6 seperti web, sekaligus menutup dua masalah yang selama ini tidak terlihat. Build `admin/` sebenarnya sudah gagal sejak lama (`tsc` tidak menemukan deklarasi untuk impor CSS) karena panel ini belum punya `vite-env.d.ts`, dan satu-satunya berkas testnya tidak pernah benar-benar berjalan sejak `typescript` 7 melepas `transpileModule`. Keduanya diperbaiki: deklarasi tipe ditambahkan, test memakai transformer bawaan Vite seperti di web, dan kontrak uji login admin diselaraskan dengan backend yang sudah melepas kode 2FA sejak 23 September. Bundle panel turun dari 277,7 kB menjadi 268,0 kB.

- **CI panel admin:** `admin/` kini punya job sendiri di Build (test + `npm run build`) dengan filter area `admin/**`, sehingga kerusakan serupa tidak lagi lolos diam-diam.

- **Dependensi:** `wrangler` 4.141 → 4.147 di Worker signaling dan Worker berita, Vite 8.3.1 → 8.3.2 di web, dan `gradle/actions/setup-gradle` 6.3.0 → 6.4.0 (pin SHA diverifikasi terhadap tag resmi).

- **Cara kerja repo:** sistem role agent dihapus. Pembagian sembilan role, identitas git karangan `Nama - XyVerse Team`, kunci area, dan antrean izin push `Izin: <ID-SESI>` tidak dipakai lagi — semua pekerjaan dikerjakan atas nama pemilik repo lewat PR biasa. `AGENT.md` ditulis ulang tanpa birokrasi role tetapi dengan kewajiban bukti, changelog, dan gerbang izin versi/berita/rilis yang tetap penuh; `docs/project/AGENT_BOARD.md` dibekukan ke `docs/archive/AGENT_BOARD-2026-10.md`; `docs/project/HANDOFF.md` dikelompokkan per area kode, bukan per role; `docs/project/CONTRIBUTORS.md` dibekukan dengan seluruh nama lama tetap utuh sebagai catatan sejarah. `docs/CI.md`, `docs/NEWS_STYLE.md`, dan filter `meta` di `build.yml` ikut disesuaikan.

- **Keamanan kredensial:** `.env` dan turunannya kini diabaikan git (hanya `.env.example` yang boleh masuk), supaya kunci Resend/Cloudinary/Cloudflare/Vercel/OneSignal/GitHub tidak pernah ikut ter-commit dari working tree.

- **Android UX polish:** onboarding kini memakai komposisi ilustrasi yang lebih jelas per halaman, splash mendapat aura gradasi gelap berputar, Google login pindah ke account-picker popup, trackpad default lebih ringan/halus, dock tombol lama dihapus dari UI, dan IME sesi memakai text input penuh (bukan field password).

- **Session natural render:** Web dan Android kini mengunci video sesi ke scaling natural/aspect-fit tanpa CSS `filter`, `image-rendering: pixelated/crisp`, atau copy “tajam”; SurfaceView native juga langsung letterbox dari ukuran frame awal agar tidak terasa stretch/sharpen saat decode mulai.

- **Agent board:** row aktif batch rustfmt natural quality yang sudah merged dibersihkan agar papan kembali kosong sebelum Build manual.

- **Host formatting:** log natural-quality host dirapikan agar `cargo fmt --check` pada Build penuh lulus.

- **Agent board:** row aktif batch natural session quality yang sudah merged dibersihkan agar papan kembali kosong sebelum Build manual.

- **Session natural quality:** preset kualitas kini hanya mengubah target bitrate/kompresi tanpa memaksa resolusi HD; wording Web diganti agar tidak terasa seperti efek sharpen.

- **Agent board:** row aktif batch APK overlay setter yang sudah merged dibersihkan agar papan kembali kosong sebelum Build manual.

- **APK overlay:** fungsi internal edit overlay diganti nama agar tidak bentrok dengan setter properti Kotlin saat kompilasi release.

- **Agent board:** row aktif batch APK i18n quote yang sudah merged dibersihkan agar papan kembali kosong sebelum Build manual.

- **APK i18n:** quote pada teks privasi password host di-escape agar kompilasi Kotlin release tidak gagal.

- **Agent board:** row aktif batch fix guard Android Build yang sudah merged dibersihkan agar papan kembali kosong sebelum Build manual.

- **CI Build:** job Android pada Build penuh kini benar-benar menjalankan guard operator dari `${{ github.workspace }}` sebelum checkout, bukan default `android-native`.

- **Agent board:** row aktif batch guard Android pra-checkout yang sudah merged dibersihkan agar papan kembali kosong sebelum Build manual.

- **CI Android:** guard operator pra-checkout memakai `${{ github.workspace }}` agar tidak tetap mengikuti default `android-native` sebelum checkout.

- **Agent board:** row aktif batch fix CI Android yang sudah merged dibersihkan agar papan kembali kosong sebelum Build manual.

- **CI Android:** validasi operator workflow APK kini berjalan dari root sebelum checkout, sehingga job Android manual tidak gagal karena folder `android-native` belum tersedia.

- **Agent board:** row aktif batch APK empty-state aset yang sudah merged dibersihkan agar papan kembali kosong.

- **APK empty state:** daftar host/perangkat kosong kini memakai ilustrasi `float_pc_sleep` dari paket aset melayang, selaras dengan empty state Web.

- **Agent board:** row aktif batch Web empty-state aset yang sudah merged dibersihkan agar papan kembali kosong.

- **Web empty state:** halaman Devices/History kini memakai ilustrasi `float-pc-sleep.webp` untuk state kosong perangkat/sesi, mulai mengintegrasikan paket aset melayang tanpa menyentuh hero login.

- **Aset ilustrasi lintas platform:** ditambahkan set 17 ilustrasi melayang 3D glossy dan `frame_vip.webp` untuk sumber audit, APK, dan Web, plus panduan pemakaian serta antrean integrasi host/web/APK.

- **Agent board:** row aktif batch APK i18n password host yang sudah merged dibersihkan agar papan kembali kosong.

- **APK i18n privasi:** translasi Inggris untuk status password host kini menjelaskan penyimpanan opsional melalui pilihan Ingat, bukan klaim bahwa password tidak pernah disimpan.

- **Agent board:** row aktif batch Host stop-session JSON yang sudah merged dibersihkan agar papan kembali kosong.

- **Host panel stop-session:** hasil aksi Putus sesi kini diparse sebagai JSON boolean `stopped`, bukan string matching, sehingga respons tetap terbaca meski format JSON berubah spasi/urutan field.

- **APK wallpaper storage:** penyimpanan wallpaper memakai temp file per-host, fallback write bila rename gagal, dan cache memori hanya dibersihkan setelah save sukses sehingga preview lama tidak mudah hilang karena kegagalan IO.

- **APK wallpaper transfer:** respons wallpaper dengan ID cocok tetapi tipe tak dikenal sekarang membatalkan transfer, sehingga state pending tidak menunggu timeout untuk pesan yang tidak valid.

- **APK wallpaper transfer:** chunk wallpaper invalid, ukuran berlebih, total berubah, atau Base64 rusak sekarang langsung membatalkan transfer dan membersihkan state agar retry preview tidak tertahan sampai timeout.

- **APK wording wallpaper:** teks perangkat/akun tidak lagi menyebut cuplikan layar host; preview kartu dijelaskan sebagai wallpaper lokal yang tidak diunggah.

- **Agent board:** baris aktif/review untuk batch PR #80–#88 yang sudah merged dibersihkan, sehingga papan kembali menunjukkan tidak ada area kerja yang terkunci.

- **Host panel koneksi:** tombol Putus sesi memakai tint danger ringan saat normal/hover/pressed, supaya aksi memutus client terlihat berbeda dari tombol navigasi biasa.

- **APK overlay stick safety:** stick keyboard/mouse, joystick gamepad, dan scroll wheel kini mereset knob/vector visual saat masuk mode edit, mencegah vector stick mouse lama hidup lagi setelah edit ditutup.

- **Docs/audit preview:** TODO handoff preview wallpaper host yang sudah selesai ditutup, dan sisa wording internal aktif yang menyiratkan “langsung HD” dirapikan menjadi 720p/1080p eksplisit.

- **Host panel takeover:** hover dan hit-test tombol Ambil alih sesi ini kini memakai kondisi session mismatch yang sama dengan klik, sehingga tombol takeover memberi feedback visual saat benar-benar tampil.

- **Web session wording:** label resolusi/bitrate tidak lagi menyiratkan HD paksa; UI menjelaskan 720p/1080p sebagai permintaan mode yang tetap mengikuti batas capture/encoder tanpa filter peningkat detail.

- **Host panel koneksi:** halaman Koneksi kini punya tombol Putus sesi untuk menghentikan sesi HP/browser aktif lewat kanal kontrol privat host, tanpa mematikan proses host seluruhnya.

- **APK overlay mouse:** Stick mouse sekarang terus mengirim gerakan selama jari ditahan ke arah tertentu, bukan hanya saat jari sedang bergerak, sehingga aksi held untuk kontrol mouse terasa konsisten.

- **APK overlay editor:** kontrol overlay kini bisa dipilih dengan tap saat mode edit, selain drag, sehingga resize/hapus layout custom tidak perlu menggeser posisi dulu. Hint editor juga diperjelas.

- **APK overlay autosave:** layout overlay sekarang otomatis tersimpan saat keluar mode edit, saat app masuk background, atau saat sesi ditutup ketika masih edit, jadi posisi/ukuran custom tidak hilang kalau user menekan Selesai atau disconnect tanpa tombol Simpan.

- **APK overlay hold safety:** saat masuk mode edit, overlay melepas semua input yang sedang held terlebih dulu agar tombol, trigger, stick, atau mouse tidak nyangkut ketika layout diedit.

- **APK overlay editor:** highlight kontrol terpilih sekarang konsisten untuk tombol, stick, joystick gamepad, trigger, dan scroll wheel, sehingga resize/hapus kontrol lebih jelas saat mode edit.

- **APK overlay editor:** drag saat mode edit kini memakai delta gerak terakumulasi dari pusat kontrol awal untuk tombol, stick, joystick gamepad, dan scroll wheel, sehingga penempatan kontrol tidak lagi terasa loncat/seret pendek saat layout tersimpan ulang.

- **Preview wording:** sisa label/test internal yang menyebut "HD preview" diganti menjadi wallpaper preview, supaya audit no forced HD/filter konsisten dengan perilaku runtime.

- **Host panel status:** kartu Capture sekarang netral saat host berhenti, tidak lagi terlihat seperti error engine basi. Tombol takeover sesi berubah menjadi "Ambil alih sesi ini" dan merestart engine dari panel aktif bila host lama masih running di sesi Windows lain.

- **Web auto quality:** mode otomatis selalu mulai 720p30, naik lebih pelan setelah jalur stabil, melewati tier yang tidak didukung encoder, dan manual fallback kembali ke 720p. Kartu/riwayat perangkat menampilkan wallpaper dengan `contain` supaya preview tidak dicrop seperti efek cover.

- **Host wallpaper preview:** generator preview tidak lagi memaksa canvas HD 1920×1080/Lanczos. Wallpaper dikirim ringkas, mempertahankan rasio asli, tanpa upscaling, dan pesan gagal tidak lagi menyebut "preview HD". Panel host juga menjelaskan bahwa kartu perangkat memakai wallpaper Windows, bukan frame video sesi.

- **APK native:** preview kartu perangkat sekarang hanya memakai wallpaper host, bukan tangkapan frame sesi; permintaan wallpaper diulang aman tanpa menimpa transfer yang sedang jalan, tampilan kartu tidak lagi crop/High filter. Overlay virtual diperluas dengan preset QWERTY, F1–F12, numpad, mouse, dan gamepad/XInput lengkap (stick kiri/kanan, trigger, ABXY, D-pad, Start/Back), semua tombol tetap berbentuk ring bulat, bisa digeser, diubah ukuran, dan aksi tahan mengikuti durasi sentuhan.

- **Dokumentasi stack aktif:** ROADMAP, SETUP, ARCHITECTURE, dan VERSIONING disinkronkan dengan stack saat ini: Android native, host Rust + panel Win32, web Vite, Worker Cloudflare, serta `VERSION` sebagai sumber versi. Referensi aktif ke Flutter/Tauri/Electron dipindahkan menjadi catatan sejarah.

- **Actions dikunci manual:** semua workflow produksi/helper sekarang hanya `workflow_dispatch`; pemicu `push`, `pull_request`, `workflow_run`, dan jadwal cleanup dihapus. Setiap job berhenti di langkah awal bila actor bukan `xykal`, supaya build/deploy/release hanya bisa dijalankan pemilik repo atau token pemilik.

## [6.11.9] — 2026-10-03

- Overlay default FPS: WASD + mouse bulat, bisa digeser. Keyboard tetap IME HP.


## [6.11.8] — 2026-10-03

- Kartu perangkat pakai wallpaper host, bukan cuplikan HD.
- Keyboard rel = IME HP. Overlay kontrol bulat, bisa digeser.
- Sesi foreground service. Resolusi Auto, tidak maksa 1080.


## [6.11.7] — 2026-10-02

- Host: daftar HP ringkas (ikon+status), bukan foto besar. Remote HP dari PC = Premium.
- Rail web: umpan tekan. Connecting APK pakai fase pairing. Banner installer baru.
- Tombol/toggle APK tekan halus.



## [6.11.6] — 2026-10-02

- Wallpaper Windows ke kartu APK; libxypreview/libxyscroll/libxygamepad.
- Virtual XInput 0x0E (ViGEm bila tidak ada pad fisik).
- LEGAL native + SECURITY.md. Mascot onboard/empty; hero login asli. Lanskap terkunci.

- **UX sesi native:** editor overlay lebih mudah diketuk (chip 36dp, pustaka wrap), tombol overlay lebih kontras, keyboard PC lebih tinggi, salinan HID/sambung lebih singkat.

- **Inno Setup:** English license + must accept, language dialog, Desktop shortcut task, skip VB-CABLE if driver already on the PC. Publisher string XyVerse Technology Global.

- **HID OTG/BT:** keyboard alfabet, mouse, gamepad/joystick terdeteksi; overlay on-screen otomatis hilang (pola remote gaming: kontrol sentuh hanya bila tidak ada perangkat fisik). Rail tetap.

- **APK HID:** keyboard/mouse fisik (hover, klik, scroll) dan gamepad (stick kiri WASD, stick kanan mouse, tombol A/B/X/Y). `libxyhid.so` deadzone stick. `libstreamxy.so` tetap protokol.
- **Host/installer:** virtual audio yang sudah ada di-skip. NSIS: pilih bahasa, lisensi Inggris + I accept, folder, checkbox Desktop/Start Menu shortcut.

- **APK overlay kontrol bebas (bukan dok):** posisi %, ukuran, radius, stick WASD/panah/mouse, F-keys, numpad, mouse, chord. Rel samping tidak diubah. Edit lewat tombol Dok.

- **APK HUD keyboard PC:** tombol Keyboard di rel membuka papan ABC / 123 / F1–F12 / Numpad / Penuh (bukan IME HP). Numpad juga bisa dipasang di dok. Rel samping tidak diubah.

- **Sisa Flutter dihapus:** folder `android/` (plugin registrant), `FLUTTER_VERSION` di `release.yml`, concurrency deploy web. Tidak ada `*.dart`/`pubspec.yaml`. Rilis publik ditahan.

## [6.11.5] — 2026-10-02

- Splash VO sekali, tanpa gema dobel. HUD web UDP RELAY/P2P + LOSS. Release sertakan `XyDesk.apk` universal.

## [6.11.4] — 2026-10-02

- Overlay scroll dua mode: pil geser + tombol ↑↓. Google slide isi progres.
- GPU: NVIDIA/AMD/Intel; skip Basic Render. Host privacy-screen DXGI.
- Legal cookie + E2E di APK/web. Splash VO XyDesk. Web login tanpa slang.

## [6.11.3] — 2026-10-02

- Login: tautan Syarat & Ketentuan, Kebijakan Privasi, Kebijakan Cookie, Lisensi.
- Ingat perangkat: checkbox, bukan toggle.
- Connecting: morph tetap, status “Menyambung…” ikut bahasa.
- Onboarding/welcome: ilustrasi generate + elemen melayang.
- HUD: UDP RELAY/P2P, Wi‑Fi/Data seluler, LOSS. Cuplikan perangkat HD 1920.
- Host jendela/shortcut bernama XyDesk.

## [6.11.2] — 2026-10-02

- **APK:** overlay pass-through memakai FrameLayout (ComposeView final); keep `XyHid`.

- **TURN produksi:** ExpressTurn (`TURN_DIRECT_*`) terpasang di Worker signaling. Cadangan Open Relay bukan syarat. GitHub Secrets TURN tetap kosong supaya deploy tidak menimpa Worker.

- **Native Tentang: cek pembaruan dari GitHub Releases** (`update.json` di `latest/download`). Draft tidak dihitung sebagai rilis. Belum memasang APK sendiri.

- **Hub HTTP admin:** `/stats`, `/hub/devices`, `/kick` menolak permintaan tanpa header `x-internal-admin` (selaras AuthStore). `admin.js` mengirim header itu. Belum deploy worker.

- **HANDOFF + README diselaraskan ke stack aktual (2026-10-01):** antrian Flutter/Electron dicoret; klien = Kotlin native, panel = Win32. README tidak lagi menyebut Tauri v2 + Next.js sebagai shell Windows.

- **Native presence: rotasi ID bila hello ditolak sebelum welcome:** soket beranda memakai ID tetap `app-presence-*`; setelah putus kotor, hub membalas `id sudah online` dan loop tetap menganggap soket terbuka tanpa daftar online. Kini error pra-welcome memutar ID dan menutup soket; `RtcSession` sudah punya jalur yang sama.

- **Keamanan dan kebersihan repo (28 September):** (1) Worker signaling menolak upgrade `/ws` dari Origin browser yang tidak terdaftar di `CORS_ORIGINS` (native host/APK tanpa Origin tetap lolos) — token yang bocor tidak bisa dipakai lintas situs; server Go cadangan mendapat `XYDESK_ALLOWED_ORIGINS` dengan bawaan same-origin, menggantikan `CheckOrigin: true`. (2) `release.yml` memaku zip VB-CABLE vendor ke SHA-256; hash berbeda menghentikan rilis. Salinan `VBCABLE_Setup_x64.exe` (900 KB) dihapus dari repo — sudah diunduh dari vendor saat rilis. (3) Sepuluh workflow eksperimen `workflow_dispatch` tanpa pemakai dihapus; tersisa `build`, `release`, `deploy-web`, `deploy-signaling`, `deploy-news`. (4) GitHub Release lama dan 14 tag eksperimen dihapus; tersisa v6.8.7. (5) Dokumen proyek pindah: `AGENT_BOARD/HANDOFF/ROADMAP/SETUP/CONTRIBUTORS` → `docs/project/`, audit lama → `docs/archive/`; semua tautan diperbarui, `AGENT.md`, `README.md`, `CHANGELOG.md`, `LICENSE` tetap di root. Komentar "Founder request ..." di kode diganti kalimat teknis. Audit yang TIDAK menemukan masalah: tidak ada secret di seluruh history git; `npm audit` web/worker 0 kerentanan; JWT HS256 memeriksa `alg`; PBKDF2 + perbandingan waktu-konstan untuk admin; control API host hanya `127.0.0.1` + bearer; lockout pairing per-peer dan global.

- **Kandidat UX dan domain (27 September, belum diuji/deploy):** web mendapat halaman perangkat, detail, riwayat sesi dan sesi remote terpisah; tujuan sesi dapat direstore dari route/metadata tanpa credential URL. Dock klik kiri/kanan/scroll berlabel, posisi/ukuran UI tersimpan dan editor custom lebih lega. Panel host default 1100×720 logical px dengan pembatas work area/DPI. Kandidat domain publik www dan aplikasi remote, redirect app, CORS serta callback Google per origin diperbarui. DNS/OAuth/deploy belum diterapkan; login/pair ulang diperlukan pada origin baru. Rincian dan acceptance: `docs/qa/ui-navigation-domains-2026-09-27.md`.

- **Kandidat lanjutan hardening (belum diuji CI):** startup pertama serentak memakai lock identitas lintas proses dan penulisan atomic yang meneruskan error; parser password panel menangani JSON escape/Unicode dan menolak data rusak; launcher bisa menerima bearer control lewat pipe privat tanpa stdout/file credential. Regresi multiprocess, parser dan IPC ditambahkan ke validasi manual. Tidak ada perubahan versi atau push. Lihat `docs/qa/web-host-hardening-2026-09-27.md` dan `docs/CONTROL-IPC.md`.

- **Kandidat hardening lengkap web + host (27 September, belum diuji CI):** seluruh 17 butir audit mendapat perubahan source lokal: lease privat per akun Windows (menggantikan kandidat mutex batch 1), cancellation/deadline/retry web, SDP applied, audio bounded + guard COM, clipboard/ordering input, worker identitas panel + heartbeat segar, auth/nonce/logout, watchdog decoded-frame, dan penghapusan secret startup normal. Workflow manual `Validate Web Host` dipisahkan dari deploy/rilis. Tidak ada push, bump versi, dispatch atau klaim runtime PASS. Batasan dan gerbang tersisa: `docs/qa/web-host-hardening-2026-09-27.md`.

- **Kandidat perbaikan host batch 1 (27 September):** fallback GDI mengambil dan memvalidasi frame desktop-DC sebelum mengembalikan sukses; perubahan geometri meminta restart pipeline. Konversi NV12 menolak buffer cacat lewat error, bukan panic. Standby memeriksa sesi sebelum akuisisi mutex dan handle dimiliki guard sampai host selesai. Regresi ditambahkan (termasuk lifecycle handle Win32), tetapi compile/test CI dan RDP nyata belum dijalankan. Namespace/ACL lintas user, web, versi dan produksi tidak berubah. Rincian: `docs/qa/host-batch-1-2026-09-27.md`.

- **Fokus web + host, tanpa lab runner interaktif (27 September):** workflow Test Lab RDP/Tailscale dihapus atas arahan pemilik; unit test/build/packaging tetap ada. Audit statis mendalam mencatat 17 temuan pada capture GDI, leadership, SDP, sesi, audio/input, panel, auth, diagnostik dan CI (`docs/archive/AUDIT-WEB-HOST-2026-09-27.md`), dengan checklist acceptance manual di perangkat pemilik (`docs/WEB-HOST-MANUAL-QA.md`). Temuan runtime belum diperbaiki/diuji pada sesi audit; tanpa bump versi/deploy.

- Audit statis 27 September: peta arsitektur aktual, penanganan registrasi Flutter, cakupan CI admin/Windows, batas otorisasi Hub, dan acceptance Windows/RDP dicatat di `docs/archive/AUDIT-2026-09-27.md`. Tidak ada perubahan runtime atau klaim test baru.

- **RDP capture pulih dan diagnosis lebih terlihat (2026-09-26):** ketika monitor-DC GDI diam-diam mengembalikan frame hitam selama 15 sampel, host mencoba desktop-DC `GetDC(0)` sebagai jalur kedua; sesi yang benar-benar terkunci atau berjalan di sesi berbeda tetap dilaporkan, tidak disamarkan sebagai gambar sehat. Telemetry capture kini menyertakan frame hitam dan pasangan ID sesi, dan panel Statistik client web menampilkan peringatan yang sesuai. Panel native Windows disederhanakan mengikuti Paper web: radius panel/kartu 10, kontrol 8, garis kartu dihilangkan, warna putih/lavender dan aksen ungu dipertahankan. Belum diverifikasi di desktop RDP Windows asli.
- **Dukungan semua lingkungan: VPS RDP, VM, PC fisik (2026-09-26):** menjawab permintaan pemilik "support semua software hardware, model, vps rdp, vm, pc fisik". (1) **Kepemimpinan lintas sesi**: installer mendaftarkan host startup di tiap login interaktif pemakai (HKCU Run, ikut terhapus saat uninstall); instance di sesi pemegang layar aktif otomatis menjadi leader (capture + signaling + control API), instance lain standby dan mengambil alih senyap lewat mutex bernama + berkas stepdown — tanpa kedip pindah sesi, tanpa schtasks. Panel menambah kartu jujur lintas sesi plus tombol "Jalankan host di sesi ini" sebagai jalan pintas bila takeover belum terjadi. (2) **Mode HD tanpa bar letterbox untuk layar lebar** (keluhan lapangan VPS 1948x900): canvas mengikuti rasio sumber dengan tinggi 720 selama budget macroblock memungkinkan, mengecil proporsional bila tidak — rasio selalu aman; sumber sempit tetap letterbox internal dengan contentRect. (3) Tangga capture yang sudah ada (DXGI duplication / Windows Graphics Capture / virtual display / GDI bitblt + watchdog failover) serta encoder NVENC→openh264 terus menjadi fallback otomatis untuk mesin ber-GPU, VM tanpa GPU, dan VPS headless; audio tetap merosot anggun jadi sesi video-saja bila perangkat tidak ada.

- **Diagnosa sesi panel diluruskan + isi dalam panel dirapikan (2026-09-26):** dua keluhan pemilik dibereskan langsung. (1) Peringatan "sesi proses vs sesi layar aktif" bisa menuduh salah di VPS: sesi konsol (auto-login akun layanan, mis. runneradmin) selalu dianggap "layar aktif", padahal pemilik bekerja lewat sesi RDP-nya sendiri — panel sampai bilang layar aktif milik runneradmin. Kini host mendaftar semua sesi: sesi RDP yang berstatus aktif (stasiun bukan Console) didahulukan sebagai pemegang layar, konsol hanya fallback bila RDP tidak ada; kartu identitas dan log menyebut nama user asli kedua sesi. (2) "Resize" tidak hanya tarik tepi jendela: isi dalam panel dirapikan — tiga ikon sidebar digambar ulang (denyut dibuat kompak tak menusuk, pairing jadi gembok rapi, slider diberi napas sehingga rel tidak menabrak knob), hierarki teks diseragamkan (judul kartu & label kaps kecil semibold, status utama semibold, nilai ID/kode pairing besar Consolas), dan padding kartu disatukan. Gerbang probe/bentuk/teks/tata letak tetap hijau.
- **Panel Windows dirombak ulang sesuai umpan balik lapangan pemilik (2026-09-25):** setelah dites di Windows asli, pemilik minta: lebih lebar, ada sidebar, tanpa bayangan, dan jangan semua teks. Jadi panel kini **900×560 dengan sidebar ikon di kiri** (Status / Pairing / Kontrol — ikon digambar stroke, label kecil hanya penegas) dan **bayangan di luar jendela dihapus total** (sudut membulat + tepi halus tetap digambar sendiri). Halaman Status mendapat kartu baru **CAPTURE** yang jujur: backend apa yang dipakai, dan — ini penangkal "layar hitam" — engine kini mendeteksi dua penyebab klasik gambar hitam di client padahal koneksi hijau: frame hitam beruntun (layar terkunci / secure desktop) dan proses host yang hidup di sesi berbeda dari layar aktif (klasik RDP; BitBlt antar-sesi selalu hitam). Keduanya ditulis ke `capture.json`, tampil di panel dengan warna peringatan, dan tercatat di log dengan langkah perbaikannya. Gerbang CI ikut bentuk baru: probe mengharapkan 900×560 + margin 0 + hit-test sidebar; pemeriksa bentuk kini membuktikan luar panel benar-benar kosong (tanpa bayangan). Uji tata letak Linux 95 → 114 pemeriksaan.
- **WebView2 dicabut atas arahan pemilik — panel native penuh dengan desain “Paper” web (2026-09-26):** panel kembali 100% C++/GDI tetapi kini mengikuti identitas visual website (kanonik unifikasi Sep 2026): latar putih, aksen ungu #7c3aed, kartu bernada overlay #f5f3ff dengan garis tepi zinc, kolom sidebar ungu lembut dengan pemisah, tombol utama ungu dengan teks putih, warna status token (#167347/#855400/#a52a36). Panel juga kini **bisa ditarik ukurannya** dari tepi kiri/kanan/atas/bawah (720×480 sampai 1600×1100, kursor panah dua otomatis) dan kartu CAPTURE menyerap tinggi ekstra; gerbang probe/shape/teks/tata letak tetap hijau dengan latar putih.
- **Website dirapikan (2026-09-26):** aturan CSS ganda `.connect-cta`/`.session-retry` dilebur, aturan satu-baris raksasa diformat, radius disatukan ke skala desain; 110 uji web tetap hijau.
- **Panel dapat kulit WebView2 — UI kelas web, bukan gambar tangan (2026-09-26):** keluhan pemilik "UI/UX masih sangat jelek" dijawab dengan ganti mesin gambar: panel kini dirender **Microsoft Edge WebView2 (Chromium)** dari `panel.html` yang tertanam di EXE — latar aurora beranimasi, pill navigasi morphing dengan pegas, halaman meluncur halus, kartu glassmorphism, tombol salin dengan umpan balik, dan **banner merah besar** saat host salah sesi (menyebut nama user host vs user layar aktif + perintah jelas menjalankan dari sesi RDP yang benar). State host didorong 1×/detik lewat `ExecuteScript`, perintah tombol masuk lewat `WebMessage`. Mesin tanpa WebView2 Runtime otomatis jatuh ke panel GDI lama (fallback, bukan dead-end); loader `WebView2Loader.dll` (SDK vendor, lisensi BSD-3) dibundel installer. Gerbang CI probe/shape/teks/tata letak tetap hijau lewat jalur headless.
- **Pindah sesi otomatis dicabut — diganti diagnosis yang menyebut nama (2026-09-25):** trik schtasks pada butir di bawah ternyata berkedip-kedip di server multi-user pemilik: panel membuka diri di sesi milik pembuat task (runneradmin), bukan sesi RDP pemilik (xyadmin), sehingga tutup-buka berulang tanpa pernah pindah. Pindah sesi antar-user memang tidak mungkin user-mode — jadi sekarang panel berhenti mencoba: kartu CAPTURE dan host.log menyebut **nama user Windows** kedua sesi (lewat `WTSQuerySessionInformationW`), bukan cuma nomor, plus instruksi tegas: jalankan XyDesk dari sesi RDP user Anda sendiri.
- **Panel morphing (2026-09-25):** pill sidebar kini meluncur halus antar item saat halaman berganti, konten halaman meluncur masuk dengan ease, dan latar panel dapat pendar gradien halus di sepertiga atas — permukaan tidak lagi datar. Animasi berjalan 60fps hanya selama transisi (timer 16ms dimatikan sendiri saat selesai), CPU tetap senyap.
- **Installer NSIS + MSI berjenama dan terasa nyata (2026-09-25):** protes pemilik "install sebentar banget kayak ga ada yang di install" dijawab: wizard kini memakai banner brand (palet #0E1016/#7D69EE) di header dan welcome/finish, detail pemasangan menulis tiap tahap (menyalin → verifikasi → registry → shortcut), berkas inti diverifikasi ada di disk sebelum lanjut (gagal = install dibatalkan), dan halaman finish punya centang **Run XyDesk Control Panel now** yang sudah tercentang — panel langsung terbuka setelah install supaya hasilnya terlihat. MSI WiX mendapat banner + dialog bermerek yang sama.
- **(DICABUT — lihat butir di atas) Panel memindah diri otomatis ke sesi layar aktif saat host terjebak di sesi lain (2026-09-25):** tes lapangan pemilik membuktikan akar "layar hitam": log host menulis `sesi proses 1 vs sesi layar aktif 2 — BERBEDA` — aplikasi host hidup di sesi RDP/konsol lama sementara layar dipegang sesi baru, dan capture GDI antar-sesi *selalu* hitam. Sekarang begitu mismatch terdeteksi stabil (8 detik berturut-turut, bukan flapping saat login), panel memakai Task Scheduler (`schtasks /create` + `/run`, user-mode, tanpa admin) untuk memulai dirinya di sesi interaktif yang aktif, lalu instance lama membereskan diri (engine lama berhenti di `WM_DESTROY` sehingga ID perangkat tidak kembar). Bila schtasks ditolak kebijakan mesin, perilaku lama tetap: peringatan jujur di kartu CAPTURE + log. Trik antar-sesi ini belum bisa diuji di sandbox (tanpa Windows asli) — diverifikasi lewat kompilasi mingw + gerbang probe/shape/teks; tes lapangan pemilik adalah pembuktian terakhir.
- **Keyboard virtual web akhirnya jujur soal huruf besar/kecil (2026-09-25):** tombol huruf dulu selalu tampil KAPITAL walau Caps Lock mati — pemilik protes. Label huruf kini mengikuti mode seperti keyboard fisik: kecil secara bawaan, besar saat Caps **atau** Shift tertahan (Caps⊕Shift, jadi Caps+Shift = kecil lagi persis keyboard nyata); tombol Caps sendiri tetap menunjukkan CAPS/Caps.
- **Banner "tersambung tapi belum ada gambar" di web menyebut sebab RDP/layar terkunci (2026-09-25):** penjelasan kini menyebut kemungkinan host hidup di sesi RDP lama atau layar terkunci (mengirim gambar hitam), bukan hanya "pilih layar lain". Logika teksnya di `session_guidance.ts` dengan uji baru.
- **Panel Windows dapat tombol caption lengkap: perkecil, perbesar/pulihkan, plus perilaku taskbar yang benar (2026-09-25):** sejak jendela custom 23 Sep, baris judul hanya punya tombol tutup — dan "minimize" dari menu sistem malah menyembunyikan panel ke tray, sehingga tombol taskbar tidak pernah berguna. Sekarang caption punya tiga tombol dengan urutan dan rasa hover ala Windows (perkecil, perbesar, tutup; hanya tutup yang memerah): perkecil = minimize sungguhan ke taskbar (panel terdaftar di taskbar karena `WS_EX_APPWINDOW`), klik taskbar/Alt+Tab memulihkan; perbesar = panel dizoom proporsional memenuhi area kerja — jendela `WS_POPUP` berlapis tidak bisa maximize Win32, jadi zoom dikalikan ke DPI efektif sehingga seluruh tata letak dan font ikut membesar lewat jalur skala yang sudah teruji; klik lagi (atau dobel-klik area judul, kebiasaan Windows) mengembalikan ukuran dan posisi semula. Tombol baru ikut di `layout.h` sebagai satu sumber: hit-test, urutan Tab, dan probe CI; gerbang `--panel-probe` di `build.yml` kini mengharapkan `Minimize,Maximize,Close,…`. Uji tata letak Linux naik dari 79 ke 95 pemeriksaan; bentuk & teks panel diverifikasi lewat EXE mingw yang dijalankan di Wine (snapshot BMP + probe JSON lulus semua gerbang yang sama dengan CI). Teks petunjuk panel ikut menyebut dobel-klik judul. Batas jujur: interaksi caption (hover, minimize ke taskbar, tray) belum dirasakan di Windows asli — menunggu uji pemilik dengan installer.

- **Uji interop browser↔host diselaraskan dengan kebijakan gambar 20 Sep (2026-09-25):** `web/e2e/hd_interop.mjs` — satu-satunya uji yang membuktikan **Chromium nyata mendekode H264 dari encoder Rust produksi lewat RTP** — ternyata basi sejak keputusan "tanpa pita DAN tanpa crop": ia masih mengharapkan mode 1 ber-letterbox 1920×1080 dengan sudut hitam, padahal kebijakan sekarang memperkecil proporsional sumber 2336×1080 menjadi **1920×888 tanpa pita** (bentuk yang sudah dikunci unit test `video_policy`/`software_video`/`video_layout`). Siapa pun yang menjalankannya akan merah, dan itu menyembunyikan regresi sungguhan. Ekspektasi diperbarui per mode: 1 → 1920×888 (sudut atas wajib KONTEN, bukan hitam), 2 → 2336×1080 utuh, 0 → 1280×720 dengan pita letterbox yang justru WAJIB (kontrak Virtual720: keluaran persis 720p). Dijalankan sungguhan di lingkungan sesi: 3/3 mode hijau — frame terdekode, 0 drop, level H264 5.1 dinegosiasikan; bukti piksel + tangkapan layar di `docs/qa/`.
- **Host tidak lagi bisa macet menunggu pendaftaran yang ditolak (2026-09-25):** ada satu jalur nyata yang membuat PC host tampak hidup tapi tak terjangkau: setelah putus jaringan singkat, soket lamanya belum tentu sudah ditutup server, dan ketika host menyambung ulang dengan ID yang sama, hub menolak pendaftarannya (`id sudah online`) — tetapi host hanya mencatat error itu dan terus menunggu `welcome` yang tidak akan pernah datang, selamanya, sampai prosesnya di-restart manual. Sekarang error yang tiba sebelum `welcome` dianggap kegagalan pendaftaran: koneksi diputus dan host menyambung ulang sendiri dalam hitungan detik (soket zombie biasanya sudah dibersihkan server dalam waktu itu). Error setelah `welcome` tetap diperlakukan seperti sebelumnya (kabar tingkat sesi, tidak meruntuhkan signaling). Kontrak hub-nya dikunci tiga uji baru di Worker (tolak duplikat tanpa mengganggu pemegang ID, terima pendaftaran pertama, tolak hello tanpa ID) supaya perlindungan anti-pembajakan sesi di server tidak ikut "diperbaiki" orang. 189 uji Rust hijau; batas jujur: skenario zombie asli perlu uji lapangan (cabut-sambung jaringan PC host).
- **Pemeriksa kredensial relay kini membuktikan `turns:` juga (2026-09-25):** gerbang CI `check_turn_auth.py` sebelumnya hanya mengallocate relay `turn:` (UDP) dan melewati `turns:` (TLS) dengan jujur — artinya relay TLS bisa lolos gerbang tanpa pernah diuji. Sekarang keduanya diallocate sungguhan: TLS diverifikasi terhadap CA sistem (atau `--tls-ca` untuk CA sendiri; `--tls-insecure` hanya untuk lab), dan `--self-test` menguji jalur TLS terhadap server tiruan bersertifikat sendiri — termasuk membuktikan sertifikat tanpa CA tepercaya DITOLAK, karena di dalam terowongan itulah kredensial dikirim. Sekalian menutup cacat lama: URL TURN tanpa port eksplisit dulu tidak dikenali sebagai TURN dan dilewati diam-diam.
- **Infrastruktur dipulihkan setelah repo pindah akun (2026-09-25):** repository berpindah ke akun GitHub `xykal` dan semua rahasia CI di akun lama tidak ikut — akibatnya `Deploy Signaling` merah dan tidak ada workflow yang bisa jalan. Dipulihkan: 12 secret + 3 variable Actions dipasang ulang; `XYDESK_SECRET`, `ADMIN_SECRET`, dan `AUTH_SECRET` **dirotasi** (nilai lama tidak bisa diselamatkan), jadi token perangkat dan sesi login yang terbit sebelum 25 Sep tidak sah lagi — client mengambil token baru otomatis dan pengguna cukup login ulang. Kunci email Resend lama (sudah dicabut, membuat OTP dan email berita gagal diam-diam) diganti kunci baru di GitHub maupun di Worker signaling dan berita. Keystore penanda tangan APK **dibuat baru** karena yang lama hilang: APK rilisan berikutnya memakai signer baru, sehingga pemasangan lama tidak bisa diperbarui di tempat dan perlu dipasang ulang sekali. Kredensial relay TURN produksi tidak berubah dan tetap dipegang Worker. Gerbang allocate TURN akhirnya terbukti jalan di CI lewat run `Deploy Signaling` yang hijau.
- **Relay TURN host diperbaiki: sesi native gagal walau kredensialnya sah (2026-09-23):** uji lapangan paket uji menampilkan `peer connection gagal … invalid turn server credentials` lalu sesi mati. Kalimat itu datang dari webrtc-rs, dan artinya bukan "kredensial ExpressTurn salah" melainkan **"tipe kredensial yang diserahkan host tidak sah"**: `host/src/main.rs` memetakan server relay dengan `..Default::default()`, yang meninggalkan `credential_type` sebagai `Unspecified` (nilai bawaan webrtc 0.11), dan `RTCIceServer::urls()` menolak URL `turn:` ber-kredensial pada tipe itu (`ErrTurnCredentials`). Pemeriksaannya berjalan di dalam `RTCPeerConnection::new`, jadi sesi gagal sebelum satu paket pun dikirim — relay di server sebenarnya hidup (dibuktikan dengan allocate TURN sungguhan: `200 OK`, relay address diberikan; kredensial palsu ditolak `401` sebagai kontrol). Sekarang pemetaannya memakai `ice_server_from()` yang selalu memasang `credential_type: Password`, log "TURN siap" menyebut URL relay (tanpa kredensial), dan baris `error` ikut mencetak `reason`. Ikut diperbaiki sebab kegagalan itu sebelumnya tak terdengar: `hub.js` tidak merelai tipe `error`, jadi host menerima `tipe tak dikenal` sementara client tidak pernah diberi tahu apa pun — sekarang `error` direlai dua arah (tidak sesama role) dan sampai ke penanganan `error` yang memang sudah ada di web maupun client. Gerbang baru: `tool/check_turn_auth.py` (allocate TURN sungguhan, dengan `--self-test` loopback) yang kini wajib lulus di akhir `deploy-signaling.yml` — gerbang lama hanya membuktikan Worker menjawab, bukan bahwa relaynya bisa dipakai. 3 uji Rust + 2 uji hub baru; batas jujur: belum diuji di Windows asli.
- **Panel Windows dirombak jadi jendela custom (2026-09-23):** panel host berhenti memakai bentuk bawaan Windows. Sebelumnya jendela bergaris Windows biasa (`WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU`), jadi caption, border, sudut, dan bayangannya milik sistem — di Windows 10 kotak, di Windows 11 mengikuti radius bawaan yang tidak bisa diatur. Sekarang jendelanya `WS_POPUP` berlapis (`WS_EX_LAYERED` + `UpdateLayeredWindow`): judul, logo, tombol tutup, sudut membulat **radius 16**, tepi yang dihaluskan, dan bayangan semuanya digambar aplikasi sendiri, sehingga tampilannya sama di Windows 10 maupun 11. Seluruh isi panel ikut digambar (kartu status, kartu Device ID dan kode pairing, lima tombol, titik status) dengan umpan balik hover/tekan, navigasi keyboard (Tab/Enter/Esc), dan notifikasi tindakan yang hilang sendiri. Jendela digeser dari area judul; klik di luar bentuk diteruskan ke desktop. Dukungan DPI ditangani `WM_DPICHANGED`. Angka tata letak dipindah ke `packaging/native-host/layout.h` — satu sumber untuk menggambar, hit-test klik, urutan Tab, dan uji: 79 pemeriksaan di Linux (`packaging/tests/test-native-panel-layout.sh`) plus dua jalur pemeriksaan dari EXE yang dikompilasi di CI (`--panel-probe` dan `--panel-snapshot` + `tool/check_panel_shape.py`, yang membuktikan sudut benar busur radius 16, tepi halus, dan bayangan memudar). **Shell Tauri/Electron lama (`desktop/`) dihapus** atas keputusan pemilik; rujukan di CI, tool, README, `AGENT.md`, dan dokumen desain dibersihkan mengikutinya. Batas jujur: diuji di Wine + Xvfb, bukan Windows asli — tray, Alt+Tab, dan perilaku caption baru terbukti di lapangan. Tidak ada perubahan versi.
- **Installer native Windows terbukti terbentuk utuh (2026-09-23):** dua perbaikan WiX sebelumnya membuat *lint* hijau, tetapi belum satu paket pun pernah diperiksa. Sekarang `prepare-windows-installer.yml` dijalankan sungguhan dari Build yang lulus (run `35894169582` @ `0bd310c`, 11/11 job hijau) dan hasilnya diperiksa dari byte-nya: `XyDesk-x64.msi` (MSI WiX Toolset 4.0.6.0, `Template x64;1033`, memuat `XyDesk.exe`, `xydesk-host.exe`, kedua berkas lisensi English, versi 6.8.5) dan `XyDesk-x64.exe` (PE32, NSIS 3.12), masing-masing dengan `.sha256` yang cocok dengan berkasnya. Run installer `35895089978` hijau tanpa menerbitkan GitHub Release dan tanpa mengubah versi — rilis tetap keputusan pemilik. **Batas jujur:** instalasi sungguhan di mesin Windows belum dijalankan; bukti dan batasnya di `docs/qa/installer-native-2026-09-23.md`.
- **Sesi web yang tersambung tanpa gambar kini menjelaskan diri (2026-09-23, P1.1):** status `connected` berasal dari ICE/DTLS, bukan dari frame pertama — jadi pengguna bisa menatap layar gelap tanpa satu pun petunjuk. Sekarang muncul banner yang bisa ditindaklanjuti: judul "Tersambung tapi belum ada gambar", penjelasan sebab, dan tombol **Ganti layar** yang membuka panel sesi tempat layar dipilih. Ketika relay TURN juga tidak tersedia, banner menyebutnya sebagai sebab yang paling mungkin — satu-satunya penjelasan yang masuk akal di jaringan yang butuh relay. Logikanya dipisah ke `web/src/session_guidance.ts` (bukan tersebar di JSX) supaya bisa diuji: 7 uji baru, dan kalimat sebab relay di ketiga platform (host/web/client) kini berasal dari satu daftar yang sama.
- **Relay TURN akuntabel juga di client Android (2026-09-23):** `_fetchTurn()` yang menyusutkan SEMUA kegagalan menjadi daftar kosong digantikan `_fetchRelay()` yang membaca status HTTP + `reason` + `hint` dari `/turn-ice` (kontrak yang sama dengan Worker versi baru), lalu menyimpannya sebagai `TurnRelay` yang ikut mengalir di `SessionStats` (`relayOk`, `relayServers`, `relayReason`, `relayHint`). Panel sesi mendapat baris "Relay TURN", dan banner "belum ada gambar" menyebut relay ketika memang itu sebab yang paling mungkin — sebelumnya pengguna hanya melihat layar gelap tanpa penjelasan di jaringan yang butuh relay. Kalimat sebabnya sengaja sama persis dengan web dan host, karena ketiganya berbicara ke endpoint yang sama. 9 uji baru (`test/webrtc/relay_test.dart`); `flutter analyze` bersih dan `flutter test` 77/77 di Flutter 3.44.9 — versi yang sama dengan yang dipakai CI.
- **Installer MSI bisa dibangun lagi (2026-09-23):** jalur MSI yang dikirim bersama NSIS sejak perubahan installer dual-format ternyata belum pernah lulus, dan job lint barunya yang membongkarnya — dua kesalahan di satu perintah `wix build`. Pertama, `WIX0144: The extension 'WixToolset.UI.wixext' could not be found` (run `35879908181`): WiX v4 terpasang, tetapi extension UI tidak pernah masuk cache global — tidak ada satu pun `wix extension add` di seluruh workflow. Kedua, setelah extension tersedia, `WIX0118: Additional argument '-dSourceDir=…' was unexpected` (run `35889077917`): parser WiX v4 hanya menerima define sebagai token terpisah. Kini `build.yml`, `release.yml`, dan `prepare-windows-installer.yml` memasang `WixToolset.UI.wixext/4.0.6` (versi disamakan tool WiX-nya, dengan `throw` bila gagal) dan memakai bentuk `-d SourceDir=…`; kedua kesalahan direproduksi dan diperbaiki di lingkungan sesi memakai WiX v4.0.6 asli, lalu dibuktikan hijau di runner.
- **Gerbang CI host pulih (2026-09-23):** `host/src/main.rs` tidak lolos `cargo fmt --check` sejak commit sebelum ini, sehingga job "Uji Logika Host (Rust)" merah di langkah `Cek format` (run `35879908181` dan `35772171870`) — dan karena job `windows` menunggu job itu, seluruh rantai Windows ikut tertahan. Kode diformat ulang; `cargo fmt --check`, `cargo clippy --all-targets -- -D warnings`, dan `cargo test --all-targets` hijau di lingkungan sesi.
- **Relay TURN yang bisa dipertanggungjawabkan (2026-09-23):** relay berhenti menjadi kegagalan yang hilang tanpa jejak, di tiga sisi sekaligus. **Backend** — penolakan `/turn-ice` tidak lagi teks `forbidden` kosong: 403 membawa `{error, reason, hint}` (`no-credentials`, `token-invalid`, `ticket-invalid`, `ticket-revoked`) dan gangguan AuthStore dijawab 503 `turn-auth-unavailable`, dibedakan dari penolakan karena gangguan sesaat tidak boleh terbaca sebagai akun dicabut. **Web** — `turnIce()` mengembalikan sebabnya (`no-servers`, `providers-failed`, `turn-not-configured`, `turn-forbidden`, `turn-auth-unavailable`, `network`, `http-<status>`), sesi menyimpan status relay (`relayState`, `relayServers`, `relayReason`, `relayHint`), dan panel Statistik menampilkan baris "Relay TURN" plus catatan ketika relay tidak ada — sebelumnya semua kegagalan menyusut jadi daftar kosong yang sama. **Host Engine** — modul baru `host/src/relay.rs` membaca balasan `/turn-ice` lengkap (termasuk `reason`/`hint` dari Worker), menerjemahkan sebabnya, dan melaporkan hasil terakhir di `/status` sebagai blok `relay`; kegagalan tidak lagi hanya satu baris yang hilang di log. Ketiadaan relay tetap **tidak pernah** menggagalkan sesi: banyak jaringan memang tersambung langsung. 13 uji baru di Worker, 12 di web, 7 di host. Tidak ada perubahan versi.

- **Packaging Windows dual-format dan host diagnostics:** workflow installer kini menghasilkan MSI WiX dan NSIS EXE x64 dari payload native C++ + Rust yang sama, dengan EULA English, third-party notices, dependency manifest, dan checksum masing-masing. Panel native menulis stdout/stderr engine ke `%LOCALAPPDATA%\\XyDesk\\host.log`, menampilkan exit code saat engine berhenti, dan menyediakan tombol Buka log host. Error offer/answer/ICE WebRTC tidak lagi langsung mematikan proses Rust; error dicatat dan sesi dikembalikan ke keadaan menunggu.

- **Perbaikan kursor + UX (2026-09-20 malam):** BUG KRITIS diperbaiki — kursor absolut terkunci di pojok kiri atas karena piksel desktop diberikan ke pemetaan fraksi (CaptureRect::point); ditambah CaptureRect::pixel translasi 1:1 + tes regresi. Desktop kini satu pintu: hanya shortcut "XyDesk Control Panel"; launcher manual dan Virtual720 pindah ke Start Menu (installer membersihkan shortcut desktop lama saat upgrade). Web: tombol HUD mouse dan tombol mapping custom default bulat penuh (panel & keyboard tetap kotak); keyboard virtual dirombak putih ala keyboard HP — menempel penuh di dasar layar (tidak melayang), tap instan tanpa transisi; riwayat: klik kartu membuka halaman detail (wallpaper besar + spesifikasi + waktu/durasi) dengan tombol Hubungkan eksplisit, bukan langsung reconnect. Tidak ada rilis versi pada perubahan ini.

- **Revisi keputusan operator (2026-09-20): tanpa pita DAN tanpa crop.** Kebijakan crop 16:9 (2026-09-19) dicabut karena memotong konten di lapangan. Sekarang: host otomatis meminta mode desktop 16:9 yang didukung (bila target persis 1920×1080/1280×720 tidak ada, mode 16:9 lain yang didukung dipilih otomatis; desktop yang sudah 16:9 dibiarkan); frame selalu memuat SELURUH desktop pada rasio aslinya, diperkecil proporsional tanpa upscale. Virtual720 tetap wajib persis 1280×720. Perbaikan paket manual: log fit tidak lagi kosong (redirect `*>&1`), baris `[primary]` kosong kini mencatat peringatan sebenarnya, Console-Primary memilih monitor virtual berdasarkan identitas driver (bukan resolusi saat ini), dan Start saat host sudah berjalan menampilkan status alih-alih error merah. Tidak ada rilis versi pada perubahan ini.

- Panel kontrol GUI sementara (Console-Panel.ps1 + shortcut desktop "XyDesk Control Panel"): Start/Stop/Status/Fit/Kredensial/Log lewat klik, membungkus Console-Virtual720.ps1 (satu prompt UAC, jalur scheduled task tidak berubah). Referensi engine stardesk.net ditinjau; tidak ada kode/aset yang disalin. Tetap tanpa rilis versi.

- **Keputusan mutlak operator (2026-09-19): tanpa driver virtual, stream wajib 16:9 tanpa pita.** Letterbox diganti crop 16:9 (center horizontal, anchor bawah agar taskbar terlihat), tanpa upscale; pemetaan input mengikuti crop sehingga klik tetap presisi; telemetri menambah cropRect dan label encoder (nvenc/openh264) yang juga tampil di panel Statistik web. Manajer console mendapat -Action Fit untuk menarik jendela nyasar ke layar stream dalam satu perintah. Tidak ada rilis versi pada perubahan ini.

- **Perbaikan lapangan UXHD:** rail kontrol kembali kolom kanan; checkbox izin preview dihapus (izin mengingat otomatis, tetap bisa dilupakan/dicabut); tombol pointer mapping dipisah dari rail dan diberi tint terlihat; layout lama sekali dimigrasi menambah tombol mouse/scroll yang hilang; monitor virtual 720p dijadikan primary di sesi console supaya jendela aplikasi terbuka di layar yang di-stream; thread encoder software 4 untuk latensi encode lebih rendah. Paket baru menyusul dengan hash terpisah.

- **UXFINISH:** scoped member/guest remembered history with automatic resume; automatic wallpaper without opt-in/manual capture panel; WebP hero; border/name mouse/key controls with automatic chord labels and size/radius; pointer-down keyboard; adaptive bitrate presets and capability-gated30/60FPS through real encoder policy; bundled pinned VDD with explicit administrator setup preserving existing devices/configuration and no reboot/RDP changes. Validation and exact package revision recorded separately; no claim of zero lag or user-machine validation.

- **Console/guest repair (Windows35421194650 + NSIS35421544833 SUCCESS):** suppressed native pointer composition + real mouse events; managed host ticket refresh/heartbeat and console process-tree supervision; no guest duration cap; short connection tickets with remembered, host-revocable browser grants. Sourcece95f51; Linux176/Windows165, web84/backend188, PS5/PS7 kill-on-close PASS. Web/backend deployed; user verified real console720 with previous build, new cursor/reconnect still requires field verification. No RDP/driver/security-policy changes. `docs/qa/console-guest-2026-09-19.md`.

- **VDD3010 hotfix (Windows35412599966 SUCCESS; PS5/PS7 masing-masing35 assertions, WhatIf dan catalog trust PASS):**3010 adalah sukses-butuh-reboot, bukan penolakan. Jangan rollback device accepted0/3010; simpan pending state, jangan ulang install pada boot yang sama. Resume opt-in memvalidasi ownership, pinned driver bytes, config dan existing hardware ID sebelum meneruskan setup lama. Tanpa reboot/service/RDP restart otomatis. Bug lama menghapus device setelah3010 pada laporan pengguna; visibleVirtual kosong sesudah cleanup bukan bukti session isolation.

- **VDISPLAY720 closure:** source691dd09, Windows35408521780 **SUCCESS Linux168/Windows157**; setup/native helper WhatIf + real pinned archive/Windows catalog trust PASS. NSIS35408977237 **SUCCESS** termasuk shortcut Virtual720. Belum driver install/virtual capture di runner atau RDP pengguna; integration candidate, bukan bukti lock mutlak. Tidak deploy web/backend. `docs/qa/virtual720-package-2026-09-18.json`.

- **Virtual720 — paket uji siap:** profil opt-in1280×720 dengan adapter identity + current-session monitor verification, canvas720 terkunci, monitor lain/fallbackRDP ditolak. Discovery lama tidak lagi menebak dari resolusi/file, install/service restart/manager command tebakan dihapus. Setup eksplisit memakai driver resmi terpinSHA dan Windows catalog trust, tanpa security bypass/reboot/tscon; driver console belum tentu terlihat dari RDP. Tidak mengklaim field proof atau session-isolation bypass.

- **HOST-FINISH closure:** final source56317a2 (runtime43fc35c), Windows run35403913522 **SUCCESS: Linux164/Windows153** termasuk real-Opus roundtrip; NSIS35404435326 **SUCCESS** install/reinstall/uninstall. Installer baru SHA256704f2adda398d034042fa22648fd07e110359a333a757c30826405b01e5f91fd. Web76/browser2viewport + live asset/OAuth/bindings PASS, versi1d669576; backend55cdda7c tetap. Petunjuk installer diperbaiki dan paket43fc35c disupersede. Tidak version bump/official release/driver/RDP access. Bukti `docs/qa/host-finish-{package,production}-2026-09-18.json`; batas field-test di `host-finish-2026-09-18.md`.

- **Host finish — siap installer + web live:** permintaan otomatis mode desktop16:9 pada sesi terotorisasi, hanya mode terdaftar + CDS_TEST, baca ukuran kembali dan status penolakan/override. Tidak registry/driver/restart RDP; opt-out launcher -KeepDesktopResolution. Web menampilkan requested/observed, bukan label HD palsu. Tambah Windows real-Opus roundtrip44.1/48/96kHz. Menyertakan patch audio bd75757 dan input ec0f047.

- **Input queue (kode, belum installer):** full queue tidak mematikan input; backpressure async + batal saat disconnect, coalesce posisi absolut berurutan tanpa melewati klik/key-up. Host Linux **161 tests PASS**, format PASS; belum pembuktian latency Windows/RDP. `docs/qa/input-queue-2026-09-18.md`.

- **Audio repair (kode; belum build Windows/deploy):** format WASAPI lengkap + packetizer streaming tepat 960 frame, perbaikan drain/silence dan frame render, mic tanpa expiry 30 detik + queue bounded, web replaceTrack/cancel cleanup, virtual-input requirement tanpa speaker fallback/driver install. Linux **156**, web **76**, format/build web dan Windows ABI checker PASS. Bukti/batas: `docs/qa/audio-repair-2026-09-18.md`. Keluhan input lambat dan otomatis 16:9 belum selesai.

### CONTROL-REFINE — web sudah live
- Mapping transparan border+label, custom picker/search tanpa select browser, tombol tambahan dan shortcut; tap kiri/tahan500ms kanan di dua mode; HUD rounded kiri/kanan/scroll/Windows/switch.
- Riwayat baris wallpaper+nama+chevron, reconnect pairing inline mempertahankan perangkat dan route/history tanpa menyimpan password. Gerak absolut belum terkirim digabung saat antrean penuh; tombol/release tetap berurutan.
- Web70/build/Chromium2viewport+gesture+F1+reconnect/privacy PASS. Tidak mengubah host/RDP atau menyimpulkan sebab delay jaringan; mode desktop16:9 masih perlu detail RDP pengguna. Bukti `docs/qa/control-refine-2026-09-18.md`.
- Deploy web-only diizinkan user: source87427ae, versi Worker740345c3. Hash JS/CSS cocok, HTML cocok tanpa beacon Cloudflare, OAuth/bindings tetap; Chromium produksi portrait/landscape membuktikan kartu horizontal dan pairing inline tanpa mengirim password/pairing. Backend/installer tetap. Bukti `docs/qa/control-refine-production-2026-09-18.json`.

### LETTERBOX-LATENCY — paket uji tervalidasi; web/backend sudah deploy
- Menggantikan interpretasi HD/preview sebelumnya: kanvas tepat1280×720/1920×1080 dengan desktop utuh + pita hitam, pemetaan input padding, cursor Windows di video GDI/DXGI/WGC tanpa panah web pengganti, wallpaper otomatis sekali saat koneksi dengan opt-out/cancellation.
- Gap frame H264 memerlukan IDR baru, antrean lama dibuang, RTP mengikuti timestamp capture; telemetry host encode/queue/write dan web RTT/jitter/buffer/decode/loss dipisah. Wallpaper background dibatasi buffering/pacing/deadline.
- Linux152, web66, Windows release141, NSIS builder7/install-reinstall-uninstall, narrow Windows cross-check dan Chromium real decode3mode/UI2viewport+privacy PASS. Installer baru source37c5eea, Windows35391667677/NSIS35392306102. Bukan bukti runtime RDP/Android atau bebas lag. Versi tetap. Bukti paket `docs/qa/letterbox-package-2026-09-18.json`.
- Rollout produksi diizinkan operator: backend55cdda7c/web8f093d6d. Backend184/web66 dan SQLite runtime PASS; hash JS/CSS sama dengan build, HTML cocok setelah mengabaikan beacon analytics Cloudflare yang sudah ada. OAuth/bindings dipertahankan, auth tanpa login401, React produksi portrait/landscape PASS. RDP/host tidak diubah; login akun dan streaming perangkat nyata belum diuji. Bukti `docs/qa/letterbox-production-2026-09-18.json`.

### Perbaikan dalam validasi — HOSTGEOMETRY
- Host: konteks DPI proses/thread, koordinat fisik monitor capture untuk pointer absolut, pemilihan WGC melalui nama perangkat (bukan indeks nol ke API indeks satu), origin fallback GDI, dan deteksi perubahan geometri GDI/RDP setiap 500 ms.
- Web: klik Konek meminta fullscreen dari gesture lalu mencoba landscape; browser yang menolak mendapat petunjuk manual. Retry otomatis tidak meminta fullscreen.
- H264 menerima batas decoder ternegosiasi Level3.1/4.0/5.1, pilihan720p/1080p/asli dengan aspek utuh tanpa upscale, native maksimal4096×2160/15fps. Chromium nyata sudah mendecode1920×1080 dan2336×1080 dari encoder host melalui RTP sintetis.
- Feedback cursor Windows20Hz, overlay tidak diduplikasi pada WGC; input down yang sukses dilepas saat channel tutup, antrean input dibatasi, tombol Pause tidak salah menjadi Ctrl.
- Preview manual kini wallpaper Windows lokal HD≤1920×1080/256KiB, bukan frame aplikasi; path dan decoder dibatasi. Transfer chunk, validasi JPEG, penyimpanan akun bertransaksi/chunk dan kuota browser diselaraskan tanpa mengubah auth/OAuth.
- NSIS hanya menerima artifact run engine sukses dari SHA yang sama, dengan checksum ZIP/engine; tidak lagi memakai engine lama. Windows137tes dan NSIS install/reinstall/uninstall sudah lulus pada source4a3e75a. Belum deploy; uji RDP pengguna tetap diperlukan. Bukti: `docs/qa/hostgeometry-checkpoint-2026-09-18.md`.

- Kontrol web: preview diambil/ganti manual dan dipakai ulang per ID, riwayat satu kartu per perangkat; keyboard memiliki tombol tutup atas dan melepas modifier. Mapping menambah shortcut kombinasi, F1–F24/numpad/navigasi/tanda baca, mouse samping dan scroll horizontal; ownership keyboard fisik/virtual/mapping disatukan. Preset WASD digital tersedia. HD dan presisi input Windows belum dinyatakan selesai.

- Backend sesi baru: ticket client terikat identitas/generasi akun; WS dan penerbitan TURN memeriksa pencabutan. Alarm Hub memeriksa client diam tiap ~15 detik dan mengirim bye ke host sesuai nonce koneksi. Ticket/koneksi legacy tetap kompatibel; belum ada pemutusan massal atau bukti media Windows.

- Backend: validasi identitas/ban/generasi JWT akun pada profil, riwayat, mutasi akun dan penerbitan token signaling; token legacy sehat tetap berlaku. Login dan perubahan akun transaksional, OTP sekali pakai saat konkurensi; respons auth tidak boleh dicache. Tidak mencabut ticket signaling yang sudah terbit atau otomatis memutus sesi P2P aktif.

- Sesi web: loading di dalam viewport, ukuran panah/sensitivitas, editor mapping keyboard/mouse dengan posisi/ukuran bebas, riwayat tamu lokal dan akun di server dengan preview opt-in. Pemilihan HD dan resume belum tersedia.

- Web sesi: panah lebih besar/tidak terpotong di tepi, tombol Temukan panah, pemulihan layout; audio play/retry dan mute lokal, diagnostik audio, preferensi terkirim setelah channel siap. URL memakai ID acak (belum resume saat refresh).

- Host software: filter bilinear menggantikan nearest-neighbor tanpa mengubah batas H.264 Level3.1. Antrean mouse mempertahankan posisi sebelum klik/drag.

- Kontrol web HP: trackpad default, panah lokal, koordinat gambar tanpa pita hitam, tap/cancel/dua jari dan tahan-geser. Label kualitas menunjukkan target dan batas encoder, bukan janji resolusi/fps.


### Fixed
- H264 software: batasi ukuran kirim ke 1280x720 proporsional, 30fps dan 14Mbps sesuai Level3.1 yang diiklankan, bukan mengirim capture besar dengan SPS Level5.1. Desktop RDP tidak diubah; log membedakan resolusi capture/kirim dan SPS.
- Sesi web memenuhi viewport saat Connected, tidak lagi terkotak 16:9 dalam halaman. Fullscreen native diminta dari tombol untuk seluruh surface beserta kontrol; fallback tetap memenuhi viewport. Toolbar landscape tidak terpotong, scroll halaman dipulihkan saat sesi berakhir.
- Pemutar web: pisahkan track video dari audio, panggil play setelah sesi tampil, sediakan retry melalui gesture, dan laporkan keadaan elemen video. Penghitung decode yang tidak tersedia tidak lagi ditampilkan sebagai nol.
- Video host: baca RTCP sender agar interceptor memproses NACK/retransmisi dan PLI/FIR meminta keyframe (dibatasi 500ms). Statistik web memilih video utama, bukan RTX, serta menampilkan byte/paket/frame/keyframe dan PLI/NACK tanpa pembulatan Mbps.
- Host RDP: benar-benar memilih GDI, bukan hanya mencetak fallback sambil menjalankan DXGI; jalur RDP tidak memanggil pembuatan/pemasangan virtual display. Diagnostik membedakan sampel pojok dari seluruh RGB, dan nol frame tidak lagi menyuruh tscon.
- Host video: penantian frame kini dibatasi agar state Connected dapat menyalakan capture Windows yang belum menghasilkan frame. Koneksi tertutup tetap menghentikan pump saat sumber diam; jadwal penyelamatan IDR tidak dipercepat oleh tick state.
- Launcher host uji memakai SHA-256 .NET agar tidak bergantung pada autoload Get-FileHash di Windows PowerShell; installer NSIS membedakan direktori default dan /D eksplisit tanpa mengabaikan pilihan pengguna.
- Host menutup media dan mencabut izin pairing saat signaling putus; Hub mengaitkan answer dengan nonce socket agar kick/close memberitahu peer terkait, termasuk client yang mengabaikan close.
- Web: cleanup pada fase terminal, antrean SDP/ICE, validasi asal pesan media, UUID client, tipe clipboard biner, track tanpa stream, dan label codec dari statistik yang benar.
- Host LAN-only menerima STUN kosong; lockfile diselaraskan dengan manifest 6.8.5 yang sudah ada; koreksi parser FU-A pada tes loopback.
- Sesi admin setelah aktivasi password memakai cookie HttpOnly host-only, pemeriksaan Origin pada POST, dan pencabutan sesi saat logout; JWT Google lama ditolak setelah migrasi.
- OAuth admin memakai client khusus melalui `ADMIN_GOOGLE_CLIENT_ID`; client web/APK tidak diubah dan tidak menjadi fallback untuk login admin. Konfigurasi publik build admin dipisahkan dari secret.
- Worker: entrypoint runtime hanya mengekspor handler dan kelas Durable Object; konstanta helper untuk tes tidak lagi membuat startup workerd gagal.
- Backend admin: hapus fallback identitas Google palsu dan bypass captcha; pakai verifikasi Google dengan signature helper yang benar, hostname Turnstile, dan sesi admin audience khusus satu jam.
- Maintenance backend: transaksi batch + audit log + pemeriksaan revision; galat storage menjadi 503 dan konflik menjadi 409, tidak sukses palsu. Pembacaan publik tidak mengungkap email pengubah.
- Statistik backend: metrik tanpa sumber menjadi null; kegagalan pembacaan tidak dilaporkan sebagai data kosong/nol.
- Panel: login GIS asli dengan penanganan captcha kedaluwarsa, logout saat 401, simpan maintenance satu request; halaman Backend/Server memakai pemeriksaan RPC nyata. Purge belum tersedia menghasilkan 501.
- Admin: hapus fallback statistik dummy; tampilkan kegagalan API dan perbarui statistik setiap 15 detik setelah tersedia token.
- Dashboard: hapus grafik, tren, latensi, dan status kesehatan statis yang menyerupai hasil pengukuran.
- Maintenance: kontrol dikunci sebelum status dimuat; edit sebagai draft, simpan dalam satu batch, lalu verifikasi baca ulang. Kegagalan meminta muat ulang sebelum mencoba lagi.
- Logs: kegagalan HTTP ditampilkan sebagai galat, bukan daftar kosong yang tampak sukses.

### Added
- Installer NSIS per-user untuk paket engine uji Windows x64: wizard, shortcut Desktop/Start Menu, entri Apps, dan uninstaller berbasis daftar berkas. Folder lain/identitas uji tidak dihapus; tanpa autostart, driver, service, atau deploy.
- Paket uji host Windows x64 terpisah: workflow build-only MSVC, smoke executable, launcher manual dengan verifikasi PE/checksum dan identitas uji terisolasi; tanpa Test Lab, installer, rilis, atau deploy otomatis.
- Harness remote lintas runtime memakai Worker/SQLite, binary host Rust, dan Chromium: pairing, decode/render H264, kontrol bitrate, kick dua arah, reconnect, dan putus manual. Bukti serta batas Windows/TURN di `docs/REMOTE_CORE_QA.md`.
- Setup satu kali username/password + TOTP, kode pemulihan sekali pakai, limiter login, enkripsi seed TOTP, verifier password ber-pepper, dan audit login paralel yang tidak saling menimpa. Google ditutup hanya setelah akun baru terverifikasi; tidak membuat password pemilik otomatis.
- `cloudflare npm run test:runtime`: uji bundle pada runtime SQLite lokal, mencakup transaksi, konflik konkurensi, audit, health, dan pembatasan akses.
- Endpoint admin health read-only untuk Worker/AuthStore/Hub; status engine dinyatakan belum tersedia.
- Panduan konfigurasi dan rollout di `admin/README.md`; total 28 tes backend admin baru dan 3 tes API panel tambahan.
- Dua belas tes regresi API admin memakai Node test runner dan TypeScript yang sudah tersedia.

## [6.11.1] - 2026-10-01

- **Host + klien, mic virtual (1 Oktober):** host kini mengirim `micInput.reason` (`no-driver` / `no-endpoint`) sehingga klien native menampilkan petunjuk yang tepat, bukan "perbarui host"; host tanpa hak admin mencoba memasang VB-CABLE lewat UAC satu kali per versi saat start (penanda di folder konfigurasi), bukan hanya saat sudah admin. Mulai versi ini host dan klien dikerjakan satu agen (koordinasi lintas agen di #31/#34 ditutup).

## [6.11.0] - 2026-10-01

- **Klien Android native (1 Oktober, putaran 3):** status online diperbaiki dari akarnya — hub signaling mendapat pesan `presence` (jawab hanya ID yang ditanya, maks 50) karena `list` sengaja kosong demi privasi, klien bertanya tiap 10 d; blur teks saat pindah halaman dihilangkan (transisi root tanpa skala); cuplikan PC JPEG q92 + filter tinggi; 24 pengaturan baru di Akun > Sesi dan Akun > Umum: orientasi, rel otomatis sembunyi, resolusi awal, batas bitrate, getaran sesi, audio bisu/mic/clipboard saat mulai, percepatan kursor, kecepatan scroll, ketuk dua jari, gestur tiga jari, ukuran & posisi HUD, posisi dok, ingat perangkat bawaan, sambung otomatis ke PC terakhir, selalu relay, simpan cuplikan, tampilkan ID, hapus riwayat otomatis, ukuran teks, interval cek berita.

- **Klien Android native (1 Oktober, putaran 2):** tab Riwayat dilebur ke Perangkat (riwayat sesi per PC di dalam detail kartu, ringkasan pemakaian di atas); Perangkat punya mode daftar/grid; panah detail berputar ke bawah saat terbuka; halaman Sambungkan punya saklar "Ingat perangkat ini" dan password tersimpan dihapus otomatis bila host menolaknya; status online tidak lagi berkedip offline sesaat setelah sesi (butuh dua daftar berturut-turut) dan disegarkan saat kembali ke aplikasi; cuplikan PC 1280 px dengan cache memori dan dekode di luar thread UI (gulir lebih mulus); dok kontrol di dalam sesi dengan katalog 50+ tombol (tetikus, tombol, modifier tahan, arah, F1–F12, kombinasi, media) yang bisa dipilih dan diukur dari rel sesi; halaman Akun disusun ulang (Umum, Sesi, Privasi & Keamanan, Tentang); catatan rilis dan lisensi pihak ketiga dibaca di dalam aplikasi; keamanan: `allowBackup=false`, WebView tanpa akses file/konten dan hanya meneruskan `mailto:`/`tel:` ke luar.

- **Klien Android native (1 Oktober):** tab Beranda jadi "Koneksi" dan email tidak lagi tampil di halaman Sambungkan; Riwayat satu baris per PC (sesi terakhir + jumlah sesi), tanpa mode grid; S&K dan Kebijakan Privasi dibaca di dalam aplikasi dari `docs/LEGAL.md` yang disalin ke aset saat build; halaman Izin menampilkan status mikrofon/notifikasi yang sebenarnya dan bisa meminta izin langsung; trackpad: dua jari = scroll saja (cubit-zoom dan tahan-klik-kanan dihapus, klik kanan = ketuk dua jari); HUD sesi ditumpuk ke bawah (label kecil lalu nilai) dengan pilihan item di Pengaturan > Sesi.

## [6.10.0] - 2026-10-01

- Satu update besar klien Android native: Berita tersambung ke news worker (komentar, suka, notifikasi), riwayat grid/daftar + detail sesi bergrafik, host alias/favorit/online-offline realtime, mic HP ke PC dengan DSP di host, mode presentasi, pengaturan lengkap, peramban internal. Rincian: `changelogs/6.10.0.md`.

## [6.9.0] - 2026-09-30

- **Client Android ditulis ulang native.** Flutter (`lib/`, `android/`, `test/`, `pubspec`) dihapus; klien kini Kotlin + Jetpack Compose tanpa Material di `android-native/`, dengan pustaka C++ sendiri `libstreamxy` (protokol input, telemetri latensi) dan decoder H.264 low-latency langsung ke Surface (fallback otomatis). Paket `id.xyverse.xydesk` — pasang ulang, tidak bisa update di atas APK Flutter.
- **XyDesk Virtual Display Adapter & instalasi driver otomatis (NSIS + MSI).** Controller native Win32 C++ `xydesk-vdd-ctl.exe` (SetupAPI + `newdev.dll`) membuat Root-Enumerated PnP Device Node (`Root\MttVDD` / `Root\IddSampleDriver`), mengikat driver UMDF2 IddCx secara paksa (`UpdateDriverForPlugAndPlayDevicesW`), menamai perangkat `XyDesk Virtual Display Adapter` di Device Manager, menghasilkan blok EDID 256-byte kustom (`XyDesk VDD`, manufaktur `XYD`), dan memasang matriks 26 resolusi (720p..8K, 16:9, 16:10, 21:9, 32:9, Tablet/Mobile) × 8 refresh rate (30Hz..240Hz) beserta driver Virtual Audio VB-CABLE langsung saat setup berlangsung.
- Sumber versi pindah dari `pubspec.yaml` ke berkas `VERSION`; Build membangun APK native per ABI (`XyDesk-arm64-v8a.apk`, `XyDesk-armeabi-v7a.apk`, `XyDesk.apk`) bertanda tangan keystore rilis dari secret. Rincian: `changelogs/6.9.0.md`.

## [6.8.8] - 2026-09-29

- **Encoder hardware AMD/Intel lewat Media Foundation.** Host mencoba NVENC, lalu MFT (`host/src/mft.rs`), baru openh264; satu pemilih untuk jalur WGC/GDI/DXGI. `meta.encoder` bernilai `nvenc` / `mft` / `openh264`. Belum diuji di GPU nyata; fallback aman.
- Engine Rust dilink CRT statis (tanpa `vcruntime140.dll`); workflow manual `host-check.yml`; `release.yml` mendapat input `draft`. Rincian: `changelogs/6.8.8.md`.

## [6.8.7] - 2026-09-28

- **Resolusi dan FPS otomatis — pengguna tidak perlu mengatur apa pun.** Preset **Otomatis** di web dan APK kini memilih resolusi (720p/1080p) dan FPS (30/60) sendiri dari tiga fakta: sisi terpanjang layar perangkat (HP 720p tidak pernah diminta 1080p), encoder host dari `meta.encoder` (openh264 software tidak pernah diminta 60 FPS; 1080p hanya selama latensi pemrosesan tetap di bawah 90 ms), dan statistik berjalan (loss, antrean jitter, RTT membengkak, FPS yang benar-benar tiba, estimasi glass-to-glass dari probe 6.8.6). Mulai ringan, naik satu tingkat tiap 12 detik stabil, turun begitu keteteran, berhenti mencoba setelah dua kali gagal — tiap perubahan adalah restart encoder di host, jadi dibuat jarang. Logika identik di `web/src/auto_preset.ts` dan `lib/webrtc/auto_preset.dart` dengan skenario tes yang sama (9 + 8). Panel Video web memperoleh sakelar **Otomatis / Manual** yang menampilkan keputusan dan alasannya; setelan manual lama tetap ada di balik "Manual".
- **APK: setelan Quality/Resolution akhirnya benar-benar dikirim ke host.** Sebelumnya `StreamQuality`, bitrate, dan label "1080p60 (FHD)" / "1440p60 (QHD)" di aplikasi Android tidak pernah mengirim pesan 0x0A/0x0B/0x0C/0x0F apa pun; host selalu memakai default 720p30. Kini `RtcService.sendVideoPrefs` mengirimnya saat meta pertama tiba dan saat pengguna mengganti preset di tengah sesi (`ref.listenManual`). Label preset disamakan dengan yang benar-benar diminta: Medium 720p30, High 1080p30, Ultra 1080p60. Kartu Quality menampilkan keputusan otomatis saat ini.
- Tidak ada perubahan host/Rust dan protokol; host 6.8.6 ke bawah tetap kompatibel (pesan lama).

## [6.8.6] - 2026-09-28

- **Latensi mulai diukur, bukan diklaim.** Panel Statistik web mendapat bagian **Latensi**: per frame lewat `requestVideoFrameCallback` — terima→tampil p50/p95/max, jarak frame p95. **Host** menstempel waktu capture ke tiap paket video (RTP header extension `abs-capture-time`, `host/src/abs_capture_time.rs`) dan web memintanya dinegosiasi; di Chrome/Edge baris latensi menjadi **"Layar ke layar"** terukur langsung (capture DXGI → tampil). Tanpa itu, tampil "Perkiraan (tanpa encode PC)" = RTT/2 + terima→tampil, dilabeli jujur sebagai batas bawah. Tombol **Unduh laporan latensi** menghasilkan JSON `xydesk-latency-report/1` untuk dilampirkan ke laporan lag. APK: panel sesi menampilkan **Buffer video** dan **Decode / frame**. Client/host lama tidak terpengaruh (extension hanya dikirim bila dinegosiasi). Verifikasi: 8 unit + loopback produksi (paket RTP membawa NTP capture yang cocok jam), 209 test host lulus, clippy/fmt bersih; web 7 test probe + 42 rtc, `tsc -b` bersih.
- Root repo dirapikan: bahan artikel → `docs/articles/`, diagnosis/ringkasan → `docs/reports/`, `CONTROL_MAPPING_PROFILE.md` & `GUEST_SESSION_COUNTDOWN.md` → `docs/`; item HANDOFF watchdog no-frame yang sudah selesai sejak lama ditutup. Rincian: `docs/LATENCY.md` §2a–2b.

## [6.8.5] - 2026-09-13

> Build 59. Semua tombol admin nyata — ban/role/revoke/kick/terminate/purge/logs Hibernation+storage, gada dummy.

Lihat detail di [changelogs/6.8.5.md](changelogs/6.8.5.md).

## [6.8.4] - 2026-09-13

> Build 58. Realtime nyata — Hub + AuthStore live, gada dummy placeholder.

Lihat detail di [changelogs/6.8.4.md](changelogs/6.8.4.md).

## [6.8.3] - 2026-09-13

> Build 57. Admin endpoint nyata — Worker `/admin/*` live, login Turnstile + Google, stats/devices/maintenance konek web+apk.

Lihat detail di [changelogs/6.8.3.md](changelogs/6.8.3.md).

## [6.8.2] - 2026-09-13

> Build 56. Rebuild admin — Vite kotak, no rounded, lucide, login Turnstile nyata, konek web+apk.

Lihat detail di [changelogs/6.8.2.md](changelogs/6.8.2.md).

## [6.8.1] - 2026-09-13

> Build 55. Fix E0599 Write import — `use std::io::Write` untuk `writeln!`.

Lihat detail di [changelogs/6.8.1.md](changelogs/6.8.1.md).

## [6.8.0] - 2026-09-13

> Build 54. Super lengkap — Admin dashboard (statistik, maintenance, control user/mesin/hosting/backend/server) + Hero 2D kartun responsif.

Lihat detail di [changelogs/6.8.0.md](changelogs/6.8.0.md).

## [6.7.18] - 2026-09-13

> Build 53. CRITICAL — Fix panic no reactor running, window gagal kebuka. `tokio::spawn` → `tauri::async_runtime::spawn` + `start()` di `setup`.

Lihat detail di [changelogs/6.7.18.md](changelogs/6.7.18.md).

## [6.7.17] - 2026-09-13

> Build 52. Fix Build Desktop writeln — `writeln!` macro, bukan trait. PowerShell quote fix.

Lihat detail di [changelogs/6.7.17.md](changelogs/6.7.17.md).

## [6.7.16] - 2026-09-13

> Build 51. Windows Tauri Debug + License English + Adaptive No Box — window log, license EN, adaptive transparan.

Lihat detail di [changelogs/6.7.16.md](changelogs/6.7.16.md).

## [6.7.15] - 2026-09-13

> Build 50. Tauri Window Fix + Logo Transparan (No White BG) — window pasti show, semua logo transparan identik README.

Lihat detail di [changelogs/6.7.15.md](changelogs/6.7.15.md).

## [6.7.14] - 2026-09-11

> Build 49. Driver Offline Bundle + 1-Klik Auto-Download (No Manual) — fix hitam tapi konek tanpa PowerShell manual.

Lihat detail di [changelogs/6.7.14.md](changelogs/6.7.14.md).

## [6.7.13] - 2026-09-11

> Build 48. Installer Windows (Inno Setup), Logo README identik all platform, Windows blink fix, Pengaturan lengkap (Tampilan/Bahasa, Kontrol/Pintasan, Jaringan/Keamanan), Guard tile #F5F3FF lolos.

Lihat detail di [changelogs/6.7.13.md](changelogs/6.7.13.md).

## [6.7.12] - 2026-09-09

> Build 47. In-App Update Modal Portrait 3:4 AI, Background Android Notification Download, Splash Luminous Glow, Desktop Tauri v2 fixes, 1-Click Virtual Driver Installers, dan pembaruan README.

### Ditambahkan
- **In-App Update Experience (AI Portrait Modal 3:4)**: Dialog visual pembaruan murni rasio 3:4 portrait AI dengan elemen 3D gaming morphing, motion blur, dan tombol floating close `X`.
- **Background Download Progress Notification (Android)**: Notifikasi progress unduh APK pada drawer sistem Android, memungkinkan download tetap jalan di background.
- **Direct APK Install Flow**: Pemasangan langsung APK terverifikasi setelah unduhan selesai.
- **Splash Screen Luminous Ambient Glow (Flutter)**: Aura ambient bloom violet lembut di belakang logo watermark dengan kurva transisi halus.
- **1-Click Elevated Driver Installer (Desktop)**: Dukungan instalasi instan untuk Virtual Display Driver (IddSampleDriver) dan Virtual Mic/Audio (VB-CABLE) langsung via Tauri backend dengan PowerShell UAC elevation.
- **Updater desktop (PC)**: kartu "Pembaruan aplikasi" di Pengaturan — cek otomatis, unduh installer terverifikasi SHA-256, pasang lalu mulai ulang. Manifest `update.json` kini membawa kunci `windows` (x64/arm64).
- **Artikel arsip 6.5.2**: `changelog-v6-5-2` diterbitkan retroaktif — tidak ada lagi tautan versi yang 404.
- **README.md Komprehensif**: Penambahan badge status CI/CD lengkap, dukungan platform, matriks teknologi, dan panduan kontribusi komunitas.
- **Panduan Update Popup Guide (`docs/APP_UPDATE_POPUP_GUIDE.md`)**: Dokumentasi arsitektur, prompt template, dan alur notifikasi.

### Diperbaiki
- **Unifikasi UI/UX tiga platform** (operator: web = acuan): token Paper mengikuti web — `bg #ffffff`,
  `overlay`/`input #f5f3ff`, `accent-soft` 10%; radius satu skala 8/12/16/20 + pil + tuts-3 (kartu = 16
  di semua platform); garis pemisah dihapus di desktop + web (kartu = bayangan, baris daftar = ubin
  overlay + gap, strip/tab = jalur input + tab putih, tabel = zebra/ubin, chip = isi overlay dengan
  aktif isi-lembut + teks dalam); switch desktop = trek `textLow @45%` tanpa outline + flat aksen saat
  on; aturan tersisa yang disengaja tercatat tertutup di `docs/DESIGN.md` ("Garis yang disengaja").
- **Installer Windows di-brand**: gambar wizard `wizard-image.bmp` (164×314) + `wizard-small.bmp` (55×55)
  dari logo asli — bukan lagi default polos Inno Setup.
- **Korupsi CSS web**: blok `@media` rusak di `web/src/style.css` (~L1067) dihapus; ~10 deklarasi yatim
  (`margin`, `padding`, `border`, `grid-*`) ikut terbuang, aturan `.sesi-panel h2` duplikat digabung.
- **Tempel teks panjang dari HP**: dipecah otomatis per 2.000 karakter (tidak lagi dipotong host di 4.096).
- **Profil desktop**: versi & server kini tampil (kontrak `get_info` diluruskan; versi tidak lagi hardcode basi).
- **Label perangkat**: nama akun tampil di pesan pairing web; hub Go tidak lagi membuang `name`/`platform` saat relay.
- **`news/seed.sql`**: 5 alias slug diluruskan + 5 artikel lama ditarik dari produksi — repo kembali jadi cermin penuh.
- **`build-apk-only.yml`**: signing rilis + Google client id + hapus step license palsu (belum pernah jalan sebelum diperbaiki).
- **Ikon launcher kembali ke logo asli**: tile terang `#F5F3FF` + X ungu glossy di Android (legacy, adaptive, splash-safe), favicon web, dan semua `.ico` Windows. Lapisan foreground adaptive icon dibuat murni transparan (100% alpha = 0 di luar glyph X) agar serasi sempurna dengan adaptive system background plate di Android.
- **Desktop Tauri v2 Config**: Konfigurasi bundle targets pada `desktop/src-tauri/tauri.conf.json` untuk stabilitas kompilasi release Windows x64 & arm64.
- **Inventaris Lisensi Pihak Ketiga**: Sinkronisasi seluruh dependensi lockfile ke 509 komponen resmi.

## [6.7.11] - 2026-09-07

> Build 46. Fix CI Build 34249803875 — Windows x64/arm64 3 errors PROPERTYKEY not found.

### Diperbaiki
- **CI Build 34249803875**: 2 job gagal — `Windows x64` + `Windows arm64` 3 errors PROPERTYKEY not found in PropertiesSystem, actually in Foundation. Fixed.
- Lihat detail di [6.7.11](./changelogs/6.7.11.md)

## [6.7.10] - 2026-09-07

> Build 45. Fix CI Build 34245728600 — Windows x64/arm64 cargo build 8 errors (Win32 not found, PROPERTYKEY, PWSTR).

### Diperbaiki
- **CI Build 34245728600**: 2 job gagal — `Windows x64` + `Windows arm64` cargo build 8 errors: `screen.rs` Win32 not found, `audio.rs` PROPERTYKEY not found, `audio.rs` PWSTR mismatched. Fixed with Foundation+Com features, ::windows absolute, PWSTR direct.
- Lihat detail di [6.7.10](./changelogs/6.7.10.md)

## [6.7.9] - 2026-09-07

> Build 44. Fix CI Build 34171262232 — Windows x64/arm64 cargo build errors (format, PropertiesSystem, GetSystemMetrics, Security/Threading).

### Diperbaiki
- **CI Build 34171262232**: 2 job gagal — `Windows x64` + `Windows arm64` cargo build release error 4 distinct: `screen.rs` format `{:.1}` no arg, `audio.rs` PropertiesSystem feature, `gdi.rs` GetSystemMetrics wrong module, `virtual_display.rs` Security/Threading features. Fixed.
- Lihat detail di [6.7.9](./changelogs/6.7.9.md)

## [6.7.8] - 2026-09-07

> Build 43. Fix CI Build 34170927923 — flutter seamless Divider + clippy single_element_loop.

### Diperbaiki
- **CI Build 34170927923**: 2 job gagal — `Verifikasi aturan seamless` (Divider) + `Clippy single_element_loop`. Fixed.
- Lihat detail di [6.7.8](./changelogs/6.7.8.md)

## [6.7.7] - 2026-09-07

> Build 42. Fix CI Build 34170387236 — flutter analyze unused + host bitrate 0 auto.

### Diperbaiki
- **CI Build 34170387236**: 2 job gagal — `Analisis Statis Flutter` (unused _connecting, _ConnectingView, missing tokens import) + `Uji Logika Host` (bitrate 0 auto should be allowed). Fixed, 122 tests pass.
- Lihat detail di [6.7.7](./changelogs/6.7.7.md)

## [6.7.6] - 2026-09-07

> Build 41. Hotfix CI — format Dart + Rust + CHANGELOG + TURN direct kind. Build 6.7.5 gagal 4 jobs, fixed.

### Diperbaiki
- **CI Build 34169492118**: 4 job gagal — `Cek Lintas-Dokumen`, `Analisis Statis Flutter`, `Uji Logika Host Rust`, `Uji Backend`. Fixed.
- Lihat detail di [6.7.6](./changelogs/6.7.6.md)

## [6.7.5] - 2026-09-07

> Build 40. Web Perfection — Founder: "Sempurnakan web, serta jalur jalur dan lain lain okey? Gas"

### Ditambahkan
- **Web quality & bitrate UI** `web/src/session_ui.tsx`: `StreamQuality auto|medium|high|ultra`, `BitrateMbps 0|8|15|25|50`, `QUALITY_META`, `BITRATE_OPTIONS`, `DEFAULT_PREFS` quality auto bitrate 0. Tab Gambar: chips Otomatis/Sedang/Tinggi/Ultra + Otomatis/8/15/25/50 Mbps + live stats. Callback `onQuality`/`onBitrate` ke host via 0x0A/0x0B.
- **Host protocol 0x0A/0x0B** `host/src/input.rs` + `main.rs`: `VideoQuality(u8)` `VideoBitrate(u16)`, mapping quality auto→DEFAULT medium 8M high 15M ultra 25M, bitrate 0 auto else 1-50M.
- **Web rtc codec** `web/src/rtc.ts`: `InputCodec.quality()` `bitrateMbps()` + `RtcSession.setQuality/setBitrate`.
- **Hero 3D glossy morphing + floating motion blur** `web/src/style.css` + `App.tsx`: 3 orb radial glossy blur morph + 3 glass card 1080p60 LIVE 24ms NVENC backdrop blur motion blur, hero-glow-morph 9s, logo translateZ.
- **Routing /n/:slug** `web/src/App.tsx`: `currentRoute()` handle `/n/:slug` share short link `news.xydesk.my.id/n/:slug` + `/n` → `/news`. Static routes /, /connect, /download, /legal, /news, /billing, /news/:slug, /n/:slug verified.
- **Download ABI active** `web/src/App.tsx`: switcher active class dari localStorage `xydesk.download.arch`.

### Diperbaiki
- **NEWS_IMAGE_BLOCK domain** `web/src/App.tsx` + `desktop/app/page.tsx`: `app.xystudio.my.id` salah → `(app.)?xydesk.my.id` allow app.xydesk.my.id & xydesk.my.id, fix image block.
- **device.ts** `web/src/device.ts`: `XyDesk-Windows-x64-Setup.exe` → `XyDesk-x64.exe` / `XyDesk-arm64.exe` match RELEASE_BASE.
- **Panel sempit** `web/src/style.css`: `.spanel` min 460px calc(100%-108px) max 480 min 380 padding 20 gap 14 mobile 440px 92vw.
- **Prefs migration** `web/src/App.tsx`: spread DEFAULT untuk localStorage lama tanpa quality/bitrate.

### Build
- `web` vite 8.2.1 37 modules 295.69kB gzip 93.22kB SUCCESS
- `desktop` Next.js 15.1.6 4 static pages 15.8kB SUCCESS
- `host` Cargo.toml 6.7.5 + protocol 0x0A/0x0B ready
- `pubspec.yaml` 6.7.5+40, `web/package.json` 6.7.5, `desktop/package.json` 6.7.5

## Daftar versi (per file)

- [6.7.11](./changelogs/6.7.11.md) - 2026-09-07 — Fix CI Windows PROPERTYKEY Foundation
- [6.7.10](./changelogs/6.7.10.md) - 2026-09-07 — Fix CI Windows cargo build 8 errors (Win32, PROPERTYKEY, PWSTR)
- [6.7.9](./changelogs/6.7.9.md) - 2026-09-07 — Fix CI Windows cargo build 4 errors
- [6.7.8](./changelogs/6.7.8.md) - 2026-09-07 — Fix CI seamless Divider + clippy
- [6.7.7](./changelogs/6.7.7.md) - 2026-09-07 — Fix CI flutter analyze + host bitrate 0 auto
- [6.7.6](./changelogs/6.7.6.md) - 2026-09-07 — Hotfix CI format + TURN direct
- [6.7.5](./changelogs/6.7.5.md) - 2026-09-07 — Web Perfection
- [6.7.4](./changelogs/6.7.4.md) - 2026-09-07 — License EN + Admin Auto + Simple Splash + Quality + Spacious Panel
- [6.7.3](./changelogs/6.7.3.md) - 2026-09-07 — Fix Android update + Session Loading + Banner lock + Changelog split
- [6.7.2](./changelogs/6.7.2.md) - 2026-09-07 — Virtual Display + Virtual Mic + NSIS auto-installer
- [6.7.1](./changelogs/6.7.1.md) - 2026-09-07 — Fix VM/RDP hitam + audio mati + UI desktop v3.0
- [6.7.0](./changelogs/6.7.0.md) - 2026-09-07 — DXGI utama, audio 0x88890008 fix, auth desktop
- [6.6.1](./changelogs/6.6.1.md) - 2026-09-06 — Fix layar hitam saat diam
- [6.6.0](./changelogs/6.6.0.md) - 2026-09-06 — Fix splash stuck + CSP web
- [6.5.4](./changelogs/6.5.4.md) - 2026-09-05
- [6.5.3](./changelogs/6.5.3.md) - 2026-09-04
- [6.5.2](./changelogs/6.5.2.md) - 2026-09-03
- [6.5.1](./changelogs/6.5.1.md) - 2026-09-02
- [6.5.0](./changelogs/6.5.0.md) - 2026-09-01
- [6.4.0](./changelogs/6.4.0.md) - 2026-08-30
- [6.3.0](./changelogs/6.3.0.md) - 2026-08-25
- [6.2.2](./changelogs/6.2.2.md) - 2026-08-20
- [6.2.1](./changelogs/6.2.1.md) - 2026-08-18
- [6.2.0](./changelogs/6.2.0.md) - 2026-08-15
- [6.1.0](./changelogs/6.1.0.md) - 2026-08-10
- [6.0.0](./changelogs/6.0.0.md) - 2026-08-01
- [2.5.0](./changelogs/2.5.0.md) - 2026-07-20
- [2.4.0](./changelogs/2.4.0.md) - 2026-07-15
