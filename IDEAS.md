# IDEAS — backlog XyDesk

Format: impact (H/M/L) / effort (S/M/L). Dipangkas berkala.

| Ide | Kenapa penting | Impact | Effort |
|---|---|---|---|
| Encoder MFT (AMD/Intel) dengan fallback NVENC > MFT > openh264, zero-copy DXGI | Pengguna non-NVIDIA sekarang jatuh ke software encode 15 fps di mode native | H | L |
| Proxy gambar artikel lewat worker berita, lalu `img-src` tanpa `https:` | Tutup pelacakan pihak ketiga lewat gambar komentar/artikel | M | M |
| VERSIONINFO di `packaging/native-host/main.rc` (CompanyName, ProductName) | Properti file Windows kosong terlihat tidak resmi; SmartScreen menilai metadata | M | S |
| Pin semua GitHub Action ke SHA penuh + Dependabot actions | Tag bergerak = supply chain; sudah ada Dependabot untuk auto-bump | H | S |
| `signaling/main.go`: ReadTimeout/IdleTimeout/MaxHeaderBytes + semaphore per IP, proving test | Slowloris pada origin Go bila terekspos langsung | M | S |
| Katalog i18n web (JSON id+en) setelah ARB Flutter | Web masih hardcode Indonesia; pengguna iPhone/iPad global | M | M |
| Pecah `host/src/screen.rs` (capture vs encoder policy) | 2271 baris, tempat MFT akan masuk; pecah dulu agar diff MFT kecil | M | M |
| Halaman status publik (uptime signaling/news/TURN) dari `/status` yang sudah ada | Kepercayaan pengguna saat sesi gagal: jelas salah siapa | M | S |
| LICENSE dual-language bertanggal + Privacy Policy/ToS terpisah, tanda DRAFT | Play Store dan pengguna global butuh dokumen EN | M | S |
