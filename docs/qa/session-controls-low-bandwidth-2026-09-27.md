# Web sesi: koneksi terbatas dan kontrol netral

Tanggal: 27 September 2026. Izin operator: “gas”, termasuk push/Actions/deploy.

## Status terverifikasi

- Executable web: `19fcd583c58c52984acfc10ae43ec0e66989d39f`.
- Actions: https://github.com/xykal/XyDesk/actions/runs/36300640365 — **success**, termasuk unit, TS/Vite, Chromium, stale-SHA gate dan deploy Cloudflare.
- Delapan skenario browser lulus; `browserErrors: []`. Media sintetis nyata, bukan sesi Windows nyata.
- Artifact browser: `10924634329`; bundle web: `10925675285`.
- Production melalui curl: `/connect?device=123456789` dan `/controls` mengirim HTML dengan `/assets/index-BeG1LObb.js`; bundle memuat keyboard HP, tombol Tampilkan kontrol dan header-navigation.
- Remote mengirim `Cache-Control: no-store`, `X-Robots-Tag: noindex, nofollow`. www tetap 200 dengan public cache policy.
- Python urllib menerima 403, tetapi curl GET/HEAD berhasil. Jangan menyebut verifikasi urllib berhasil.

## Perubahan

1. Auto mulai 1 Mbps, batas bawah 1 Mbps yang memang didukung engine. Loss/jitter/kenaikan RTT relatif memicu penurunan dengan cooldown 6 detik; kenaikan setelah 30 sampel stabil. RTT tinggi tetapi stabil tidak otomatis dianggap congestion. Tidak mengubah resolusi; pilihan tetap minimal 720p.
2. Manual bitrate dihormati, tidak lagi diam-diam diubah adaptive controller. Pilihan 1/2/4 Mbps ditambahkan. Preferensi manual lama tetap dipertahankan.
3. Gerak absolut mulai digabung saat backlog reliable >64 byte, sebelumnya >1024. Posisi terakhir tetap dikirim sebelum klik/scroll/gerak relatif; release tidak dibuang. Tidak memindahkan klik dan gerak ke dua kanal yang dapat saling menyalip.
4. Rail kanan sedikit diperbesar. Hide melepaskan input, menutup panel/keyboard, menghilangkan rail dan mapping; tinggal tombol Show. Mouse inspector tambahan dihapus dari sesi; mapping bawaan menjadi default.
5. Kontrol berbingkai putih, ukuran border tetap, icon ditempatkan di tengah via grid. Editor memakai warna netral; tombol pengaturan canvas berupa icon. Tidak ada animasi dekoratif pada studio/kontrol.
6. Menu berupa navigasi di dalam header, bukan dialog viewport. Navigasi remote duplikat disembunyikan di viewport ponsel yang diuji (390px).
7. Keyboard: toolbar ringkas di kanan, pilihan virtual/keyboard HP. Mode HP adalah composer teks/IME: ketik lalu Kirim teks, dengan tombol Backspace/Enter PC; bukan injeksi setiap keystroke IME. Virtual letter case mengikuti Caps XOR Shift, termasuk event Shift/Caps fisik selama keyboard tampil.
8. `/connect?device=ID` menerima tepat sembilan digit dan memakai jalur session restore yang sudah account-scoped: saved grant bila tersedia, password prompt bila tidak. Tidak menaruh password di URL. Grant dicabut tetap mengikuti penanganan existing; tidak ada klaim uji revoke end-to-end baru.

## Bukti browser

- First-attachment canvas MediaStream tetap memainkan frame 160×90.
- Menu terpasang pada header dan Escape menutupnya.
- Nama/label/sisi inspector tersimpan.
- Stick WASD bergerak dan kembali ke tengah; gamepad analog asli tetap disabled.
- Inspector muat di viewport 390px.
- Menu mobile tanpa navigasi remote duplikat.
- Rail hide/restore dan composer native mengirim input.
- Link ID membuka password prompt tanpa password di URL.

Run pendahulu tidak deploy: `36300320700` (fixture CJS interop), `36300382413` (dua instance React akibat import fixture manual), `36300465946` (race geometri sesudah keluar fullscreen), `36300533426` (label accessible termasuk help text). Fixture sekarang memakai modul TSX Vite yang sama, menunggu fullscreen exit/paint, dan mencocokkan label lengkap. Tidak menggunakan forced click. Screenshot CI dipakai untuk menemukan sisa warna ungu sebelum diperbaiki.

## Batas penerimaan

Belum mengukur ulang RTT, kualitas atau input-to-photon dari HP pengguna ke Windows di jaringan berbeda. Estimasi 2 Mbps berasal dari pengguna, bukan hasil pengukuran. 0 ms lewat internet tidak mungkin. Pada bitrate rendah detail gambar dapat menurun, terutama 1080p. Mulai pengujian dengan 720p/30fps/Auto; manual 1 Mbps tersedia bila diperlukan.

Belum menguji keyboard native/IME di perangkat iOS/Android nyata, controller fisik, OAuth, audio/mikrofon, atau RDP nyata dalam batch ini.

## Permintaan Windows yang MASIH TERBUKA

**Tidak ada perubahan native host, build EXE/MSI/APK baru, atau stable promotion pada batch ini.** Sidebar collapse/icons, profile/status akun, settings bitrate/password, guide `?`, ID/password compact dan link di panel belum diimplementasikan. Analog gamepad host juga belum tersedia.

Temuan implementasi untuk lanjutan:
- Native `startHost()` saat ini hanya mewariskan log/stdin; belum membaca control bootstrap.
- Engine sudah memiliki `--control-info-handle`, frame privat <=1024 byte berisi protocol=1, PID, URL loopback, token hex32. Bukan stdout/stderr/file heartbeat.
- Launcher perlu pipe privat dengan STARTUPINFOEX handle allowlist, pembacaan bounded nonblocking/deadline, validasi PID/loopback/port/token, dan lifecycle reset ketika child berganti. Jangan menyalin bearer ke log/URL/file publik.
- API existing: POST `/action`, header `x-xydesk-token`, aksi `video-bitrate` (`bitrate_mbps`/alias `bitrateMbps`), `set-password` dan `new-password`. Response flat dapat dibaca engine_json; password dikirim sebagai body, bukan command line.
- Implementasikan HTTP loopback async dengan timeout/body limit, redirect disabled, dan error UI jujur. Sinkronkan password panel hanya setelah respons sukses dari child yang sama.
- Profile Windows bukan bukti login akun web; jangan menampilkan login Google palsu.
- Layout/test native existing dan IPC tests harus tetap lolos di Actions sebelum paket baru dibuat. Pertahankan isolasi antar-akun Windows.
