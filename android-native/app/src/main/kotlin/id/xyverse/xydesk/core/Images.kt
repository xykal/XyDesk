package id.xyverse.xydesk.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import id.xyverse.xydesk.net.Api
import id.xyverse.xydesk.ui.kit.Xy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/** Pemuat gambar kecil: OkHttp + cache memori; cukup untuk sampul berita, tanpa pustaka tambahan. */
object Images {
    private val cache = LruCache<String, Bitmap>(24)

    fun peek(url: String): Bitmap? = cache.get(url)

    @Volatile private var dir: File? = null

    /** Panggil sekali dari Application/Activity supaya gambar juga tersimpan di disk (offline). */
    fun attach(ctx: Context) { dir = File(ctx.cacheDir, "img").apply { mkdirs() } }

    suspend fun load(url: String, maxWidth: Int = 1080): Bitmap? = cache.get(url) ?: withContext(Dispatchers.IO) {
        val file = dir?.let { File(it, sha1(url)) }
        val bytes = file?.takeIf { it.length() > 0 }?.readBytes() ?: runCatching {
            Api.http.newCall(Request.Builder().url(url).build()).execute().use { res -> if (res.isSuccessful) res.body?.bytes() else null }
        }.getOrNull()?.also { b -> runCatching { file?.writeBytes(b) } } ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.also { cache.put(url, it) }
    }

    fun clearDisk() { dir?.listFiles()?.forEach { it.delete() } }

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

@Composable
fun RemoteImage(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    var bmp by remember(url) { mutableStateOf(Images.peek(url)) }
    LaunchedEffect(url) { if (bmp == null && url.isNotBlank()) bmp = Images.load(url) }
    val b = bmp
    if (b != null) Image(b.asImageBitmap(), null, modifier, contentScale = contentScale)
    else Box(modifier.background(Xy.overlay))
}
