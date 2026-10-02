package id.xyverse.xydesk.ui

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice

/**
 * Deteksi HID eksternal (USB OTG atau Bluetooth): keyboard alfabet,
 * mouse, gamepad, joystick. Bukan layar sentuh atau tombol volume HP.
 */
class HidMonitor(context: Context, private val onChange: (HidPresence) -> Unit) : InputManager.InputDeviceListener {
    private val im = context.getSystemService(InputManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    data class HidPresence(
        val keyboard: Boolean = false,
        val mouse: Boolean = false,
        val gamepad: Boolean = false,
    ) {
        val any get() = keyboard || mouse || gamepad
        fun label(): String = buildList {
            if (keyboard) add("keyboard")
            if (mouse) add("mouse")
            if (gamepad) add("gamepad")
        }.joinToString(" + ").ifEmpty { "tidak ada" }
    }

    fun start() {
        im?.registerInputDeviceListener(this, handler)
        emit()
    }

    fun stop() {
        im?.unregisterInputDeviceListener(this)
    }

    override fun onInputDeviceAdded(deviceId: Int) = emit()
    override fun onInputDeviceRemoved(deviceId: Int) = emit()
    override fun onInputDeviceChanged(deviceId: Int) = emit()

    fun current(): HidPresence = scan()

    private fun emit() = onChange(scan())

    private fun scan(): HidPresence {
        var k = false
        var m = false
        var g = false
        InputDevice.getDeviceIds().forEach { id ->
            val d = InputDevice.getDevice(id) ?: return@forEach
            if (d.isVirtual) return@forEach
            val s = d.sources
            if (s and InputDevice.SOURCE_MOUSE != 0 || s and InputDevice.SOURCE_MOUSE_RELATIVE != 0) m = true
            if (s and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK) != 0) g = true
            if (s and InputDevice.SOURCE_KEYBOARD != 0 && d.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC) k = true
        }
        return HidPresence(k, m, g)
    }
}
