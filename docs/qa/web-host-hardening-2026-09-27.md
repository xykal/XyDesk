# Kandidat hardening web + host — 27 September 2026

Sesi: `SESI-20260927-OPERATOR-HARDEN` — Operator - XyDesk Team  
Baseline: `f5fd41f49ee0bd606cca8bd0bc0880c79c0a5318`

## Status yang boleh disimpulkan

Perubahan source untuk seluruh 17 butir audit telah disiapkan lokal. Ini **bukan penutupan acceptance, bukan bukti build berhasil, dan bukan persetujuan rilis**. Tidak ada commit/push, dispatch Actions, deploy, atau bump versi. Tidak ada kompilasi/unit test lokal. Patch batch 1 sudah tidak mewakili keseluruhan kandidat ini.

Pemilik memilih **per akun Windows**: profil/identitas terpisah, koordinasi console/RDP hanya pada akun yang sama. Bukan service machine-wide, bukan takeover user lain, bukan bypass lock screen/UAC. `XYDESK_HOME` harus tetap direktori privat akun yang sama; jangan diarahkan ke direktori bersama.

## Pemetaan seluruh audit

Semua status pada kolom implementasi berarti *kandidat source*, belum runtime PASS.

| ID | Implementasi lokal | Regresi / bukti yang masih diperlukan |
|---|---|---|
| WH-01 | Jalur fallback GDI mengambil ulang frame, memvalidasi RGBA, menolak perubahan geometri; NV12 checked; DC dilepas sebelum bitmap terpilih dihapus | Regresi GDI/pixfmt ditambahkan; M03–M06, RDP/handle nyata belum dijalankan |
| WH-02 | Lease file eksklusif dalam profil, filter WTS nama akun + domain (informasi kosong ditolak), RDP aktif akun yang sama didahulukan; proses tetap standby | Unit guard Windows dan filter akun; M05–M06 dan dua akun nyata belum dijalankan |
| WH-03 | Eligibility sebelum akuisisi; guard hidup sepanjang leadership; media/socket ditutup sebelum melepas slot; auth/connect dapat dibatalkan saat sesi berubah | Regresi eligibility/drop/reacquire; takeover nyata belum dijalankan |
| WH-04 | Satu helper initial/restart mengirim localDescription yang diterapkan, termasuk fallback SDP | Regresi SDP applied/rejected/restart; browser-host nyata belum dijalankan |
| WH-05 | Deadline request mencakup fetch dan body; AbortSignal dari attempt sampai guest/signaling/TURN; stop menutup request RTC | Regresi hung body, transport yang tidak menolak abort, pre-abort; M15 belum dijalankan |
| WH-06 | Satu owner timer/controller; connect manual mengganti attempt; unmount/disconnect membatalkan; callback stale diabaikan | Regresi attempt/timer ownership; M14–M15 belum dijalankan |
| WH-07 | CaptureSource Drop memberi sinyal stop saat idle; queue producer 4 + bridge 1, drop nonblocking; gate Connected/umur 100 ms; timeout write 250 ms; gap sequence mempertahankan clock | Unit cancellation/drop dan perhitungan gap; M10–M12/M16/M22 belum dijalankan |
| WH-08 | Guard COM thread-local non-Send; setiap inisialisasi berhasil diimbangi uninitialize setelah objek COM dilepas | Review source dan gerbang compile Windows; hardware/COM lifecycle belum dijalankan |
| WH-09 | Payload clipboard maksimal 65535 byte, termasuk pemotongan aman di batas UTF-8; opcode + payload sesuai batas host | Regresi batas byte/UTF-8 ditambahkan; M09 paste besar belum dijalankan |
| WH-10 | Gerak/coalesced position dan klik dikirim pada kanal reliable yang sama; posisi tertunda dikirim sebelum klik | Regresi ordering dengan kanal lossy tersedia; M08 dengan loss/reorder belum dijalankan |
| WH-11 | Identity probe async, PeekNamedPipe, deadline 5 detik, cap output 8 KiB, terminasi child yang macet; UI timer menerima hasil | Compile panel/probe dan M21 fixture macet belum dijalankan; layout probe bukan tes hang |
| WH-12 | Heartbeat publik per PID; panel memeriksa PID, creation time, ukuran dan freshness 5 detik; state starting/connecting/standby/ready/streaming, bukan sekadar proses hidup | Unit snapshot tanpa secret; M01/M21 stale/PID reuse belum dijalankan |
| WH-13 | `/auth/me` mempertahankan status HTTP; hanya 401 current-account mencabut JWT; transient error diberi peringatan; cleanup membatalkan respons lama | Regresi klasifikasi 401/403/500; React account-switch M19 belum dijalankan |
| WH-14 | State + nonce OAuth dicocokkan dan dikonsumsi sekali; logout akun menghapus Google founder token | Regresi nonce salah/hilang/cocok, fragment scrub, helper logout; provider nyata belum dijalankan |
| WH-15 | Metadata ukuran tanpa framesDecoded positif tidak membatalkan watchdog; baseline statistik direset saat track/report berganti | Regresi zero decoded-frame; M03/freeze browser nyata belum dijalankan |
| WH-16 | `Validate Web Host` manual, permissions read-only, timeout, concurrency; web build/test, host Linux/Windows unit/clippy, panel compile/layout/probe; kandidat Windows berlabel validation-only | YAML diperiksa struktural; workflow belum dipush/dispatch; tidak memicu rantai Build → deploy/release |
| WH-17 | Bearer control dan password tidak dicetak saat startup normal; heartbeat hanya allowlist field publik | Review jalur stdout; review log runtime bersih masih wajib. Mode CLI eksplisit identity/password dan contoh probe tetap sensitif |

