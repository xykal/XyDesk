# ARSIP — AGENT_BOARD (beku 6 Okt 2026)

> **Papan ini sudah tidak dipakai.** Sistem role, kunci area, dan antrean
> izin push dihapus pada 6 Okt 2026: seluruh pekerjaan dikerjakan atas nama
> pemilik repo lewat PR biasa (lihat `AGENT.md`). Berkas ini disimpan utuh
> sebagai catatan sejarah — jangan menambah baris baru di sini.
> Log sebelum 28 Sep 2026 ada di `AGENT_BOARD-2026-09.md`.

---

## Isi papan saat dibekukan

Papan ini adalah satu-satunya sumber kebenaran **siapa lagi kerja apa** dan
**push siapa yang sudah diizinkan**. `AGENT.md` mewajibkan: baca papan ini di
awal sesi, **kunci** areamu, barulah kerja. `HANDOFF.md` mencatat *hasil*
lintas role; papan ini mencatat *keadaan saat ini* (real-time).

> Papan ini ikut berkembang lewat commit ke `main`. Karena agent baru boleh
> push setelah diizinkan, alur persetujuannya:
> 1. Agent menyampaikan permintaan izin (chat/laporan sesi) berisi ID sesi +
>    ringkasan, dan menulis baris `MENUNGGU` di antrean bawah.
> 2. Operator mengubah status baris itu menjadi `DISETUJUI` pada commit
>    kecil di `main`.
> 3. Agent push dengan setiap commit memuat `Izin: <ID-SESI>` di body.
>
> **Catatan 5 Sep 2026:** `verify-push-auth.yml` dihapus operator sendiri
> (commit `b4ce4a4`) dan `main` tidak memakai branch protection, jadi tidak
> ada mesin yang memeriksa langkah 1–3 lagi. Alurnya tetap wajib diikuti
> sebagai kebiasaan tim — papan dan penanda `Izin:` adalah satu-satunya
> jejak audit yang tersisa sampai operator menghidupkan gerbangnya lagi.

## Aturan operator (sejak 3 Sep 2026)

1. **Versi & berita ditentukan operator** — agent tidak menetapkan nomor
   versi, isi/terbitnya berita, atau bump `pubspec.yaml` sendiri.
2. **Cek sesi lain sebelum rilis** — build penuh/rilis hanya diajukan bila
   semua sesi yang menyentuh area rilis sudah `SELESAI`; kalau belum →
   tahan, lapor operator, jangan memaksakan.
3. **Tiap agent menulis bahan artikel kerjanya** — blok dampak pengguna +
   screenshot asli (gaya `docs/NEWS_STYLE.md`), ditulis saat sesi
   ditutup; role CI/Release menyatukan bahan semua agent menjadi SATU
   artikel saat rilis.
4. **Build/kompilasi/kemasan/deploy/rilis = kewenangan role CI/Release
   (Cakra)** — semua lewat `workflow_dispatch` setelah izin operator;
   push agent lain **tidak boleh memicu actions**. Dulu ada pengecualian
   untuk gerbang audit izin `verify-push-auth.yml`, tetapi workflow itu
   dihapus operator pada 5 Sep 2026 (commit `b4ce4a4`) — sekarang tidak
   ada satu pun workflow yang jalan karena push. Agent push kode +
   dokumen saja.
5. **Jalur deploy cepat (restu operator di chat, 3 Sep 2026)** — untuk
   layanan yang butuh cepat live: **Web app** (Danu) serta **worker
   Backend/Edge dan worker berita** boleh deploy langsung tanpa menunggu
   dispatch CI/Release. Syarat kumulatif: (a) perubahan sudah di `main`
   (gerbang `verify-push-auth` sudah dihapus operator pada 5 Sep 2026,
   jadi tidak ada run yang perlu ditunggu); (b) build memakai env
   produksi yang benar
   (mis. `VITE_GOOGLE_CLIENT_ID` untuk web); (c) verifikasi pasca-deploy
   wajib dan tercatat (contoh web: md5 bundle live == build, content-type
   JS benar); (d) dicatat terbuka di baris sesi papan + item HANDOFF ke
   CI/Release pada sesi yang sama. Build/rilis penuh (APK, Windows,
   installer, tag rilis) TETAP kewenangan CI/Release. Kredensial deploy
   adalah milik operator — pembagiannya ke lingkungan agent lain adalah
   keputusan operator, bukan agent.
