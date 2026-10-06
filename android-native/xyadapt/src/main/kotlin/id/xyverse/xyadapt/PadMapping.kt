package id.xyverse.xyadapt

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pemetaan gamepad fisik → keyboard + mouse.
 *
 * ## Kenapa perlu
 *
 * Jalur biasa mengirim gamepad apa adanya sebagai laporan XInput lewat ViGEm
 * di host. Jalur itu hanya bekerja bila **driver ViGEm benar-benar terpasang**
 * (telemetri host mengirim `gamepad.available=false` beserta alasannya bila
 * tidak), dan bahkan ketika terpasang masih ada seluruh kelas program yang
 * tidak pernah membaca XInput sama sekali: peramban, penjelajah berkas,
 * banyak game lama, dan hampir semua aplikasi kerja. Di situ gamepad di
 * tangan pengguna jadi benda mati, padahal perangkat itulah satu-satunya yang
 * ia pegang.
 *
 * Pemetaan di sini mengubah gamepad menjadi penekanan tombol keyboard dan
 * gerak mouse, yang diterima **setiap** program Windows tanpa driver apa pun —
 * jalur `SendInput` yang sama dengan keyboard Bluetooth.
 *
 * ## Yang membuatnya tidak sepele
 *
 * - **Stik itu analog, tombol itu biner.** Menekan W pada ambang yang persis
 *   sama dengan melepasnya membuat tombol bergetar (menekan-melepas puluhan
 *   kali per detik) ketika jempol diam di dekat ambang. Karena itu ambang
 *   nyala dan ambang mati dibuat berbeda — histeresis.
 * - **Gerak mouse harus kontinu.** Stik mengirim posisi, mouse butuh delta;
 *   deltanya pecahan dan harus diakumulasi, kalau tidak gerak pelan hilang
 *   sama sekali. `RelMotion` yang sama dengan tangkapan pointer dipakai lagi.
 * - **Tombol yang tersangkut.** Bila sesi putus atau pemetaan dimatikan saat
 *   stik sedang didorong, W di PC akan tertekan selamanya. `release()` wajib
 *   mengeluarkan semua pelepasan yang tertunda.
 *
 * Seluruh berkas ini murni: tanpa Android, tanpa soket, bisa diuji penuh.
 */
object PadBits {
    const val DPAD_UP = 0x0001
    const val DPAD_DOWN = 0x0002
    const val DPAD_LEFT = 0x0004
    const val DPAD_RIGHT = 0x0008
    const val START = 0x0010
    const val BACK = 0x0020
    const val L3 = 0x0040
    const val R3 = 0x0080
    const val LB = 0x0100
    const val RB = 0x0200
    const val A = 0x1000
    const val B = 0x2000
    const val X = 0x4000
    const val Y = 0x8000

    /** Urutan tetap, dipakai saat membandingkan dua bitmask. */
    val ALL = intArrayOf(
        DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, START, BACK,
        L3, R3, LB, RB, A, B, X, Y,
    )

    fun name(bit: Int): String = when (bit) {
        DPAD_UP -> "DPAD_UP"
        DPAD_DOWN -> "DPAD_DOWN"
        DPAD_LEFT -> "DPAD_LEFT"
        DPAD_RIGHT -> "DPAD_RIGHT"
        START -> "START"
        BACK -> "BACK"
        L3 -> "L3"
        R3 -> "R3"
        LB -> "LB"
        RB -> "RB"
        A -> "A"
        B -> "B"
        X -> "X"
        Y -> "Y"
        else -> "0x%04X".format(bit)
    }
}

/** Virtual-key Windows yang dipakai profil bawaan. */
object Vk {
    const val BACKSPACE = 0x08
    const val TAB = 0x09
    const val ENTER = 0x0D
    const val SHIFT = 0xA0
    const val CTRL = 0xA2
    const val ALT = 0xA4
    const val ESC = 0x1B
    const val SPACE = 0x20
    const val LEFT = 0x25
    const val UP = 0x26
    const val RIGHT = 0x27
    const val DOWN = 0x28
    const val A = 0x41
    const val C = 0x43
    const val D = 0x44
    const val E = 0x45
    const val F = 0x46
    const val Q = 0x51
    const val R = 0x52
    const val S = 0x53
    const val W = 0x57
}

