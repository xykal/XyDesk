# Login admin: password + Cloudflare Turnstile

## Alur login produksi

1. Dashboard `admin.xydesk.my.id` menampilkan username dan password.
2. Cloudflare Turnstile memverifikasi browser sebelum request login diteruskan.
3. Worker memverifikasi password dengan PBKDF2-HMAC-SHA256 dan pepper server
   `ADMIN_AUTH_KEY`.
4. Jika benar, server menerbitkan cookie sesi `__Host-xydesk_admin` yang
   `Secure`, `HttpOnly`, `SameSite=Strict`, dan berlaku satu jam.

Tidak ada TOTP, authenticator, atau recovery code yang diminta pada login
produksi. Field TOTP/recovery lama yang mungkin masih berada di storage hanya
legacy data dan tidak dipakai dalam verifikasi password-only.

## Setup awal

Sebelum akun password aktif, pemilik membuktikan identitas melalui login Google
admin yang masih diizinkan. Pemilik lalu memilih username 3–32 karakter dan
password unik 14–128 karakter. Cloudflare Turnstile tetap wajib. Setelah setup
satu akun selesai, login Google ditutup dan dashboard memakai password +
Turnstile.

Model saat ini satu akun admin awal; bukan sistem multi-admin.

## Penyimpanan dan proteksi

- Password: PBKDF2-HMAC-SHA256 dengan salt acak 16 byte dan 100.000 iterasi.
  Verifier diberi HMAC-SHA256 dengan pepper server `ADMIN_AUTH_KEY`.
- Sesi: token acak 256 bit, hanya hash yang disimpan server. Cookie host-only
  untuk `admin.xydesk.my.id` melalui Worker signaling.
- Login dibatasi 5 percobaan per username per 15 menit dan 20 per IP per 15
  menit. Turnstile diverifikasi sebelum hashing password.
- Semua POST admin memeriksa Origin admin. CORS ber-credentials hanya diberikan
  kepada origin admin yang diizinkan; tidak memakai wildcard.
- Audit mencatat login berhasil dan reset recovery tanpa password atau token.

## Konfigurasi produksi

- `ADMIN_AUTH_KEY`: secret acak minimal 32 karakter untuk pepper password dan
  penyimpanan credential. Jangan mengganti atau menghapusnya tanpa migrasi
  terencana.
- `TURNSTILE_SECRET` dan sitekey publik admin diperlukan untuk login.
- `ADMIN_GOOGLE_CLIENT_ID` hanya untuk bootstrap setup awal. Setelah password
  aktif, endpoint Google mengembalikan `google-login-disabled`.
- `AUTH_SECRET`, `GOOGLE_CLIENT_ID`, dan secret web/APK tidak dirotasi oleh
  perubahan login admin.

## Recovery

Saat password terlupa, pemilik infrastruktur dapat menjalankan prosedur
break-glass satu kali dengan secret `ADMIN_RECOVERY_KEY` sementara. Prosedur
tersebut:

- mengganti username/password;
- mencabut seluruh sesi admin lama;
- tidak membuat TOTP atau recovery code;
- mengembalikan `ADMIN_RECOVERY_KEY` menjadi tidak aktif setelah dipakai.

Recovery key tidak boleh ditanam di frontend, dikirim melalui chat, atau
dibiarkan aktif setelah reset. Tidak ada bypass publik untuk reset password.

## Verifikasi

`cloudflare npm test` mencakup hashing/salt, penyimpanan credential, cookie
flags, reset recovery, session invalidation, rate limit, CSRF, dan penolakan
Google setelah password aktif. Pemeriksaan browser harus memverifikasi alur
Turnstile → password → cookie HttpOnly → reload → logout.
