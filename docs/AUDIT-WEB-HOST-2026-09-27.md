# Audit web + host XyDesk

**Tanggal:** 27 September 2026  
**Baseline source:** `main` — `f5fd41f49ee0bd606cca8bd0bc0880c79c0a5318`  
**Sesi:** `SESI-20260927-OPERATOR-WEBHOST` — Operator - XyDesk Team  
**Mandat:** matangkan web dan host terlebih dahulu; hapus lab RDP/Tailscale Actions; acceptance manual pada mesin pemilik.

## Status implementasi sesudah audit

Seluruh WH-01–WH-17 kini memiliki kandidat perubahan source lokal pada sesi
`SESI-20260927-OPERATOR-HARDEN`, melanjutkan batch 1. Pemilik memilih
**per akun Windows**, bukan koordinasi lintas user. Lihat
[matriks implementasi, batasan dan validasi tertahan](qa/web-host-hardening-2026-09-27.md).
Belum compile/unit test CI maupun acceptance perangkat; temuan tidak ditutup
hanya karena kode berubah. Audit di bawah adalah bukti baseline awal, bukan
uraian source sesudah patch. Seluruh push/dispatch/rilis tetap ditahan.

## 1. Keputusan eksekutif

**Belum layak diberi label “web + host sudah matang”.** Bukan karena kurang framework atau bahasa, melainkan beberapa kontrak dasar masih tidak konsisten: buffer capture saat fallback, kepemimpinan antar-sesi Windows, SDP yang dikirim, lifecycle audio, serta pembatalan koneksi/retry.

Tidak perlu rewrite total. Pertahankan React/TypeScript untuk web, Rust untuk engine, C++ Win32 untuk panel. Tutup masalah berurutan dengan regresi yang menjalankan jalur produksi. Unit test saja tidak membuktikan Windows/RDP atau browser nyata.

### Peta prioritas

| ID | Prioritas | Area | Ringkasan | Bukti saat audit |
|---|---|---|---|---|
| WH-01 | P1 | Capture | Fallback GDI sukses menghasilkan satu frame dengan buffer kosong | Alur source terkonfirmasi |
| WH-02 | P1 | Host | Mutex/berkas koordinasi tidak menyediakan leadership lintas user/sesi seperti klaim | Struktur source terkonfirmasi; runtime Windows belum diuji |
| WH-03 | P1 | Host | Standby dapat mengambil mutex saat tidak berhak promosi lalu mengunci dirinya | Alur source terkonfirmasi |
| WH-04 | P1 | Web–host | SDP yang dikirim tidak sama dengan SDP hasil modifikasi yang diterapkan | Alur source terkonfirmasi |
| WH-05 | P1 | Web | Permintaan token sebelum watchdog tidak memiliki deadline/cancel | Struktur source terkonfirmasi; jaringan macet belum direproduksi |
| WH-06 | P1 | Web | Retry otomatis lama tidak dibatalkan saat connect manual baru | Alur source terkonfirmasi; perlu browser regression |
| WH-07 | P1 | Audio | Forward audio/mic tidak memiliki pembatalan sesi eksplisit | Struktur source terkonfirmasi; dampak thread perlu ukur |
| WH-08 | P2 | Audio Windows | CoInitializeEx tidak diimbangi CoUninitialize di modul audio | Source terkonfirmasi |
| WH-09 | P2 | Web–host | Batas clipboard payload dan batas pesan berbeda satu byte | Kontrak source terkonfirmasi |
| WH-10 | P2 | Input | Gerak dan klik memakai kanal berbeda tanpa kontrak ordering lintas kanal | Risiko protokol; perlu uji loss/reorder |
| WH-11 | P2 | Panel | Baca identitas dapat memblokir UI sebelum timeout proses berlaku | Alur source terkonfirmasi |
| WH-12 | P2 | Panel | Status proses hidup dianggap host aktif; telemetry tidak punya freshness | Source terkonfirmasi; UI aktual belum diobservasi |
| WH-13 | P2 | Web auth | Gangguan `/auth/me` dapat menghapus sesi; respons lama tidak dijaga | Alur source terkonfirmasi |
| WH-14 | P2 | Web auth | Nonce OAuth tidak dicocokkan; logout utama menyisakan token Google | Source terkonfirmasi; bukan bukti eksploitasi |
| WH-15 | P2 | Diagnostik | Deteksi tidak ada frame dibatalkan oleh metadata ukuran tanpa bukti decode positif | Alur source terkonfirmasi untuk laporan stats tersebut |
| WH-16 | P2 | CI | Validasi kandidat belum terpisah aman dari rantai deploy/rilis; cakupan Windows terbatas | Konfigurasi terkonfirmasi |
| WH-17 | P2 | Privasi/log | Token control lokal tercetak ke log host | Rantai stdout → file terkonfirmasi |

