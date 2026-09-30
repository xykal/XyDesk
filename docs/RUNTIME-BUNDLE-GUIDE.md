# Panduan Anatomi Runtime, Library Pendukung, Driver & Native Stack XyDesk

Dokumen ini disusun sebagai referensi teknis bersama untuk menjawab dua pertanyaan arsitektur utama:
1. **Kenapa installer Windows (`XyDesk-x64.exe` ~5,4 MB / ~20 MB saat terpasang) tidak menampilkan puluhan file `.dll` di folder instalasi, dan bagaimana driver pendukungnya bekerja?**
2. **Kenapa APK Android Native (`XyDesk-arm64-v8a.apk` ~13 MB / `XyDesk-armeabi-v7a.apk` ~8,2 MB) hanya memiliki 2 library `.so` utama (`libjingle_peerconnection_so.so` dan `libstreamxy.so`), apa saja isi di dalamnya, dan apa yang dilengkapi pada PR ini?**

---

## 1. Bedah Anatomi Windows Host (`XyDesk.exe` + `xydesk-host.exe`)

### A. Kenapa Tidak Ada Tumpukan File `.dll` di Folder `%LOCALAPPDATA%\Programs\XyDesk`?
Berbeda dengan aplikasi berbasis Electron/Flutter/CEF yang membawa puluhan file `.dll` eksternal (`flutter_windows.dll`, `libcef.dll`, `vcruntime140.dll`, `avcodec.dll` > 120 MB), XyDesk Windows menggunakan **Static Linking (`/MT` dan `+crt-static`)**:

| Komponen | Lokasi Sumber | Cara Bundling di `xydesk-host.exe` / `XyDesk.exe` |
|---|---|---|
| **MSVC C/C++ Runtime (`vcruntime140` / `msvcp140`)** | `host/.cargo/config.toml` (`+crt-static`) & `cl.exe /MT` | **Ditautkan statis (Static Link)** langsung ke dalam `XyDesk.exe` dan `xydesk-host.exe`. Jalan di Windows 10/11 bersih tanpa instalasi VC++ Redistributable. |
| **Codec Audio Opus 1.5.2 (`libopus`)** | `host/vendor/opus` + `host/build.rs` (`cc` crate) | **Dikompilasi dari source C & ditautkan statis (`static=opus`)** ke dalam `xydesk-host.exe`. Tidak butuh `opus.dll`. |
| **Codec Video Software H.264 (`OpenH264`)** | Crate `openh264` (`OpenH264API::from_source()`) + NASM | **Dikompilasi dari source C + Assembly NASM & ditautkan statis** ke dalam `xydesk-host.exe`. Tidak butuh unduh `openh264.dll` saat runtime. |
| **Stack WebRTC (`DTLS`, `SRTP`, `SCTP`, `ICE/TURN`)** | Crate `webrtc = "0.11"` + `rustls` | **Ditautkan statis** seluruhnya di dalam `xydesk-host.exe`. |
| **Hardware GPU Encoder (`NVENC` & `Media Foundation MFT`)** | `host/src/nvenc.rs`, `host/src/mft.rs` | Memanggil DLL driver GPU & OS bawaan `C:\Windows\System32` (`nvEncodeAPI64.dll` untuk NVIDIA, `mfplat.dll` / `mfreadwrite.dll` untuk AMD/Intel/NVIDIA, `d3d11.dll` & `dxgi.dll` untuk DXGI Desktop Duplication). |
| **Injeksi Input (`Mouse`, `Keyboard`, `Unicode`)** | `host/src/input_dispatch.rs` | Memanggil API kernel user-mode resmi `user32.dll` (`SendInput`) dengan latensi `< 1 ms`. |

Selain itu, installer `XyDesk-x64.exe` menggunakan kompresi **`SetCompressor /SOLID lzma` (kamus 32 MB)** di NSIS, sehingga biner `xydesk-host.exe` + `XyDesk.exe` + paket driver (~20 MB terpasang) terkompresi menjadi **~5,45 MB** saat diunduh.

