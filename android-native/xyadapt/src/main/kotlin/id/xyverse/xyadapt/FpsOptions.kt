package id.xyverse.xyadapt

/**
 * Pilihan laju yang masuk akal untuk ditawarkan ke pengguna.
 *
 * Dua pagar, bukan satu:
 *
 *  1. **Panel HP.** Meminta 120 fps ke host saat layar hanya 60 Hz tidak
 *     menambah satu frame pun yang terlihat — yang bertambah cuma bitrate,
 *     panas, dan baterai. Jadi opsi di atas refresh rate panel disembunyikan.
 *  2. **Level H264.** Host tetap memotong lewat `video_policy::fps_limit`
 *     (720p L5.1 → 144, 1080p L5.1 → 120, L4.0 → 60, L3.1 → 30). UI boleh
 *     menawarkan; host yang memutuskan. Angka yang benar-benar dipakai
 *     dilaporkan balik lewat `fpsLimit` di meta sesi.
 *
 * Toleransi 6 Hz dipakai karena panel "120 Hz" kerap melapor 119,98 dan
 * "144 Hz" melapor 143,86.
 */
object FpsOptions {
    /** Semua laju yang dimengerti protokol kontrol `0x0f` di host. */
    val ALL = listOf(30, 60, 120, 144)

    /** Minimum yang selalu ditawarkan, termasuk saat refresh rate tak terbaca. */
    val BASE = listOf(30, 60)

    const val TOLERANCE_HZ = 6f

    /** Laju yang boleh ditawarkan untuk panel [refreshHz]. */
    fun forDisplay(refreshHz: Float): List<Int> {
        if (!refreshHz.isFinite() || refreshHz <= 0f) return BASE
        val allowed = ALL.filter { it <= refreshHz + TOLERANCE_HZ }
        return if (allowed.size < BASE.size) BASE else allowed
    }

    /** Jatuhkan [fps] tersimpan ke opsi terdekat yang masih valid di panel ini. */
    fun clampToDisplay(fps: Int, refreshHz: Float): Int {
        val options = forDisplay(refreshHz)
        return options.lastOrNull { it <= fps } ?: options.first()
    }
}
