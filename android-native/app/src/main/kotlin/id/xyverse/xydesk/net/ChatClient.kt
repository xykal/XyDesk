package id.xyverse.xydesk.net

import id.xyverse.xyadapt.ChatRules
import id.xyverse.xydesk.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/** Satu gelembung di ruang chat global. */
data class ChatMessage(
    val id: String,
    val from: String,
    val hue: Int,
    val text: String,
    val at: Long,
    val mine: Boolean,
    /** Dari profil akun lewat server — tidak pernah dari isi pesan. */
    val vip: Boolean = false,
)

/** Keadaan sambungan yang terlihat di layar. */
enum class ChatLink { CONNECTING, ONLINE, OFFLINE }

/**
 * Klien chat global. Satu soket, sambung ulang dengan jeda menanjak, dan
 * seluruh keadaan diekspos sebagai StateFlow supaya layar tinggal membaca.
 *
 * Token dikirim lewat query karena WebSocket tidak bisa membawa header
 * Authorization — sama seperti jalur signaling yang sudah ada.
 */
class ChatClient(private val jwt: String) {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _link = MutableStateFlow(ChatLink.CONNECTING)
    val link: StateFlow<ChatLink> = _link

    private val _online = MutableStateFlow(0)
    val online: StateFlow<Int> = _online

    private val _me = MutableStateFlow("")
    val me: StateFlow<String> = _me

    private val _meVip = MutableStateFlow(false)
    val meVip: StateFlow<Boolean> = _meVip

    /** Pesan kesalahan sekali tampil; layar yang menghapusnya setelah dibaca. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    private var ws: WebSocket? = null
    private var attempt = 0
    private var stopped = false

    fun start() {
        stopped = false
        open()
    }

    fun stop() {
        stopped = true
        runCatching { ws?.close(1000, "keluar") }
        ws = null
    }

    fun clearNotice() {
        _notice.value = null
    }

    /** Kirim pesan. Mengembalikan false bila ditahan di sisi klien. */
    fun send(raw: String): Boolean {
        val text = ChatRules.sanitize(raw)
        if (text.isEmpty()) return false
        val socket = ws ?: return false
        return socket.send(JSONObject().put("type", "msg").put("text", text).toString())
    }

    /** Jeda sebelum percobaan berikutnya; dipakai layar untuk menunda `open()`. */
    fun retryDelayMs(): Long = ChatRules.retryDelayMs(attempt)

    private fun open() {
        if (stopped) return
        _link.value = if (_messages.value.isEmpty()) ChatLink.CONNECTING else _link.value
        val url = BuildConfig.API_URL.replaceFirst("http", "ws") + "/chat/ws?token=$jwt"
        ws = Api.http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                attempt = 0
                _link.value = ChatLink.ONLINE
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { handle(JSONObject(text)) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _link.value = ChatLink.OFFLINE
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _link.value = ChatLink.OFFLINE
                attempt += 1
            }
        })
    }

    /** Dipanggil layar saat `link` jatuh ke OFFLINE dan jeda sudah lewat. */
    fun reopen() {
        runCatching { ws?.cancel() }
        open()
    }

    private fun handle(o: JSONObject) {
        when (o.optString("type")) {
            "welcome" -> {
                _me.value = o.optString("you")
                _meVip.value = ChatRules.isVip(o.optString("tier"))
                _online.value = o.optInt("online", 1)
                val arr = o.optJSONArray("messages") ?: return
                val list = ArrayList<ChatMessage>(arr.length())
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { list.add(bubble(it)) }
                }
                _messages.value = list
            }
            "msg" -> {
                val next = _messages.value + bubble(o)
                _messages.value = if (next.size > KEEP) next.takeLast(KEEP) else next
            }
            "presence" -> _online.value = o.optInt("online", _online.value)
            "error" -> _notice.value = ChatRules.errorText(o.optString("reason"), o.optLong("retryMs", 0L))
            else -> Unit
        }
    }

    private fun bubble(o: JSONObject): ChatMessage {
        val from = o.optString("from")
        return ChatMessage(
            id = o.optString("id"),
            from = from,
            hue = o.optInt("hue", 0),
            text = o.optString("text"),
            at = o.optLong("at", System.currentTimeMillis()),
            mine = from.isNotEmpty() && from == _me.value,
            vip = ChatRules.isVip(o.optString("tier")),
        )
    }

    private companion object {
        /** Riwayat yang ditahan di memori; server sendiri hanya menyimpan 50. */
        const val KEEP = 200
    }
}
