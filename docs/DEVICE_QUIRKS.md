# Aturan Perangkat & Matriks Kemampuan XyDesk

> Pola ini dipelajari dari `streamer-config.json` StarDesk (UU Remote): keputusan
> teknis berbasis **data**, bukan kode — supaya bisa diperbarui tanpa merilis ulang.

## 1. `config/device-quirks.json` — aturan kecocokan perangkat

| Bagian | Fungsi | Pemakai |
|---|---|---|
| `prefer_software_encode` | GPU yang bermasalah dengan NVENC/MFT langsung diarahkan ke encoder software (openh264) | host (`host/src/quirks.rs` → `screen.rs::pick_hardware_encoder`) |
| `disable_hevc` | Nonaktifkan HEVC untuk kelas GPU lama | host (siap dipakai kebijakan codec) |
| `android_decoder_caps` | Batasi resolusi sesi untuk model HP berdecoder lemah | aplikasi Android (siap dipakai kebijakan resolusi) |

**Pola dicocokkan sebagai substring** tanpa memperhatikan huruf besar/kecil
(bukan regex) — cukup untuk kelas seperti `GTX 7`, `GT 9`, tanpa dependensi baru.

**Memperbarui tanpa rilis:** letakkan salinan di `%USERPROFILE%\.xydesk\device-quirks.json`
di mesin host — salinan pengguna menyalip yang ditanam build. (Untuk rilis massal,
jalur distribusi lewat CloudSync/update.json menyusul.)

## 2. `config/capabilities.json` — matriks kemampuan per versi host

Setiap fitur punya `min_host` (versi host minimum). Klien (Android; web menyusul)
menyembunyikan tombol/panel fitur bila host yang dikendalikan belum mendukungnya:

```kotlin
if (Capabilities.supports("super_screen", hostVersion)) { /* tampilkan panel */ }
```

Sumber kebenaran: `config/capabilities.json`. Salinan di
`android-native/app/src/main/assets/capabilities.json` dipakai aplikasi —
**perbarui dua-duanya bersama**.

## 3. Uji

- Rust: `cargo test -p xydesk-host quirks` (tiga kasus: parsing, pencocokan
  GPU, aturan nonaktif).
- Android: `Capabilities.compare` murni tanpa dependensi.
