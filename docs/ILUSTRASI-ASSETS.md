# Ilustrasi Melayang & Bingkai VIP — Panduan Pemakaian

18 aset ilustrasi 3D glossy bernuansa ungu (aksen `#7C3AED` → `#6D28D9`),
transparan penuh, untuk tema terang **Quiet Surface**. XyDesk tidak punya tema
gelap; aset ini dioptimalkan untuk background putih `#FFFFFF` dan kartu
`#F4F4F7`. Tipografi pendamping: **Manrope** (lihat `ui/kit/Theme.kt`).

## Lokasi & penamaan

| Target | Folder | Konvensi nama |
|---|---|---|
| Sumber diaudit (semua platform) | `assets/img/` | `float_*.webp`, `frame_vip.webp` |
| APK Android | `android-native/app/src/main/res/drawable-nodpi/` | sama (snake_case, `R.drawable.float_*`) |
| Web | `web/public/` | kebab-case `float-*.webp`, `frame-vip.webp` (max 640px, sealiran `float-mouse.webp` dst.) |

Semua file **sudah lulus `tool/audit_assets.py`** (alpha bersih, tepi 4px
transparan, margin ≥5%, min 192px) — diverifikasi ulang saat ditambahkan.
File baru di `assets/img/` wajib lulus gerbang yang sama.

## Aset & pemakaian yang disarankan

| Aset | Pemakaian disarankan |
|---|---|
| `frame_vip` | Bingkai profil pengguna VIP berlangganan — **semua platform** (APK, web, host) |
| `float_pc_mascot` | Onboarding welcome, hero login pelengkap (jangan ganti `hero_login.webp`) |
| `float_pc_sleep` | Empty state: riwayat kosong / belum ada sesi / belum ada host |
| `float_phone` + `float_wifi` | Onboarding connect / status koneksi / signaling |
| `float_cursor` + `float_keycap` | Panduan gestur & kontrol (onboarding gestures, tutorial HUD) |
| `float_monitor` | Onboarding host / multi-monitor / pilih layar |
| `float_mail` | Layar OTP email / verifikasi |
| `float_bolt`, `float_rocket` | Materi low-latency (hero web, promo rilis) |
| `float_shield`, `float_padlock` | Layar keamanan / pairing zero-trust |
| `float_gamepad`, `float_headset` | Materi gaming HUD / audio loopback |
| `float_cloud`, `float_sparkle`, `float_globe`, `float_globe` | Dekorasi melayang (animasi halus, delay acak per elemen) |

## Spec bingkai VIP (`frame_vip`)

- Kanvas `1045x1045`, **ring tepat di tengah kanvas** (sudah dinormalisasi).
- Ring tebal (~159px) dengan permata + mahkota emas di atas.
- **PENTING — proporsi foto:** foto profil TIDAK boleh seukuran frame.
  Foto dipasang **48% dari lebar frame**, rata tengah, agar duduk pas di
  lingkaran dalam ring. Kalau foto `matchParentSize`, ring akan menutupi
  pinggiran foto.

Contoh Compose (foundation, tanpa Material):

```kotlin
@Composable
fun VipAvatar(
    avatar: Painter,
    isVip: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = avatar,
            contentDescription = "Foto profil",
            modifier = Modifier.fillMaxSize(0.48f).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        if (isVip) {
            Image(
                painter = painterResource(R.drawable.frame_vip),
                contentDescription = "Bingkai VIP",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
fun FloatingAsset(
    resId: Int,
    modifier: Modifier = Modifier,
    amplitudeDp: Float = 10f,
    durationMillis: Int = 2600,
    delayMillis: Int = 0,
) {
    val t = rememberInfiniteTransition(label = "float")
    val y by t.animateFloat(
        initialValue = -amplitudeDp, targetValue = amplitudeDp,
        animationSpec = infiniteRepeatable(
            tween(durationMillis, delayMillis, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "y",
    )
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        modifier = modifier.offset { IntOffset(0, y.roundToInt()) },
    )
}
```

Di web, pasangan sederhana: CSS `@keyframes` translate-y ±10px,
`animation-delay` acak per elemen biar tidak serempak.

## Aturan

1. **Tema terang saja** — jangan pasang di permukaan gelap tanpa uji kontras.
2. **Jangan mengedit file WebP langsung.** Perubahan = regenerasi dari sumber
   (pipeline: render di green screen `#00F000` → chroma-key → WebP RGBA q90),
   lalu wajib lulus `tool/audit_assets.py`.
3. `hero_login.webp` dan logo (`design/logo-asli.png`) tidak tersentuh PR ini —
   jangan ikut diganti.
4. Ukuran file rata-rata <40KB/aset; total seluruh set ~650KB per folder.
   Jangan menambah duplikat PNG jika WebP sudah ada.