P1 = blokir klaim kesiapan inti sebelum diperbaiki/divalidasi. P2 = perbaikan penting sebelum rilis stabil. Tidak ada item yang diberi label eksploitasi remote terkonfirmasi atau P0 tanpa bukti.

## 2. Cakupan dan batas pemeriksaan

### Ditelusuri

- Web: transport `RtcSession`, negosiasi/recovery SDP, token API, mount/unmount sesi dan retry pada `App.tsx`, audio/video playback, input pointer/keyboard, statistik, guest grants, OAuth, history/preview, adaptive bitrate, dan kebijakan CSP.
- Host: startup/leadership, pairing dan gerbang offer/ICE/bye, sesi WebRTC, capture GDI dan pemanggil encoder, video pump/keyframe, input queue dan pelepasan tombol, audio capture/render, control API, identitas, refresh token, remembered grants.
- Panel native: peluncuran/pengawasan child process, identitas, telemetry file, start/stop dan log.
- Packaging/CI: HKCU startup, Build Windows/Linux, paket uji manual, dependensi workflow deploy/rilis, penghapusan lab.
- Test: inventaris dan pembacaan beberapa fixture/regresi yang relevan, bukan eksekusi test suite.

### Tidak dibuktikan pada sesi ini

Tidak ada build/kompilasi/test suite lokal atau CI, browser automation, benchmark, pengujian Windows, panggilan API produksi, penggunaan kredensial, atau inspeksi run Actions live. Audit ini berbasis source; bukan sertifikasi seluruh baris kode, pentest komprehensif, pemindaian advisory dependensi, atau bukti visual semua halaman. Flutter/backend tidak diaudit ulang menyeluruh; hanya kontrak yang berhubungan dengan web/host dibaca.

Bug statis dapat diketahui tanpa menjalankan aplikasi. Namun gejala aktual, frekuensi, dan dampaknya pada perangkat tertentu tetap memerlukan reproduksi. Angka test historis tidak dipakai sebagai hasil sesi ini.

## 3. Temuan terperinci

### WH-01 — Frame kosong tepat saat fallback GDI

**Sumber:** `host/src/gdi.rs`, `fallback_to_desktop_dc()` sekitar 111–120 dan `grab()` sekitar 145–193; `host/src/screen.rs:1254–1271,1637–1657`; `host/src/software_video.rs:129–137`; `host/src/pixfmt.rs:31–65`.

`grab()` telah mengisi `self.rgba`. Ketika 15 sampel gelap memicu fallback dan fallback berhasil, `fallback_to_desktop_dc()` mengganti buffer dengan `Vec::with_capacity(...)` yang panjangnya nol. Fungsi kembali ke `grab()` lalu mengembalikan `Ok((&self.rgba, w, h))` tanpa mengambil piksel baru.

**Dampak:** SoftwareEncoder menolak panjang buffer dan loop GDI berhenti. Pada jalur NVENC dengan dimensi nonzero genap, `rgba_to_nv12` mengindeks buffer kosong dan dapat panic. Jadi perbaikan layar hitam memiliki cacat tepat di transisi pemulihan. Tidak mengklaim seluruh proses pasti crash; itu tergantung jalur/thread dan pengawasan capture.

