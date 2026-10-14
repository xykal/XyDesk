# XyDeskVDD — XyDesk Virtual Display Adapter (driver source)

Driver display virtual IddCx UMDF2 milik XyDesk. Hardware ID `Root\XyDeskVDD`,
monitor terlihat sebagai "XyDesk VDD" (EDID kustom XYD 256-byte — identik dengan
`build_xydesk_edid()` di `host/src/virtual_display.rs`).

## Provenance

Fork dari [ge9/IddSampleDriver](https://github.com/ge9/IddSampleDriver)
(sample driver Microsoft IddCx), lisensi **MIT**. Perubahan XyDesk:

- EDID sample 128-byte diganti EDID kustom XYD 256-byte (CEA-861, HDR-ready).
- `loadOptions()` kini mengembalikan bool dan membaca dua path:
  `C:\XyDeskVDD\option.txt` (utama) → fallback `C:\IddSampleDriver\option.txt`
  (path warisan yang juga ditulis oleh host XyDesk).
- Nama project/solution/INF/DLL: `XyDeskVDD`.
- INF ditulis ulang (UTF-8): HWID `Root\XyDeskVDD`, `CatalogFile=XyDeskVDD.cat`,
  `UmdfService=XyDeskVDD`, `DeviceGroupId=XyDeskVDDGroup`.

Lisensi MIT asli berlaku untuk file hasil fork (Driver.cpp/h, Trace.h,
vcxproj, sln). Build: workflow `.github/workflows/vdd-build.yml` (dispatch
manual, hemat kuota CI) — output `XyDeskVDD-driver-win64` artifact.

## Struktur

| File | Fungsi |
|---|---|
| `Driver.cpp` / `Driver.h` | Implementasi IddCx (callback adapter & monitor, mode dari option.txt) |
| `Trace.h` | Konfigurasi WPP tracing |
| `XyDeskVDD.inf` | INF instalasi (Root\XyDeskVDD) |
| `XyDeskVDD.vcxproj` / `.sln` | Project MSBuild (WindowsUserModeDriver10.0) |
| `option.txt` | Matriks resolusi default (disalin installer ke C:\XyDeskVDD) |
