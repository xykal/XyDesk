# libxyadapt

Pustaka Kotlin murni milik XyDesk (tanpa dependensi Android) yang dipakai client native:

- `Trackpad` — mesin gestur trackpad: ketuk = klik kiri, tahan = klik kanan, dua jari ketuk = klik kanan,
  tiga jari ketuk = klik tengah, dua jari geser = scroll, ketuk-ketuk-tahan = seret, kurva akselerasi kursor.
- `AdaptiveVideo` — pengendali kualitas otomatis: dari sampel fps/RTT/dekode memutuskan bitrate,
  resolusi, dan target fps dengan histeresis, hanya mengeluarkan perintah saat ada perubahan.
- `StatsLine` — format baris statistik ringkas (fps, ms, jalur, jaringan).

Murni JVM agar bisa diuji tanpa emulator (`./gradlew :xyadapt:test`).

---

Pure-Kotlin library (no Android dependency) used by the native client: trackpad gesture engine,
adaptive video controller with hysteresis, and compact stats formatting. JVM-only so it is unit-testable.
