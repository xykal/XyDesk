# Relay TURN jujur — bukti sesi 2026-09-23

Sesi: `SESI-20260923-TARA-TURNREASON` (Backend / Edge),
`SESI-20260923-DANU-RELAYSTATUS` (Web),
`SESI-20260923-GALIH-RELAYHOST` (Host Engine). Tiga sub-sesi berurutan atas
satu instruksi operator di chat ("host engine, backend, web server"), lalu
dilanjutkan dua sub-sesi paritas: `SESI-20260923-LARAS-RELAYCLIENT` (Client
Flutter) dan `SESI-20260923-DANU-FRAMEGUIDE` (Web), ditutup
`SESI-20260923-CAKRA-INSTALLER` (CI / Release). Semuanya sudah **di-push** ke
`main` dan web/worker sudah **di-deploy**; izin operator bertahap di chat
("ya gas saja", lalu "gas semua").

## Masalah yang ditutup

Relay TURN adalah jalan keluar terakhir WebRTC; tanpa relay, pengguna di
belakang CGNAT/NAT simetris tidak akan pernah tersambung. Sebelum sesi ini,
kegagalan memakai relay tidak bisa dibaca di mana pun:

- Worker menjawab **teks polos `forbidden` 403** untuk semua sebab sekaligus
  (tanpa token, token basi, tiket dicabut, sampai gangguan server otorisasi).
- Client web `turnIce()` menelan setiap kegagalan menjadi **daftar kosong yang
  sama** — `if (!res.ok) return []` dan `catch { return [] }`.
- Host hanya mencetak satu baris `eprintln` yang hilang di log, tanpa sebab
  yang terstruktur dan tanpa jejak di `/status`.

Akibatnya persis satu jenis laporan yang tidak bisa ditindaklanjuti:
"menyambung… tapi tidak pernah jadi".

## Yang berubah

| Sisi | Sebelum | Sesudah |
|---|---|---|
| `cloudflare/src/worker.js` | `403 forbidden` (teks) | `403 {error:'turn-forbidden', reason, hint}` — `no-credentials`, `token-invalid`, `ticket-invalid`, `ticket-revoked` |
| AuthStore tidak terjangkau | `403` (terbaca seperti "ditolak") | `503 {error:'turn-auth-unavailable'}` — gangguan sesaat dibedakan dari penolakan |
| `web/src/api.ts` | `RTCIceServer[]`, semua gagal → `[]` | `{servers, ok, reason, hint}`; reason `no-servers`, `providers-failed`, `turn-not-configured`, `turn-forbidden`, `turn-auth-unavailable`, `network`, `http-<status>` |
| `web/src/rtc.ts` | tidak ada jejak relay | `relayState` / `relayServers` / `relayReason` / `relayHint` di statistik; `stop()` reset ke `pending` |
| `web/src/session_ui.tsx` | hanya baris "Jalur WebRTC" | + baris "Relay TURN" dan catatan `role="status"` saat relay tidak ada |
| `host/src/relay.rs` (baru) | parse JSON sendiri di `main.rs` | tipe `RelayOutcome`, `reason_label`, telemetry |
| `host` `/status` | tanpa blok relay | `relay: {state: ready\|unavailable\|unknown, servers, reason, label, detail, checkedAtMs}` |

Keputusan yang dipertahankan: **ketiadaan relay tidak pernah menggagalkan
sesi.** Banyak jaringan memang tersambung langsung; mematikan sesi hanya
karena relay tidak ada akan merusak kasus paling umum.

## Bukti yang dijalankan (lokal, hari ini)

```
cloudflare/  node --test                    → 201/201 pass, 0 fail
web/         node --test                    → 102/102 pass, 0 fail
web/         npm run build (tsc -b + vite)  → hijau, dist/assets/index-CBmuV7k6.js
host/        cargo test --all-targets       → 177 lib + 9 integrasi, 0 fail
host/        cargo clippy --all-targets -- -D warnings → bersih
host/        cargo fmt --check              → bersih (sebelumnya GAGAL)
host/        cargo check --bins             → hijau
```

Uji baru: 13 (`cloudflare/test/turn-auth.test.js`), 12 (4 di
`web/test/rtc.test.js`, 8 di `web/test/turn_ice_api.test.js`), 7
(`host/src/relay.rs`). Satu ekspektasi lama sengaja diubah:
`cloudflare/test/bound-ticket.test.js` dulu mengharap `403` saat AuthStore
mati, sekarang `503` + `error` — kredensial tetap tidak keluar, tetapi
sebabnya kini jujur.