**Perbaikan:** transisi backend harus menghasilkan frame lengkap yang sesuai dimensi atau sinyal restart eksplisit; jangan mengembalikan sukses dengan piksel kosong. Validasi panjang buffer sebelum konversi NV12. Bila geometri berubah, encoder/input geometry harus dibangun ulang sebagai satu transisi.

**Regresi:** simulasi frame gelap ke-15 dan fallback berhasil/gagal; invariant setiap frame sukses `len == width * height * 4`; test software dan NVENC boundary; tes RDP asli untuk backend yang benar-benar terpilih.

### WH-02 — Leadership lintas sesi tidak memiliki kanal koordinasi yang sama

**Sumber:** `host/src/leadership.rs:84–137`; `host/src/identity.rs:247–258`; `packaging/nsis/XyDesk.nsi:107`; `packaging/windows/generate_wix.py:94–101`.

Mutex bernama `Local\\XyDesk-Host-Leader` berada dalam namespace sesi Windows, bukan satu namespace seluruh mesin. Berkas `stepdown.json` memakai `config_dir()` yang default-nya profil user. Pengguna berbeda tidak membaca berkas yang sama. Startup installer aktif memakai HKCU, tidak otomatis memasang startup untuk setiap akun Windows pada mesin.

**Dampak:** klaim “satu leader per mesin dan takeover lintas user otomatis” belum ditopang mekanisme ini. Instance di sesi lain dapat tidak melihat mutex/permintaan yang sama. HKCU bukan bug dengan sendirinya, tetapi kontrak produknya harus sesuai.

**Perbaikan desain:** tetapkan dulu apakah identitas/host milik user, sesi, atau mesin. Jika memang perlu koordinasi mesin, gunakan IPC lintas sesi dengan ACL ketat dan aturan pemilik eksplisit. Jangan sekadar mengganti `Local` menjadi `Global` dan membuka direktori bersama ke semua user; itu memperluas batas kepercayaan.

**Regresi/manual:** dua sesi user sama, dua user berbeda, console + RDP, session switch dan logoff; hanya leader yang berhak memegang signaling/capture; identitas user lain tidak terbaca/terambil.

### WH-03 — Standby bisa merebut mutex sebelum syarat promosi lulus

**Sumber:** `host/src/main.rs:466–482`; `host/src/leadership.rs:19–26,88–117`.

Dalam loop standby, argumen `coba_mutex()` dievaluasi sebelum pemanggilan `boleh_promosi(sessiku, sesi_aktif, ...)`. Saat `sessiku != sesi_aktif`, mutex tetap dapat diciptakan dan handle disimpan. `boleh_promosi` mengembalikan false, tetapi handle tidak dilepas. Saat sesi menjadi aktif kemudian, percobaan mutex melihat objek yang dibuat sendiri sebagai `ERROR_ALREADY_EXISTS` dan tetap gagal.

**Perbaikan:** akuisisi hanya setelah eligibility lulus, dan gunakan guard RAII yang dilepas bila promosi gagal. Hindari resource ownership tersembunyi dalam argumen fungsi boolean.

**Regresi:** mulai standby tanpa leader dalam namespace-nya; pindah menjadi sesi aktif; pastikan promosi sukses dan handle tidak bocor. Test fungsi boolean saat ini tidak menguji lifecycle mutex asli.

### WH-04 — Offer H.264 yang diterapkan berbeda dari yang dikirim

**Sumber:** `web/src/rtc.ts`, akhir `negotiate()` sekitar 685–697 dan `recoverConnection()` sekitar 718–727; `web/src/video_negotiation.ts`.

Web memanggil `setLocalDescription` dengan hasil `offerWithH264Level(...)`, tetapi pesan signaling tetap membawa `offer.sdp` asli. Hal yang sama terjadi di recovery. Ketika probe decoder memilih level `28` atau `33`, dua pihak tidak menerima representasi offer yang sama.

**Dampak:** host memilih kebijakan video dari SDP yang berbeda; kemampuan HD yang diprobe browser tidak tersampaikan konsisten. Tidak mengklaim semua browser gagal, karena fallback level `1f` tidak mengubah SDP.

