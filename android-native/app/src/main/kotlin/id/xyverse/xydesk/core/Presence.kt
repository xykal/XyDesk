package id.xyverse.xydesk.core

import id.xyverse.xydesk.net.Api
import id.xyverse.xydesk.net.SignalMessage
import id.xyverse.xydesk.net.Signaling
import id.xyverse.xydesk.net.SignalingListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Kehadiran host: satu soket signaling ringan selama beranda terbuka. Server
 * menyiarkan `devices` (hanya host) tiap ada yang masuk/keluar, jadi titik
 * online/offline di kartu perangkat ikut berubah seketika.
 */
class Presence(private val jwt: String) : SignalingListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val id = "app-presence-" + SecureRandom().nextInt(900_000).plus(100_000)
    private var signaling: Signaling? = null
    private var loop: Job? = null
    private val _online = MutableStateFlow<Set<String>?>(null)
    /** null = belum tahu (soket belum menjawab); jangan tampilkan apa pun. */
    val online: StateFlow<Set<String>?> = _online

    fun start() {
        if (loop != null) return
        loop = scope.launch {
            var backoff = 2000L
            while (isActive) {
                val s = Signaling(id, this@Presence)
                signaling = s
                val ok = runCatching { s.connect(Api.signalToken(jwt, id)) }.isSuccess
                if (!ok) { delay(backoff); backoff = (backoff * 2).coerceAtMost(60_000); continue }
                backoff = 2000
                while (isActive && s.open) { delay(25_000); s.list() }
                delay(backoff)
            }
        }
    }

    fun stop() {
        loop?.cancel(); loop = null
        signaling?.close(); signaling = null
        _online.value = null
    }

    override fun onOpen() { signaling?.list() }

    override fun onMessage(m: SignalMessage) {
        if (m.type != "devices") return
        val arr = m.json.optJSONArray("devices") ?: return
        _online.value = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .filter { it.optString("role") == "host" }.map { it.optString("id").filter(Char::isDigit) }.toSet()
    }

    override fun onClosed(reason: String) { _online.value = null }

    companion object {
        fun hostKey(host: String) = host.filter(Char::isDigit)
    }
}
