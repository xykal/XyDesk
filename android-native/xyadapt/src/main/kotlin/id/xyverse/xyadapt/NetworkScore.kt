package id.xyverse.xyadapt

import kotlin.math.abs

/** Peringkat koneksi satu kata untuk stats ringkas; dihitung dari jendela sampel pendek. */
enum class Grade(val label: String) { BAGUS("bagus"), SEDANG("sedang"), BURUK("buruk") }

/**
 * Menilai kualitas sesi dari fps, RTT, dan jitter RTT (selisih antar sampel).
 * Jendela 5 sampel supaya satu detik jelek tidak langsung mengubah label.
 */
class NetworkScore(private val window: Int = 5) {
    private val fps = ArrayDeque<Double>()
    private val rtt = ArrayDeque<Double>()

    fun push(fpsNow: Double, rttMs: Double) {
        add(fps, fpsNow); add(rtt, rttMs)
    }

    val jitterMs: Double
        get() = rtt.zipWithNext { a, b -> abs(a - b) }.average().takeIf { !it.isNaN() } ?: 0.0

    fun grade(targetFps: Int): Grade {
        if (fps.isEmpty()) return Grade.SEDANG
        val f = fps.average()
        val r = rtt.average()
        val j = jitterMs
        return when {
            f >= targetFps * 0.85 && r < 80 && j < 15 -> Grade.BAGUS
            f < targetFps * 0.5 || r > 200 || j > 60 -> Grade.BURUK
            else -> Grade.SEDANG
        }
    }

    private fun add(q: ArrayDeque<Double>, v: Double) {
        q.addLast(v)
        while (q.size > window) q.removeFirst()
    }
}