## Pemeriksaan lokal yang benar-benar dilakukan

- Formatting Rust menggunakan rustfmt standalone resmi; tidak menjalankan cargo/compiler.
- `git diff --check`: tidak menemukan whitespace error pada pemeriksaan terakhir sebelum laporan.
- Parser TypeScript/TSX dan C++: temuan parser yang muncul juga ada pada baseline (ampersand JSX serta macro Win32 CALLBACK/WINAPI). Ini bukan type-check/link-check MSVC.
- Review call-site modul audio, leadership, RTC, fixture VM dan observer panel.
- `node --check` pada enam file regresi web yang diubah/ditambahkan tidak menemukan syntax error; tidak mengimpor/menjalankan test.
- YAML workflow terbaca; hanya `workflow_dispatch`, permissions `contents: read`, dua definisi job (host matrix Linux/Windows). Tidak menjalankan job.
- Pemeriksaan ulang parser source setelah edit akhir: tidak ada signature error sintaks tambahan dibanding baseline. `rustfmt --check` untuk sepuluh file Rust yang disentuh dan `git diff --check` selesai tanpa error.

Unit test yang ditulis **belum dijalankan**. Hasil CI, penggunaan CPU/RAM, latency, A/V sync, kebocoran thread/handle, dan kompatibilitas Windows/browser belum diketahui.

## Batasan dan risiko yang jangan disembunyikan