**Perbaikan:** kirim SDP yang benar-benar berhasil diterapkan (`pc.localDescription` atau objek final yang sama), termasuk ketika fallback setLocalDescription dipakai. Uji bahwa jawaban host sesuai codec/level yang dinegosiasikan.

**Regresi:** level 1f/28/33, probe unsupported, munging ditolak, ICE restart; assert SDP signaling sama dengan SDP lokal, lalu Chromium/Firefox nyata dengan host produksi.

### WH-05 — Deadline belum meliputi persiapan token

**Sumber:** `web/src/rtc.ts:start()` sekitar 447–456; `web/src/api.ts:post(),signalToken(),turnIce()`; `web/src/App.tsx:connect()/ensureToken()`.

`RtcSession.start()` menunggu `signalToken()` sebelum menyalakan watchdog pairing. `connect()` juga menunggu `ensureToken()`, termasuk guest issuance. Fetch pada jalur ini tidak memiliki timeout/AbortSignal aplikasi. UI sudah masuk pairing, sehingga janji gagal-terbatas 20 detik tidak mencakup fase awal tersebut.

**Dampak:** request yang tidak selesai dapat menahan UI sampai timeout jaringan milik browser/OS. Stop dapat mencegah sebagian pekerjaan lanjutan, tetapi tidak membatalkan request jaringan itu sendiri.

**Perbaikan:** satu cancellation/deadline milik attempt sejak klik Connect, diteruskan ke fetch dan negosiasi; pisahkan status izin/token, signaling, pairing dan media. Error harus actionable dan tombol cancel selalu bekerja.

**Regresi:** guest endpoint/token endpoint menggantung, cancel saat izin diambil, respons datang setelah cancel, retry setelah timeout. Jangan menambahkan timeout yang hanya menutup modal tetapi meninggalkan request berjalan tanpa guard.

### WH-06 — Retry manual dan otomatis dapat berjalan bersamaan

**Sumber:** `web/src/App.tsx:connect()` sekitar 2285–2430; timer retry sekitar 2357 dan 2426; `disconnect()` sekitar 2434–2463.

Timer auto-reconnect dibersihkan pada disconnect, tetapi tidak di awal `connect()` manual baru. Fase error membuka tombol Coba lagi sementara timer masih aktif. Timer lama dapat memanggil `connect(true)` saat attempt manual sedang atau sudah tersambung. `sessionRef.current` kemudian diganti tanpa stop eksplisit sesi sebelumnya pada awal connect.

**Dampak:** koneksi paralel, sesi lama tidak lagi terjangkau oleh ref UI, history attempt tertimpa, atau status dari pekerjaan lama membingungkan pengguna. Guard `sessionRef` pada callback membantu menghindari sebagian update UI, tetapi bukan teardown transport lama.

**Perbaikan:** cancel timer lama, invalidasi generation/attempt lama, tutup sesi sebelumnya, dan cegah reentrancy sebelum mulai attempt baru. Retry harus memiliki satu pemilik.

**Regresi browser:** putus sesudah connected → klik Coba lagi sebelum timer habis → majukan clock; hanya satu attempt/socket/peer aktif. Uji double click, route change, tombol batal, dan auto-reconnect berulang.

### WH-07 — Forward audio tidak punya lifecycle cancellation yang tegas

**Sumber:** `host/src/main.rs:1505–1565`; `host/src/audio.rs:588–645`.

Forward audio/mic membuat task dan thread terlepas. Pengiriman memakai `blocking_send`, bukan strategi buang-paket-lama yang diklaim komentarnya. Loop capture hanya keluar bila ada error perangkat atau pengiriman paket gagal. Saat WASAPI tidak menghasilkan paket, loop tidur/poll tidak memeriksa pembatalan sesi. Task audio juga tidak memilih antara `session closed` dan `recv/write_sample`.

