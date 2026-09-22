# XyDesk Desktop — Native Win32 C++ + Rust Engine

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
```

Kompilasi C++ panel harus memakai static MSVC runtime (`/MT`). Build final tidak
mengandalkan Inno Setup; `packaging/windows/XyDesk.iss` dipertahankan sebagai
legacy reference saja.
