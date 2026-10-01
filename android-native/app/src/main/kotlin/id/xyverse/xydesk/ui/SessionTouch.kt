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
 * ke koordinat absolut 0..1 (mode sentuh langsung). Trackpad: 1 jari gerak, ketuk klik kiri,
 * ketuk 2 jari klik kanan, geser 2 jari gulir; tanpa cubit dan tanpa tahan-klik-kanan.
 */
class SessionTouch(
    config: TrackpadConfig,
    private val send: (ByteArray) -> Unit,
    private val haptic: () -> Unit,
) {
    var directTouch = false
    private val handler = Handler(Looper.getMainLooper())
    private val pad = Trackpad(config) { apply(it) }
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
            MotionEvent.ACTION_DOWN -> pad.down(t, e.x, e.y)
            MotionEvent.ACTION_POINTER_DOWN -> pad.pointerDown()
            MotionEvent.ACTION_POINTER_UP -> pad.pointerUp()
            MotionEvent.ACTION_MOVE -> pad.move(t, e.x, e.y)
            MotionEvent.ACTION_UP -> pad.up(t)
            MotionEvent.ACTION_CANCEL -> pad.cancel()
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
                if (!moved) click(0)
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
            is Act.Zoom -> {
                send(StreamXy.key(VK_CTRL, true))
                send(StreamXy.scroll(0, act.steps * 120))
                send(StreamXy.key(VK_CTRL, false))
            }
            is Act.Swipe3 -> when (act.dir) {
                1 -> chord(VK_ALT, VK_TAB)
                -1 -> { send(StreamXy.key(VK_ALT, true)); send(StreamXy.key(VK_SHIFT, true)); send(StreamXy.key(VK_TAB, true)); send(StreamXy.key(VK_TAB, false)); send(StreamXy.key(VK_SHIFT, false)); send(StreamXy.key(VK_ALT, false)) }
                else -> chord(VK_WIN, VK_TAB)
            }
            Act.Haptic -> haptic()
        }
    }

    private fun click(button: Int) {
        send(StreamXy.button(button, true))
        handler.postDelayed({ send(StreamXy.button(button, false)) }, 40)
    }

    private fun chord(vararg vks: Int) {
        vks.forEach { send(StreamXy.key(it, true)) }
        vks.reversed().forEach { send(StreamXy.key(it, false)) }
    }

    fun release() = handler.removeCallbacksAndMessages(null)

    private companion object {
        const val VK_TAB = 0x09
        const val VK_SHIFT = 0xA0
        const val VK_CTRL = 0xA2
        const val VK_ALT = 0xA4
        const val VK_WIN = 0x5B
    }

    private fun now() = System.currentTimeMillis()
}