**Dampak yang perlu diukur:** task/thread dapat bertahan setelah sesi berakhir, khususnya saat sumber diam. Backpressure dapat menunda pembacaan WASAPI; channel kecil sendiri tidak menjamin audio tetap segar. Tidak mengklaim memori pasti tumbuh tak terbatas karena channel yang terlihat memang bounded.

**Perbaikan:** cancellation token/closed signal lintas task-thread, join/teardown yang jelas, deadline tulis, dan kebijakan audio freshness/drop yang terukur. Drop harus konsisten dengan timestamp/durasi RTP, bukan sekadar membuang byte.

**Regresi:** 30 kali connect/disconnect dalam keadaan hening dan bersuara; thread/handle kembali ke baseline, audio tidak tertunda progresif, perangkat dapat dilepas/dipasang lagi.

### WH-08 — COM audio belum dikelola dengan guard

**Sumber:** `host/src/audio.rs:init_com(),device(),device_by_id(),capture()` sekitar 242 dan seterusnya.

Banyak helper memanggil `CoInitializeEx`; modul ini tidak memiliki `CoUninitialize`. Inisialisasi sukses berulang pada thread yang sama tetap perlu pasangan uninitialize. Pemanggilan polling status dapat masuk ke helper tersebut berulang kali.

**Perbaikan:** guard COM per thread dengan aturan kepemilikan jelas; drop objek COM sebelum uninitialize dan tangani model apartment yang tidak cocok. Jangan menambahkan uninitialize membabi buta untuk hasil gagal.

**Regresi Windows:** polling status/audio lama, capture berulang, hotplug, dan thread lifecycle. Unit test Linux tidak membuktikan keseimbangan COM.

### WH-09 — Clipboard maksimum web ditolak host

**Sumber:** `web/src/rtc.ts:InputCodec.clipboardSet()` sekitar 163–182; `host/src/main.rs` handler `dc.on_message` sekitar 1163–1172.

Encoder web mengizinkan payload UTF-8 64 KiB lalu menambahkan satu byte opcode: panjang pesan 65.537 byte. Host hanya menerima pesan `<= 65.536` byte. Payload tepat pada batas dapat ditolak diam-diam sebelum parser clipboard melihatnya.

**Perbaikan:** sepakati apakah limit menghitung payload atau total pesan. Gunakan konstanta kontrak bersama/dokumentasi, dan beri feedback untuk penolakan oversize.

**Regresi:** 65.535/65.536/65.537 byte total serta UTF-8 multibyte di titik pemotongan; arah browser → host dan host → browser.

### WH-10 — Ordering pointer dan tombol tidak dijamin lintas data channel

**Sumber:** `web/src/rtc.ts:negotiate()/sendInput()` sekitar 631–642,735–754; handler input/pointer host di `host/src/main.rs:1163–1190`.

Gerak absolut/relatif dan scroll dapat dikirim lewat kanal `pointer` unordered/lossy; tombol lewat `input` reliable. Mengirim gerak lalu klik dari JavaScript tidak menjamin host menerima gerak itu lebih dulu. Flush pending move juga masih memakai kanal pointer.

**Risiko:** di jaringan loss/reorder, klik dapat terjadi pada posisi sebelumnya. Ini risiko kontrak, bukan bukti semua klik saat ini salah.

**Perbaikan desain:** klik membawa koordinat/sequence barrier yang dapat diterapkan atomik di host, atau posisi final dan klik berbagi urutan reliable. Pertahankan gerak realtime lossy tanpa membuat tombol bergantung pada paket yang dapat hilang.

**Regresi:** reorder/drop gerak tepat sebelum mouse-down; klik target kecil tetap tepat. Test jitter/loss dan drag, bukan hanya pemetaan koordinat murni.

### WH-11 — Panel dapat macet saat membaca identitas

**Sumber:** `packaging/native-host/main.cpp:253–287`, pemanggilan startup sekitar 1609.

`readIdentity()` membaca anonymous pipe secara blocking sampai EOF sebelum `WaitForSingleObject(...,5000)`. Timeout lima detik tersebut tidak membatasi `ReadFile` sebelumnya. Child yang hidup tapi tidak menutup stdout dapat menahan UI.

