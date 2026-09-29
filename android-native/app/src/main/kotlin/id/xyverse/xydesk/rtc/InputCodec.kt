package id.xyverse.xydesk.rtc

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Enkoder biner kanal `input` — cermin `host/src/input.rs::decode`. */
object InputCodec {
    private const val MOUSE_MOVE_REL: Byte = 0x01
    private const val MOUSE_MOVE_ABS: Byte = 0x02
    private const val MOUSE_BUTTON: Byte = 0x03
    private const val SCROLL: Byte = 0x04
    private const val KEY: Byte = 0x05
    private const val TEXT: Byte = 0x06
    private const val VIDEO_QUALITY: Byte = 0x0A

    private fun buf(n: Int, tag: Byte): ByteBuffer =
        ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN).put(tag)

    fun moveRel(dx: Int, dy: Int): ByteArray =
        buf(5, MOUSE_MOVE_REL).putShort(dx.toShort()).putShort(dy.toShort()).array()

    /** x, y ternormalisasi 0..1 pada layar host. */
    fun moveAbs(x: Float, y: Float): ByteArray {
        val nx = (x.coerceIn(0f, 1f) * 65535).toInt()
        val ny = (y.coerceIn(0f, 1f) * 65535).toInt()
        return buf(5, MOUSE_MOVE_ABS).putShort(nx.toShort()).putShort(ny.toShort()).array()
    }

    /** button: 0 kiri, 1 kanan, 2 tengah. */
    fun button(button: Int, down: Boolean): ByteArray =
        buf(3, MOUSE_BUTTON).put(button.toByte()).put(if (down) 1 else 0).array()

    fun scroll(dx: Int, dy: Int): ByteArray =
        buf(5, SCROLL).putShort(dx.toShort()).putShort(dy.toShort()).array()

    fun key(vk: Int, down: Boolean): ByteArray =
        buf(4, KEY).putShort(vk.toShort()).put(if (down) 1 else 0).array()

    fun text(s: String): ByteArray {
        val bytes = s.toByteArray(Charsets.UTF_8)
        return buf(1 + bytes.size, TEXT).put(bytes).array()
    }

    fun quality(preset: Int): ByteArray = buf(2, VIDEO_QUALITY).put(preset.toByte()).array()
}
