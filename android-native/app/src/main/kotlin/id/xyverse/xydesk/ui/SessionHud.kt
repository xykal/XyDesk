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
    /** Latar pill gelap membulat (gaya pusat kendali desktop) + padding rapat. */
    fun pill(view: android.widget.TextView) {
        val d = android.graphics.drawable.GradientDrawable()
        d.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        d.cornerRadius = view.resources.displayMetrics.density * 14f
        d.setColor(0x61000000) // hitam ~38% — terbaca di video terang & gelap
        view.background = d
        view.setShadowLayer(0f, 0f, 0f, 0) // bayangan teks tidak perlu lagi
        val h = (view.resources.displayMetrics.density * 11).toInt()
        val v = (view.resources.displayMetrics.density * 8).toInt()
        view.setPadding(h, v, h, v)
    }

    fun render(items: List<Pair<String, String>>): CharSequence {
        val sb = SpannableStringBuilder()
        items.forEachIndexed { i, (label, value) ->
            if (i > 0) sb.append("\n")
            val a = sb.length
            sb.append(label)
            sb.setSpan(RelativeSizeSpan(0.62f), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(0xD9FFFFFF.toInt()), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("\n")
            val v = sb.length
            sb.append(value)
            sb.setSpan(StyleSpan(Typeface.BOLD), v, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }
}
