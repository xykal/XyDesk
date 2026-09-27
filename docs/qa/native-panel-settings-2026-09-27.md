# Native panel: sidebar dan pengaturan privat

Izin operator: “gas” (27 Sep 2026), termasuk implementasi, push, Actions dan paket uji.

## Implementasi

- Jendela tetap luas 1100×720, dapat di-resize. Sidebar hamburger 104/52px, ikon tetap terlihat saat dilipat. Tidak ada animasi perpindahan halaman. Resource logo XyDesk asli menggantikan huruf X placeholder.
- Pairing ID/password dibatasi 560px, kartu 72px, value font 22px. Link ID-only dan Salin link tepat di bawah kartu. Input ID link wajib sembilan digit; password tidak masuk URL.
- Ikon atas membuka pengaturan, profil Windows, dan panduan. Profil menyebut user Windows/proses engine, bukan mengarang login Google. Login akun web masih terpisah.
- Dialog pengaturan standar Win32/Segoe UI: target bitrate 1/2/4/8/15/25/50 Mbps dan default engine 8 Mbps (bukan Auto adaptif web), password kustom dan acak. Password disimpan engine; perubahan mencabut remembered access sesuai API existing.
- Target bitrate panel berlaku selama engine berjalan, dapat ditimpa client; bukan preferensi persisten. Dialog menunjukkan hasil request, tidak mengklaim telemetry bitrate terbaru.

## Batas keamanan

`control_client.h`: launcher suspended + job object sebelum ResumeThread; STARTUPINFOEX handle allowlist hanya log, stdin NUL, dan ujung tulis pipe. Ujung baca tidak diwariskan. Bootstrap <=1024 byte, timeout 10 detik, protocol/PID/URL loopback/port/token divalidasi. Capability hanya di memori dan di-reset saat child berhenti.

WinHTTP worker: proxy dinonaktifkan, redirect ditolak, timeout, response <=8192 byte, POST `/action` dengan header `x-xydesk-token`. UI tidak diblokir oleh HTTP. Password JSON di-escape; tidak ada token/password pada command line produksi, URL browser atau heartbeat. Hasil hanya diterapkan ke child dengan PID yang sesuai. Error/timeout tidak dianggap sukses; timeout aksi dapat berarti hasil belum diketahui.

Identity tetap per akun Windows (atau XYDESK_HOME yang ditetapkan operator). Paket uji tidak mengarang identitas terpisah; password dapat memengaruhi instalasi lain pada identitas akun yang sama. Teks installer/panduan diperbaiki agar jujur.

## Bukti Actions pertama

- `d4ef78105c6620484ba2bf42d46ad367c7e7b15e`
- https://github.com/xykal/XyDesk/actions/runs/36301577792 — **success**, web + Windows + Linux.
- Linux: geometry, JSON parser, bootstrap contract/link/escaping tests.
- Windows: Rust format/clippy/tests, IPC Python, panel MSVC compile, geometry/shape snapshots, C++ launcher/WinHTTP integration with a real engine on loopback in an isolated temporary profile. Tests cover wrong bearer rejection, valid/invalid bitrate, rejected short password, escaped password response/persistence, random password and capability reset. No credentials or response bodies printed.
- Artifact `10925522842`: validation executable + snapshots. Pairing/collapsed screenshots reviewed; final small alignment/readiness corrections follow this baseline.

Final release-mode package validation is in Prepare Host Windows (native tests/integration included). Do not call that package ready until the corresponding run succeeds. No main Build, Flutter/APK build, production web deployment, version bump or stable promotion required.

## Remaining hardware acceptance

No claim of actual Windows/RDP capture latency, phone-network quality, real controller analog support or native Google account login. Analog host remains unsupported. No driver installation, RDP reconnection, account switching or Windows display-mode changes added.
