package id.xyverse.xydesk.core

import android.content.Context
import android.graphics.Bitmap
import java.io.File

/** Cuplikan terakhir layar host per ID, disimpan sebagai JPEG kecil di penyimpanan internal. */
object Previews {
    private fun dir(context: Context) = File(context.filesDir, "previews").apply { mkdirs() }

    fun file(context: Context, host: String): File = File(dir(context), host.filter(Char::isDigit) + ".jpg")

    fun save(context: Context, host: String, bitmap: Bitmap) {
        val tmp = File(dir(context), "tmp.jpg")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 78, it) }
        tmp.renameTo(file(context, host))
    }

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }
}