**Perbaikan:** pekerjaan identitas di worker thread dengan deadline menyeluruh, output bounded, dan teardown child saat deadline lewat; thread UI tidak menunggu pipe tanpa batas.

**Regresi:** fixture child normal, macet, exit tanpa JSON, output terlalu besar, JSON terpotong. Panel tetap dapat digerakkan/ditutup dan error jelas.

### WH-12 — Panel belum membedakan proses hidup, standby, ready, dan capture segar

**Sumber:** `packaging/native-host/main.cpp:311–368,1279–1340,1797–1810`; `host/src/screen.rs:capture_health::write_json()` sekitar 1152–1172.

`startHost()` menetapkan `g.running=true` dan status hijau setelah spawn/assign job sukses, bukan setelah engine mendapat welcome. Startup standby tetap merupakan proses hidup. Telemetry `capture.json` tidak mempunyai PID/generation/timestamp freshness; pembaca panel mempertahankan nilai lama bila file tidak tersedia dan tidak mengaitkannya dengan proses saat ini. Penulisan file langsung juga dapat terbaca parsial.

**Dampak:** “Host aktif” dapat dibaca sebagai siap dikoneksikan padahal hanya prosesnya hidup, dan diagnosis capture dapat tertinggal dari sesi lama.

**Perbaikan:** model status launching/standby/connecting/ready/streaming/error; telemetry atomik dengan PID/session/generation/timestamp; tampilkan stale/unknown setelah TTL. Gunakan control API/IPC yang terautentikasi bila diperlukan, bukan menyamakan child liveness dengan kesehatan produk.

**Regresi:** internet mati, standby, restart, file telemetry lama/parsial, engine exit mendadak. UI tidak boleh mengklaim ready sebelum bukti.

### WH-13 — Error jaringan dianggap logout, respons auth lama dapat menang

**Sumber:** `web/src/api.ts:me()`; `web/src/App.tsx:RemoteApp` sekitar 1674–1682.

`me()` memetakan seluruh HTTP non-OK menjadi `unauthorized`; effect menghapus JWT untuk semua exception, termasuk jaringan/5xx. Effect juga tidak memiliki cleanup/generation guard terhadap respons JWT lama.

**Dampak:** gangguan sementara dapat mengeluarkan pengguna. Request lama yang gagal setelah akun baru disimpan dapat menghapus token terbaru.

**Perbaikan:** hanya invalidasi sesi pada penolakan autentikasi yang relevan; network/5xx menjadi error sementara. Sebelum mengubah state/storage, pastikan request masih milik akun/token yang sama.

**Regresi:** 401 vs 403 vs 503 vs offline; ganti akun sebelum respons lama tiba; logout saat request pending.

### WH-14 — OAuth nonce dan pembersihan logout belum konsisten

**Sumber:** `web/src/google.ts:beginGoogleLogin()/consumeGoogleRedirect()/clearStoredGoogleIdToken()`; `web/src/App.tsx:signOut()` sekitar 1759–1766.

Nonce acak dikirim ke Google, tetapi tidak disimpan atau dibandingkan dengan nonce ID token saat callback. State sudah dicocokkan—itu pertahanan yang ada dan tidak boleh diabaikan dalam penilaian. Logout utama menghapus JWT aplikasi tetapi tidak memanggil `clearStoredGoogleIdToken()`, sehingga token Google untuk mode founder berita dapat tertinggal sampai kedaluwarsa. Fungsi clear ada dan dipakai di jalur berita, bukan logout utama tersebut.

**Perbaikan:** review flow OIDC end-to-end dan nonce binding dengan verifikasi server tetap sebagai otoritas signature/audience/expiry. Satukan kontrak logout lintas fitur; jelaskan apakah remembered-host grants sengaja dipertahankan. Jangan menganggap decode JWT lokal menggantikan verifikasi server.

**Regresi:** nonce hilang/salah, state salah, callback berulang, logout lalu membuka berita founder. Ini pekerjaan auth dengan izin khusus sebelum perubahan produksi; audit tidak membuktikan takeover akun.

