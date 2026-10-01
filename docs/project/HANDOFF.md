# HANDOFF — Antrian Kerja Lintas Role

Hanya item yang **masih terbuka**. Log lama: `../archive/HANDOFF-2026-09.md`
(beku). Menutup item = hapus di sini + satu baris `CHANGELOG.md` `[Belum terbit]`.

Dibersihkan operator 2026-10-01: item Flutter/`desktop/` Electron yang sudah
diganti klien Kotlin + panel Win32 dicoret. Verifikasi perangkat nyata tetap
item terbuka (tidak bisa ditutup dari CI).

## Untuk: Client Android native

- [ ] **Screenshot sesi Android untuk berita** — rail + panel pengaturan, dari
  APK rilis di perangkat/emulator dengan video host asli. Pola nama:
  `web/public/news/shots/<versi>-android-sesi-*.jpg`.
- [ ] **Bukti lapangan** — pairing, IME 0x06 TEXT, clipboard opt-in, mic ke
  host, presence online/offline. CI tidak menggantikan HP.

Tertutup 2026-10-01: error pra-welcome. `RtcSession` rotasi ID; `Presence`
rotasi ID (#40). Clipboard tarik `0x09` sudah ada (opt-in).

## Untuk: Desktop Shell (Win32, `packaging/native-host/`)

- [ ] **Uji Windows asli** — TURN relay, tray, capture RDP/GDI. Wine/CI bukan
  bukti lapangan. Lihat `docs/qa/relay-turn-native-2026-09-23.md`.
- [ ] Screenshot host Windows untuk berita: ID+QR + panel saat sesi.

Tertutup sebagai usang: shell Electron/`desktop/` (dihapus 2026-09-23).

## Untuk: CI / Release

- [ ] **Keystore Android dirotasi** — APK lama tidak update-in-place; artikel
  rilis berikutnya harus jujur: uninstall dulu. Verifikasi
  `apksigner verify --print-certs` pada APK pertama keystore baru.
- [ ] `update.json` hanya aset GitHub Release, bukan di domain app/signal.
  Pastikan klien native membaca URL rilis yang benar sebelum mengandalkan
  in-app update.
- [ ] Versi, berita, `workflow_dispatch` Build/Release = keputusan operator.
  Jangan terbitkan ulang nomor build yang sama.

Usang: resep verifikasi Flutter Linux; permintaan dispatch Galih 3 Sep 2026.

## Untuk: Backend / Edge

- [ ] Defense-in-depth: HTTP DO Hub (`/kick`, `/stats`, `/hub/devices`)
  hanya tidak dirutekan `worker.js`. Pertimbangkan header `x-internal-admin`.
- [ ] Relay produksi satu penyedia (ExpressTurn). Cadangan
  `OPENRELAY_API_KEY` / `TURN_REST_*` masih kosong. Deploy CI tidak menimpa
  secret TURN Worker bila triplet GitHub kosong (disengaja).
- [ ] Billing sewa PC otomatis: butuh keputusan gateway + provisioning;
  bukan kerja sesi ini.

## Untuk: News & Konten

- [ ] Artikel berikutnya: apa+kenapa, changelog pengguna, screenshot native
  Android + host Windows (web sudah ada `web-sesi-*`). Penulis: Haekal Saputra.

## Untuk: Host Engine

- [ ] Encoder MFT: belum diuji GPU AMD/Intel nyata (hanya RDP).
- [ ] Mic klien → VB-CABLE: alasan `micInput.reason` ada di 6.11.1; bukti
  lapangan masih butuh PC + APK.

Tertutup: hello ditolak hub tidak lagi menggantung loop host.
