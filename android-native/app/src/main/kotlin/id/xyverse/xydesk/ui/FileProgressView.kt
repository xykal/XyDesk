package id.xyverse.xydesk.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Baris kemajuan kiriman berkas: satu kapsul gelap di atas layar dengan
 * teks dan tombol Batal.
 *
 * Sengaja sebuah View biasa, bukan Compose: ia harus bisa ditempel lewat
 * `addContentView` di atas permukaan video tanpa menyentuh tata letak yang
 * sudah ada, dan isinya hanya satu baris teks yang berubah.
 */
class FileProgressView(context: Context) : FrameLayout(context) {
    private val label = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 13f
        maxLines = 2
    }

    private val cancel = TextView(context).apply {
        text = "Batal"
        setTextColor(Color.parseColor("#FF8A80"))
        textSize = 13f
        setPadding(24, 0, 0, 0)
    }

    var onCancel: () -> Unit = {}

    init {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(28, 18, 28, 18)
            background = GradientDrawable().apply {
                cornerRadius = 28f
                setColor(Color.parseColor("#D9000000"))
            }
            addView(label)
            addView(cancel)
        }
        addView(
            row,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                .apply { topMargin = 48 },
        )
        cancel.setOnClickListener { onCancel() }
        visibility = GONE
    }

    /** `cancellable=false` dipakai untuk pesan akhir yang hilang sendiri. */
    fun show(text: String, cancellable: Boolean) {
        label.text = text
        cancel.visibility = if (cancellable) View.VISIBLE else View.GONE
        visibility = VISIBLE
    }

    fun hide() {
        visibility = GONE
    }
}