/** Keadaan gamepad mentah satu kejadian. Sumbu dinormalkan ke [-1, 1]. */
data class PadState(
    val buttons: Int = 0,
    val lt: Float = 0f,
    val rt: Float = 0f,
    val lx: Float = 0f,
    val ly: Float = 0f,
    val rx: Float = 0f,
    val ry: Float = 0f,
)

/** Satu perintah untuk dikirim ke host. */
sealed class PadAction {
    data class Key(val vk: Int, val down: Boolean) : PadAction()
    data class Button(val index: Int, val down: Boolean) : PadAction()
    data class Move(val dx: Int, val dy: Int) : PadAction()
    data class Scroll(val x: Int, val y: Int) : PadAction()
}

/**
 * Profil pemetaan. Semua nilai bisa diganti pengguna nanti; bawaannya
 * mengikuti kebiasaan penembak orang-pertama di PC (WASD + mouse look),
 * karena justru di situ gamepad tanpa XInput paling sering mentok.
 */
data class PadProfile(
    /** Bit tombol gamepad → virtual-key. Bit yang tidak ada diabaikan. */
    val keys: Map<Int, Int> = DEFAULT_KEYS,
    /** Stik kiri → empat tombol: atas, bawah, kiri, kanan. */
    val stickKeys: IntArray? = intArrayOf(Vk.W, Vk.S, Vk.A, Vk.D),
    /** Stik kanan → gerak mouse. */
    val look: Boolean = true,
    /** Trigger kiri/kanan → indeks tombol mouse (0 kiri, 1 kanan, 2 tengah). */
    val leftTrigger: Int? = 1,
    val rightTrigger: Int? = 0,
    /** Pengali kecepatan pandangan; dijepit seperti sensitivitas mouse. */
    val sensitivity: Float = 1f,
) {
    companion object {
        val DEFAULT_KEYS: Map<Int, Int> = mapOf(
            PadBits.A to Vk.SPACE,
            PadBits.B to Vk.CTRL,
            PadBits.X to Vk.E,
            PadBits.Y to Vk.R,
            PadBits.LB to Vk.Q,
            PadBits.RB to Vk.SHIFT,
            PadBits.L3 to Vk.C,
            PadBits.R3 to Vk.F,
            PadBits.START to Vk.ESC,
            PadBits.BACK to Vk.TAB,
            PadBits.DPAD_UP to Vk.UP,
            PadBits.DPAD_DOWN to Vk.DOWN,
            PadBits.DPAD_LEFT to Vk.LEFT,
            PadBits.DPAD_RIGHT to Vk.RIGHT,
        )

        /** Profil "kursor": gamepad jadi penunjuk, bukan pemain game. */
        val DESKTOP = PadProfile(
            keys = mapOf(
                PadBits.B to Vk.ESC,
                PadBits.Y to Vk.ENTER,
                PadBits.X to Vk.BACKSPACE,
                PadBits.START to Vk.ENTER,
                PadBits.BACK to Vk.TAB,
                PadBits.DPAD_UP to Vk.UP,
                PadBits.DPAD_DOWN to Vk.DOWN,
                PadBits.DPAD_LEFT to Vk.LEFT,
                PadBits.DPAD_RIGHT to Vk.RIGHT,
            ),
            stickKeys = null,
            look = true,
            leftTrigger = 1,
            rightTrigger = 0,
        )

        val DEFAULT = PadProfile()
    }

    override fun equals(other: Any?): Boolean =
        other is PadProfile && keys == other.keys && look == other.look &&
            leftTrigger == other.leftTrigger && rightTrigger == other.rightTrigger &&
            sensitivity == other.sensitivity &&
            (stickKeys?.toList() ?: emptyList<Int>()) == (other.stickKeys?.toList() ?: emptyList<Int>())

    override fun hashCode(): Int = keys.hashCode() * 31 + (stickKeys?.toList()?.hashCode() ?: 0)
}

