package id.xyverse.xyadapt

/** Perintah ke host saat tangga kualitas berubah. */
sealed interface VideoCmd {
    data class Bitrate(val mbps: Int) : VideoCmd
    data class Resolution(val mode: Int) : VideoCmd
    data class Fps(val target: Int) : VideoCmd
}

/** Satu anak tangga: resolusi (0 = 720p, 1 = 1080p) dan bitrate. */
data class Rung(val resolution: Int, val mbps: Int) {
    val label get() = (if (resolution == 1) "1080p" else "720p") + " · $mbps Mbps"
}

/**
 * Pengendali kualitas otomatis dengan histeresis: turun cepat saat buruk
 * (3 sampel), naik pelan saat stabil (8 sampel). Hanya mengeluarkan perintah
 * saat anak tangga berubah, jadi aman dipanggil tiap detik.
 */
class AdaptiveVideo(
    private val ladder: List<Rung> = DEFAULT_LADDER,
    start: Int = 2,
    private val downAfter: Int = 3,
    private val upAfter: Int = 8,
) {
    var index = start.coerceIn(0, ladder.lastIndex)
        private set
    private var bad = 0
    private var good = 0
    private var announced = false

    val rung get() = ladder[index]

    /** Sampel satu detik; [targetFps] = fps yang diminta ke host. */
    fun sample(fps: Double, rttMs: Double, decodeMs: Double, targetFps: Int): List<VideoCmd> {
        val budget = 1000.0 / targetFps.coerceAtLeast(1)
        val isBad = fps < targetFps * 0.7 || rttMs > 180 || decodeMs > budget * 0.9
        val isGood = fps >= targetFps * 0.9 && rttMs < 90 && decodeMs < budget * 0.5
        if (isBad) { bad++; good = 0 } else if (isGood) { good++; bad = 0 } else { bad = 0; good = 0 }
        val before = index
        if (bad >= downAfter && index > 0) { index--; bad = 0 }
        if (good >= upAfter && index < ladder.lastIndex) { index++; good = 0 }
        if (before == index && announced) return emptyList()
        announced = true
        return commandsFor(ladder[before], ladder[index], force = before == index)
    }

    /** Perintah awal agar host mulai di anak tangga sekarang. */
    fun initial(): List<VideoCmd> {
        announced = true
        return listOf(VideoCmd.Resolution(rung.resolution), VideoCmd.Bitrate(rung.mbps))
    }

    fun reset(start: Int = 2) {
        index = start.coerceIn(0, ladder.lastIndex); bad = 0; good = 0; announced = false
    }

    private fun commandsFor(from: Rung, to: Rung, force: Boolean): List<VideoCmd> = buildList {
        if (force || from.resolution != to.resolution) add(VideoCmd.Resolution(to.resolution))
        if (force || from.mbps != to.mbps) add(VideoCmd.Bitrate(to.mbps))
    }

    companion object {
        val DEFAULT_LADDER = listOf(Rung(0, 3), Rung(0, 6), Rung(1, 8), Rung(1, 12), Rung(1, 18))
    }
}
