# Kebijakan Versi & Rilis XyDesk

> Berlaku sejak 1 September 2026. Dokumen ini mengikat: CI menolak rilis yang
> melanggarnya, bukan sekadar menyarankan.

## Kenapa dokumen ini ada

Dalam satu hari, 31 Agustus 2026, XyDesk melompat **2.5.0 → 6.0.0 → 6.1.0**
dalam rentang lima jam. Tidak ada satu pun perubahan yang merusak kompatibilitas.
Total 21 tag untuk produk yang **belum pernah masuk beta test**.

Masalahnya bukan estetika. Nomor versi adalah alat komunikasi dengan satu tugas:
menjawab "seberapa besar lompatan ini, dan apa yang harus saya waspadai?"
Ketika 2.5.0 → 6.0.0 terjadi karena warnanya berubah jadi ungu, nomor itu
berhenti menjawab apa pun. Pengguna tidak bisa lagi menilai risiko update; kamu
sendiri tidak bisa lagi membaca riwayat rilis untuk mencari kapan sesuatu rusak.

Versi yang inflasi juga menutup pintu masa depan: kalau 6.x sudah terpakai hari
ini, tidak tersisa ruang untuk menandai perubahan yang benar-benar besar nanti.

---

## 1. Arti setiap angka — `MAJOR.MINOR.PATCH+BUILD`

