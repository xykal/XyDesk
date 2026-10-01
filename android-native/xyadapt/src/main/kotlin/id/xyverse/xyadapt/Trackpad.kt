package id.xyverse.xyadapt

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/** Perintah hasil gestur; pemanggil mengubahnya ke paket protokol. */
sealed interface Act {
    data class MoveRel(val dx: Int, val dy: Int) : Act
    data class Button(val button: Int, val down: Boolean) : Act
    data class Click(val button: Int) : Act
    data class Scroll(val dx: Int, val dy: Int) : Act
    /** Zoom: langkah positif = perbesar (Ctrl+roda ke atas). */
    data class Zoom(val steps: Int) : Act
    /** Geser tiga jari: -1 kiri, +1 kanan (ganti jendela), -2 atas (tampilkan tugas). */
    data class Swipe3(val dir: Int) : Act
    data object Haptic : Act
}

data class TrackpadConfig(
    val speed: Float = 1.4f,
    val accel: Float = 0.6f,
    val naturalScroll: Boolean = false,
    val holdRightClickMs: Long = 480,
    val tapMs: Long = 250,
    val doubleTapMs: Long = 280,
    val slopPx: Float = 8f,
    val scrollStepPx: Float = 10f,
    val scrollUnit: Int = 40,
    val pinchStepPx: Float = 28f,
    val swipe3Px: Float = 90f,
    val twoFingerTap: Boolean = true,
)

/**
 * Mesin gestur trackpad, bebas dari kelas Android supaya bisa diuji di JVM.
 * Ketuk = klik kiri · tahan = klik kanan · dua jari ketuk = klik kanan · tiga jari ketuk = klik tengah
 * dua jari geser = scroll · ketuk lalu ketuk-tahan = seret.
 * Pemanggil wajib memanggil [tick] sekitar [TrackpadConfig.holdRightClickMs] setelah [down].
 */
class Trackpad(var config: TrackpadConfig = TrackpadConfig(), private val out: (Act) -> Unit) {
    private var fingers = 0
    private var maxFingers = 0
    private var downAt = 0L
    private var lastT = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var startX = 0f
    private var startY = 0f
    private var moved = false
    private var dragging = false
    private var holdFired = false
    private var lastTapUp = -10_000L
    private var remX = 0f
    private var remY = 0f
    private var scrollAcc = 0f
    private var pinchDist = -1f
    private var pinchAcc = 0f
    private var swipeX = 0f
    private var swipeY = 0f
    private var swiped = false

    val isDragging get() = dragging

    fun down(t: Long, x: Float, y: Float) {
        fingers = 1; maxFingers = 1
        downAt = t; lastT = t
        lastX = x; lastY = y; startX = x; startY = y
        moved = false; holdFired = false; scrollAcc = 0f; remX = 0f; remY = 0f
        pinchDist = -1f; pinchAcc = 0f; swipeX = 0f; swipeY = 0f; swiped = false
        dragging = t - lastTapUp <= config.doubleTapMs
        if (dragging) out(Act.Button(0, true))
    }

    fun pointerDown() {
        fingers++
        if (fingers > maxFingers) maxFingers = fingers
    }

    fun pointerUp() {
        fingers = (fingers - 1).coerceAtLeast(1)
    }

    /** Jarak antar dua jari pertama; panggil tiap MOVE saat dua jari menempel. */
    fun pinch(distance: Float) {
        if (maxFingers != 2 || dragging) return
        if (pinchDist < 0f) { pinchDist = distance; return }
        pinchAcc += distance - pinchDist
        pinchDist = distance
        val steps = (pinchAcc / config.pinchStepPx).toInt()
        if (steps != 0) {
            pinchAcc -= steps * config.pinchStepPx
            moved = true
            out(Act.Zoom(steps))
        }
    }

    fun move(t: Long, x: Float, y: Float) {
        val dx = x - lastX
        val dy = y - lastY
        if (!moved && sqrt(dx * dx + dy * dy) < config.slopPx && maxFingers == 1) return
        moved = true
        val dt = (t - lastT).coerceAtLeast(1)
        lastT = t; lastX = x; lastY = y
        if (maxFingers >= 3) {
            swipeX += dx; swipeY += dy
            if (!swiped) {
                val dir = when {
                    swipeX > config.swipe3Px -> 1
                    swipeX < -config.swipe3Px -> -1
                    swipeY < -config.swipe3Px -> -2
                    else -> 0
                }
                if (dir != 0) { swiped = true; out(Act.Haptic); out(Act.Swipe3(dir)) }
            }
            return
        }
        if (pinchDist >= 0f && abs(pinchAcc) > config.pinchStepPx * 0.5f) return
        if (maxFingers >= 2 && !dragging) {
            scrollAcc += if (config.naturalScroll) dy else -dy
            val steps = (scrollAcc / config.scrollStepPx).toInt()
            if (steps != 0) {
                scrollAcc -= steps * config.scrollStepPx
                out(Act.Scroll(0, steps * config.scrollUnit))
            }
            return
        }
        val v = sqrt(dx * dx + dy * dy) / dt
        val gain = config.speed * (1f + config.accel * min(1.5f, v / 1.2f))
        remX += dx * gain; remY += dy * gain
        val ix = remX.toInt(); val iy = remY.toInt()
        remX -= ix; remY -= iy
        if (ix != 0 || iy != 0) out(Act.MoveRel(ix, iy))
    }

    /** Dipanggil oleh timer; klik kanan bila jari tetap diam sejak [down]. */
    fun tick(t: Long) {
        if (fingers != 1 || moved || dragging || holdFired || maxFingers != 1) return
        if (t - downAt < config.holdRightClickMs) return
        holdFired = true
        out(Act.Haptic)
        out(Act.Click(1))
    }

    fun up(t: Long) {
        fingers = 0
        if (dragging) {
            out(Act.Button(0, false))
            dragging = false
            return
        }
        if (holdFired) return
        val quick = t - downAt <= config.tapMs
        if (!moved && quick) {
            when (maxFingers) {
                1 -> { out(Act.Click(0)); lastTapUp = t }
                2 -> if (cfg.twoFingerTap) out(Act.Click(1))
                else -> out(Act.Click(2))
            }
        }
    }

    fun cancel() {
        if (dragging) out(Act.Button(0, false))
        fingers = 0; dragging = false; holdFired = true
    }

    companion object {
        fun distance(ax: Float, ay: Float, bx: Float, by: Float) = abs(ax - bx) + abs(ay - by)
    }
}