1. Akun dan domain WTS harus dapat dibaca; jika tidak, host fail-closed dalam standby. Pemilihan beberapa sesi RDP aktif bersifat deterministik berdasarkan session ID, bukan deteksi sesi mana yang terakhir disentuh pengguna.
2. File lease berada di profil; salinan identitas atau override direktori bersama antar-user tidak didukung. Perubahan ini tidak mengubah ACL maupun memasang service.
3. **Catatan awal, ditangani kandidat lanjutan di bawah:** inisialisasi identitas lama memakai API read/create yang toleran terhadap kegagalan I/O. Skenario dua startup pertama serentak pada profil kosong perlu diperiksa terpisah; lease signaling bukan transaksi penulisan identitas.
4. Batas polling/queue audio tidak menjamin panggilan driver WASAPI selalu kembali tepat waktu. Perlu pengukuran driver asli, audio diam, mute, cabut perangkat, dan connect/disconnect berulang.
5. Gerak pointer reliable memperbaiki urutan dengan klik, tetapi jaringan macet dapat menambah latency. Regresi ordering bukan bukti performa.
6. Heartbeat publik bukan endpoint otorisasi; file tidak boleh berisi password/token. Kegagalan menulis menjadi status stale, bukan ready palsu. File per-PID lama dapat tertinggal setelah proses mati dan diabaikan panel.
7. **Catatan awal, ditangani kandidat lanjutan di bawah:** native parser identitas lama memakai parser JSON sederhana; dukungan password kustom berkarakter escape/non-ASCII memerlukan pemeriksaan tersendiri. Deadline/worker tidak dengan sendirinya memperbaiki parser itu.
8. **Diperbarui pada lanjutan:** token control tidak lagi tersedia lewat stdout startup. Panel native tidak memakai control bearer tersebut; integrasi lama yang mem-parsing stdout harus memakai pipe privat yang kini disiapkan (lihat `../CONTROL-IPC.md`), bukan mengembalikan token ke host.log. Contoh control probe dan `--identity-json` hanya untuk penggunaan eksplisit yang aman.
9. Log lama tidak dihapus, credential yang pernah dibagikan belum dirotasi oleh agent, dan penghapusan Test Lab lokal belum membatalkan run/secret lama di GitHub.

## Gerbang lanjutan — tetap ditahan sampai izin

1. Setelah review/push secara eksplisit diizinkan, pin SHA kandidat; jangan menjalankan Build umum untuk sekadar validasi.
2. Dispatch **Validate Web Host** sekali pada SHA/ref yang benar. Perbaiki kegagalan sebelum label acceptance selesai.
3. Artefak `validation-only-windows-<sha>` bukan installer/rilis. Letakkan panel dan engine dari artefak yang sama di satu direktori saat diuji. Tidak ada Tailscale, RDP runner, tunnel, atau idle keepalive.
4. Jalankan M01–M22 pada perangkat pemilik, termasuk console/RDP akun yang sama dan isolasi dua akun. Semua masih **BELUM DIUJI**.
5. Simpan run ID, SHA, hash artefak dan bukti tersensor; jangan unggah token, password, SDP atau screenshot desktop pribadi. Baru setelah itu putuskan readiness/rilis.

## Bahan artikel internal, belum diterbitkan

Arah dampak pengguna: kegagalan koneksi lebih bisa dibatalkan, percobaan lama tidak mengganggu yang baru, posisi klik lebih konsisten, panel membedakan host siap dari sekadar hidup, dan sesi Windows tidak mengambil layar akun lain. Belum boleh menjanjikan latency, FPS, audio sinkron, atau dukungan seluruh VPS. Screenshot asli belum tersedia; jangan menggantinya dengan gambar sintetis sebagai bukti runtime.

## Lanjutan lokal disetujui — identitas, parser, IPC control

Pemilik memilih melanjutkan tiga celah tambahan tanpa push. Ketiga catatan
source nomor 3, 7, 8 di atas kini mendapat kandidat lanjutan berikut; semuanya
**tetap menunggu hasil Actions**, bukan runtime PASS.

### A. Startup pertama serentak

- `identity.lock` menggunakan exclusive `File::try_lock` lintas proses;
  deadline 3 detik dan pelepasan otomatis saat file/proses ditutup. Lockfile
  tidak dihapus agar proses lain tidak mengunci file yang berbeda.
- Main membaca/membuat pasangan ID + password dalam satu critical section.
  `set_password` menggunakan lock yang sama. Publikasi file menggunakan
  temporary file unik + sync + rename; file lama tidak ditruncate lebih dulu.
- Error I/O, data korup, atau timeout diteruskan ke caller. Tidak ada lagi
  identitas acak sementara ketika penyimpanan gagal; data korup tidak diganti
  diam-diam. Pesan error tidak berisi nilai password.
