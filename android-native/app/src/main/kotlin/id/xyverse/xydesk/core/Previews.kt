package id.xyverse.xydesk.core

import android.content.Context
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
 * Wallpaper terakhir host per ID: JPEG di penyimpanan internal + cache memori,
 * dibaca di luar thread UI supaya daftar tidak tersendat saat digulir.
 */
object Previews {
    private val memory = LruCache<String, ImageBitmap>(12)

    private fun dir(context: Context) = File(context.filesDir, "previews").apply { mkdirs() }

    fun wallpaperFile(context: Context, host: String): File = File(dir(context), Presence.hostKey(host) + ".wall.jpg")

    fun saveJpeg(context: Context, host: String, bytes: ByteArray) {
        if (!XyPreview.jpegOk(bytes)) return
        File(dir(context), "w.jpg").apply { writeBytes(bytes); renameTo(wallpaperFile(context, host)) }
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
            val f = wallpaperFile(context, host).takeIf { it.exists() }
            if (f == null) null
            else BitmapFactory.decodeFile(f.path)?.asImageBitmap()?.also { memory.put(key, it) }
        }
    }

    /** Dipakai kartu perangkat: null sampai wallpaper terbaca; ikut berubah saat file diganti. */
    @Composable
    fun rememberPreview(host: String, tick: Any? = null): State<ImageBitmap?> {
        val ctx = LocalContext.current
        return produceState<ImageBitmap?>(null, host, tick) { value = load(ctx, host) }
    }
}
