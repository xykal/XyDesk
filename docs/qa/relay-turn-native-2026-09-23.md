# Relay TURN host native: sebab sebenarnya "invalid turn server credentials" (23 Sep 2026)

Sesi `SESI-20260923-OPERATOR-TURNFIX` (Operator - XyDesk Team). Ditemukan dari
laporan uji lapangan paket `XyDesk-x64.msi`/`XyDesk-x64.exe` yang baru
diserahkan: sesi web (Chrome Android) → host gagal pada detik-detik pertama
setelah offer.

## Yang dilaporkan pengguna (apa adanya)

```
[xydesk-host] TURN siap: 1 server relay; ICE memilih direct bila tersedia
[xydesk-host] peer connection gagal untuk web-96d572f2-…: invalid turn server credentials
[xydesk-host] error: tipe tak dikenal
[xydesk-host] sesi diakhiri client web-96d572f2-…
```

Dua hal berbeda tersembunyi di empat baris itu: **sesinya gagal**, dan
**sebabnya tidak pernah sampai ke pengguna**.

## Rantai sebab (yang pertama: sesi gagal)

1. `host/src/main.rs::fetch_turn_servers` memetakan server relay ke
   `RTCIceServer { urls, username, credential, ..Default::default() }`.
2. `..Default::default()` meninggalkan `credential_type` sebagai **`Unspecified`**
   — itulah nilai `#[default]` di webrtc 0.11
   (`webrtc-0.11.0/src/ice_transport/ice_credential_type.rs`).
3. `RTCIceServer::urls()` di webrtc 0.11
   (`webrtc-0.11.0/src/ice_transport/ice_server.rs`) untuk skema `turn:`/`turns:`:
   username/credential kosong → `ErrNoTurnCredentials`; lalu
   `match credential_type { Password => set password, Oauth => no-op,
   _ => Err(ErrTurnCredentials) }`.
4. `ErrTurnCredentials` berbunyi **"invalid turn server credentials"** — dan
   komentarnya berbunyi "credentials are partial or **malformed**". Pemeriksaan
   ini berjalan di `RTCPeerConnection::new` → `init_configuration` →
   `server.validate()` (`webrtc-0.11.0/src/peer_connection/mod.rs:273`),
   jadi ia gagal **sebelum satu paket STUN pun dikirim**.
5. `Session::new_with_video_level` mengembalikan `Err`; host mencetak
   "peer connection gagal …" lalu mengirim `{type:'error', error:'peer-connection-gagal'}`.

**Kredensialnya sendiri SAH.** Kalimat galat dari library menyesatkan: yang
"malformed" adalah tipe kredensial di sisi host, bukan kredensial ExpressTurn.

Kapan masuk: pemetaan itu ditulis `0339a40f` (Galih, 2026-09-23) ketika relay
pindah ke modul `host/src/relay.rs`. Sesi itu sendiri mencatat "belum ada
pembuktian di Windows", dan host native memang baru diuji lapangan hari ini —
itu sebabnya bug ini tidak pernah terlihat sebelumnya. Relay di server tidak
pernah dipakai oleh host native sejak hari itu.

## Rantai sebab (yang kedua: kegagalan itu tak terdengar)

6. `cloudflare/src/hub.js` merelai hanya `pair, pair-response, offer, answer,
   ice, bye`. Tipe `error` jatuh ke `default` → hub menjawab
   `{error:'tipe tak dikenal', reason:'error'}` **kepada host**, dan client
   tidak pernah menerima kabar apa pun.
7. Karena itu log host berakhir dengan baris yang membingungkan
   (`error: tipe tak dikenal`), sementara sisi client hanya melihat sesi mati
   tanpa penjelasan. Host juga tidak mencetak field `reason`, sehingga
   diagnosis pertama kehilangan satu petunjuk lagi.

## Bukti

**1. Kredensial TURN produksi benar-benar bisa allocate** (`tool/check_turn_auth.py`,
allocate STUN ber-MESSAGE-INTEGRITY, bukan sekadar "Worker menjawab"):

```
→ allocate turn:free.expressturn.com:3478 sebagai <username>…
  OK — relay 136.69.188.36:40142, realm 'localhost'
→ allocate turn:free.expressturn.com:3478?transport=tcp sebagai <username>…
  OK — relay 136.69.188.36:53352, realm 'localhost'
Ringkas: 2 diperiksa, 0 gagal
```

Kontrol negatif (kredensial palsu) → `allocate ditolak: 401 (kredensial TIDAK
diterima server TURN)`, exit 3. Alat ini juga punya `--self-test` (server TURN
tiruan di loopback) supaya bisa diuji di CI tanpa jaringan.

**2. Reproduksi lokal di Rust** (`cargo test --bin xydesk-host relay_ice_tests`):

- dengan kode lama (`..Default::default()`):
  `panicked …: ErrTurnCredentials` — galat yang sama dengan lapangan;
- dengan perbaikan: 3/3 lulus, termasuk
  `peer_connection_dengan_relay_dan_stun_terbentuk` yang benar-benar memanggil
  `api.new_peer_connection(...)` dengan STUN + TURN.

**3. Hub** (`cloudflare/test/hub.test.js`): tes baru
`error dari host diteruskan ke client…` **gagal di kode lama**
(`not ok 17`, pass 17/fail 1) dan lulus di kode baru; seluruh suite cloudflare
203/203 hijau.

