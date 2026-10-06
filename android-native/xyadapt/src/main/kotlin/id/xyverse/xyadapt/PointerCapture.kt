package id.xyverse.xyadapt

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Aturan tangkapan pointer (pointer capture) untuk mouse fisik.
 *
 * ## Kenapa perlu ditangkap
 *
 * Tanpa tangkapan pointer, Android memberi kita posisi kursornya sendiri dan
 * kursor itu berhenti di tepi layar HP. Gerak mouse lalu dikirim sebagai
 * posisi absolut, dan begitu kursor HP menyentuh tepi, gerak berikutnya ke
 * arah yang sama bernilai nol — di PC kursornya ikut berhenti. Untuk
 * menggeser dokumen panjang itu hanya mengganggu; untuk game FPS, yang
 * memutar pandangan dengan gerak relatif tak terbatas, itu membuat permainan
 * mustahil dimainkan.
 *
 * Dengan tangkapan pointer, kursor HP hilang dan yang kita terima adalah
 * **delta** (`AXIS_RELATIVE_X/Y`) yang tidak pernah kehabisan ruang. Delta
 * itu dikirim sebagai `0x01 MOUSE_MOVE_REL` — opcode yang sudah lama ada di
 * host tetapi tidak pernah dipakai aplikasi.
 *
 * Semua isi berkas ini murni supaya bisa diuji tanpa perangkat.
 */
object CaptureRules {
    /**
     * Apakah tangkapan pointer seharusnya aktif sekarang.
     *
     * Tiap syarat ada alasannya:
     * - tanpa mouse fisik tidak ada yang bisa ditangkap, dan menangkap
     *   pointer akan menghilangkan kursor tanpa memberi apa pun sebagai
     *   gantinya;
     * - saat IME terbuka pengguna sedang mengetik di HP-nya, dan kursor yang
     *   hilang membuat ia tidak bisa menutup papan ketiknya;
     * - saat mengatur tata letak kontrol pengguna justru sedang menyeret
     *   tombol di layar HP;
     * - mode presentasi dimaksudkan untuk ditonton, bukan dikendalikan.
     */
    fun wanted(
        mousePresent: Boolean,
        connected: Boolean,
        enabled: Boolean,
        imeOpen: Boolean = false,
        editing: Boolean = false,
        presenting: Boolean = false,
    ): Boolean =
        mousePresent && connected && enabled && !imeOpen && !editing && !presenting
}

/**
 * Pengumpul gerak relatif.
 *
 * Protokol mengirim bilangan bulat, sementara sensor mouse dan pengali
 * sensitivitas menghasilkan pecahan. Membulatkan tiap kejadian sendiri-
 * sendiri membuang sisa pecahannya, dan pada gerak pelan — membidik dalam
 * game, menyeret tepi jendela — mouse jadi terasa seperti tersangkut:
 * gerakan 0,4 piksel berkali-kali menjadi nol selamanya. Sisa pecahan di
 * sini disimpan dan ikut pada kejadian berikutnya.
 */
class RelMotion {
    private var restX = 0f
    private var restY = 0f

    /** Sensitivitas di luar rentang ini ditolak; 1.0 = apa adanya. */
    fun clampSensitivity(value: Float): Float = value.coerceIn(MIN_SENS, MAX_SENS)

    /**
     * Menyuapkan satu kejadian. Mengembalikan `null` bila belum ada satu
     * piksel penuh yang pantas dikirim — paket nol hanya membebani jalur
     * input tanpa memindahkan apa pun.
     */
    fun feed(dx: Float, dy: Float, sensitivity: Float = 1f): IntArray? {
        if (!dx.isFinite() || !dy.isFinite()) return null
        val s = clampSensitivity(sensitivity)
        restX += dx * s
        restY += dy * s
        val outX = restX.toIntTowardZero()
        val outY = restY.toIntTowardZero()
        if (outX == 0 && outY == 0) return null
        restX -= outX
        restY -= outY
        return intArrayOf(outX.clampToI16(), outY.clampToI16())
    }

    /** Membuang sisa pecahan; dipanggil saat tangkapan dilepas. */
    fun reset() {
        restX = 0f
        restY = 0f
    }

    /** Sisa pecahan yang belum terkirim — untuk uji dan diagnosa. */
    fun pending(): FloatArray = floatArrayOf(restX, restY)

    companion object {
        const val MIN_SENS = 0.2f
        const val MAX_SENS = 5f

        /** Satu "klik" roda scroll Windows. */
        const val WHEEL_DELTA = 120

        /**
         * Nilai sumbu scroll Android → satuan WHEEL_DELTA. Dibulatkan ke
         * bilangan terdekat, bukan dipotong: scroll 0,6 klik yang menjadi
         * nol membuat roda terasa mati.
         */
        fun wheel(value: Float): Int =
            if (!value.isFinite() || value == 0f) 0 else (value * WHEEL_DELTA).roundToInt().clampToI16()

        private fun Float.toIntTowardZero(): Int = when {
            this >= 1f -> this.toInt()
            this <= -1f -> this.toInt()
            else -> 0
        }

        private fun Int.clampToI16(): Int = coerceIn(-32768, 32767)

        private fun Float.clampToI16(): Int = when {
            this > 32767f -> 32767
            this < -32768f -> -32768
            else -> toInt()
        }

        /** Dipakai uji untuk memastikan ambangnya simetris. */
        fun significant(value: Float): Boolean = abs(value) >= 1f
    }
}

private fun Int.clampToI16(): Int = coerceIn(-32768, 32767)