## Temuan di luar scope (bukan area sesi ini)

1. **Job "Uji Logika Host (Rust)" merah di `main`.** `host/src/main.rs` tidak
   lolos `cargo fmt --check` sejak sebelum sesi ini (diverifikasi dengan
   `git show HEAD:host/src/main.rs` → `rustfmt --check` gagal juga). Run
   `35879908181` (HEAD `1473799`) dan `35772171870` gagal di langkah
   `Cek format`; job `windows` menunggu job ini sehingga rantai Windows ikut
   tertahan. Sudah dibersihkan di sesi ini; perlu dispatch `Build` untuk
   memastikan hijau di runner.
2. **MSI WiX tidak bisa dibangun.** Job `installer-lint` memasang WiX 4.0.6
   lalu `wix build -ext WixToolset.UI.wixext` → `WIX0144: The extension
   'WixToolset.UI.wixext' could not be found`. Tidak ada `wix extension add`
   di workflow mana pun, sedangkan `release.yml` dan
   `prepare-windows-installer.yml` memakai `-ext` yang sama. Area CI/Release;
   dicatat di `HANDOFF.md` dengan saran perbaikan.

## Status rilis

Di-push dan di-deploy pada 2026-09-23 (izin operator di chat, jalur papan
#5): worker signaling versi `eeb2a63e-2243-4bb0-abce-4ca36148b983`; web versi
`7ab10bf5-1827-4f03-99e2-637d6737a592` (bundle `index-BB7mntUO.js`) untuk
gelombang pertama, lalu **versi `48048613-3f02-4c81-8c1a-d8ca4c28259a` bundle
`index-l7Eyy51I.js`** setelah paritas client + panduan frame masuk (`main`
`0bd310c`). Verifikasi pasca-deploy gelombang kedua: md5 `index.html` live ==
build `6ea6ae17a725360f1db9f2bf6a9433eb`, md5 bundle live == build
`81cdd6b703e5ad25cba7d3c188ade009`, `content-type` `text/javascript`, dan
penanda `session-frame-guidance` / "Ganti layar" / "Relay TURN" ada di bundle
yang benar-benar direferensikan `index.html` (bukan chunk tebakan).

CI: `Build` `35889559888` (11/11) atas source gelombang pertama; `Build`
`35894169582` @ `0bd310c` **11/11 hijau** setelah paritas Flutter + panduan
frame masuk; `prepare-windows-installer.yml` run `35895089978` SUCCESS
menghasilkan MSI + NSIS nyata dari Build itu (`docs/qa/installer-native-2026-09-23.md`).

## Batas jujur (yang BELUM dibuktikan)

- **Tidak ada uji di Windows.** `host/src/relay.rs` murni Rust tanpa API
  Windows dan lulus di Linux, tetapi jalur relay sungguhan (host di belakang
  CGNAT) hanya bisa dibuktikan di lapangan.
- **Tidak ada uji perangkat nyata / browser E2E** untuk perubahan web; yang
  dijalankan adalah uji unit + build produksi. Yang diperiksa di produksi
  adalah byte bundle (md5/cmp), header, rute, dan penanda fitur di dalam
  bundle — bukan klik manusia.
- **Jalur sukses relay belum diuji di produksi.** Verifikasi pasca-deploy
  menutup jalur penolakan (tanpa token → `no-credentials`, token rusak →
  `token-invalid`) dan jalur admin yang tak boleh berubah; jalur
  `X-Admin`/`ADMIN_SECRET` tidak bisa diuji karena secret itu tidak ada di
  lingkungan sesi, dan relay sungguhan hanya terbukti saat ada dua perangkat
  nyata di jaringan yang butuh relay.
- **Client Flutter sudah tidak gagal senyap** (`_fetchRelay()` + `TurnRelay`
  + baris "Relay TURN", 9 uji baru, `flutter test` 77/77), tetapi **belum
  dijalankan di HP nyata** — buktinya uji unit + `flutter analyze`, bukan sesi
  sungguhan ke host.
- **Panduan frame di web belum diuji dengan mata pengguna**: yang diperiksa
  adalah uji unit `session_guidance.test.js` (7 kasus) dan byte bundle live,
  bukan klik manusia di sesi yang benar-benar membeku.