/**
 * Penerjemah gamepad → keyboard/mouse. Menyimpan keadaan: tiap suapan hanya
 * mengeluarkan **perubahan**, bukan keadaan penuh, supaya jalur input tidak
 * dibanjiri penekanan ulang 100 kali per detik.
 */
class PadKbm(private var profile: PadProfile = PadProfile.DEFAULT) {
    private val held = LinkedHashSet<Int>()
    private val mouseHeld = LinkedHashSet<Int>()
    private var stickOn = booleanArrayOf(false, false, false, false)
    private val motion = RelMotion()

    fun profile(): PadProfile = profile

    /** Mengganti profil melepas apa pun yang sedang ditahan profil lama. */
    fun setProfile(next: PadProfile): List<PadAction> {
        val out = release()
        profile = next
        return out
    }

    /**
     * Menyuapkan satu keadaan gamepad.
     *
     * @param frameMs jarak waktu dari kejadian sebelumnya; dipakai supaya
     *   kecepatan pandangan tidak bergantung pada laju laporan gamepad
     *   (ada pad 60 Hz dan ada yang 250 Hz).
     */
    fun feed(state: PadState, frameMs: Int = 16): List<PadAction> {
        val out = ArrayList<PadAction>(8)
        buttons(state.buttons, out)
        stick(state.lx, state.ly, out)
        triggers(state.lt, state.rt, out)
        look(state.rx, state.ry, frameMs, out)
        return out
    }

    /**
     * Melepas semua yang sedang ditahan. Wajib dipanggil saat sesi putus,
     * pemetaan dimatikan, atau aplikasi masuk latar — tanpa ini tombol di PC
     * tertinggal dalam keadaan tertekan dan pengguna tidak punya cara
     * melepasnya dari HP.
     */
    fun release(): List<PadAction> {
        val out = ArrayList<PadAction>(held.size + mouseHeld.size)
        held.forEach { out.add(PadAction.Key(it, false)) }
        mouseHeld.forEach { out.add(PadAction.Button(it, false)) }
        held.clear()
        mouseHeld.clear()
        stickOn = booleanArrayOf(false, false, false, false)
        motion.reset()
        return out
    }

    /** Untuk uji dan diagnosa. */
    fun heldKeys(): List<Int> = held.toList()

    fun heldButtons(): List<Int> = mouseHeld.toList()

    private fun buttons(mask: Int, out: MutableList<PadAction>) {
        for (bit in PadBits.ALL) {
            val vk = profile.keys[bit] ?: continue
            key(vk, mask and bit != 0, out)
        }
    }

    private fun stick(x: Float, y: Float, out: MutableList<PadAction>) {
        val map = profile.stickKeys ?: return
        if (map.size < 4) return
        // y pad: negatif = atas, mengikuti konvensi Android.
        val wanted = booleanArrayOf(
            axisOn(-y, stickOn[0]),
            axisOn(y, stickOn[1]),
            axisOn(-x, stickOn[2]),
            axisOn(x, stickOn[3]),
        )
        for (i in 0..3) {
            if (wanted[i] == stickOn[i]) continue
            stickOn[i] = wanted[i]
            key(map[i], wanted[i], out)
        }
    }

    private fun triggers(lt: Float, rt: Float, out: MutableList<PadAction>) {
        profile.leftTrigger?.let { mouse(it, triggerOn(lt, mouseHeld.contains(it)), out) }
        profile.rightTrigger?.let { mouse(it, triggerOn(rt, mouseHeld.contains(it)), out) }
    }

