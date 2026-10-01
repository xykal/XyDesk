package id.xyverse.xyadapt

/**
 * Jejak sesi: sampel fps dan RTT per detik, dipadatkan jadi maksimal `buckets`
 * titik supaya muat disimpan di riwayat dan digambar sebagai grafik kecil.
 */
class SessionTrace(private val buckets: Int = 60) {
    private val fps = ArrayList<Double>()
    private val rtt = ArrayList<Double>()

    fun push(fpsNow: Double, rttMs: Double) {
        fps += fpsNow.coerceAtLeast(0.0); rtt += rttMs.coerceAtLeast(0.0)
    }

    val size get() = fps.size

    fun avgFps(): Double = if (fps.isEmpty()) 0.0 else fps.average()

    fun avgRtt(): Double = rtt.filter { it > 0 }.let { if (it.isEmpty()) 0.0 else it.average() }

    /** Bentuk ringkas `fps:rtt,fps:rtt,...` dengan rata-rata per ember. */
    fun encode(): String {
        if (fps.isEmpty()) return ""
        val step = Math.ceil(fps.size / buckets.toDouble()).toInt().coerceAtLeast(1)
        return fps.indices.step(step).joinToString(",") { i ->
            val end = minOf(i + step, fps.size)
            val f = fps.subList(i, end).average(); val r = rtt.subList(i, end).average()
            "${Math.round(f)}:${Math.round(r)}"
        }
    }

    companion object {
        fun decode(s: String): List<Pair<Int, Int>> = s.split(',').filter { it.contains(':') }.mapNotNull { p ->
            val (a, b) = p.split(':', limit = 2)
            val f = a.toIntOrNull() ?: return@mapNotNull null
            val r = b.toIntOrNull() ?: return@mapNotNull null
            f to r
        }
    }
}

/** Banding versi `major.minor.patch` (akhiran `+build` diabaikan). */
object Semver {
    fun newer(candidate: String, installed: String): Boolean {
        val a = parts(candidate); val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(v: String) = v.trim().removePrefix("v").substringBefore('+').substringBefore('-')
        .split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
}
