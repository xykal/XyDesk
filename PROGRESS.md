# PROGRESS

Start: 2026-09-28

Riwayat sebelum tanggal ini: tidak ada log harian (lihat `CHANGELOG.md`
dan `docs/archive/` untuk jejak rilis dan audit lama).

## 2026-09-29 — hari kerja ke-2
Done:
- Encoder MFT (Media Foundation, AMD/Intel/NVIDIA): `host/src/mft.rs` +
  `mft_setup.rs`, pemilih tunggal NVENC > MFT > openh264 (WGC/GDI/DXGI),
  label `video.encoder` nvenc|mft|openh264. Host Check 36501126463 dan
  Build 36501298475 hijau. Belum diuji di GPU nyata.
- `host-check.yml` manual (fmt + clippy Linux, clippy target Windows).
- 6.8.8+62: CRT statis engine Rust, `release.yml` input `draft` + repo var
  `RELEASE_DRAFT=true`; Build 36503307846 hijau, Release 36504191667 hijau
  sebagai DRAFT (tidak publik). Rilis publik tetap v6.8.7.
Blocked:
- Verifikasi fungsi MFT butuh mesin AMD/Intel; tim hanya punya RDP.
Next:
- Hapus `RELEASE_DRAFT` saat siap publik; ekstraksi string hardcode ke ARB;
  zero-copy DXGI -> MFT; VERSIONINFO main.rc.

## 2026-09-28 — hari kerja ke-1
Done:
- Audit `docs/AUDIT-2026-09-28.md`; HIGH-1 (HSTS, frame-ancestors,
  object-src + proving test), HIGH-3 (timeout job release.yml), MED-4
  (atribusi XyVerse: brand.ts / brand.dart / brand.rs, footer, meta author,
  About, `--version`), MED-5 (SECURITY.md, PROGRESS.md, IDEAS.md,
  .editorconfig), MED-6 (dependabot.yml).
- Refactor selesai lebih awal hari ini: App.tsx, main.rs, session_page,
  session_panels dipecah; jalur tempel ADMIN_TOKEN dihapus dari web publik
  (commit 291822c, 193c0a4, 6060f9e, 2e2a6cf; semua Build hijau).
- Rebrand XySpace Tech -> XyVerse Technology Global di 26 berkas (web,
  klien, host, installer NSIS/Inno/WiX, legal, docs); nama tim baru ikut
  masuk daftar nama terlindungi worker berita (commit f346ca0, de56db3;
  Build 36494761138 hijau, deploy-web + deploy-news hijau).
- HIGH-2 selesai: 11 GitHub Action di-pin SHA penuh (d9a75ad, Build
  36496526154 hijau). OG image dibuat ulang lewat tool/art/og_image.py
  (tagline lama terpotong) (360b78c).
- i18n: kStrings -> 12 ARB + flutter gen-l10n, API context.tr tetap
  (af9724e, c2df261; Build 36498369872 hijau).
- Encoder MFT (Media Foundation, AMD/Intel/NVIDIA) di host: `mft.rs` +
  `mft_setup.rs`, pemilih tunggal NVENC > MFT > openh264 untuk WGC/GDI/DXGI,
  label `video.encoder` = nvenc|mft|openh264. Workflow manual
  `host-check.yml` (fmt + clippy Linux, clippy target Windows) untuk iterasi
  cepat. Belum diuji di GPU nyata — butuh PC AMD/Intel kall.
Blocked:
- tidak ada.
Next:
- Uji MFT di PC AMD/Intel (log `MFT aktif`); zero-copy DXGI -> MFT;
  ekstraksi string hardcode Indonesia ke ARB; harness latency lanjutan.
