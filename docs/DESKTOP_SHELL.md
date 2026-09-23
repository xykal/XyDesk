# XyDesk Desktop — Native Win32 C++ + Rust Engine

> **Catatan 2026-09-23:** folder `desktop/` (shell Tauri + Electron lama) sudah
> dihapus atas keputusan pemilik repo. Yang dikirim ke pengguna adalah panel
> native C++ di `packaging/native-host/` — dokumen ini memang sudah
> menggambarkan panel itu, jadi isinya tetap berlaku. Sisa rujukan lama di
> changelog dan dokumen audit dibiarkan sebagai catatan sejarah.

## Posisi dalam arsitektur

XyDesk Desktop adalah satu **Control Panel Windows native Win32 C++** yang
menjalankan engine streaming Rust (`xydesk-host.exe`) sebagai proses internal.
Panel tidak membuka terminal atau PowerShell untuk pemakaian normal.

Pemisahan peran yang ketat:

| Lapisan | Teknologi | Tanggung jawab |
|---|---|---|
| Control Panel | Native Win32 C++ | Quiet Surface, tray, Start/Stop, Device ID, pairing code, diagnostics, dan lifecycle engine |
| Engine Streaming | Rust (`xydesk-host.exe`) | Signaling, pairing, capture DXGI/GDI, encode, WebRTC, WASAPI audio, dan input |
| Installer | WiX MSI + NSIS EXE | Dua format installer x64 dari payload terverifikasi yang sama |
| Optional support | VDD + VB-CABLE payload | Berkas engineering yang tetap opt-in; tidak diunduh diam-diam |

Satu produk tetap memiliki satu UI Windows. Rust berjalan sebagai proses internal
terpisah supaya kegagalan engine tidak menjatuhkan panel, sementara panel dapat
menampilkan status, exit code, dan log engine.

### Prinsip Quiet Surface:
1. Tidak membuka terminal atau PowerShell untuk pemakaian normal.
2. Start/Stop, Device ID, pairing code, koneksi, dan diagnostics terlihat dari
   satu panel.
3. Engine hanya dianggap siap setelah proses hidup dan endpoint control tersedia.
4. Kegagalan negosiasi WebRTC dicatat ke log tanpa mematikan panel.

---

## Driver dan dependency Windows

Payload MSI dan NSIS dapat membawa driver/support files yang telah diverifikasi,
tetapi pemasangan driver tetap opt-in dan mengikuti kebijakan Windows. Installer
tidak mengunduh DLL atau driver secara diam-diam. Berkas non-sistem, lisensi,
dan runtime yang dibundel harus dicatat di `DEPENDENCIES-WINDOWS.txt` serta
third-party notices.

- **Virtual Display Driver (`IddSampleDriver`)**: untuk skenario headless/RDP
yang disetujui pengguna.
- **Virtual Audio & Mic Driver (`VB-CABLE`)**: untuk rute audio/mic virtual
yang dipilih pengguna.
- Native C++ dibangun dengan static MSVC runtime (`/MT`) agar panel tidak
  bergantung pada instalasi Visual C++ Redistributable terpisah.

---

## Lifecycle dan diagnostics engine

Control Panel menjalankan `xydesk-host.exe` dengan `CreateProcessW` dan Windows Job
Object. Output stdout/stderr engine diarahkan ke:

```text
%LOCALAPPDATA%\XyDesk\host.log
```

Panel memeriksa exit code setiap detik. Jika engine berhenti, status menampilkan
kode exit dan lokasi log; tombol **Buka log host** membuka file tersebut.
Negosiasi WebRTC yang gagal harus menutup sesi terkait dan tetap membiarkan
engine menunggu koneksi berikutnya, bukan membuat proses mati.

Engine juga menyediakan Control API lokal untuk status dan tindakan internal.
Auth token Control API tidak boleh ditampilkan ke pengguna atau dikirim ke
jaringan publik.

---

## Identitas, pairing, dan privasi

