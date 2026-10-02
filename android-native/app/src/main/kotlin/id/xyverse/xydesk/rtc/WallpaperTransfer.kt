package id.xyverse.xydesk.rtc

import android.util.Base64
import id.xyverse.xydesk.core.XyPreview
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/** Permintaan wallpaper Windows (0x0D) + rakit potongan JSON dari host. */
class WallpaperTransfer {
    private val next = AtomicInteger(Random.nextInt())
    private var id = 0
    private val chunks = ArrayList<String>()
    private var total = 0
    private var size = 0
    var onJpeg: ((ByteArray) -> Unit)? = null

    fun request(): ByteArray {
        chunks.clear(); total = 0; size = 0
        id = next.incrementAndGet()
        val b = ByteArray(5)
        b[0] = 0x0d
        b[1] = (id and 0xff).toByte()
        b[2] = (id shr 8 and 0xff).toByte()
        b[3] = (id shr 16 and 0xff).toByte()
        b[4] = (id shr 24 and 0xff).toByte()
        return b
    }

    fun receiveJson(text: String) {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return
        if (o.optInt("id") != id) return
        when (o.optString("type")) {
            "wallpaper-error" -> return
            "wallpaper" -> {
                val t = o.optInt("total")
                val idx = o.optInt("index")
                val data = o.optString("data")
                if (t < 1 || t > 22 || idx != chunks.size || data.length > 16384) return
                if (total != 0 && total != t) return
                total = t
                size += data.length
                if (size > 350_000) return
                chunks.add(data)
                if (chunks.size == total) {
                    val raw = runCatching { Base64.decode(chunks.joinToString(""), Base64.DEFAULT) }.getOrNull() ?: return
                    if (XyPreview.jpegOk(raw)) onJpeg?.invoke(raw)
                    chunks.clear()
                }
            }
        }
    }
}
