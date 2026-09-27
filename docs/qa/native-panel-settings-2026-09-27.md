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


## Paket final terverifikasi

- Source executable `1b873c3a615b0cbad43f8f76a0499626d9ce4700`.
- [Prepare Host Windows 36301838178](https://github.com/xykal/XyDesk/actions/runs/36301838178): **success**, termasuk kontrak/layout Linux, release-mode MSVC client/engine integration, Windows library tests dan packaging gates. Perbaikan posisi ikon/link dan status readiness sudah termasuk.
- [Prepare Host NSIS 36302323685](https://github.com/xykal/XyDesk/actions/runs/36302323685): **success**. Install ke path berspasi, hash engine, shortcut native, reinstall, direktori tak terkait ditolak, uninstall menjaga identity/extra files, serta default per-user install/uninstall diuji.
- Portable artifact `10925703270`, evidence `10926262058`, installer artifact `10926207447`. Artifact Actions memerlukan akses GitHub dan memiliki retensi 14 hari; salinan workspace telah diunduh.
- Installer `XyDesk-Host-Test-Setup-x64.exe`: 4,992,045 bytes; SHA256 `ad95a97b257329408c97b8532ae9ee056ab4c5cf5719c481efb9bb14c637c1c9`.
- Portable `XyDesk-Host-Test-x64-1b873c3.zip`: 8,181,308 bytes; SHA256 `9b75ff1abeb2b09df8d5e882a852a4359359e445718d401c3f6099c2c3b917a0`.
- Transfer checksum, source manifest, dan hash engine dalam ZIP diperiksa. Screenshot release pairing/collapsed direview; link-copy kini sejajar kartu dan ikon collapsed terpusat vertikal.
- Paket ini unsigned/test, bukan stable. Tidak ada MSI/APK baru. Web live tetap `19fcd58`.
- Sebelum uji, hentikan host lama lewat Hentikan/menu tray; X hanya menyembunyikan panel. Native/profile/settings yang sebelumnya terbuka kini tersedia dengan batas di atas. Hardware/RDP latency dan analog gamepad tetap belum divalidasi/diimplementasikan.

## Follow-up candidate: browser account / connection QR

Streaming, engine identity/coordination, adaptive video and input transport remain frozen.
Native candidate adds browser OAuth (PKCE S256, random state, exclusive loopback listener),
per-user Windows Credential Manager storage, account/help dialogs, ID-only locally generated
QR and password visibility. UI account login does not bind host ownership. Logout removes
only the native saved session, not Google's browser cookies. Real Google consent, phone QR
scan, and cancel/logout on a physical Windows desktop still need manual acceptance.

Web 0eee624: deployment workflow 36304233124 succeeded. Independently inspected artifact
10926488029 `touch-keyboard.png`: all ABC columns now visible. Previous 550a757 screenshot
had clipping even though smoke passed; new assertions cover row bounds and toggle overlap.
Native tests/build pending at time of this entry; do not treat code presence as validation.

### Verified follow-up evidence

- Source candidate: `8ead5ce897740e26264c9dc23d6d939b5121a25f`.
- Validate Web Host `36304441008`: all three jobs success (Linux, Windows, web).
  Includes RFC7636 S256 vector, OS randomness, callback parse/duplicate rejection,
  token header safety and profile validation. QR C++ output independently decoded
  with jsQR; expected ID-only `/connect?device=123456789` confirmed.
- Artifact `10925529796`: real Win32 resource/render screenshots for settings,
  profile, guide and QR, plus pairing/collapsed main panels. Visually inspected:
  no clipped dialog content. Profile screenshot is an offline fixture, NOT a
  successful account login. Screenshot mode opens neither engine nor OAuth.
- Web live HTTP checked against deployment artifact `10926473223`: identical bytes:
  `/assets/index-Cpnr-DHQ.js`, SHA256
  `fd6d34cbe41ecddf72424bce80531c040d5f7aeff13a476db43b953aeed86859`;
  `/assets/index-B12wERhM.css`, SHA256
  `245e27cf4d53a8ec014098dde9120e0b1c3c3d0a155b411b2d03352fa2a5dac0`.
- Protected-file comparison against `2e8ab26` is empty: `host/src`, RTC,
  adaptive_video, remote_pointer, video_playback, session_fullscreen,
  native control_client/control_contract unchanged.
- Prepare Host Windows `36304643208`: pending at this entry; installer not yet issued.

Remaining manual acceptance: Google browser consent and return, session restore,
logout/close cancellation on Windows, actual HP camera QR scan, native form actions
under a real running host, orientation on the user's browser. Browser permission
and Google consent UI remain browser-controlled; app dialogs cannot replace those.
Landscape lock is best effort after fullscreen, not guaranteed by every browser.

### Follow-up packaging complete

- Prepare Host Windows `36304643208`: success; artifact `10926014747`.
- Prepare Host NSIS `36305057832`: success; artifact `10926464556`.
- Portable ZIP SHA256 `578a745d9cb968d5905f300044013d31e2c54a69d6e9c2a820a4dacda8d222cc`
  (8,335,217 bytes).
- Installer SHA256 `7f4995b415865b7b2afc0311fd8df834a912248ce548a38464b74356794ec3c9`
  (5,106,679 bytes). Both downloaded hashes matched CI checksum files.
- Rebuilt engine SHA256 `052bcf29ca628550053452ac5a9e0272d753da203563868469615f1e713ac9f3`;
  source unchanged, binary NOT byte-identical to prior build. New JSON and QR
  license notices verified present inside portable payload.
- Unsigned test candidate, version stays 6.8.5; no stable/latest promotion.
- Delivered folder `deliverables/XyDesk-native-8ead5ce/`, includes installer,
  portable ZIP, checksums, NSIS validation record and `BACA-DULU.md`.