- API `load_or_create_*` sekarang mengembalikan `io::Result`; call-site di repo
  diperbarui. Tidak mengubah ID/password profil yang valid.
- File baru Unix dibuat mode 0600. Windows tetap mewarisi ACL profil privat;
  direktori bersama tetap tidak didukung. Tidak ada pelebaran ACL.
- Ditambahkan source regresi lock timeout/reacquire dan kegagalan rename;
  integration test menjalankan delapan proses `--identity-json` bersamaan,
  memeriksa identitas identik/persisten, profil rusak dan path bukan direktori.
  CI Linux menjalankannya melalui `--tests`; Windows melalui target eksplisit.

### B. Parser password panel

- `engine_json.h` adalah parser kontrak **objek datar engine**, bukan pustaka
  JSON umum: nilai string/bool/integer/null; input maksimal 16 KiB.
- Menangani quote/backslash, UTF-8 literal, escape Unicode serta surrogate
  pair. Konversi ke UTF-16 Win32 memakai `MultiByteToWideChar` dengan validasi.
- Duplicate key, input terpotong, escape salah, UTF-8 overlong/invalid,
  surrogate tunggal, integer overflow dan trailing garbage ditolak.
- Pembacaan identitas tetap di worker, cap 8 KiB dan deadline 5 detik. Password
  sangat panjang yang membuat respons melewati cap tidak diterima panel;
  cap ini bukan janji password dengan panjang tak terbatas.
- Source regresi C++ baru masuk script layout/parser CI; header yang sama
  dipakai panel produksi. Belum dikompilasi/dijalankan.

### C. Bootstrap token control privat

- Flag Windows opsional `--control-info-handle HANDLE` menerima ujung tulis
  pipe yang diwariskan launcher. Frame JSON berisi protocol, PID, URL loopback
  dan token, maksimal 1 KiB, satu kali lalu handle ditutup.
- Handle stdio dan file disk ditolak; mode pipe nonblocking mencegah parent
  yang tidak membaca menahan startup. Kegagalan IPC tidak fallback ke log.
- Tanpa flag, startup normal tetap tidak mengeluarkan bearer. Tidak menambah
  berkas credential, endpoint publik atau ACL lintas akun. Panel native yang
  hanya menggunakan heartbeat tidak diwajibkan meminta bearer.
- Integrasi lama wajib mengganti parsing stdout dengan pipe inheritance yang
  dibatasi ke handle terkait; kompatibilitas stdout sengaja tidak dikembalikan.
  Petunjuk: `docs/CONTROL-IPC.md`. Launcher pemegang pipe dipercaya dan harus
  menjaga token di memori, bukan menyalin frame ke log.
- Regresi Windows: roundtrip pipe/EOF, penolakan file log, pipe penuh; probe
  antarproses di `tool/check_control_ipc.py` memeriksa PID, endpoint, penolakan
  bearer salah dan penerimaan bearer valid pada HTTP loopback. Profil sementara,
  token signaling fixture, tidak menghubungi layanan produksi; payload tidak
  dicetak. Semua baru source dan belum dieksekusi.

### Pemeriksaan lanjutan yang benar-benar dilakukan

- ABI fungsi Win32 dicek terhadap source crate `windows 0.61.3` dengan SHA256
  cocok `Cargo.lock`; tidak ada upgrade dependency/lockfile.
- `File::try_lock` dicek pada dokumentasi std (stabil sejak Rust 1.89);
  toolchain workflow sudah stable. Tidak ada kompilasi lokal.
- Rust diformat dengan formatter standalone. Parser sintaks C++ pada header
  dan fixture baru tidak melaporkan error. Python AST serta YAML workflow
  dapat diparse. Ini bukan bukti compile, runtime, inheritance atau ACL PASS.
- Workflow validasi tetap manual/read-only dan tidak terhubung deploy/rilis;
  tiga Windows feature flag di crate yang sudah ada diaktifkan untuk IPC.
