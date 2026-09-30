package id.xyverse.xydesk.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import id.xyverse.xyadapt.AdaptiveVideo
import id.xyverse.xyadapt.StatsLine
import id.xyverse.xyadapt.VideoCmd
import id.xyverse.xydesk.core.LatencyRing
import id.xyverse.xydesk.rtc.RtcSession

/**
 * Mengumpulkan statistik tiap detik, menyusun baris ringkas untuk pojok kiri atas,
 * dan (saat kualitas Auto) menyerahkan sampel ke `AdaptiveVideo` yang memutuskan
 * resolusi/bitrate. Perintah hanya dikirim saat anak tangga berubah.
 */
class SessionMetrics(
    private val context: Context,
    private val session: RtcSession,
    private val onLine: (String) -> Unit,
    private val onCmd: (VideoCmd) -> Unit,
) {
    private val decodeRing = LatencyRing()
    private val rttRing = LatencyRing()
    private val adaptive = AdaptiveVideo()
    private var lastFrames = 0L
    private var lastDecodeSec = 0.0
    private var nativeFrames = 0L
    private var lastNativeFrames = 0L
    var lowLatency = false
    var targetFps = 60
    var auto = true
        set(v) { field = v; if (v) { adaptive.reset(); adaptive.initial().forEach(onCmd) } }

    fun onNativeDecode(ms: Float) {
        decodeRing.push(ms); nativeFrames++
    }

    fun tick() {
        session.stats { fps, frames, decodeSec, rttMs, relay ->
            val shownFps = if (lowLatency) (nativeFrames - lastNativeFrames).toDouble() else fps
            lastNativeFrames = nativeFrames
            val d = frames - lastFrames
            if (!lowLatency && d > 0) decodeRing.push(((decodeSec - lastDecodeSec) / d * 1000).toFloat())
            lastFrames = frames; lastDecodeSec = decodeSec
            if (rttMs > 0) rttRing.push(rttMs.toFloat())
            val decode = decodeRing.p(50f).toDouble()
            val rtt = rttRing.p(50f).toDouble()
            if (auto) adaptive.sample(shownFps, rtt, decode, targetFps).forEach(onCmd)
            onLine(StatsLine.format(shownFps, rtt, relay, network()))
        }
    }

    fun network(): String {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return ""
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return ""
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Seluler"
            else -> ""
        }
    }

    fun close() {
        decodeRing.close(); rttRing.close()
    }
}
