# Native C++ Control Panel

The Windows user entrypoint is a small Win32 C++ application. It starts and stops
`xydesk-host.exe` directly with `CreateProcessW`, uses a Windows Job Object for
child lifetime, and never opens PowerShell or a console window.

The Rust engine remains responsible for signaling, capture, encoding, identity,
audio and input. The panel is deliberately quiet: Start, Stop, Device ID, pairing
code, Open Web, Restart, and host diagnostics. Engine stdout/stderr is written to
`%LOCALAPPDATA%\XyDesk\host.log`; an engine exit is shown with its exit code
instead of the generic status alone.

Production packaging is generated in two formats from the same verified bundle:
WiX MSI for managed Windows deployment and NSIS EXE for consumer installation.
The bundle carries the English EULA, third-party notices, dependency manifest,
and any explicitly verified support DLLs.

## Jendela digambar sendiri

Panel tidak memakai caption, border, atau pembulatan bawaan Windows. Jendelanya
`WS_POPUP` berlapis (`WS_EX_LAYERED`), isinya digambar ke permukaan 32-bit lalu
ditempelkan dengan `UpdateLayeredWindow`. Sudut panel/kartu radius 10 dan kontrol
radius 8 memberi bentuk lebih tegas; warna Paper, aksen ungu, dan permukaan lembut
mengikuti client web. Tepi dihaluskan dengan cakupan piksel — sama di Windows 10/11. Rincian desain ada di
`docs/DESKTOP_SHELL.md`.

Tata letaknya hidup di `layout.h` (murni angka, tanpa API Windows) supaya angka
yang digambar, hit-test klik, urutan Tab, dan uji tidak bisa melenceng satu sama
lain.

## Pemeriksaan

```bash
# Tata letak (Linux/macOS, tanpa Windows): 79 pemeriksaan
./packaging/tests/test-native-panel-layout.sh
```

```powershell
# Dari EXE yang sudah dikompilasi: ukuran jendela + hasil hit-test
XyDesk` Control Panel.exe --panel-probe panel-probe.json
# Gambar panel apa adanya, lalu periksa bentuknya per piksel
XyDesk` Control Panel.exe --panel-snapshot panel.bmp
python tool/check_panel_shape.py panel.bmp
```

Keduanya dijalankan CI Windows di `build.yml` (job `Lint MSI dan NSIS
Installer`), jadi perubahan window chrome tidak bisa lolos tanpa bukti bentuk.