XyDesk memakai [Semantic Versioning 2.0.0](https://semver.org) dengan kontrak
yang eksplisit untuk aplikasi (bukan pustaka).

### PATCH — `6.1.0 → 6.1.1`

Naikkan bila **hanya** memperbaiki perilaku yang salah.

- Perbaikan bug, kebocoran memori, crash.
- Perbaikan teks, ejaan, jarak antar elemen.
- Perbaikan keamanan yang tidak mengubah protokol.

Syarat: pengguna tidak perlu melakukan apa pun, dan tidak ada yang baru untuk
dipelajari.

### MINOR — `6.1.1 → 6.2.0`

Naikkan bila ada **kemampuan baru** yang kompatibel ke belakang.

- Fitur baru (audio forward, multi-monitor, badge komentar).
- Layar atau alur baru.
- Field baru yang opsional di protokol/API.
- Deprecation yang masih berfungsi.

Syarat: host versi lama tetap bisa bicara dengan client versi baru, dan sebaliknya.

### MAJOR — `6.2.0 → 7.0.0`

Naikkan **hanya** bila ada yang patah. Ini daftar tertutup:

1. **Protokol wire berubah tidak kompatibel** — tag input, format SDP/data
   channel, atau skema token signaling. Host lama tidak lagi bisa menyambung.
2. **Format data tersimpan berubah** dan tidak bisa dimigrasikan otomatis —
   pengguna kehilangan riwayat, perangkat tersimpan, atau harus login ulang.
3. **Fitur dicabut**, bukan diganti.
4. **Persyaratan platform naik** — mis. Android minSdk atau Windows minimum.
5. **Model lisensi atau harga berubah** secara mendasar.

Kalau alasannya tidak ada di lima poin itu, **itu bukan MAJOR.** Redesign
visual bukan MAJOR. Rebranding bukan MAJOR. Menambah sepuluh fitur sekaligus
bukan MAJOR — itu MINOR yang besar.

### BUILD — `+21`

Bilangan bulat naik satu setiap rilis, tidak pernah turun, tidak pernah dipakai
ulang. Google Play dan installer Windows memakainya untuk mengurutkan update.
Ini satu-satunya angka yang boleh naik tanpa pembenaran.

---

## 2. Tahap peredaran — terpisah dari nomor versi

Nomor versi menjawab "seberapa besar perubahannya". Tahap menjawab
"seberapa layak dipasang". Keduanya bergerak sendiri-sendiri.

| Tahap | Arti | Distribusi |
|---|---|---|
| **Pra-beta** | Dibangun dari `main`, belum diverifikasi di perangkat nyata | Tidak ada. Tombol unduh mati. |
| **Beta** | Lulus uji lab, diuji terbatas oleh penguji undangan | Tautan langsung ke penguji |
| **Stabil** | Lulus beta tanpa cacat kritis | Publik |

Sumber kebenaran tahap web saat ini: `web/src/version.ts`. Stack Flutter sudah
tidak aktif; bila Android native membutuhkan gating tahap sendiri, sumbernya
harus ditambahkan eksplisit dan disetel bersamaan dengan web.

### Gerbang menuju Beta — semua wajib lulus

XyDesk **belum** memenuhi ini per 1 September 2026:

- [ ] Uji dengar audio WASAPI loopback + mic passthrough di PC Windows nyata.
- [ ] Capture DXGI dan pemilih multi-monitor terverifikasi di perangkat keras.
- [ ] HUD, mouse, dan keyboard virtual terbukti mengendalikan host sungguhan.
- [ ] Latency ujung-ke-ujung terukur di jaringan nyata (bukan loopback lab).
- [ ] Push notifikasi terkirim dan terbuka di perangkat uji.
- [ ] Sesi 30 menit tanpa putus, tanpa kebocoran memori.

### Gerbang menuju Stabil

- [ ] Minimal 10 penguji beta, minimal 7 hari, tanpa cacat kritis terbuka.
- [ ] Crash-free session rate ≥ 99% pada perangkat penguji.
- [ ] Dokumen legal & inventaris lisensi mutakhir (`node tool/gen-licenses.mjs --check`).
- [ ] Changelog rilis terbit sebagai artikel News.

---

## 3. Cara menaikkan versi — prosedur

Versi hidup di **satu** tempat: `VERSION`. Semua manifest turunan harus
mengikuti nilai ini. Formatnya:

```
6.11.9+74
^^^^^^ ^^
SemVer BUILD
```

| Konsumen | Cara membaca |
|---|---|
| Android/APK | `android-native/app/build.gradle.kts` membaca `../VERSION` untuk `versionName` dan `versionCode` |
| Web | `vite.config.ts` membaca `VERSION` saat build → `__APP_VERSION__` |
| Host Rust | `host/Cargo.toml` disamakan dengan SemVer dari `VERSION` saat rilis |
| Release CI | `release.yml` mengurai `VERSION` → tag `vX.Y.Z` dan build number |
| Gerbang meta | `tool/check_version.py` membandingkan `VERSION`, `host/Cargo.toml`, `web/package.json`, dan entri teratas `CHANGELOG.md` |

**Jangan pernah** menulis nomor versi di tempat lain. Footer web pernah memajang
angka lama selama beberapa rilis karena angkanya diketik tangan di JSX.

### Tahap rilis

Tahap peredaran web saat ini berada di `web/src/version.ts` (`RELEASE_STAGE`).
Unduhan publik tetap ditahan selama tahap `pra-beta`. Bila Android native nanti
memiliki gating tahap sendiri, sumbernya harus disebut eksplisit di dokumen ini
dan ikut dicek lintas-dokumen.

### Langkah rilis

1. Tentukan tingkat perubahan dengan aturan §1. Kalau ragu antara MINOR dan
   MAJOR, pilih MINOR — kamu selalu bisa naik MAJOR nanti, tetapi tidak bisa
   turun.
2. Perbarui `VERSION` (SemVer + BUILD naik satu).
3. Samakan `host/Cargo.toml` dan `web/package.json` dengan SemVer dari
   `VERSION`. Android membaca `VERSION` langsung, jadi tidak punya nomor
   terpisah.
4. Tulis entri `CHANGELOG.md`: **apa** yang berubah, **kenapa**, dan **apa
   dampaknya bagi pengguna**. Bukan daftar commit.
5. Jalankan generator/check lisensi bila dependensi berubah, lalu perbarui
   `docs/THIRD-PARTY-LICENSES.md` bila perlu.
6. Terbitkan artikel changelog di News (§4). Ini wajib saat rilis publik, bukan
   opsional.
7. Jalankan Build penuh di SHA yang akan dirilis. `release.yml` hanya dijalankan
   operator setelah Build penuh hijau dan memakai SHA itu.

### Yang TIDAK boleh

- Menaikkan MAJOR untuk menandai "rilis besar" secara perasaan.
- Menaikkan versi lebih dari sekali sehari tanpa alasan teknis. Kalau ada tiga
  perbaikan dalam sehari, itu satu rilis PATCH, bukan tiga.
- Membuat tag saat gerbang mutu merah.
- Merilis ke publik saat tahap masih pra-beta.

## 4. Changelog wajib per rilis

Setiap rilis menghasilkan **dua** dokumen, dan keduanya wajib:

1. **`CHANGELOG.md`** — untuk pengembang. Dilampirkan ke GitHub Release.
2. **Artikel di XyDesk News** (kategori `rilis`) — untuk pengguna.

Artikel News bukan salinan CHANGELOG. Formatnya:

- **Apa yang berubah** — dalam bahasa pengguna, bukan nama fungsi.
- **Kenapa diubah** — masalah nyata apa yang diperbaiki. Bagian ini yang
  paling sering dilewati dan paling berharga.
- **Apa dampaknya** — yang perlu pengguna lakukan, atau tidak sama sekali.
- **Yang masih belum** — jujur soal batasan. Ini yang membangun kepercayaan.

Slug artikel changelog mengikuti pola `changelog-v6-2-0`, karena tautan versi
di footer web dan di aplikasi menunjuk ke sana secara otomatis
(`CHANGELOG_SLUG` di `web/src/version.ts`).

> ⚠️ **Ini WAJIB dikirim saat menerbitkan artikel rilis.** Worker berita
> mengacak slug jadi `p-<hash>` secara bawaan; pola `changelog-v<X>-<Y>-<Z>`
> adalah **satu-satunya** pengecualian yang boleh diminta sendiri lewat field
> `slug` di `POST /api/admin/publish` (lihat `adminPublish` di
> `news/src/worker.js`). Kalau field itu lupa diisi, artikel tetap terbit
> tetapi tombol versi di footer jatuh ke 404.
>
> Ini sudah terjadi: artikel rilis **6.4.0** terbit sebagai `p-8f5aa26aa3bc`,
> sehingga `changelog-v6-4-0` → HTTP 404 (terverifikasi 3 Sep 2026), sementara
> `changelog-v6-2-0` … `changelog-v6-3-0` semuanya masih HTTP 200. Rilis 6.1
> dan 6.0 kena hal yang sama. Ditindaklanjuti lewat `project/HANDOFF.md` (area News +
> prosedur rilis).

---

## 5. Kenapa 6.x dipertahankan, bukan direset

Nomor 6.1.0 sudah beredar sebagai tag publik. Mengembalikannya ke 0.x atau
1.x akan membuat pengguna yang sudah memasang menerima "update" bernomor lebih
kecil — installer dan Play Store akan menolaknya, dan riwayat rilis jadi tidak
terbaca.

Jadi 6.x diteruskan, tetapi **berhenti bergerak liar**. Angka 7 disimpan untuk
saat protokolnya benar-benar patah.

> Catatan status (4 Okt 2026): versi aktif dibaca dari `VERSION`. Contoh angka
> di dokumen ini tetap ilustratif, bukan perintah menaikkan versi.