## Perbaikan

- `host/src/main.rs` — `ice_server_from()` selalu memasang
  `credential_type: RTCIceCredentialType::Password`; log "TURN siap" kini
  menyebut URL relay (hanya `skema:host:port`, kredensial & query dibuang);
  baris `error` kini mencetak `reason`; ditambah `mod relay_ice_tests` (3 tes).
- `cloudflare/src/hub.js` — tipe `error` ikut direlai (dua arah, tidak sesama
  role), sehingga `peer-connection-gagal`, `turn-gagal`, `offer-ditolak` sampai
  ke client yang memang sudah punya penanganannya (`web/src/rtc.ts`,
  `lib/webrtc/rtc_service.dart`).
- `tool/check_turn_auth.py` — alat baru (lihat atas); `tool/check_turn_live.js`
  diberi catatan batasnya.
- `.github/workflows/deploy-signaling.yml` — langkah wajib pasca-deploy:
  "Buktikan kredensial TURN bisa allocate (bukan sekadar ada)", 3 percobaan.
  Gerbang lama (hitung penyedia) hijau walaupun kredensialnya sudah mati.

## Bagaimana perbaikan ini dikirim (semua terverifikasi)

| Langkah | Hasil |
|---|---|
| Commit | `41ba057` (host + hub + alat + gerbang), `a1090e3` (dokumen + redaksi); lokal == `origin/main` |
| Build `35911857824` @ `a1090e3` | **11/11 SUCCESS** — termasuk "Uji Logika Host (Rust)" (menjalankan tes baru di runner) dan "Windows x64 (native C++ panel + Rust engine)" |
| Installer `35912714925` @ `a1090e3` | **SUCCESS** — artefak `XyDesk-Windows-x64-Installer` |
| Paket uji baru | `XyDesk-x64.exe` sha256 `32bea417…b0ca` (5.072.868 B), `XyDesk-x64.msi` sha256 `6b4ae84e…ddfb` (6.393.856 B); gerbang teks EXE di dalam payload Build: "6 teks panel utuh, 148 rentetan bebas mojibake"; biner host memuat log baru (`server relay (`) sebagai bukti build dari kode yang diperbaiki |
| Worker signaling | dideploy lewat jalur cepat #5 (API Cloudflare, kode dari `a1090e3`): versi `ce199fe5-165e-41bd-bff2-8dd8a8d52f64` (sebelumnya `03e40e2c…`). Secret Worker tidak disentuh (deploy kode saja). Verifikasi pasca-deploy: `/healthz` 200, `/turn-ice` 403 berstruktur (`turn-forbidden` + `no-credentials` + `hint`) |
| Allocate relay | `tool/check_turn_auth.py` — `turn:free.expressturn.com:3478` UDP **dan** `?transport=tcp` keduanya OK (relay address diberikan); kredensial palsu ditolak 401 |

**Yang belum: `Deploy Signaling` lewat GitHub masih MERAH — dan itu bukan karena
perubahan ini.** Sejak 22 Sep 2026 run itu berhenti di langkah "Deploy ke
Cloudflare" dengan alasan `RESEND_API_KEY belum diatur di GitHub Secrets/
Variables` (anotasi run `35771592422` dan `35911873731`), sehingga langkah gerbang
baru pun ter-skip. Secret `RESEND_API_KEY` ada di daftar GitHub (diperbarui
22 Sep 16:36Z) tetapi isinya kosong saat dibaca runner; kunci Resend di vault
kerja juga menjawab 403 saat diuji 23 Sep — jadi kemungkinan besar kunci lama
sudah dicabut. Yang perlu dilakukan pemilik: buat kunci Resend baru lalu isi
ulang secret `RESEND_API_KEY`, lalu dispatch `Deploy Signaling` supaya gerbang
allocate ikut berjalan di CI.

## Batas jujur

- Perbaikan **belum diuji di Windows asli**; bukti di atas dari Linux
  (`cargo test`, Node, allocate TURN nyata). Paket uji baru wajib dibangun lalu
  dijalankan ulang oleh operator.
- Relay masih bergantung pada **satu penyedia** (ExpressTurn free, kredensial
  `direct`). Bila penyedia itu mati, gerbang deploy baru akan berteriak — itu
  memang tujuannya — tetapi relay tetap hilang sampai penyedia cadangan diisi
  (`OPENRELAY_API_KEY`/`TURN_REST_*` sudah didukung kode, belum diisi).
- Alat baru hanya memeriksa URL `turn:` (UDP). URL `turns:` (TLS) dilewati
  dengan jujur, belum diperiksa.
- **Kebocoran (diredaksi 23 Sep 2026, rotasi tetap wajib):** username +
  credential TURN produksi tertulis apa adanya di `AGENT_BOARD.md` (sesi
  2026-09-07 dan 2026-09-11), `HANDOFF.md`, `cloudflare/README.md`, dan
  `docs/BACKEND_FIX_20260911.md` pada repo **publik** sejak 7 Sep 2026.
  Nilainya sudah diganti penanda di lima berkas itu, tetapi **riwayat git
  tidak bisa dihapus** — cukup lama bagi crawler. Satu-satunya obat adalah
  memutar kredensial di ExpressTurn lalu memasang ulang `TURN_DIRECT_*`;
  sesudah itu cukup tulis "nilai ada di secret Worker" tanpa nilainya.
