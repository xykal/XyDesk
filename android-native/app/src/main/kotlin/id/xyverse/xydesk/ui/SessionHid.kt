package id.xyverse.xydesk.ui

import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import id.xyverse.xyadapt.PadAction
import id.xyverse.xyadapt.PadKbm
import id.xyverse.xyadapt.PadProfile
import id.xyverse.xyadapt.PadState
import id.xyverse.xydesk.core.StreamXy
import id.xyverse.xydesk.core.XyGamepad
import id.xyverse.xydesk.core.XyHid

/**
 * Keyboard/mouse BT/USB OTG tetap SendInput (tanpa .so ekstra).
 * Gamepad fisik dikirim sebagai laporan XInput 0x0E (libxygamepad + libxyhid),
 * kecuali bila pemetaan ke keyboard/mouse sedang aktif — lihat [PadKbm].
 */
class SessionHid(private val send: (ByteArray) -> Unit) {
    private var buttons = 0
    private var lt = 0
    private var rt = 0
    private var lx = 0
    private var ly = 0
    private var rx = 0
    private var ry = 0

    // --- jalur pemetaan pad → keyboard/mouse ---
    private val kbm = PadKbm()
    private val ui = Handler(Looper.getMainLooper())
    private var raw = PadState()
    private var kbmActive = false
    private var pumping = false
    private var lastPump = 0L

    /** 0 mati, 1 selalu, 2 otomatis (bawaan). */
    var kbmMode: Int = PadKbm.MODE_AUTO
        set(value) { field = value; sync() }

    /** Telemetri host: ViGEm siap atau tidak. */
    var hostGamepadAvailable: Boolean = true
        set(value) { field = value; sync() }

    /** Dari HidMonitor: ada gamepad fisik menempel atau tidak. */
    var padPresent: Boolean = false
        set(value) { field = value; sync() }

    var profileDesktop: Boolean = false
        set(value) {
            field = value
            dispatch(kbm.setProfile(if (value) PadProfile.DESKTOP else PadProfile.DEFAULT))
        }

    fun mappingActive(): Boolean = kbmActive

    /**
     * Menghitung ulang apakah pemetaan harus aktif. Saat berhenti aktif,
     * semua tombol yang masih ditahan dilepas dan pad virtual dinolkan —
     * kalau tidak, W (atau stik) tertinggal tertekan di PC.
     */
    fun sync() {
        val want = PadKbm.active(kbmMode, padPresent, hostGamepadAvailable)
        if (want == kbmActive) return
        kbmActive = want
        dispatch(kbm.release())
        stopPump()
        if (want) {
            // Lepaskan pad virtual supaya tidak ada tombol XInput tersangkut.
            buttons = 0; lt = 0; rt = 0; lx = 0; ly = 0; rx = 0; ry = 0
            flush()
        }
    }

    /** Dipanggil saat sesi berhenti / aplikasi ke latar. */
    fun releaseAll() {
        dispatch(kbm.release())
        stopPump()
    }

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
        if (kbmActive) {
            raw = raw.copy(buttons = buttons)
            feed()
        } else {
            flush()
        }
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
        val l2 = event.getAxisValue(MotionEvent.AXIS_LTRIGGER).let { if (it > 0f) it else event.getAxisValue(MotionEvent.AXIS_BRAKE) }
        val r2 = event.getAxisValue(MotionEvent.AXIS_RTRIGGER).let { if (it > 0f) it else event.getAxisValue(MotionEvent.AXIS_GAS) }
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        buttons = buttons and 0xFFF0
        if (hatY < -0.5f) buttons = buttons or 0x0001
        if (hatY > 0.5f) buttons = buttons or 0x0002
        if (hatX < -0.5f) buttons = buttons or 0x0004
        if (hatX > 0.5f) buttons = buttons or 0x0008

        if (kbmActive) {
            raw = PadState(buttons, l2, r2, left[0], left[1], right[0], right[1])
            feed()
            return true
        }
        lx = XyGamepad.axis(left[0])
        ly = XyGamepad.axis(-left[1])
        rx = XyGamepad.axis(right[0])
        ry = XyGamepad.axis(-right[1])
        lt = XyGamepad.trigger(l2)
        rt = XyGamepad.trigger(r2)
        flush()
        return true
    }

    private fun flush() {
        send(StreamXy.gamepad(buttons, lt, rt, lx, ly, rx, ry))
    }

    /**
     * Satu suapan ke pemeta. Stik yang ditahan tidak menghasilkan kejadian
     * baru dari Android, jadi selama stik kanan di luar zona mati kita
     * memompa sendiri tiap ~16 ms — tanpa itu pandangan hanya bergerak
     * sekali lalu berhenti walau jempol masih mendorong.
     */
    private fun feed() {
        val now = System.nanoTime()
        val dt = if (lastPump == 0L) 16 else ((now - lastPump) / 1_000_000L).toInt().coerceIn(1, 50)
        lastPump = now
        dispatch(kbm.feed(raw, dt))
        val looking = kotlin.math.hypot(raw.rx, raw.ry) > PadKbm.LOOK_DEADZONE
        if (looking) startPump() else stopPump()
    }

    private val pump = object : Runnable {
        override fun run() {
            if (!kbmActive) { pumping = false; return }
            feed()
            if (pumping) ui.postDelayed(this, 16)
        }
    }

    private fun startPump() {
        if (pumping) return
        pumping = true
        ui.postDelayed(pump, 16)
    }

    private fun stopPump() {
        pumping = false
        ui.removeCallbacks(pump)
        lastPump = 0L
    }

    private fun dispatch(actions: List<PadAction>) {
        actions.forEach { a ->
            when (a) {
                is PadAction.Key -> send(StreamXy.key(a.vk, a.down))
                is PadAction.Button -> send(StreamXy.button(a.index, a.down))
                is PadAction.Move -> send(StreamXy.moveRel(a.dx, a.dy))
                is PadAction.Scroll -> send(StreamXy.scroll(a.x, a.y))
            }
        }
    }

    private fun mouseButton(event: MotionEvent): Int = when {
        event.buttonState and MotionEvent.BUTTON_SECONDARY != 0 -> 1
        event.buttonState and MotionEvent.BUTTON_TERTIARY != 0 -> 2
        else -> 0
    }
}