### B. Driver Pendukung yang Dibundel di Dalam Installer (`drivers/`)
Pada workflow `release.yml` (`windows-installer`), dua driver kernel/UMDF sudah diunduh, diverifikasi SHA-256, dan dimasukkan ke dalam installer (`%LOCALAPPDATA%\Programs\XyDesk\drivers\`):
1. **Virtual Display Driver (`drivers\IddSampleDriver\`)**:
   - Berisi `iddsampledriver.dll`, `iddsampledriver.inf`, `iddsampledriver.cat`, `iddsampledriver.cer`, `option.txt`, `install.bat`, dan `uninstall.bat`.
   - Berguna saat PC host tanpa monitor fisik (headless/server/RDP) agar Windows tetap memiliki monitor virtual `1920x1080@60Hz`.
2. **Virtual Audio & Microphone Driver (`drivers\audio\`)**:
   - Berisi `VBCABLE_Driver_Pack45` (`VBCABLE_Setup_x64.exe`, `vbaudio_cable64_win7.sys`, `.inf`, `.cat`, `install-audio.bat`, `uninstall-audio.bat`).
   - Memungkinkan audio host ditangkap jernih dan mikrofon dari HP/Web diteruskan ke PC sebagai mikrofon virtual.

### C. Celah yang Diperbaiki pada Sisi Windows di PR Ini
- Sebelumnya, `XyDesk.nsi` menyalin folder `drivers\` ke disk tetapi belum membuat pintasan instalasi driver di Start Menu, dan `host/src/virtual_display.rs::try_install_driver()` hanya mengembalikan pesan teks manual `Setup-VirtualDisplay.ps1`.
- Di PR ini:
  1. `packaging/nsis/XyDesk.nsi` otomatis mendaftarkan pintasan **Install Virtual Display Driver** dan **Install Virtual Audio Driver** di Start Menu bila bundel driver tersedia di `$INSTDIR\drivers\`.
  2. `host/src/virtual_display.rs::try_install_driver()` kini memeriksa keberadaan `drivers\IddSampleDriver\install.bat` di sebelah `xydesk-host.exe` dan menjalankannya langsung (`cmd.exe /C install.bat /silent`) apabila proses berjalan sebagai Administrator.
  3. `packaging/windows/DEPENDENCIES-WINDOWS.txt` diperbarui agar merinci seluruh pustaka statis, DLL sistem/GPU dinamis, dan struktur bundel driver.

---

## 2. Bedah Anatomi APK Android Native (`android-native/`)

### A. Kenapa di `lib/<abi>/` Hanya Ada `libjingle_peerconnection_so.so` dan `libstreamxy.so`?
Pada APK native (`XyDesk-arm64-v8a.apk` dan `XyDesk-armeabi-v7a.apk`), direktori `lib/<abi>/` memuat:
1. **`libjingle_peerconnection_so.so` (~11,3 MB di `arm64-v8a`)**:
   - Ini adalah **monolithic native library** dari `io.github.webrtc-sdk:android:125.6422.07`.
   - Di dalam satu file `.so` ini sudah tertaut secara statis seluruh pustaka C/C++ inti WebRTC:
     - **`libopus`**: decode & encode audio Opus low-latency.
     - **`BoringSSL` + `libsrtp`**: enkripsi transport DTLS-SRTP.
     - **`usrsctp`**: transport DataChannel biner (`input`) unordered/unreliable 0-RTT.
     - **`libvpx` + `libyuv`**: codec VP8/VP9 & konversi warna SIMD ARM NEON.
     - **`JavaAudioDeviceModule` (OpenSLES / AAudio)** & **`MediaCodec` Hardware Bridge**.
2. **`libstreamxy.so` (~326 KB, `c++_static`)**:
   - Engine C++20 JNI milik XyDesk (`android-native/app/src/main/cpp/streamxy/`) yang menangani serialisasi protokol input biner little-endian (`streamxy/input.cpp`) dan ring buffer persentil latensi (`streamxy/stats.cpp`).
   - Dikompilasi dengan `-DANDROID_STL=c++_static`, sehingga runtime C++ (`libc++_shared.so`) sudah menyatu di dalam `libstreamxy.so`.
3. **`libandroidx.graphics.path.so` (~10 KB)**:
   - Pustaka native vektor path milik Jetpack Compose UI.

### B. Yang Dilengkapi & Diperkuat pada `android-native/` di PR Ini
Agar `android-native` memiliki kapabilitas penuh setara `host/src/input.rs` dan `host/src/session.rs`:

1. **Paritas Penuh Protokol Biner di `libstreamxy.so` (`streamxy.h`, `input.cpp`, `jni.cpp`, `StreamXy.kt`)**:
   - Menambahkan tag protokol yang sebelumnya belum ada di `libstreamxy.so`:
     - `0x08 CLIPBOARD_SET` (`sx_input_clipboard_set` & `sx_clipboard_decode`)
     - `0x09 CLIPBOARD_REQ` (`sx_input_clipboard_req`)
     - `0x0B VIDEO_BITRATE` (`sx_input_bitrate`, `u16 le` Mbps)
     - `0x0F VIDEO_FPS` (`sx_input_fps`, `u8` 30/60 FPS)
   - Mengganti batas statis `uint8_t b[2048]` pada JNI `text` & `clipboardSet` dengan buffer dinamis terukur (hingga `32 KB`) agar paste teks panjang tidak terpotong.
2. **Inisialisasi `JavaAudioDeviceModule` & Pemutaran Audio Host di `RtcSession.kt` + `SessionActivity.kt`**:
   - Mengonfigurasi `JavaAudioDeviceModule` dengan `AudioAttributes.USAGE_MEDIA` + hardware AEC/NS saat membangun `PeerConnectionFactory`.
   - Mengaktifkan `org.webrtc.AudioTrack` saat track audio remote diterima (`onAddTrack`) dan menambahkan kendali `setAudioMuted(Boolean)`.
   - Menyetel `volumeControlStream = AudioManager.STREAM_MUSIC` di `SessionActivity` agar tombol volume fisik HP mengatur suara stream PC.
3. **Sinkronisasi Clipboard Dua Arah Otomatis (HP ↔ PC)**:
   - Mendaftarkan `DataChannel.Observer` pada kanal `"input"` di `RtcSession.kt`: saat kanal terbuka, otomatis mengirim `0x09 CLIPBOARD_REQ` untuk menarik clipboard PC; saat menerima paket `0x08 CLIPBOARD_SET` dari PC, langsung menyalin ke `ClipboardManager` Android dengan guard anti-echo.
   - Memantau `ClipboardManager.OnPrimaryClipChangedListener` di `SessionActivity.kt` sehingga setiap teks yang disalin di HP otomatis terkirim ke PC lewat `0x08 CLIPBOARD_SET`.
4. **Hardening `LowLatencyH264Decoder` (`LowLatencyDecoder.kt`)**:
   - Menambahkan sinkronisasi `wait(8)` ms saat antrean input buffer `MediaCodec` penuh sesaat agar decoder tidak langsung mengembalikan `VideoCodecStatus.NO_OUTPUT` pada burst paket RTP besar.
   - Membersihkan `freeInputs` dan `inflight` secara thread-safe saat `release()`.
5. **Auto-Retry Pra-Welcome pada `Signaling` (`RtcSession.kt`)**:
   - Menutup celah `HANDOFF.md` (soket zombie `id sudah online` / error sebelum `welcome`): bila signaling menerima `error` sebelum fase `pair-response`, `RtcSession` otomatis merotasi `deviceId` baru dan mencoba sambung ulang sekali setelah `600 ms`.
6. **Perluasan `SessionToolbar.kt`**:
   - Menambahkan toggle **Mode Sentuh (`Trackpad` relatif vs `Sentuh` langsung `moveAbs`)**, toggle **Suara/Bisu (`Audio` / `Bisu`)**, dan baris tombol cepat PC (**`Esc`**, **`Tab`**, **`Win`**, **`Ctrl+C`**, **`Ctrl+V`**, **`CAD`**).
