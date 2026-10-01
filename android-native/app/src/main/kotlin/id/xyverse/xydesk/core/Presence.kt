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
import java.security.SecureRandom

/**
 * Kehadiran host: satu soket signaling ringan selama beranda terbuka. Klien
 * menanyakan `presence` untuk ID yang sudah dikenalnya (hub tidak membuka daftar
 * global) tiap beberapa detik dan saat kembali ke aplikasi.
 *
 * ID klien dirotasi bila hub menolak `hello` sebelum `welcome` (mis. `id sudah
 * online` dari soket zombie). Tanpa rotasi, loop menunggu `s.open` yang tidak
 * pernah dapat `presence`.
 */
class Presence(private val jwt: String, private val known: () -> Collection<String>) : SignalingListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var clientId = newPresenceId()
    @Volatile private var welcomed = false
    private var signaling: Signaling? = null
    private var loop: Job? = null
    private val _online = MutableStateFlow<Set<String>?>(null)
    private var previous: Set<String> = emptySet()
    /** null = belum tahu (soket belum menjawab); jangan tampilkan apa pun. */
    val online: StateFlow<Set<String>?> = _online

    fun start() {
        if (loop != null) return
        loop = scope.launch {
            var backoff = 2000L
            while (isActive) {
                welcomed = false
                val id = clientId
                val s = Signaling(id, this@Presence)
                signaling = s
                val ok = runCatching { s.connect(Api.signalToken(jwt, id)) }.isSuccess
                if (!ok) {
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(60_000)
                    continue
                }
                backoff = 2000
                while (isActive && s.open) {
                    delay(10_000)
                    ask()
                }
                delay(backoff)
            }
        }
    }

    fun stop() {
        loop?.cancel(); loop = null
        signaling?.close(); signaling = null
        _online.value = null
    }

    override fun onOpen() { ask() }

    /** Tanya ulang sekarang, mis. saat kembali dari sesi. */
    fun refresh() = ask()

    private fun ask() {
        val ids = known().map { hostKey(it) }.filter { it.length >= 6 }.distinct()
        signaling?.takeIf { it.open }?.presence(ids)
    }

    /**
     * Host dianggap offline hanya bila absen di dua daftar berturut-turut: host yang
     * baru saja memutus sesi sering mendaftar ulang beberapa detik kemudian.
     */
    override fun onMessage(m: SignalMessage) {
        when (m.type) {
            "welcome" -> welcomed = true
            "error" -> if (!welcomed) rotateAndDrop()
            "presence" -> {
                val arr = m.json.optJSONArray("online") ?: return
                val now = (0 until arr.length()).map { arr.optString(it).filter(Char::isDigit) }.toSet()
                val shown = _online.value
                _online.value = if (shown == null) now else now + (shown - now).filter { it in previous }
                previous = now
            }
        }
    }

    override fun onClosed(reason: String) {
        if (!welcomed) clientId = newPresenceId()
        previous = emptySet()
    }

    private fun rotateAndDrop() {
        clientId = newPresenceId()
        signaling?.close()
    }

    companion object {
        fun hostKey(host: String) = host.filter(Char::isDigit)
        private fun newPresenceId(): String =
            "app-presence-" + SecureRandom().nextInt(900_000).plus(100_000)
    }
}
