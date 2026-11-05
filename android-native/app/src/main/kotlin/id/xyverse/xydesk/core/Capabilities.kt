package id.xyverse.xydesk.core

import android.content.Context
import org.json.JSONObject

/**
 * Matriks kemampuan per versi host (sumber: config/capabilities.json,
 * disalin ke assets). Fitur disembunyikan bila host yang dikendalikan
 * belum mendukungnya — pola "capability gate" per versi supaya upgrade
 * bertahap aman.
 */
object Capabilities {
    private val defaults = mapOf(
        "file_transfer" to "6.0.0",
        "clipboard" to "6.0.0",
        "virtual_display" to "7.0.0",
        "gamepad" to "7.0.0",
        "super_screen" to "1.0.0",
        "qr_pairing" to "1.0.0",
    )

    @Volatile
    private var minHost: Map<String, String> = defaults

    /** Muat matriks dari assets (idempoten; gagal = pakai bawaan). */
    fun load(ctx: Context) {
        runCatching {
            val text = ctx.assets.open("capabilities.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(text).getJSONObject("features")
            val map = mutableMapOf<String, String>()
            obj.keys().forEach { key ->
                map[key] = obj.getJSONObject(key).optString("min_host").ifEmpty { "0.0.0" }
            }
            if (map.isNotEmpty()) minHost = map
        }
    }

    /** true bila [hostVersion] (mis. "7.0.0") sudah mendukung [feature]. */
    fun supports(feature: String, hostVersion: String): Boolean {
        val min = minHost[feature] ?: return true
        return compare(hostVersion, min) >= 0
    }

    /** Bandingkan versi "X.Y.Z"; bagian yang hilang dianggap 0. */
    fun compare(a: String, b: String): Int {
        fun parts(v: String) = v.trim().removePrefix("v").substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
        val pa = parts(a); val pb = parts(b)
        for (i in 0..2) {
            val d = (pa.getOrNull(i) ?: 0).compareTo(pb.getOrNull(i) ?: 0)
            if (d != 0) return d
        }
        return 0
    }
}
