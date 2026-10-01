package id.xyverse.xydesk.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Cuplikan terakhir layar host per ID: JPEG di penyimpanan internal + cache memori,
 * dibaca di luar thread UI supaya daftar tidak tersendat saat digulir.
 */
object Previews {
    private val memory = LruCache<String, ImageBitmap>(12)

    private fun dir(context: Context) = File(context.filesDir, "previews").apply { mkdirs() }

    fun file(context: Context, host: String): File = File(dir(context), Presence.hostKey(host) + ".jpg")

    fun save(context: Context, host: String, bitmap: Bitmap) {
        val tmp = File(dir(context), "tmp.jpg")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        tmp.renameTo(file(context, host))
        memory.remove(Presence.hostKey(host))
    }

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
        memory.evictAll()
    }

    suspend fun load(context: Context, host: String): ImageBitmap? {
        val key = Presence.hostKey(host)
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = file(context, host)
            if (!f.exists()) null
            else BitmapFactory.decodeFile(f.path)?.asImageBitmap()?.also { memory.put(key, it) }
        }
    }

    /** Dipakai kartu perangkat: null sampai cuplikan terbaca; ikut berubah saat file diganti. */
    @Composable
    fun rememberPreview(host: String, tick: Any? = null): State<ImageBitmap?> {
        val ctx = LocalContext.current
        return produceState<ImageBitmap?>(null, host, tick) { value = load(ctx, host) }
    }
}
