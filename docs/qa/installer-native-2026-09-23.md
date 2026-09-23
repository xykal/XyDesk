# Installer native Windows — MSI + NSIS terbentuk dari Build yang lulus

Sesi: `SESI-20260923-CAKRA-INSTALLER` (CI / Release), atas izin operator di
chat ("gas semua"). Sumber: `main` `0bd310c` — versi tetap `6.8.5+59`.

## Yang dijalankan, berurutan

1. `Build` run **`35894169582`** @ `0bd310c`: **11/11 job SUCCESS**
   (host Rust, lint installer, Windows x64, APK, web, backend, berita,
   Flutter, lintas-dokumen, deteksi area, ringkasan).
2. Urutannya bukan pilihan: `prepare-windows-installer.yml` menolak
   `build_run_id` yang bukan run `build.yml` bersatus `success` dengan
   `head_branch` `main` dan `head_sha` == SHA checkout. Dispatch diterima
   (HTTP 204) dan run **`35895089978`** berjalan @ `0bd310c`.
3. Run installer **SUCCESS di semua langkah**, termasuk
   "Pastikan Build native berhasil pada commit yang sama",
   "Verifikasi bundle native", "Build MSI dan NSIS dari payload yang sama",
   dan "upload-artifact".
4. Artefak `XyDesk-Windows-x64-Installer` (artifact id `10766851184`,
   10,68 MB, dibuat `2026-09-23T17:22:55Z`) diunduh lewat API dan diperiksa
   **di lingkungan sesi**, dari byte-nya sendiri — bukan disimpulkan dari log
   runner.

## Hasil pemeriksaan

| Berkas | Ukuran | SHA-256 | `.sha256` bawaan |
|---|---|---|---|
| `XyDesk-x64.msi` | 6.373.376 B | `43d4ebbe10b2bbd2f039aea0dde6e24d382b8fc0da37fdf24ad80519713eedd8` | cocok |
| `XyDesk-x64.exe` | 5.054.077 B | `c6d5de20c55d19cd2dfb856cafe66669af3f1694ca64a43561f5b2f4d82ef52e` | cocok |

Jenis paket dibaca dari isi berkas, bukan dari namanya:

- **MSI** — OLE *Composite Document* bergaya MSI Installer: `Template
  x64;1033`, `Name of Creating Application: WiX Toolset (4.0.6.0)`,
  `Author: XySpace Tech`, waktu buat `2026-09-23 17:22:38`. Nama berkas
  payload yang diharapkan ada di dalam database: `XyDesk.exe`,
  `xydesk-host.exe`, `LICENSE-XyDesk.txt`,
  `LICENSE-XyDesk-English.rtf`, plus versi `6.8.5`.
- **EXE** — `PE32 executable for MS Windows … Nullsoft Installer
  self-extracting archive` (NSIS v3.12 pada manifest-nya), versi `6.8.5`
  tersimpan UTF-16. Payload NSIS dikompres LZMA sehingga nama berkas di
  dalamnya memang tidak terbaca `strings`; isi payload diverifikasi langkah
  "Verifikasi bundle native" di runner **sebelum** paket dibangun, jadi
  ketiadaan nama berkas di `strings` bukan tanda paket kosong.

## Yang sengaja TIDAK dilakukan

- **Tidak ada GitHub Release.** Rilis dan nomor versi adalah keputusan pemilik
  (AGENT.md 2.1); `prepare-windows-installer.yml` memang dirancang hanya
  menghasilkan artefak. Daftar rilis terakhir tetap `uxhd11-9869711`
  (2026-09-21) — tidak ada rilis bertanggal 2026-09-23, dan `releases/latest`
  tidak tersentuh.
- **Tidak ada instalasi di mesin Windows.** Yang terbukti: paket terbentuk
  utuh, strukturnya benar, hash-nya cocok. Install/uninstall/reinstall
  sungguhan tetap perlu uji lapangan (`host/TEST-LAB-WINDOWS.md`).
- **Tidak ada tanda tangan Authenticode.** SmartScreen tetap akan
  memperingatkan; menyalakannya butuh sertifikat — keputusan pemilik, di luar
  scope sesi ini.

## Catatan operasional

- Artefak disimpan **30 hari** (`retention-days: 30`); hash di atas berguna
  untuk memverifikasi unduhan selama masa itu.
- Menjalankan ulang jalur ini untuk paket berikutnya cukup: dispatch `Build`
  di `main`, tunggu 11/11 hijau, lalu dispatch installer dengan
  `build_run_id` run itu — urutan itu wajib, karena validasi SHA-nya
  membandingkan run Build dengan `main` saat installer dijalankan.
