# Security Policy / Kebijakan Keamanan

## English

**Supported versions.** Only the latest release on
[GitHub Releases](https://github.com/xykal/XyDesk/releases) and the live
web client at https://remote.xydesk.my.id receive fixes.

**Reporting.** Email `xycdigital@gmail.com` with subject `XyDesk security`.
Include affected component (Android / Windows host / web / signaling /
news worker), version or commit, reproduction steps, and impact. Do not
open a public issue for unpatched vulnerabilities. Expect an
acknowledgement within 72 hours and a status update within 14 days.
Coordinated disclosure: 90 days from report, sooner once a fix ships.

**Scope.** Code in this repository and the services it deploys
(`signal.xydesk.my.id`, `news.xydesk.my.id`, `remote.xydesk.my.id`).
Out of scope: volumetric DDoS, social engineering, issues in third-party
services (Cloudflare, Google, OneSignal), and findings that require a
compromised host machine.

**Controls in place.** WebSocket Origin allowlist, per-IP and per-account
rate limits with lockout on OTP/login, PBKDF2 + TOTP for admin, JWT
algorithm pinning, CSP `script-src 'self'`, HSTS, host control API bound to
127.0.0.1 with bearer token, pairing brute-force guard on the host,
`cargo clippy -D warnings`, `npm audit` clean, Dependabot. Each control has
a proving test in CI (`.github/workflows/build.yml`).

## Bahasa Indonesia

**Versi yang didukung.** Hanya rilis terbaru di GitHub Releases dan klien
web di https://remote.xydesk.my.id yang menerima perbaikan.

**Pelaporan.** Kirim email ke `xycdigital@gmail.com` dengan subjek
`XyDesk security`. Sertakan komponen, versi/commit, langkah reproduksi,
dan dampak. Jangan buka issue publik untuk celah yang belum ditambal.
Balasan awal maksimal 72 jam, pembaruan status maksimal 14 hari.
Pengungkapan terkoordinasi 90 hari sejak laporan, lebih cepat bila
perbaikan sudah dirilis.

**Cakupan dan kontrol** sama dengan bagian bahasa Inggris di atas.

Powered by XyVerse Technology Global
