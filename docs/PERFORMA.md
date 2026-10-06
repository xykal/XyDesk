# Rencana Performa XyDesk

> Dibuat 2026-10-06. Tujuannya satu: **menang di milidetik, bukan di megabyte.**

## Premis

Aplikasi remote desktop tercepat justru yang paling kecil — Parsec ±5 MB,
Moonlight ±20 MB, AnyDesk ±5 MB. Megabyte pada aplikasi besar hampir selalu
berisi *runtime* (Electron, .NET, Qt), bukan kecepatan: tiap lapisan abstraksi
menambah salinan frame dan milidetik. XyDesk hari ini 8,05 MB (MSI) dan 20,83 MB
(APK universal) karena memakai Win32 murni, DXGI/WASAPI bawaan Windows, dan
encoder milik GPU.

Jadi aturan proyek ini: **ukuran boleh naik hanya bila dibayar dengan latensi
atau kualitas yang terukur.** Target akhir ±35 MB APK / ±15 MB MSI — masih di
bawah "aplikasi umum", tiap megabyte punya alasan.

## Target terukur

| Metrik | Sekarang (asumsi, belum diukur) | Target |
|---|---|---|
| Glass-to-glass 720p60 LAN | belum pernah diukur | **< 25 ms** |
| Glass-to-glass 1080p60 Wi-Fi 5 | belum pernah diukur | < 45 ms |
| Laju maksimum | 60 fps | **144 fps** (720p L5.1) |
| Bitrate untuk kualitas setara | 8 Mbps H.264 | ~5 Mbps (AV1/HEVC) |
| CPU host saat sesi 1080p60 | belum pernah diukur | < 8% pada 6-core |

Kolom "sekarang" sengaja ditulis jujur: **belum ada satu pun angka hasil
pengukuran di PC Windows nyata.** Mengisi kolom itu adalah pekerjaan nomor nol;
tanpa dasar, semua klaim perbaikan hanya tebakan.

## Urutan pekerjaan

### Fase 1 — Nol megabyte, murni kode

| # | Pekerjaan | Biaya ukuran | Dampak | Status |
|---|---|---|---|---|
| 1.1 | Laju 120/144 fps | 0 MB | Gerakan jauh lebih halus di panel cepat | **SELESAI** (lihat di bawah) |
| 1.2 | NVENC zero-copy: D3D11 texture → CUDA → NVENC | 0 MB | Hilangkan readback GPU→CPU tiap frame; perkiraan −8…−15 ms dan CPU turun tajam | Terbuka (`host/src/screen.rs:2002`) |
| 1.3 | H.264 4:4:4 untuk konten teks | 0 MB | Hilangkan bayangan warna di teks kecil — penyebab utama kesan "kurang jernih" | Terbuka |
| 1.4 | Tangga bitrate adaptif untuk laju tinggi | 0 MB | `AdaptiveVideo` berhenti di 18 Mbps; 120 fps butuh pagu lebih tinggi | Terbuka |

### Fase 2 — Kualitas gambar generasi berikutnya

| # | Pekerjaan | Biaya ukuran | Dampak |
|---|---|---|---|
| 2.1 | Encode HEVC + AV1 (NVENC AV1 di RTX 40, AMF di RDNA 3) | ~0 MB di host | −30…50% bitrate pada kualitas sama |
| 2.2 | Decoder AV1/HEVC hardware di APK (MediaCodec) | ~0 MB | Pasangan wajib 2.1 |
| 2.3 | Decoder AV1 software (dav1d) sebagai jaring pengaman | **+6…12 MB** | HP tanpa decoder AV1 tetap kebagian 2.1 |

### Fase 3 — Yang butuh tanda tangan dan uang

| # | Pekerjaan | Biaya | Dampak |
|---|---|---|---|
| 3.1 | Driver HID kernel-level | +2…5 MB + WHQL | `SendInput` diblokir anti-cheat; tanpa ini use case gaming pincang |
| 3.2 | Virtual audio cable terbundel | +1…3 MB | Mic klien → PC tidak lagi bergantung VB-CABLE eksternal |
| 3.3 | Sertifikat Authenticode | ±$200/tahun | SmartScreen berhenti menakuti pengguna baru |

## Yang ditolak secara sadar

- **Electron/WebView untuk GUI host.** +120 MB, +200 MB RAM, dan frame pacing
  jadi korban. GUI Win32 `/MT` sekarang adalah keputusan yang benar.
- **FFmpeg penuh.** Yang dibutuhkan 2–3 encoder, bukan 400 codec. Ambil
  dav1d/libaom saja bila perlu.
- **Bundle .NET / Python runtime** untuk tooling apa pun.
- **Aset mentah besar** (video onboarding, PNG raksasa): menaikkan ukuran tanpa
  menyentuh latensi sama sekali.

## 1.1 — Laju 120/144 fps (selesai)

Batasnya bukan selera, melainkan **MaxMBPS Tabel A-1 ITU-T H.264**: melampauinya
membuat stream keluar dari level yang sudah diumumkan di SDP, dan decoder HP
berhak menolaknya.

| Kanvas | Macroblock | L3.1 (108.000) | L4.0 (245.760) | L5.1 (983.040) |
|---|---|---|---|---|
| 1280x720 | 3.600 | 30 fps | 68 → **60** | 273 → **144** |
| 1920x1080 | 8.160 | — | 30 fps | 120 → **120** |
| 4096x2160 | 34.560 | — | — | 28 → **15** (bandwidth habis lebih dulu) |

Hasil dibulatkan ke bawah ke anak tangga yang dikenal (30/60/120/144), tidak
pernah ke sisa bagi seperti 68 — pacing capture, VBV, dan GOP semuanya
mengasumsikan laju bulat.

Dua pagar di sisi client:

1. **Panel HP.** Opsi di atas refresh rate panel disembunyikan
   (`FpsOptions.forDisplay`): meminta 120 fps ke layar 60 Hz hanya membakar
   bitrate dan baterai. Toleransi 6 Hz karena panel nyata melapor 119,98/143,86.
2. **Mode layar.** Sesi meminta `preferredDisplayModeId` tercepat pada resolusi
   yang sama — tanpa itu banyak panel 120 Hz tetap berjalan 60 Hz dan separuh
   frame dibuang di tahap tampil.

GOP ikut berubah: dulu konstanta 120 frame (IDR tiap 4 dtk di 30 fps, 0,8 dtk di
144 fps), kini `gop_for_fps` menjaga ~2 detik di laju mana pun.

**Belum diuji:** semua angka di atas adalah hasil uji logika, bukan pengukuran.
Perlu PC Windows + GPU nyata dan HP 120/144 Hz untuk membuktikan bahwa 144 fps
benar-benar keluar dari NVENC dan sampai ke panel.
