# Panel Windows: jendela custom (frameless, sudut sendiri)

Sesi `SESI-20260923-OPERATOR-WINPANEL` (Operator — mewakili pemilik repo,
perintah langsung di chat: "ui ux windows yang rounded window nya di custom
bukan bawaan Windows", plus "tauri/electron hapus cukup native c++").
Scope: panel native Windows `packaging/native-host/`, penghapusan shell
`desktop/`, dan gerbang CI-nya.

## Yang diminta, dan apa yang dikerjakan

Sebelumnya panel adalah jendela bergaris Windows biasa: `WS_OVERLAPPED |
WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX` dengan kontrol sistem di dalamnya
(`CreateWindowExW(0, …)`), jadi seluruh chrome-nya milik Windows — caption,
border, sudut, bayangan. Tidak ada satu baris pun yang menggambar bentuk
jendela.

Sekarang jendela digambar aplikasi sendiri:

| Aspek | Sebelum | Sekarang |
|---|---|---|
| Gaya jendela | `WS_OVERLAPPED \| WS_CAPTION \| WS_SYSMENU \| WS_MINIMIZEBOX` | `WS_POPUP \| WS_SYSMENU` + `WS_EX_LAYERED`, tanpa caption/border |
| Sudut | milik Windows (Windows 11: radius bawaan, tak bisa diatur; Windows 10: kotak) | busur radius 16 digambar sendiri, sama di semua versi Windows |
| Tepi | border Windows | busur dihaluskan dengan cakupan piksel (bukan `SetWindowRgn`) |
| Bayangan | bayangan DWM | bayangan sendiri (sebaran 26 px, kekuatan 48%) |
| Judul & tombol tutup | caption Windows | digambar sendiri: logo X, judul, subjudul, tombol tutup di kanan atas |
| Kontrol | kontrol Win32 standar (`BUTTON`, `EDIT`, `STATIC`) | digambar penuh: kartu status, dua kartu identitas, lima tombol, titik status |
| Geser jendela | area caption | `WM_NCHITTEST` → `HTCAPTION` pada area judul; di luar bentuk → `HTTRANSPARENT` |
| Skala DPI | `SetProcessDPIAware` saja | `WM_DPICHANGED` menghitung ulang tata letak + font, lalu jendela dipusatkan |

Yang **tidak** berubah: pembagian tugas (panel = launcher + supervisi,
engine tetap `xydesk-host.exe` dengan Job Object), tray + menu kanan,
mode tutup = sembunyi ke tray, log host di `%LOCALAPPDATA%\XyDesk\host.log`,
dan identitas/pairing yang dibaca lewat `--identity-json`.

Tambahannya: navigasi keyboard (Tab pindah kontrol, Enter menjalankan,
Esc menyembunyikan), umpan balik hover/tekan, dan notifikasi tindakan di kartu
status ("Device ID disalin ke clipboard") yang hilang sendiri setelah 2,6 dtk.

## Menghapus shell Tauri/Electron

`desktop/` (Tauri v2 + Next.js, plus sisa jalur Electron: 36 berkas, 1009 KB)
dihapus atas keputusan pemilik. Yang dibersihkan mengikutinya:

- `.github/workflows/build.yml` — entri filter `desktop/**` dan
  `desktop/package.json` dihapus; panel native masuk area host sehingga
  perubahan UI Windows memicu job host + Windows;
- `tool/check_version.py` — blok manifest `desktop/src-tauri/*` dihapus
  (sebelumnya sudah dilewati karena `exists()`, sekarang tidak ada sisa kode
  mati);
- `tool/check_icons.py`, `tool/gen_logo.py` — target aset desktop dihapus,
  diganti ikon panel yang benar-benar dipakai;
- `README.md` — badge "Desktop Shell/Tauri" → "Panel Windows/Win32 C++",
  langkah build desktop diganti perintah uji tata letak panel;
- `AGENT.md` — scope role Desktop Shell kini `packaging/native-host/`;
- `host/src/control.rs`, `host/src/main.rs` — komentar yang menyebut "shell
  Electron + Next.js" diganti (komentar saja, tidak ada perubahan perilaku);
- `docs/DESKTOP_SHELL.md` — catatan penghapusan + bagian baru tentang jendela
  custom dan cara memeriksanya.

Dokumen historis (changelog versi lama, `docs/AUDIT_*`) sengaja dibiarkan
sebagai catatan sejarah — menghapus jejaknya justru menghilangkan audit trail.

## Bukti yang dijalankan (lingkungan sesi, hari ini)

```
packaging/tests/test-native-panel-layout.sh        → Lulus: 79 pemeriksaan
mingw-w64 g++ -std=c++20 -Wall -Wextra (cross)     → kompilasi bersih
Wine 10.0 + Xvfb + picom (8 tangkapan layar)       → panel tampil, hover/klik/
                                                      flash/restart/close-to-tray
                                                      berperilaku benar
XyDesk Control Panel.exe --panel-probe             → 608x616, panel 560x568,
                                                      radius 16, margin 24,
                                                      hits Close,Start,OpenLog,None,TitleBar
XyDesk Control Panel.exe --panel-snapshot panel.bmp → BMP 32-bit 1.498.166 B
python tool/check_panel_shape.py panel.bmp         → Lulus (radius terukur
                                                      14,2–14,8 px di 4 sudut,
                                                      piksel cakupan sebagian
                                                      di tepi busur, bayangan
                                                      memudar habis)
python tool/check_version.py                       → Lulus 6.8.5+59
```

Satu cacat nyata ikut ketahuan dan diperbaiki saat pengujian ini: komposisi
piksel tepi busur mula-mula menjatuhkan bayangan (`alpha = cakupan` saja),
sehingga tepi sudut terlihat menggelap. Sekarang `alpha = cakupan +
bayangan*(1-cakupan)` dengan warna dipremultiply oleh cakupan — terlihat di
tangkapan layar sebelum/sesudah dan dijaga uji piksel.

## CI: tiga kegagalan nyata, lalu hijau

Push pertama membuka gerbang baru, dan `Build` **35898918240** (11 job, 3 merah)
membongkar tiga masalah yang tidak terlihat di sandbox — semuanya nyata:

| Job | Akar masalah | Perbaikan |
|---|---|---|
| `Windows x64` + `Lint MSI dan NSIS Installer` | MSVC menolak `std::min/std::max` karena `windows.h` mendefinisikan makro `min`/`max` (`C2589 illegal token on right side of '::'`); mingw tidak mengeluh | `layout.h` memakai pembantu `minValue`/`maxValue`/`clampValue` sendiri (tidak bergantung urutan include); `main.cpp` menetapkan `NOMINMAX` sebelum `windows.h` |
| `Analisis Statis (Flutter)` | penghapusan `desktop/` membuat inventaris lisensi usang (npm 59 → 4) | regenerasi dengan Flutter 3.44.9 (versi CI): Dart 115 · Rust 332 · npm 4 · manual 12 = **463 komponen**, `--check` hijau |
| `Lint MSI dan NSIS Installer` (step baru) | panel adalah aplikasi subsystem WINDOWS; PowerShell **tidak menunggu** proses seperti itu dengan `&`, jadi `panel-probe.json` dibaca sebelum ditulis | kedua jalur memakai `Start-Process -Wait -PassThru` + `ExitCode` |

Catatan jujur untuk insiden kedua: percobaan pertama saya menjalankan generator
lisensi tanpa `flutter` di sandbox, dan hasilnya **473** komponen — generator
menyertakan paket dev kalau `flutter` tidak ada. Angka yang benar (463) baru
keluar setelah Flutter dipasang. Sandbox tanpa SDK menghasilkan data yang
terlihat masuk akal tapi salah; itu jenis kesalahan yang paling berbahaya untuk
dokumen legal.

### Hasil run penutup

`Build` **35900760308** @ `2358a70`: **11/11 job SUCCESS**, termasuk
`Windows x64` (panel dikompilasi MSVC), `Lint MSI dan NSIS Installer` (compile
panel + probe + snapshot + pemeriksa bentuk), `Uji Logika Host (Rust)`
(termasuk uji tata letak 79 pemeriksaan di Linux), dan `Analisis Statis
(Flutter)` (inventaris lisensi mutakhir).

Angka dari runner Windows, dibandingkan dengan hasil Wine di sesi yang sama:

```
panel-probe (runner)  {"scalePct":100,"windowWidth":608,"windowHeight":616,
                       "panelWidth":560,"panelHeight":568,"radiusPanel":16,
                       "shadowMargin":24,
                       "hits":["Close","Start","OpenLog","None","TitleBar"]}
check_panel_shape     pojok tembus pandang alpha 106 di keempat sudut
                      tepi busur punya piksel cakupan sebagian (alpha 153)
                      radius sudut terukur [14,8 | 14,2 | 14,2 | 14,8]
                      Lulus: bentuk panel sesuai
```

Nilai-nilai itu **identik** dengan tangkapan Wine di mesin sesi — bukti bahwa
yang digambar aplikasi memang sama di kedua lingkungan, bukan kebetulan salah
satu.

## Paket uji untuk pengguna

Panel yang baru dipaketkan untuk uji lapangan:

- `Build` **35903102319** @ `dca258e`: **11/11 SUCCESS**.
- `prepare-windows-installer.yml` run **35903930877** @ `dca258e`: **SUCCESS**.

| Berkas | Ukuran | SHA-256 |
|---|---|---|
| `XyDesk-x64.exe` (NSIS) | 5.070.579 B | `a05165c977963abfc5ff7e4e2cded3c91cfc77aaef30461cd490de4c3eec9f13` |
| `XyDesk-x64.msi` (WiX) | 6.381.568 B | `a5a4bd6cd2b1ed2bf96bef00b41fbbcb8a554d2fb881eaa946d9409950c619d1` |

Paket pertama (`35902747669`) **dibuang** karena mojibake; yang ini memuat panel
yang teksnya sudah diperiksa langsung di dalam EXE. Rencana uji lapangan yang
diminta dari pengguna ada di `uji-windows/CARA-UJI.md` (sudut custom, geser
jendela, tray, hover/salin, tombol host, keyboard Tab/Enter/Esc, skala 125%/150%).
Tidak ada GitHub Release; rilis tetap keputusan pemilik.

## Batas jujur

- **Mojibake MSVC `/utf-8` adalah bukti kedua bahwa sandbox tidak cukup.**
  Kompilasi mingw di Linux membaca UTF-8 apa adanya, jadi seluruh uji gambar
  saya lolos sementara build Windows mengirim "Panel host Windows Â· tanpa
  terminal" dan tooltip "XyDesk Host â€” …". Ketahuan setelah paket sudah
  terbentuk, dari pemeriksaan `strings` atas EXE yang dibangun CI. Perbaikannya
  `/utf-8` di ketiga pemanggilan `cl.exe` + `tool/check_panel_text.py` yang
  menjaga EXE (bukan sumbernya) — dan gerbangnya sudah diuji dua arah.
- **Wine bukan Windows.** Uji visual (bentuk, warna, klik, tray, close-to-tray)
  dilakukan di Wine 10.0 dengan compositor X. Yang sudah terbukti di Windows
  asli lewat CI: kompilasi MSVC, tata letak dari EXE yang dikompilasi, dan
  **bentuk jendela hasil gambar aplikasi sendiri** (snapshot BMP diperiksa per
  piksel, angkanya sama dengan Wine). Yang **belum** terbukti di Windows asli:
  interaksi nyata — tray `Shell_NotifyIcon`, menu klik kanan, Alt+Tab, geser
  jendela dari area judul, dan `WM_DPICHANGED` di monitor berskala 125%/150%.
- **Belum ada uji klik manusia.** Interaksi di CI diperiksa lewat
  `--panel-probe` (hit-test) dan `--panel-snapshot` (bentuk), bukan otomatisasi
  klik; tangkapan layar Wine adalah bukti visual, bukan uji regresi.
- **CI sudah membuktikan gerbang barunya** (`Build` 35900760308, 11/11 hijau;
  push tidak memicu Actions, jadi ini lewat dispatch manual sesuai kebijakan).
  Yang **belum** dibuktikan CI adalah perilaku runtime di Windows: tray,
  Alt+Tab, geser jendela, dan `WM_DPICHANGED` pada monitor berskala 125%/150%
  — semuanya butuh sesi Windows nyata dengan mouse manusia.
- **MSVC vs mingw.** CI memakai `cl.exe`; kompilasi lokal memakai mingw
  (tidak ada MSVC di Linux). Perbedaan warning antar-kompiler mungkin ada;
  yang dijaga sama adalah kode sumber dan hasil gambar.
- **Panel tanpa caption berarti tidak ada menu sistem versi caption** —
  minimize/maximize ditangani sendiri (minimize = sembunyi ke tray, maximize
  diabaikan), dan `Esc`/tombol tutup menyembunyikan panel. Perubahan perilaku
  ini disengaja, tetapi baru terasa wajar setelah dipakai pengguna asli.
