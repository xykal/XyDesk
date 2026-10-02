package id.xyverse.xydesk.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView

/**
 * Overlay Compose di atas video. `ComposeView` final, jadi ini FrameLayout
 * yang meneruskan sentuhan yang tidak mengenai kontrol ke permukaan di bawah.
 */
class PassThroughComposeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    var behind: View? = null
    private val compose = ComposeView(context).also {
        addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setContent(content: @Composable () -> Unit) = compose.setContent(content)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (compose.dispatchTouchEvent(ev)) return true
        val target = behind ?: return false
        val a = IntArray(2)
        val b = IntArray(2)
        getLocationOnScreen(a)
        target.getLocationOnScreen(b)
        val copy = MotionEvent.obtain(ev)
        copy.offsetLocation((a[0] - b[0]).toFloat(), (a[1] - b[1]).toFloat())
        val handled = target.dispatchTouchEvent(copy)
        copy.recycle()
        return handled
    }
}
