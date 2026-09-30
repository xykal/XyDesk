package id.xyverse.xydesk.ui

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import id.xyverse.xyadapt.Act
import id.xyverse.xyadapt.Trackpad
import id.xyverse.xyadapt.TrackpadConfig
import id.xyverse.xydesk.core.StreamXy

/**
 * Menjembatani MotionEvent ke mesin gestur `libxyadapt` (mode trackpad) atau
 * ke koordinat absolut 0..1 (mode sentuh langsung: ketuk klik kiri, tahan klik kanan).
 */
class SessionTouch(
    config: TrackpadConfig,
    private val send: (ByteArray) -> Unit,
    private val haptic: () -> Unit,
) {
    var directTouch = false
    private val handler = Handler(Looper.getMainLooper())
    private val pad = Trackpad(config) { apply(it) }
    private val hold = Runnable { pad.tick(now()) }
    private var downAt = 0L
    private var moved = false
    private var lastX = 0f
    private var lastY = 0f

    var config: TrackpadConfig
        get() = pad.config
        set(v) { pad.config = v }

    fun onTouch(view: View, e: MotionEvent): Boolean = if (directTouch) direct(view, e) else trackpad(e)

    private fun trackpad(e: MotionEvent): Boolean {
        val t = now()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pad.down(t, e.x, e.y)
                handler.postDelayed(hold, pad.config.holdRightClickMs + 10)
            }
            MotionEvent.ACTION_POINTER_DOWN -> { pad.pointerDown(); handler.removeCallbacks(hold) }
            MotionEvent.ACTION_POINTER_UP -> pad.pointerUp()
            MotionEvent.ACTION_MOVE -> pad.move(t, e.x, e.y)
            MotionEvent.ACTION_UP -> { handler.removeCallbacks(hold); pad.up(t) }
            MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(hold); pad.cancel() }
        }
        return true
    }

    private fun direct(view: View, e: MotionEvent): Boolean {
        if (e.pointerCount > 1) return true
        val nx = (e.x / view.width.coerceAtLeast(1)).coerceIn(0f, 1f)
        val ny = (e.y / view.height.coerceAtLeast(1)).coerceIn(0f, 1f)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downAt = now(); moved = false; lastX = e.x; lastY = e.y
                send(StreamXy.moveAbs(nx, ny))
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - lastX; val dy = e.y - lastY
                if (dx * dx + dy * dy > 36f) moved = true
                send(StreamXy.moveAbs(nx, ny))
            }
            MotionEvent.ACTION_UP -> {
                send(StreamXy.moveAbs(nx, ny))
                if (!moved) click(if (now() - downAt > 420) 1 else 0)
            }
        }
        return true
    }

    private fun apply(act: Act) {
        when (act) {
            is Act.MoveRel -> send(StreamXy.moveRel(act.dx, act.dy))
            is Act.Button -> send(StreamXy.button(act.button, act.down))
            is Act.Click -> click(act.button)
            is Act.Scroll -> send(StreamXy.scroll(act.dx, act.dy))
            Act.Haptic -> haptic()
        }
    }

    private fun click(button: Int) {
        send(StreamXy.button(button, true))
        handler.postDelayed({ send(StreamXy.button(button, false)) }, 40)
    }

    fun release() = handler.removeCallbacksAndMessages(null)

    private fun now() = System.currentTimeMillis()
}
