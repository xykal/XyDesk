package id.xyverse.xydesk.ui

import android.content.Context
import android.view.MotionEvent
import android.view.View
import id.xyverse.xyadapt.RelMotion
import id.xyverse.xydesk.core.StreamXy

/**
 * View tak terlihat yang memegang tangkapan pointer (pointer capture).
 *
 * Saat tangkapan aktif, kursor HP hilang dan Android mengirim **delta**
 * lewat [onCapturedPointerEvent] — bukan posisi. Delta itu diteruskan
 * sebagai `0x01 MOUSE_MOVE_REL`, opcode yang sudah ada di host sejak lama
 * tetapi tidak pernah dipakai aplikasi: sebelumnya mouse fisik selalu
 * dikirim sebagai posisi absolut, sehingga gerak berhenti begitu kursor HP
 * menyentuh tepi layar. Untuk game yang memutar pandangan dengan gerak tak
 * terbatas, itu berarti pandangan ikut berhenti.
 *
 * View ini hanya perantara. Matematika pecahan, penjepitan, dan ambang satu
 * piksel ada di [RelMotion] yang teruji di JVM.
 */
class CapturedMouseView(context: Context, private val send: (ByteArray) -> Unit) : View(context) {
    private val motion = RelMotion()
    private var buttons = 0

    /** Pengali gerak; dipakai bersama pengaturan kecepatan trackpad. */
    var sensitivity: Float = 1f

    /** Dipanggil saat pengguna ingin keluar dari tangkapan (Esc dua kali). */
    var onEscape: (() -> Unit)? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        // Tidak menggambar apa pun; ia hanya perlu bisa memegang fokus.
        setWillNotDraw(true)
    }

    /** Meminta tangkapan; aman dipanggil berulang. */
    fun capture() {
        if (!hasWindowFocus()) return
        requestFocus()
        requestPointerCapture()
    }

    /** Melepas tangkapan dan membuang sisa pecahan gerak. */
    fun release() {
        motion.reset()
        releaseButtons()
        releasePointerCapture()
    }

    override fun onCapturedPointerEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                // Sumbu relatif kadang kosong pada beberapa perangkat; getX/getY
                // pada kejadian tertangkap sudah berupa delta, jadi dipakai
                // sebagai cadangan agar mouse tidak diam sama sekali.
                val dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X).takeIf { it != 0f } ?: event.x
                val dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y).takeIf { it != 0f } ?: event.y
                motion.feed(dx, dy, sensitivity)?.let { send(StreamXy.moveRel(it[0], it[1])) }
            }
            MotionEvent.ACTION_BUTTON_PRESS -> press(event.actionButton, true)
            MotionEvent.ACTION_BUTTON_RELEASE -> press(event.actionButton, false)
            MotionEvent.ACTION_SCROLL -> {
                val v = RelMotion.wheel(event.getAxisValue(MotionEvent.AXIS_VSCROLL))
                val h = RelMotion.wheel(event.getAxisValue(MotionEvent.AXIS_HSCROLL))
                if (v != 0 || h != 0) send(StreamXy.scroll(h, v))
            }
            else -> return false
        }
        return true
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        // Kehilangan fokus jendela melepas tangkapan secara diam-diam di
        // sisi sistem; tanpa meminta ulang, mouse berhenti bekerja setelah
        // notifikasi atau panggilan masuk tanpa sebab yang terlihat.
        if (hasWindowFocus && wantCapture) capture() else releaseButtons()
    }

    /** Diisi pemanggil; menentukan apakah fokus yang kembali memicu tangkapan. */
    var wantCapture: Boolean = false

    private fun press(button: Int, down: Boolean) {
        val index = when (button) {
            MotionEvent.BUTTON_SECONDARY -> 1
            MotionEvent.BUTTON_TERTIARY -> 2
            MotionEvent.BUTTON_BACK -> 3
            MotionEvent.BUTTON_FORWARD -> 4
            else -> 0
        }
        val bit = 1 shl index
        buttons = if (down) buttons or bit else buttons and bit.inv()
        send(StreamXy.button(index, down))
    }

    /**
     * Melepas semua tombol yang masih tercatat ditekan. Tanpa ini, tombol
     * kiri yang sedang ditahan saat tangkapan dilepas akan tetap "ditekan"
     * di PC — kursor menyeret apa pun yang disentuhnya sampai sesi berakhir.
     */
    private fun releaseButtons() {
        if (buttons == 0) return
        for (i in 0..4) {
            if (buttons and (1 shl i) != 0) send(StreamXy.button(i, false))
        }
        buttons = 0
    }
}
