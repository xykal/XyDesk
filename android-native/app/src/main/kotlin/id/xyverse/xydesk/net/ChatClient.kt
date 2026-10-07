package id.xyverse.xydesk.net

import android.content.Context
import id.xyverse.xyadapt.ChatCache
import id.xyverse.xyadapt.ChatRules
import id.xyverse.xydesk.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.File

/** Kutipan pesan yang dibalas. Isinya selalu dari server, tidak pernah dikarang klien. */
data class ChatReply(val id: String, val from: String, val text: String)

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
    /** Foto profil; kosong berarti gelembung memakai avatar inisial. */
    val photo: String = "",
    /** Pesan yang dibalas, bila ada. */
    val reply: ChatReply? = null,
)

/** Keadaan sambungan yang terlihat di layar. */
enum class ChatLink { CONNECTING, ONLINE, OFFLINE }

/**
 * Riwayat chat di disk.
 *
 * Satu berkas teks kecil di cacheDir. Gunanya satu: ruang obrolan terbuka
 * langsung berisi. Tanpa ini, setiap kali tab dibuka pengguna melihat layar
 * kosong plus tulisan "menyambung" selama jaringan bekerja — padahal lima
 * puluh pesan terakhir sudah pernah ada di tangannya.
 */
class ChatDisk(context: Context) {
    private val file = File(context.cacheDir, "chat-global.txt")

    fun read(): List<ChatMessage> = runCatching {
        if (!file.exists()) return emptyList()
        ChatCache.decode(file.readText()).map {
            ChatMessage(
                id = it.id, from = it.from, hue = it.hue, text = it.text, at = it.at,
                mine = it.mine, vip = it.vip, photo = it.photo,
                reply = if (it.replyId.isEmpty()) null else ChatReply(it.replyId, it.replyFrom, it.replyText),
            )
        }
    }.getOrDefault(emptyList())

    fun write(messages: List<ChatMessage>) {
        runCatching {
            file.writeText(
                ChatCache.encode(
                    messages.map {
                        ChatCache.Row(
                            id = it.id, from = it.from, hue = it.hue, text = it.text, at = it.at,
                            mine = it.mine, vip = it.vip, photo = it.photo,
                            replyId = it.reply?.id.orEmpty(),
                            replyFrom = it.reply?.from.orEmpty(),
                            replyText = it.reply?.text.orEmpty(),
                        )
                    },
                ),
            )
        }
    }

    fun clear() {
        runCatching { file.delete() }
    }
}

/**
 * Klien chat global. Satu soket, sambung ulang dengan jeda menanjak, dan
 * seluruh keadaan diekspos sebagai StateFlow supaya layar tinggal membaca.
 *
 * Token dikirim lewat query karena WebSocket tidak bisa membawa header
 * Authorization — sama seperti jalur signaling yang sudah ada.
 *
 * Umurnya sengaja lebih panjang dari layar: instance-nya dipegang `shared()`
 * selama proses hidup, jadi berpindah tab tidak pernah membuka ulang soket
 * dan tidak pernah mengosongkan daftar pesan.
 */
class ChatClient(private val jwt: String, private val disk: ChatDisk? = null) {
    private val _messages = MutableStateFlow<List<ChatMessage>>(disk?.read().orEmpty())
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

    /** Kapan percobaan sambung sekarang dimulai — dipakai aturan "tanpa loading". */
    @Volatile var waitingSince: Long = System.currentTimeMillis()
        private set

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ws: WebSocket? = null
    private var attempt = 0
    private var stopped = false

    fun start() {
        stopped = false
        if (ws == null) open()
    }

    /**
     * Tutup soket. TIDAK dipanggil saat berpindah tab — hanya saat keluar
     * akun atau proses berakhir; chat yang menutup dirinya tiap kali layar
     * ditinggalkan akan membayar ongkos sambung ulang di setiap kunjungan.
     */
    fun stop() {
        stopped = true
        runCatching { ws?.close(1000, "keluar") }
        ws = null
        persist()
    }

    fun clearNotice() {
        _notice.value = null
    }

