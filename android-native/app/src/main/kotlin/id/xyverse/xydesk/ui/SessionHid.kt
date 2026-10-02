package id.xyverse.xydesk.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import id.xyverse.xydesk.core.StreamXy
import id.xyverse.xydesk.core.XyGamepad
import id.xyverse.xydesk.core.XyHid

/**
 * Keyboard/mouse BT/USB OTG tetap SendInput (tanpa .so ekstra).
 * Gamepad fisik dikirim sebagai laporan XInput 0x0E (libxygamepad + libxyhid).
 */
class SessionHid(private val send: (ByteArray) -> Unit) {
    private var buttons = 0
    private var lt = 0
    private var rt = 0
    private var lx = 0
    private var ly = 0
    private var rx = 0
    private var ry = 0

    fun motion(event: MotionEvent, video: View): Boolean {
        val src = event.source
        if (src and InputDevice.SOURCE_MOUSE != 0 || src and InputDevice.SOURCE_MOUSE_RELATIVE != 0) {
            return mouse(event, video)
        }
        if (src and (InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_GAMEPAD) != 0) {
            return stick(event)
        }
        return false
    }

    fun gamepadKey(event: KeyEvent): Boolean {
        if (event.source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK) == 0) return false
        val bit = XyGamepad.xbit(event.keyCode)
        if (bit == 0) return false
        buttons = if (event.action == KeyEvent.ACTION_DOWN) buttons or bit else buttons and bit.inv()
        flush()
        return true
    }

    private fun mouse(event: MotionEvent, video: View): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_MOVE -> {
                val loc = IntArray(2)
                video.getLocationOnScreen(loc)
                val x = ((event.rawX - loc[0]) / video.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                val y = ((event.rawY - loc[1]) / video.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                send(StreamXy.moveAbs(x, y))
                return true
            }
            MotionEvent.ACTION_SCROLL -> {
                val v = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                val h = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
                send(StreamXy.scroll((h * 120).toInt(), (v * 120).toInt()))
                return true
            }
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_DOWN -> {
                send(StreamXy.button(mouseButton(event), true)); return true
            }
            MotionEvent.ACTION_BUTTON_RELEASE, MotionEvent.ACTION_UP -> {
                send(StreamXy.button(mouseButton(event), false)); return true
            }
        }
        return false
    }

    private fun stick(event: MotionEvent): Boolean {
        val left = XyHid.stick(event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y), 0.18f)
        val rz = event.getAxisValue(MotionEvent.AXIS_RZ).takeIf { it != 0f } ?: event.getAxisValue(MotionEvent.AXIS_RY)
        val z = event.getAxisValue(MotionEvent.AXIS_Z).takeIf { it != 0f } ?: event.getAxisValue(MotionEvent.AXIS_RX)
        val right = XyHid.stick(z, rz, 0.18f)
        lx = XyGamepad.axis(left[0])
        ly = XyGamepad.axis(-left[1])
        rx = XyGamepad.axis(right[0])
        ry = XyGamepad.axis(-right[1])
        val l2 = event.getAxisValue(MotionEvent.AXIS_LTRIGGER).let { if (it > 0f) it else event.getAxisValue(MotionEvent.AXIS_BRAKE) }
        val r2 = event.getAxisValue(MotionEvent.AXIS_RTRIGGER).let { if (it > 0f) it else event.getAxisValue(MotionEvent.AXIS_GAS) }
        lt = XyGamepad.trigger(l2)
        rt = XyGamepad.trigger(r2)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        buttons = buttons and 0xFFF0
        if (hatY < -0.5f) buttons = buttons or 0x0001
        if (hatY > 0.5f) buttons = buttons or 0x0002
        if (hatX < -0.5f) buttons = buttons or 0x0004
        if (hatX > 0.5f) buttons = buttons or 0x0008
        flush()
        return true
    }

    private fun flush() {
        send(StreamXy.gamepad(buttons, lt, rt, lx, ly, rx, ry))
    }

    private fun mouseButton(event: MotionEvent): Int = when {
        event.buttonState and MotionEvent.BUTTON_SECONDARY != 0 -> 1
        event.buttonState and MotionEvent.BUTTON_TERTIARY != 0 -> 2
        else -> 0
    }
}
