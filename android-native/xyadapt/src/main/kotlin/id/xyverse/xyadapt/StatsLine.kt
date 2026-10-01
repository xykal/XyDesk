package id.xyverse.xyadapt

/** Baris statistik ringkas tanpa label panjang: "48 fps · 36 ms · langsung · WiFi". */
object StatsLine {
    fun format(fps: Double, rttMs: Double, relay: Boolean, network: String, decodeMs: Double = 0.0): String {
        val parts = mutableListOf("%.0f fps".format(fps), "%.0f ms".format(rttMs), if (relay) "relay" else "langsung")
        if (network.isNotEmpty()) parts += network
        if (decodeMs > 0) parts += "dekode %.1f".format(decodeMs)
        return parts.joinToString(" · ")
    }
}