### WH-15 — No-frame watchdog dapat dibatalkan terlalu awal

**Sumber:** `web/src/rtc.ts:readStats()` sekitar 811–836 dan 908–909.

Pada sampel pertama, `previousFrames < 0` membuat `lastDecodedAt=now` meskipun `framesDecoded=0`. Jika laporan juga punya width/height positif, cabang akhir membersihkan no-frame watchdog. Deteksi freeze berikutnya hanya menaikkan warning ketika bytes terus bertambah.

**Dampak bersyarat:** laporan awal berdimensi positif tetapi decode nol, kemudian payload berhenti, dapat tidak mendapat peringatan yang dijanjikan. Ini skenario stats tertentu, bukan klaim terjadi pada semua browser.

**Perbaikan:** pisahkan “laporan statistik sudah ada” dari “frame pertama berhasil didecode/ditampilkan”. Jangan membatalkan watchdog frame pertama tanpa bukti decode positif atau event pemutar yang sesuai.

**Regresi:** ukuran positif + decode nol, nol payload, payload masuk tanpa decode, penghitung decode tidak tersedia, frame pertama datang terlambat, desktop statis yang sah.

### WH-16 — Build bukan jaminan aman untuk kandidat uji

**Sumber:** `.github/workflows/build.yml:438–550`; `.github/workflows/deploy-web.yml:3–8`; `.github/workflows/release.yml:6–11`.

Fmt/clippy/test host berjalan di Linux; job Windows membangun engine/panel. Kode Windows-only dan interaksi perangkat belum mendapat bukti runtime dari itu. Selain itu Build sukses dapat memicu workflow deploy web/rilis melalui `workflow_run` dengan syarat masing-masing.

**Perbaikan:** gerbang validasi kandidat tanpa deploy otomatis, test/clippy Windows yang cocok, dan jalur artefak manual yang jelas. `prepare-host-windows.yml`/packaging build-only tetap dipertahankan. Jangan dispatch Build umum hanya untuk “cek hijau” tanpa meninjau dampak turunannya.

**Status:** tidak ada workflow di-dispatch; tidak ada run produksi diperiksa/dibatalkan.

### WH-17 — Log host mengandung bearer control lokal

**Sumber:** `host/src/main.rs:494–500`; `packaging/native-host/main.cpp:1297–1319`; `host/src/control.rs:499–540`.

Engine mencetak URL dan token control ke stdout. Panel mengarahkan stdout/stderr ke `host.log`. Control API memang loopback dan meminta token; itu pertahanan yang baik. Tetapi siapa pun yang mendapat log lengkap memperoleh bearer control yang masih berlaku selama instance tersebut hidup. Status API juga mengandung password pairing.

**Dampak:** kebocoran lewat pengiriman log bantuan/debug, bukan endpoint control yang terbuka ke internet. ACL dan kemungkinan akses user lain belum diverifikasi pada Windows.

**Perbaikan:** jangan taruh bearer di log umum; gunakan kanal IPC/file kredensial terbatas yang terpisah jika benar dibutuhkan panel. Sediakan ekspor diagnostik yang disensor. Jangan meminta pemilik mengunggah log mentah sebelum penyensoran.

**Regresi:** hasil ekspor support tidak mengandung control token, password, refresh token, resume token, atau kredensial TURN.

## 4. Hal yang sudah membantu dan perlu dipertahankan

- Host mempunyai gerbang `authorize_offer`; ICE/bye dibatasi pada peer aktif. Audit ini tidak menemukan alasan untuk menghapusnya demi “mempermudah koneksi”.
- Control API hanya bind loopback dan memeriksa token pada status/action.
- Web memfilter pesan media menurut host ID dan menserialkan pemrosesan answer/ICE async.
- Sesi terminal menutup transport; microphone permission yang terlambat memakai generation guard.
- Input memiliki ownership tombol dan cleanup saat disconnect, blur/visibility; jangan menggantinya dengan key-up global yang memutus input pengguna lain.
- Pipeline video membedakan connection-ready dari frame siap, memiliki keyframe rescue dan perlindungan sequence gap.
- Preview wallpaper membatasi ukuran/chunk serta memeriksa format; bukan menerima URL arbitrer dari peer untuk di-fetch.
- Grants host disimpan sebagai hash dan berkaitan dengan password host, bukan menyimpan pairing password di history browser.
- CSP script-src self dan tidak terlihat pemakaian `dangerouslySetInnerHTML` pada App.tsx yang diperiksa. Ini bukan bukti seluruh web bebas XSS.

