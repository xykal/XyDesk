package id.xyverse.xyadapt

/**
 * Kebijakan sambung ulang otomatis: mundur eksponensial (1 s, 2 s, 4 s…),
 * maksimal [maxAttempts] percobaan, direset saat sesi berhasil tersambung.
 * Hanya untuk putus tak terduga; penolakan password/host sibuk tidak diulang.
 */
class ReconnectPolicy(private val maxAttempts: Int = 3, private val baseMs: Long = 1_000, private val capMs: Long = 8_000) {
    var attempt = 0
        private set

    val exhausted get() = attempt >= maxAttempts

    /** Jeda sebelum percobaan berikutnya, atau null bila sudah habis. */
    fun nextDelayMs(): Long? {
        if (exhausted) return null
        val delay = (baseMs shl attempt).coerceAtMost(capMs)
        attempt++
        return delay
    }

    fun reset() {
        attempt = 0
    }

    companion object {
        /** Alasan putus yang layak dicoba ulang. */
        fun retryable(reason: String): Boolean = reason == "ended" || reason == "error" || reason == "peer_offline"
    }
}