6. **Role Operator setara pemilik repo (ditetapkan 6 Sep 2026)** —
   `Operator - XyDesk Team` boleh mengerjakan semua area sekaligus dalam
   satu sesi, tidak mengantre `DISETUJUI`, dan boleh masuk area yang
   terkunci bila operator memerintahkan langsung (baris `LAGI KERJA`
   agent lain ditulis ulang lebih dulu). Yang tetap wajib restu operator
   di chat, persis seperti role lain: **nomor versi** (aturan #1),
   **terbit/ubah berita**, dan **dispatch Build/Release/deploy** (aturan
   #4). Rinciannya di `AGENT.md` bagian 2.1 — papan ini hanya pengingat.

## Alur sesi (3 langkah)

1. **Kunci sesi** — tambah baris di tabel *Sesi aktif*, status `LAGI KERJA`.
   Format ID: `SESI-<YYYYMMDD>-<NAMA>-<AREA>`, contoh `SESI-20260903-CAKRA-CI`.
   Satu area hanya boleh dikunci satu agent. Area yang terkunci = jangan
   masuk tanpa menunggu.
2. **Minta izin push** — setelah CI area-mu hijau, pindahkan baris ke
   *Antrean izin push* (`MENUNGGU`) + ringkasan. Operator menulis
   `DISETUJUI` (atau `DITOLAK` + alasan).
3. **Tutup sesi** — setelah push hijau, pindahkan baris ke *Riwayat sesi*
   (`SELESAI` + tautan run CI). Riwayat tidak boleh dihapus.

## Sesi aktif (LOCK)

> Kosong = tidak ada area yang dikunci. Baris `SELESAI` tidak boleh menumpuk:
> begitu sesi ditutup, hapus barisnya (hasil dicatat di `CHANGELOG.md`).

| ID Sesi | Agent | Role / Area | Status | Sedang mengerjakan | Mulai |
|---|---|---|---|---|---|

## Antrean izin push

> Hanya sesi yang belum selesai. Baris yang sudah `SELESAI` dipindah ke
> `AGENT_BOARD-2026-09.md` saat sesi ditutup.

| ID Sesi | Agent | Ringkasan perubahan | Status izin | Disetujui oleh | Kapan | Run CI |
|---|---|---|---|---|---|---|
| SESI-20260928-LATENCY-HARNESS | Operator - XyDesk Team | Latency harness (6.8.6), preset otomatis + APK kirim preset (6.8.7), pemulihan gate CI, keamanan WebSocket/VB-CABLE, pemangkasan workflow, penataan dokumen | DISETUJUI | Xyckal (chat) | 2026-09-28 | Build hijau berturut-turut; Release v6.8.6, v6.8.7 |
| SESI-20261004-OPERATOR-HOST | Operator - XyDesk Team | Host: wallpaper preview 1280-edge rasio asli tanpa upscaling; panel menjelaskan preview = wallpaper, bukan frame sesi | DISETUJUI | Operator | 2026-10-04 | local: `python3 tool/check_version.py`, `git diff --check`; `cargo` tidak tersedia di sandbox |
| SESI-20261004-OPERATOR-ACTIONS | Operator - XyDesk Team | Kunci Actions: semua workflow manual-only + actor guard `xykal`; hapus pemicu push/PR/workflow_run/schedule | DISETUJUI | Operator | 2026-10-04 | local: PyYAML parse workflows, `python3 tool/check_version.py`, `git diff --check` |
| SESI-20261004-SENA-DOCS | Sena - XyVerse Team | Sinkronisasi dokumentasi stack aktif: Android native, host Rust + panel Win32, web Vite, Worker Cloudflare, `VERSION` sebagai sumber versi | DISETUJUI | Operator | 2026-10-04 | local: `python3 tool/check_version.py`, `git diff --check` |
| SESI-20261004-ARKA-ANDROID | Arka - XyVerse Team | APK native: preview hanya wallpaper host + retry aman; overlay virtual QWERTY/F1–F12/numpad/mouse/gamepad ring bulat, resize/drag, tekan-tahan | DISETUJUI | Operator | 2026-10-04 | local: `python3 tool/check_version.py`, `git diff --check`; remote Android Native CI success sebelum rebase |
| SESI-20261004-OPERATOR-WEB | Operator - XyDesk Team | Web: auto quality mulai 720p30, naik bertahap, manual fallback 720p, preview wallpaper `contain` | DISETUJUI | Operator | 2026-10-04 | local: `python3 tool/check_version.py`, `git diff --check`, `npm run build`, `node --test test/auto_preset.test.js`, full `npm test` 175/175 pass |
| SESI-20261004-OPERATOR-HOST-STATUS | Operator - XyDesk Team | Host panel: capture stopped-state netral; takeover sesi restart engine dari panel aktif | DISETUJUI | Operator | 2026-10-04 | local: `python3 tool/check_version.py`, `git diff --check`; compiler Windows native tidak tersedia di sandbox |
| SESI-20261004-OPERATOR-PREVIEW-WORDING | Operator - XyDesk Team | Preview wording: ganti sisa label internal `HD preview` menjadi wallpaper preview | DISETUJUI | Operator | 2026-10-04 | local: `python3 tool/check_version.py`, `git diff --check`, `npm test` |
| SESI-20261004-OPERATOR-ANDROID-OVERLAY-POLISH | Operator - XyDesk Team | APK overlay editor: drag kontrol mengakumulasi gerak dari pusat awal untuk tombol/stick/joystick/scroll | DISETUJUI | Operator | 2026-10-04 | local: `python3 tool/check_version.py`, `git diff --check`; Gradle tidak tersedia di sandbox |
| SESI-20261004-OPERATOR-ANDROID-OVERLAY-SELECT | Operator - XyDesk Team | APK overlay editor: selected highlight konsisten untuk stick/joystick/trigger/scroll | DISETUJUI | Operator | 2026-10-04 | local: `python3 tool/check_version.py`, `git diff --check`; Gradle tidak tersedia di sandbox |
| SESI-20261004-OPERATOR-ANDROID-HOLD-SAFETY | Operator - XyDesk Team | APK overlay: reset held input saat masuk mode edit supaya key/mouse/gamepad tidak nyangkut | DISETUJUI | Operator | 2026-10-04 | local: `python3 tool/check_version.py`, `git diff --check`; Gradle tidak tersedia di sandbox |

Riwayat lengkap (log pengiriman, antrean lama, riwayat sesi sampai 28 Sep 2026):
`AGENT_BOARD-2026-09.md`.
