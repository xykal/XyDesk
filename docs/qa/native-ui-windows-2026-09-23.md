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

## Batas jujur

- **Wine bukan Windows.** Yang diuji adalah perilaku gambar (bentuk, warna,
  interaksi) di Wine 10.0 dengan compositor X. Apinya Windows asli —
  `UpdateLayeredWindow`, `WM_NCHITTEST`, `WM_DPICHANGED`, tray
  (`Shell_NotifyIcon`) — **belum dijalankan di mesin Windows sungguhan**.
  Tray, menu klik kanan, dan perilaku Alt+Tab baru terbukti saat uji lapangan.
- **Belum ada uji klik manusia.** Interaksi di CI diperiksa lewat
  `--panel-probe` (hit-test) dan `--panel-snapshot` (bentuk), bukan otomatisasi
  klik; tangkapan layar Wine adalah bukti visual, bukan uji regresi.
- **CI belum dijalankan untuk perubahan ini.** Push tidak memicu workflow
  (kebijakan sejak 3 Sep 2026), jadi job baru di `build.yml`
  ("Uji tata letak panel native", "Kompilasi panel native dan periksa bentuk
  jendelanya") menunggu dispatch `Build` manual. Perlu satu dispatch untuk
  membuktikan keduanya hijau di runner.
- **MSVC vs mingw.** CI memakai `cl.exe`; kompilasi lokal memakai mingw
  (tidak ada MSVC di Linux). Perbedaan warning antar-kompiler mungkin ada;
  yang dijaga sama adalah kode sumber dan hasil gambar.
- **Panel tanpa caption berarti tidak ada menu sistem versi caption** —
  minimize/maximize ditangani sendiri (minimize = sembunyi ke tray, maximize
  diabaikan), dan `Esc`/tombol tutup menyembunyikan panel. Perubahan perilaku
  ini disengaja, tetapi baru terasa wajar setelah dipakai pengguna asli.
