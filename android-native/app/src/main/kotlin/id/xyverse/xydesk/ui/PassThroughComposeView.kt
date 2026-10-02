package id.xyverse.xydesk.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.platform.ComposeView

/** Compose di atas video: sentuhan yang tidak mengenai kontrol diteruskan ke permukaan di bawah. */
class PassThroughComposeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ComposeView(context, attrs) {
    var behind: View? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (super.dispatchTouchEvent(ev)) return true
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