    private fun look(x: Float, y: Float, frameMs: Int, out: MutableList<PadAction>) {
        if (!profile.look) return
        if (!x.isFinite() || !y.isFinite()) return
        val mag = sqrt(x * x + y * y)
        if (mag <= LOOK_DEADZONE) return
        // Buang zona mati lalu regangkan lagi ke 0..1, kalau tidak akan ada
        // lompatan kecepatan tepat saat jempol melewati ambang.
        val unit = ((mag - LOOK_DEADZONE) / (1f - LOOK_DEADZONE)).coerceAtMost(1f)
        // Kurva pangkat: dorongan kecil untuk membidik, dorongan penuh untuk
        // memutar badan. Linear membuat keduanya mustahil sekaligus.
        val speed = unit.pow(LOOK_CURVE) * LOOK_MAX_PX * frameSpan(frameMs)
        val nx = x / mag * speed
        val ny = y / mag * speed
        motion.feed(nx, ny, profile.sensitivity)?.let { out.add(PadAction.Move(it[0], it[1])) }
    }

    private fun key(vk: Int, down: Boolean, out: MutableList<PadAction>) {
        if (down) {
            if (held.add(vk)) out.add(PadAction.Key(vk, true))
        } else {
            if (held.remove(vk)) out.add(PadAction.Key(vk, false))
        }
    }

    private fun mouse(index: Int, down: Boolean, out: MutableList<PadAction>) {
        if (down) {
            if (mouseHeld.add(index)) out.add(PadAction.Button(index, true))
        } else {
            if (mouseHeld.remove(index)) out.add(PadAction.Button(index, false))
        }
    }

    companion object {
        /** Ambang nyala stik kiri, dan ambang mati yang lebih rendah. */
        const val STICK_ON = 0.5f
        const val STICK_OFF = 0.35f
        const val TRIGGER_ON = 0.5f
        const val TRIGGER_OFF = 0.3f
        const val LOOK_DEADZONE = 0.12f
        const val LOOK_CURVE = 2f

        /** Piksel per bingkai 16 ms saat stik didorong penuh, sensitivitas 1. */
        const val LOOK_MAX_PX = 22f

        fun axisOn(value: Float, was: Boolean): Boolean = when {
            !value.isFinite() -> false
            was -> value > STICK_OFF
            else -> value >= STICK_ON
        }

        fun triggerOn(value: Float, was: Boolean): Boolean = when {
            !value.isFinite() -> false
            was -> value > TRIGGER_OFF
            else -> value >= TRIGGER_ON
        }

        /** Bingkai dijepit: laporan yang tertunda lama tidak boleh melempar kursor. */
        fun frameSpan(frameMs: Int): Float = (frameMs.coerceIn(1, 50)) / 16f

        /**
         * Kapan pemetaan ini sebaiknya aktif.
         *
         * Mode 0 mati, 1 selalu, 2 otomatis: hidup hanya bila host memberi
         * tahu bahwa gamepad virtualnya tidak tersedia (ViGEm belum
         * terpasang). Otomatis adalah bawaan karena pengguna yang tidak punya
         * ViGEm tidak akan pernah tahu kenapa padnya diam.
         */
        const val MODE_OFF = 0
        const val MODE_ON = 1
        const val MODE_AUTO = 2

        fun active(mode: Int, padPresent: Boolean, hostGamepadAvailable: Boolean): Boolean = when {
            !padPresent -> false
            mode == MODE_ON -> true
            mode == MODE_AUTO -> !hostGamepadAvailable
            else -> false
        }

        fun nextMode(mode: Int): Int = when (mode) {
            MODE_AUTO -> MODE_ON
            MODE_ON -> MODE_OFF
            else -> MODE_AUTO
        }

        fun modeLabel(mode: Int): String = when (mode) {
            MODE_ON -> "Selalu"
            MODE_OFF -> "Mati"
            else -> "Otomatis"
        }

        /** Dipakai uji: ambang mati harus benar-benar di bawah ambang nyala. */
        fun hysteresisGap(): Float = abs(STICK_ON - STICK_OFF)
    }
}
