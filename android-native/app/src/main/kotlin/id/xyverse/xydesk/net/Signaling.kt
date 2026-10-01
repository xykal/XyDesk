package id.xyverse.xydesk.net

import id.xyverse.xydesk.BuildConfig
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

/** Pesan signaling — bentuk JSON sama persis dengan client Flutter/web. */
data class SignalMessage(val json: JSONObject) {
    val type: String get() = json.optString("type")
    val from: String? get() = json.optString("from").ifBlank { null }
    val error: String? get() = json.optString("error").ifBlank { null }
    val accepted: Boolean get() = json.optBoolean("accepted", false)
    val sdp: JSONObject? get() = json.optJSONObject("sdp")
    val candidate: JSONObject? get() = json.optJSONObject("candidate")
}

interface SignalingListener {
    fun onOpen()
    fun onMessage(m: SignalMessage)
    fun onClosed(reason: String)
}

/**
 * WebSocket ke server signaling Go. Protokol: `hello` → `pair` →
 * `pair-response` → `offer`/`answer`/`ice` → `bye`.
 */
class Signaling(
    private val deviceId: String,
    private val listener: SignalingListener,
) {
    private var ws: WebSocket? = null
    @Volatile var open = false
        private set

    fun connect(token: String) {
        val url = "${BuildConfig.SIGNALING_URL}?id=$deviceId&role=client&token=$token"
        ws = Api.http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                open = true
                send(JSONObject().put("type", "hello").put("to", deviceId).put("reason", "client"))
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                listener.onMessage(SignalMessage(JSONObject(text)))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                open = false
                listener.onClosed("ditutup $code $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                open = false
                listener.onClosed(t.message ?: "soket gagal")
            }
        })
    }

    fun pair(hostId: String, pin: String, name: String) = send(
        JSONObject().put("type", "pair").put("to", hostId).put("pin", pin)
            .put("name", name).put("platform", "android"),
    )

    fun offer(hostId: String, sdp: String) = send(
        JSONObject().put("type", "offer").put("to", hostId)
            .put("sdp", JSONObject().put("type", "offer").put("sdp", sdp)),
    )

    fun ice(hostId: String, candidate: String, sdpMid: String?, index: Int) = send(
        JSONObject().put("type", "ice").put("to", hostId).put(
            "candidate",
            JSONObject().put("candidate", candidate).put("sdpMid", sdpMid).put("sdpMLineIndex", index),
        ),
    )

    fun bye(hostId: String) = send(JSONObject().put("type", "bye").put("to", hostId))

    fun list() = send(JSONObject().put("type", "list"))

    /** Tanya status online untuk ID host yang sudah dikenal; jawaban `presence` berisi yang online. */
    fun presence(ids: Collection<String>) = send(JSONObject().put("type", "presence").put("ids", JSONArray(ids.toList())))

    fun close() {
        open = false
        ws?.close(1000, "selesai")
        ws = null
    }

    private fun send(o: JSONObject) {
        ws?.send(o.toString())
    }

    companion object {
        fun normalizeId(id: String) = id.replace(Regex("[\\s-]"), "")
    }
}