Panel native tidak menyimpan password pairing di command line. Device ID dan
pairing code ditampilkan sebagai status lokal; sesi WebRTC tetap diautentikasi
oleh engine Rust. Log diagnostik disimpan lokal di `%LOCALAPPDATA%\XyDesk` dan
harus diperlakukan sebagai data sensitif.

---

## Jendela: digambar sendiri, bukan bentuk bawaan Windows

Panel ini tidak memakai caption, border, atau pembulatan bawaan Windows
(justru tidak memakainya: `WS_POPUP`, tanpa `WS_CAPTION`). Seluruh bentuk
jendela — judul, tombol tutup, sudut membulat, bayangan — digambar aplikasi
ke permukaan 32-bit lalu ditempelkan sebagai jendela berlapis
(`WS_EX_LAYERED` + `UpdateLayeredWindow`). Konsekuensinya:

- Tampil **sama di Windows 10 dan Windows 11** — bukan bergantung pada
  `DWMWA_WINDOW_CORNER_PREFERENCE` yang hanya ada di Windows 11 dan radiusnya
  tidak bisa diatur.
- Sudut memakai **busur radius 16** (skala radius desain XyDesk 8/12/16/20,
  lihat `docs/DESIGN.md`), tepinya dihaluskan dengan cakupan piksel — bukan
  dipotong keras seperti `SetWindowRgn`.
- Bayangan digambar aplikasi (sebaran 26 px, kekuatan 48%), jadi tepi jendela
  tetap terbaca di dinding desktop gelap.
- Angka tata letak hidup di satu tempat: `packaging/native-host/layout.h`.
  Sumber yang sama dipakai menggambar, hit-test klik, urutan Tab, dan uji.
- Jendela bisa digeser dari area judul (`WM_NCHITTEST` → `HTCAPTION`), dan
  klik di luar bentuk membiarkan desktop di bawahnya bekerja
  (`HTTRANSPARENT`).
- Dukungan DPI: `WM_DPICHANGED` menghitung ulang tata letak dan font, lalu
  jendela dipusatkan kembali tanpa mengubah ukuran panel secara liar.

Bentuknya bukan klaim di dokumen: EXE-nya punya dua jalur pemeriksaan yang
dipakai CI Windows (`build.yml`, job `Lint MSI dan NSIS Installer`):

```powershell
XyDesk` Control Panel.exe --panel-probe    panel-probe.json  # ukuran + hit-test
XyDesk` Control Panel.exe --panel-snapshot panel.bmp         # gambar apa adanya
python tool/check_panel_shape.py panel.bmp                  # baca per piksel
```

`--panel-snapshot` menulis panel apa adanya sebagai BMP 32-bit; pemeriksanya
membuktikan sudut benar-benar busur radius 16, tepinya punya piksel cakupan
sebagian (bukti penghalusan), dan bayangan memudar habis di dalam margin.
Tata letak angkanya juga diuji tanpa Windows di
`packaging/tests/test-native-panel-layout.sh` (79 pemeriksaan, jalan di job
`Uji Logika Host (Rust)`).

## Pengembangan & Build

Build engine Rust dan panel native dilakukan oleh workflow Windows. Prasyarat
lokal untuk build native:

- Visual Studio Build Tools dengan workload Desktop development with C++;
- Windows SDK dan target `x86_64-pc-windows-msvc`;
- Python 3 untuk generator manifest WiX;
- WiX Toolset v4 dan NSIS untuk menghasilkan installer.

Perintah validasi yang dapat dijalankan lintas platform:

```bash
python -m py_compile packaging/windows/generate_wix.py
python packaging/windows/generate_wix.py --help
cargo fmt --check --manifest-path host/Cargo.toml
cargo check --manifest-path host/Cargo.toml
./packaging/tests/test-native-panel-layout.sh   # tata letak panel, tanpa Windows
```

Kompilasi C++ panel harus memakai static MSVC runtime (`/MT`). Build final tidak
mengandalkan Inno Setup; `packaging/windows/XyDesk.iss` dipertahankan sebagai
legacy reference saja.
