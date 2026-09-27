# XyDesk Web (Vite + React)

Frontend browser XyDesk. Flutter Web ditinggalkan untuk target browser:
bundle CanvasKit terlalu berat (unduhan MB-an sebelum layar pertama muncul).
Client web ringan (~65 KB gzip) di folder `web/` memakai backend yang sama
persis dengan aplikasi Android/Windows.

- Kandidat domain publik: `https://www.xydesk.my.id`
- Kandidat aplikasi remote: `https://remote.xydesk.my.id`
- `app.xydesk.my.id`: redirect transisi; belum diterapkan sebelum deploy berizin.
- OAuth callback: `/auth/callback` pada masing-masing origin; wajib didaftarkan di Google Console. JWT/grant localStorage origin lama tidak dipindahkan.
- API, autentikasi, dan signaling: `https://signal.xydesk.my.id`
- Fitur: login OTP email, sambung ke host (ID 9 digit + password pairing),
  viewer WebRTC + input mouse/scroll ke data channel biner.

Deployment otomatis mengambil artefak `XyDesk-Web` dari workflow Build yang
sukses (job Web Application menjalankan `npm ci && npm run build` di `web/`),
lalu mempublikasikannya ke Cloudflare Workers Static Assets.
