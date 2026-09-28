# Mengukur Latency XyDesk (glass-to-glass)

Target roadmap: **< 40 ms** di LAN, **< 80 ms** via internet (TURN).
Budget yang sudah dapat diukur otomatis vs yang belum:

| Komponen | Alat | Status |
|---|---|---|
| Encode (CPU openh264) | `xydesk-host --bench` | ✅ otomatis |
| Capture DXGI → frame | log `[xydesk-host] capture...` | ✅ otomatis (Windows) |
| RTP → client (jitter, RTT, fps) | panel Statistik (web & APK) | ✅ otomatis |
| Jitter buffer + decode per frame | panel Statistik (web & APK) | ✅ otomatis |
| Terima → tampil per frame (p50/p95) | panel Statistik web → "Latensi" | ✅ otomatis (Chrome/Edge) |
| Estimasi glass-to-glass **tanpa** capture/encode host | panel Statistik web → "Latensi" (lower-bound) | ✅ otomatis |
| **Glass-to-glass total sejati** | **metode foto (bawah)** atau abs-capture-time (2b) | ⚠️ manual |

---

## 1. Ukur budget encode (paling gampang, jalankan ini dulu)

```bash
# Di PC Windows host — konfigurasi produksi, resolusi nyata 1080p:
xydesk-host.exe --bench 300 --bench-w 1920 --bench-h 1080

# Contoh output:
#   avg 8.42 ms | p50 7.90 ms | p95 11.20 ms | max 15.04 ms
```

- `avg < 10 ms` → target encode terpenuhi, lanjut ke langkah 2.
- `avg >= 10 ms` → openh264 di CPU tidak kuat di resolusi itu → **NVENC/AMF/QuickSync** wajib sebelum klaim "cocok gaming".

Catatan: pola uji (garis berjalan) sedikit lebih murah di-encode dari UI nyata;
angka di atas adalah batas **bawah**. Yang penting order-of-magnitude.

## 2. Verifikasi jitter & RTT jalur jaringan

Yang paling jujur tanpa ngubah kode: buka client lewat **web** di Chrome,
lalu buka `chrome://webrtc-internals` → cari koneksi → baca:
- `jitter` (inbound-rtp): target **< 5 ms**
- `currentRoundTripTime` (candidate-pair): target **< 10 ms** di LAN

## 2a. Rincian latensi client — otomatis (sejak 6.8.6)

Buka sesi lewat **web** (Chrome/Edge), panel → tab **Statistik** → bagian
**Latensi**:

| Baris | Arti | Target |
|---|---|---|
| Terima → tampil p50 / p95 | `expectedDisplayTime - receiveTime` per frame (jitter buffer + decode + antre compositor) | p95 < 20 ms |
| Jarak frame p95 | interval antar frame yang benar-benar tampil; > 2× frame time = stutter | < 33 ms @60fps |
| Perkiraan (tanpa encode PC) | RTT/2 + terima→tampil p50. **Batas bawah**, bukan total | — |

Tombol **Unduh laporan latensi** menyimpan JSON (`xydesk-latency-report/1`)
berisi semua angka di atas + codec/resolusi/jalur ICE. Lampirkan file itu
saat melapor "lag" — laporan tanpa angka tidak bisa ditindaklanjuti.

Di APK, panel sesi menampilkan **Buffer video** dan **Decode / frame** dari
`getStats` yang sama (Flutter belum punya padanan `requestVideoFrameCallback`).

**Total sebenarnya** = angka "Perkiraan" + `encode_ms` dari log/`--bench`
host + waktu capture DXGI (biasanya 1 frame). Jumlahkan manual sampai 2b ada.

## 2b. Langkah berikut untuk angka sejati tanpa foto: abs-capture-time

Bila host menyertakan RTP header extension
`http://www.webrtc.org/experiments/rtp-hdrext/abs-capture-time` pada paket
video, Chrome mengisi `captureTime` di `requestVideoFrameCallback` dan probe
web **otomatis** beralih ke mode `measured` (capture→display langsung,
tanpa perlu RTT/2). Ini pekerjaan di `host/` (webrtc-rs: daftarkan
extension di `MediaEngine` + isi `abs-capture-time` per paket dari
timestamp capture DXGI, sinkron NTP). Belum dikerjakan — butuh verifikasi
di Windows nyata, bukan runner.

## 3. Glass-to-glass total — metode foto (paling jujur)

1. Di host: buka jam digital besar / stopwatch di layar.
2. Di client (HP/tablet): buka stopwatch juga, atau siapkan kamera HP **kedua**.
3. Gerakkan pointer host cepat (atau jalankan timer pergerakan), foto layar host
   DAN layar client dalam satu frame kamera.
4. Baca selisih angka jam antar dua layar → itu glass-to-glass aktual (termasuk
   refresh rate layar, ±2 frame).

> Minimal 10 sampel, ambil median. Jangan lapor p50 dari 2 sampel.

## 4. Sesi lab (opsional, terotomasi already)

Workflow `.github/workflows/test-lab.yml` menyiapkan runner Windows + RDP via
Tailscale; cocok untuk memverifikasi "video muncul + input jalan" tanpa GPU
(fungsional, **bukan** benchmark latency — runner adalah VM tanpa GPU).

## Checklist go/no-go Fase 0

- [ ] `--bench 300 --bench-w 1920 --bench-h 1080`: avg encode < 10 ms
- [ ] Foto 10 pasang layar: median glass-to-glass < 40 ms di LAN
- [ ] Tes internet via TURN: < 80 ms
- [ ] Stabilitas 30 menit tanpa re-buffer
- [ ] (Opsional) Wi-Fi rumah, bukan kabel lab
