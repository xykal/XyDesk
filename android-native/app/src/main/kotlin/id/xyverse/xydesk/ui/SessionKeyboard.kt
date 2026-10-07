package id.xyverse.xydesk.ui

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import id.xyverse.xydesk.core.KeyMap
import id.xyverse.xydesk.core.StreamXy

/** Keyboard layar (EditText tak terlihat menampung IME), tombol cepat, dan keyboard fisik. */
class SessionKeyboard(private val context: Context, private val sink: EditText, private val send: (ByteArray) -> Unit) {
    private var mirror = ""
    private var programmatic = false

    init {
        sink.setRawInputType(
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT,
        )
        sink.isSingleLine = false
        sink.showSoftInputOnFocus = true
        sink.setOnKeyListener { _, code, ev ->
            if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            val vk = when (code) {
                KeyEvent.KEYCODE_DEL -> 0x08
                KeyEvent.KEYCODE_ENTER -> 0x0D
                KeyEvent.KEYCODE_TAB -> 0x09
                KeyEvent.KEYCODE_ESCAPE -> 0x1B
                else -> return@setOnKeyListener false
            }
            tap(vk)
            true
        }
        sink.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (programmatic) return
                val next = s?.toString().orEmpty()
                val common = commonPrefix(mirror, next)
                repeat(mirror.length - common) { tap(0x08) }
                if (next.length > common) send(StreamXy.text(next.substring(common)))
                mirror = next
                if (mirror.length > 240) {
                    programmatic = true
                    sink.setText("")
                    programmatic = false
                    mirror = ""
                }
            }
        })
    }

    fun toggle() {
        programmatic = true
        sink.setText("")
        programmatic = false
        mirror = ""
        sink.requestFocus()
        context.getSystemService(InputMethodManager::class.java).showSoftInput(sink, InputMethodManager.SHOW_IMPLICIT)
    }

    fun quick(combo: QuickKey) {
        when (combo) {
            QuickKey.ESC -> tap(0x1B)
            QuickKey.TAB -> tap(0x09)
            QuickKey.WIN -> tap(0x5B)
            QuickKey.ENTER -> tap(0x0D)
            QuickKey.COPY -> chord(0xA2, 0x43)
            QuickKey.PASTE -> chord(0xA2, 0x56)
            QuickKey.UNDO -> chord(0xA2, 0x5A)
            QuickKey.SAVE -> chord(0xA2, 0x53)
            QuickKey.ALT_TAB -> chord(0xA4, 0x09)
            QuickKey.ALT_F4 -> chord(0xA4, 0x73)
            QuickKey.F11 -> tap(0x7A)
            QuickKey.PRTSC -> tap(0x2C)
            QuickKey.CAD -> chord(0xA2, 0xA4, 0x2E)
        }
    }

    /** Keyboard fisik/Bluetooth: VK langsung (tekan & lepas, termasuk modifier). */
    fun physical(event: KeyEvent): Boolean {
        val vk = KeyMap.vk(event.keyCode) ?: return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0 || vk !in 0xA0..0xA5) send(StreamXy.key(vk, true))
            KeyEvent.ACTION_UP -> send(StreamXy.key(vk, false))
        }
        return true
    }

    private fun tap(vk: Int) {
        send(StreamXy.key(vk, true)); send(StreamXy.key(vk, false))
    }

    private fun chord(vararg vks: Int) {
        vks.forEach { send(StreamXy.key(it, true)) }
        vks.reversed().forEach { send(StreamXy.key(it, false)) }
    }

    private fun commonPrefix(a: String, b: String): Int {
        val max = minOf(a.length, b.length)
        var i = 0
        while (i < max && a[i] == b[i]) i++
        return i
    }
}