    /**
     * Kirim pesan, boleh sambil membalas pesan lain.
     *
     * Yang dikirim hanya `replyTo` berupa id: kutipannya disusun server dari
     * riwayatnya sendiri. Kalau klien boleh mengirim teks kutipannya, siapa
     * pun bisa membuat orang lain seolah menulis kalimat yang tak pernah ia
     * tulis, dan hasilnya akan terlihat persis seperti kutipan asli.
     */
    fun send(raw: String, replyTo: String? = null): Boolean {
        val text = ChatRules.sanitize(raw)
        if (text.isEmpty()) return false
        val socket = ws ?: return false
        val payload = JSONObject().put("type", "msg").put("text", text)
        if (!replyTo.isNullOrEmpty()) payload.put("replyTo", replyTo)
        return socket.send(payload.toString())
    }

    /** Jeda sebelum percobaan berikutnya; dipakai layar untuk menunda `open()`. */
    fun retryDelayMs(): Long = ChatRules.retryDelayMs(attempt)

    private fun open() {
        if (stopped) return
        waitingSince = System.currentTimeMillis()
        _link.value = ChatLink.CONNECTING
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
                val fresh = ArrayList<ChatMessage>(arr.length())
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { fresh.add(bubble(it)) }
                }
                // Riwayat server menang untuk id yang sama, tapi tidak
                // menghapus pesan cache yang lebih lama: layar tidak boleh
                // melompat tepat pada saat sambungan jadi.
                _messages.value = merge(_messages.value, fresh)
                persist()
            }
            "msg" -> {
                val next = _messages.value + bubble(o)
                _messages.value = if (next.size > KEEP) next.takeLast(KEEP) else next
                persist()
            }
            "presence" -> _online.value = o.optInt("online", _online.value)
            "error" -> _notice.value = ChatRules.errorText(o.optString("reason"), o.optLong("retryMs", 0L))
            else -> Unit
        }
    }

    /** Gabungan cache + riwayat server; aturannya diuji di ChatCache.merge. */
    private fun merge(cached: List<ChatMessage>, fresh: List<ChatMessage>): List<ChatMessage> {
        if (fresh.isEmpty()) return cached
        val byId = LinkedHashMap<String, ChatMessage>(cached.size + fresh.size)
        for (m in cached) byId[m.id] = m
        for (m in fresh) byId[m.id] = m
        val merged = byId.values.sortedBy { it.at }
        return if (merged.size > KEEP) merged.takeLast(KEEP) else merged
    }

    private fun persist() {
        val snapshot = _messages.value
        val target = disk ?: return
        io.launch { target.write(snapshot) }
    }

    private fun bubble(o: JSONObject): ChatMessage {
        val from = o.optString("from")
        val reply = o.optJSONObject("reply")?.let {
            val id = it.optString("id")
            if (id.isEmpty()) null else ChatReply(id, it.optString("from"), it.optString("text"))
        }
        return ChatMessage(
            id = o.optString("id"),
            from = from,
            hue = o.optInt("hue", 0),
            text = o.optString("text"),
            at = o.optLong("at", System.currentTimeMillis()),
            mine = from.isNotEmpty() && from == _me.value,
            vip = ChatRules.isVip(o.optString("tier")),
            photo = o.optString("photo").takeIf { it.startsWith("https://") }.orEmpty(),
            reply = reply,
        )
    }

    companion object {
        /** Riwayat yang ditahan di memori; server sendiri hanya menyimpan 50. */
        const val KEEP = 200

        @Volatile private var shared: ChatClient? = null
        @Volatile private var sharedJwt: String = ""

        /**
         * Satu klien untuk seluruh proses.
         *
         * Dipanggil saat aplikasi dibuka (bukan saat tab chat dipilih) supaya
         * soketnya sudah terbuka jauh sebelum ada yang melihat layarnya.
         * Pergantian akun membuat klien lama ditutup dan cache dibuang —
         * riwayat satu akun tidak boleh muncul di layar akun lain.
         */
        @Synchronized
        fun shared(context: Context, jwt: String): ChatClient {
            val existing = shared
            if (existing != null && sharedJwt == jwt) return existing
            existing?.stop()
            val disk = ChatDisk(context.applicationContext)
            if (sharedJwt.isNotEmpty() && sharedJwt != jwt) disk.clear()
            val client = ChatClient(jwt, disk)
            shared = client
            sharedJwt = jwt
            client.start()
            return client
        }

        /** Keluar akun: tutup soket dan buang riwayat di disk. */
        @Synchronized
        fun forget(context: Context) {
            shared?.stop()
            shared = null
            sharedJwt = ""
            ChatDisk(context.applicationContext).clear()
        }
    }
}
