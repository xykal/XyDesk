package id.xyverse.xydesk.ui

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan

/**
 * HUD pojok kiri atas: tiap item ditumpuk ke bawah, label kecil lalu nilainya
 * di baris berikutnya. Tanpa latar, cukup bayangan teks dari layout.
 */
object SessionHud {
    fun render(items: List<Pair<String, String>>): CharSequence {
        val sb = SpannableStringBuilder()
        items.forEachIndexed { i, (label, value) ->
            if (i > 0) sb.append("\n")
            val a = sb.length
            sb.append(label)
            sb.setSpan(RelativeSizeSpan(0.62f), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(0xB3FFFFFF.toInt()), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("\n")
            val v = sb.length
            sb.append(value)
            sb.setSpan(StyleSpan(Typeface.BOLD), v, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }
}