## 5. UI/UX dan observabilitas yang masih harus dinilai nyata

Audit source tidak dapat menilai kualitas visual semua viewport. Target acceptance:

1. Desktop 1366×768 dan 1920×1080; mobile portrait/landscape; skala OS 100/125/150/200%.
2. Connect, cancel, retry, ganti monitor, mute/mic, fullscreen dan kembali ke history selalu dapat dilakukan tanpa reload.
3. Hanya satu sesi aktif; frame lama dibersihkan/dilabeli stale saat reconnect, bukan diperlakukan sebagai live.
4. Pesan memisahkan izin ditolak, host offline, relay gagal, transport tersambung tanpa frame, dan browser menolak playback.
5. Navigasi keyboard, focus dialog, Escape, label tombol ikon, kontras dan teks error dapat dipahami tanpa warna saja.
6. Angka bitrate/RTT/encode/decode tidak dijumlah atau dipasarkan sebagai glass-to-glass tanpa pengukuran yang benar.
7. Wallpaper otomatis/history membutuhkan penjelasan privasi yang nyata. `previewConsent=true` yang diisi kode bukan bukti persetujuan manusia; review kebijakan produk sebelum menyebutnya consent eksplisit.

## 6. Rencana perbaikan, bukan rewrite

### Batch A — capture dan leadership

WH-01 → WH-03 → keputusan model user/session untuk WH-02. Tambahkan invariant buffer dan lifecycle mutex. Jangan mengubah ACL/koordinasi lintas user secara otomatis tanpa review keamanan.

### Batch B — kontrak web–host dan lifecycle sesi

WH-04/05/06/09/15. Pecah orchestration koneksi dari App.tsx ke modul state machine yang dapat diuji, tanpa merombak visual bersamaan. Backend tetap kontrak yang ada.

### Batch C — input/audio/panel

WH-07/08/10/11/12/17. Prioritaskan stop/cancel yang benar sebelum optimasi latency. Setiap resource punya owner dan jalur teardown.

### Batch D — auth, acceptance, dan gate rilis

WH-13/14 memerlukan review auth tersendiri. WH-16 memisahkan validasi dari deployment. Jalankan [checklist manual](WEB-HOST-MANUAL-QA.md) pada SHA artefak yang sama dengan kandidat source.

**Definisi selesai:** setiap P1 diperbaiki dan mendapat regresi; seluruh acceptance wajib punya bukti pada mesin pemilik; P2 ditutup atau diterima eksplisit dengan batas terdokumentasi. Tidak menyatakan “support semua hardware” dari satu VM.

## 7. Perubahan yang benar-benar dilakukan pada sesi audit

- Menghapus `.github/workflows/test-lab.yml` dari checkout lokal atas arahan pemilik.
- Memperbarui `docs/CI.md`: lab manual pemilik, dampak penghapusan, dan peringatan rantai Build → deploy/rilis.
- Menambahkan fokus web + host di `ROADMAP.md`.
- Membuat laporan ini dan `docs/WEB-HOST-MANUAL-QA.md`.
- Mencatat hasil ke changelog, handoff, dan papan sesi.

**Tidak dilakukan:** memperbaiki 17 temuan runtime, mengubah auth/ACL, menghapus test suite, menghapus workflow pengujian driver, mencabut secrets, menghapus node Tailscale, membatalkan run remote, commit/push, build, deploy, atau bump versi. Penghapusan workflow belum berlaku pada GitHub sebelum dipush. Catatan sejarah tentang Test Lab sengaja tidak dihapus karena merupakan bukti masa lalu.
