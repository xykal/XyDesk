package id.xyverse.xydesk.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import id.xyverse.xydesk.core.StreamXy
import id.xyverse.xydesk.core.XyHid

/**
 * Keyboard/mouse Bluetooth dan gamepad USB/BT: gerak, klik, scroll, analog
 * kiri = WASD (deadzone di libxyhid).
 */
class SessionHid(private val send: (ByteArray) -> Unit) {
    private val dirHeld = BooleanArray(4)
    private val dirVk = intArrayOf(0x57, 0x53, 0x41, 0x44) // W S A D

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
        val vk = GAMEPAD_VK[event.keyCode] ?: return false
        send(StreamXy.key(vk, event.action == KeyEvent.ACTION_DOWN))
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
        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)
        val s = XyHid.stick(x, y, 0.18f)
        val rx = event.getAxisValue(MotionEvent.AXIS_Z)
        val ry = event.getAxisValue(MotionEvent.AXIS_RZ)
        val look = XyHid.stick(rx, ry, 0.18f)
        if (look[0] != 0f || look[1] != 0f) {
            send(StreamXy.moveRel((look[0] * 18).toInt(), (look[1] * 18).toInt()))
        }
        for (i in 0..3) {
            val on = s[i + 2] >= 0.5f
            if (on != dirHeld[i]) {
                dirHeld[i] = on
                send(StreamXy.key(dirVk[i], on))
            }
        }
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        if (hatX != 0f || hatY != 0f) {
            if (hatY < 0) send(StreamXy.key(0x26, true)).also { send(StreamXy.key(0x26, false)) }
            if (hatY > 0) send(StreamXy.key(0x28, true)).also { send(StreamXy.key(0x28, false)) }
            if (hatX < 0) send(StreamXy.key(0x25, true)).also { send(StreamXy.key(0x25, false)) }
            if (hatX > 0) send(StreamXy.key(0x27, true)).also { send(StreamXy.key(0x27, false)) }
        }
        return true
    }

    private fun mouseButton(event: MotionEvent): Int = when {
        event.buttonState and MotionEvent.BUTTON_SECONDARY != 0 -> 1
        event.buttonState and MotionEvent.BUTTON_TERTIARY != 0 -> 2
        else -> 0
    }

    companion object {
        private val GAMEPAD_VK = mapOf(
            KeyEvent.KEYCODE_BUTTON_A to 0x20,
            KeyEvent.KEYCODE_BUTTON_B to 0x1B,
            KeyEvent.KEYCODE_BUTTON_X to 0x51,
            KeyEvent.KEYCODE_BUTTON_Y to 0x0D,
            KeyEvent.KEYCODE_BUTTON_L1 to 0xA0,
            KeyEvent.KEYCODE_BUTTON_R1 to 0x0D,
            KeyEvent.KEYCODE_BUTTON_THUMBL to 0xA2,
            KeyEvent.KEYCODE_BUTTON_THUMBR to 0xA4,
            KeyEvent.KEYCODE_BUTTON_START to 0x5B,
            KeyEvent.KEYCODE_BUTTON_SELECT to 0x09,
            KeyEvent.KEYCODE_BUTTON_MODE to 0x5B,
        )
    }
}
