# Kontrol di layar, perangkat fisik, dan deteksi otomatis

Dokumen ini menjelaskan apa yang dikirim XyDesk ke host saat kamu menekan
sesuatu, dan kapan tombol di layar muncul atau menyingkir. Referensi
perilakunya: HUD di `xykal/XyDesk-Remote` (`docs/HUD-BUTTONS.id.md`) — satu
tombol satu aksi, bisa digeser, bisa diubah ukurannya, dan posisinya
disimpan.

## 1. Tiga jalur masuk, satu protokol

Semua input berakhir di data channel `input` sebagai pesan biner 8 byte
(lihat `host/src/input.rs`). Yang berbeda hanya dari mana asalnya:

| Asal | Diproses oleh | Dikirim sebagai |
|---|---|---|
| Sentuhan + tombol di layar | `ui/ControlOverlay.kt` | `0x05 KEY`, `0x03 MOUSE_BUTTON`, `0x04 SCROLL`, `0x0E GAMEPAD` |
| Keyboard/mouse fisik (USB OTG, Bluetooth, DeX) | `ui/SessionHid.kt` + `core/KeyMap.kt` | sama persis |
| Gamepad fisik (Xbox, DualSense, 8BitDo, …) | `ui/SessionHid.kt` + `libxygamepad` | `0x0E GAMEPAD` (laporan XInput 12 byte) |

Host tidak tahu dan tidak peduli yang mana: tombol A dari gamepad fisik dan
tombol A dari layar menghasilkan laporan XInput yang sama.

## 2. Deteksi otomatis

`ui/HidMonitor.kt` mendengarkan `InputManager` dan memperbarui daftar
perangkat setiap kali ada yang dicolok atau dicabut — tanpa perlu
menyambung ulang sesi. Yang dihitung:

- **Keyboard** — sumber `SOURCE_KEYBOARD` **dan** `KEYBOARD_TYPE_ALPHABETIC`.
  Tombol volume HP juga `SOURCE_KEYBOARD`; tanpa syarat alfabetik setiap HP
  akan mengaku punya keyboard.
- **Mouse** — `SOURCE_MOUSE` atau `SOURCE_MOUSE_RELATIVE`.
- **Gamepad** — `SOURCE_GAMEPAD` atau `SOURCE_JOYSTICK`.

Perangkat virtual (`InputDevice.isVirtual`) diabaikan — itu layar sentuh dan
IME, bukan perangkat keras.

## 3. Yang menyingkir hanyalah yang digantikan

Aturannya ada di `xyadapt/OverlayRules.kt` dan diuji di JVM
(`OverlayRulesTest`). Setiap tombol punya keluarga:

| Keluarga | Jenis kontrol | Disembunyikan bila |
|---|---|---|
| `KEYBOARD` | `KEY`, `CHORD`, `STICK_KEYS` | ada keyboard fisik |
| `MOUSE` | `MOUSE`, `SCROLL`, `SCROLL_X`, `SCROLL_WHEEL`, `STICK_MOUSE` | ada mouse fisik |
| `GAMEPAD` | `GAMEPAD_BUTTON`, `GAMEPAD_STICK_L/R`, `GAMEPAD_TRIGGER_L/R` | ada gamepad fisik |
| `SHARED` | sisanya (mis. ganti mode sentuh) | tidak pernah |

Sebelum ini keputusannya satu saklar untuk semuanya: **satu** perangkat apa
pun yang terdeteksi menyembunyikan **seluruh** lapisan. Menempelkan keyboard
Bluetooth ikut menghapus gamepad virtual dari layar, padahal tidak ada
gamepad fisik di mana pun — dan tidak ada cara mengembalikannya.

Jenis kontrol yang belum dikenal aturan ini masuk `SHARED`, artinya **ikut
tampil**. Kontrol baru lebih baik terlihat daripada hilang diam-diam karena
tabel di atas belum diperbarui.

## 4. Tiga mode, tombol "Tampil" di rail

| Mode | Perilaku |
|---|---|
| **Kontrol otomatis** (bawaan) | aturan keluarga di atas |
| **Kontrol selalu tampil** | semua tombol tetap ada walau perangkat fisik menempel — untuk yang memakai keyboard fisik tapi tetap ingin tombol makro di layar |
| **Kontrol disembunyikan** | layar bersih sepenuhnya |

Pilihan disimpan per perangkat (`overlayMode`). Saat mode atur tata letak
dinyalakan, **semua** tombol terlihat apa pun modenya: tombol yang
disembunyikan otomatis tetap harus bisa dipindah dan dihapus, kalau tidak ia
mustahil diurus justru saat sedang diurus. Menyalakan mode atur saat kontrol
dimatikan mengembalikan mode ke otomatis.

## 5. Tata letak

Sama seperti referensi: tiap tombol punya posisi (persen layar), ukuran
(34–180 px), dan aksi sendiri; geser untuk memindah, `−`/`+` untuk mengubah
ukuran, dan tata letak disimpan sebagai JSON (`overlayJson`, maksimal 96
tombol). Preset bawaan: Mouse, FPS, QWERTY, baris F1–F12, numpad, gamepad,
dan "lengkap".

## 6. Kejujuran gamepad

Gamepad virtual di PC butuh **ViGEmBus**. Kalau drivernya tidak ada, atau
kalau PC itu sudah punya gamepad fisik sendiri (yang sengaja tidak ditimpa —
dua pad di slot yang sama membuat game membaca dua perangkat yang saling
melawan), host tidak bisa menerima laporan gamepad sama sekali.

Dulu host hanya mencatat satu baris di konsolnya dan diam — orang yang
memegang HP menekan tombol A dan tidak ada apa pun yang terjadi, tanpa
penjelasan. Sekarang status itu ikut di blok `meta`:

```json
"gamepad": { "available": false, "reason": "ViGEmBus belum terpasang di PC" }
```

Aplikasi memperingatkan **sekali per sesi**, dan hanya kalau tata letakmu
memang punya tombol gamepad — memberi tahu orang yang tidak memakai gamepad
bahwa gamepad tidak tersedia hanyalah kebisingan.

## 7. Yang belum ada

- Getaran (rumble) balik dari host ke HP.
- Pemetaan tombol gamepad fisik ke aksi keyboard/mouse (ada di
  XyDesk-Remote sebagai `XyGamepadActionMapper`, belum di sini).
- Penangkapan pointer (pointer capture) untuk mouse fisik — gerak mouse
  dikirim sebagai posisi absolut, jadi mode FPS yang butuh gerak relatif tak
  terbatas belum sepenuhnya setara.
