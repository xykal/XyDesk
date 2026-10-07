package id.xyverse.xydesk.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xyverse.xydesk.core.StreamXy
import id.xyverse.xydesk.core.XyGamepad
import id.xyverse.xydesk.core.XyScroll
import id.xyverse.xyadapt.OverlayRules
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyText
import org.json.JSONArray
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val MAX_OVERLAY_ITEMS = 96

/** Gaya visual kontrol overlay di layar. */
object ControlStyle {
    const val TRANSPARENT_BORDER = 0 // Transparan dengan border neon
    const val GAMING_FRAME = 1       // Berbingkai game futuristik
    const val SOLID_DARK = 2         // Solid hitam pekat taktis
}

enum class OverlayKind {
    KEY,
    MOUSE,
    SCROLL,
    SCROLL_X,
    SCROLL_WHEEL,
    CHORD,
    TOGGLE,
    STICK_KEYS,
    STICK_MOUSE,
    GAMEPAD_BUTTON,
    GAMEPAD_STICK_L,
    GAMEPAD_STICK_R,
    GAMEPAD_TRIGGER_L,
    GAMEPAD_TRIGGER_R,
}

data class OverlayItem(
    val id: String,
    val kind: OverlayKind,
    val code: Int = 0,
    val keys: List<Int> = emptyList(),
    val x: Float,
    val y: Float,
    val size: Float,
    val radius: Float,
    val name: String = "",
    val display: String = "",
)

/** Katalog aksi Windows VK — dengan simbol ikon visual dan nama. */
object OverlayKeys {
    val all: List<Pair<Int, String>> = buildList {
        addAll(('A'..'Z').map { it.code to it.toString() })
        addAll(('0'..'9').map { it.code to it.toString() })
        for (i in 1..24) add((0x6F + i) to "F$i")
        addAll(
            listOf(
                0x1B to "⎋", 0x09 to "⇥", 0x0D to "↵", 0x08 to "⌫",
                0x2E to "⌦", 0x2D to "Ins", 0x20 to "␣",
                0xA0 to "⇧", 0xA1 to "⇧R", 0xA2 to "⌃", 0xA3 to "⌃R",
                0xA4 to "⌥", 0xA5 to "⌥R", 0x5B to "❖", 0x5C to "❖R", 0x5D to "☰",
                0x25 to "←", 0x26 to "↑", 0x27 to "→", 0x28 to "↓",
                0x24 to "⤒", 0x23 to "⤓", 0x21 to "⇞", 0x22 to "⇟",
                0x14 to "⇪", 0x90 to "🔢", 0x91 to "ScrLk", 0x13 to "Pause", 0x2C to "PrtSc",
                0xAD to "🔇", 0xAE to "🔉", 0xAF to "🔊",
                0xB0 to "⏭", 0xB1 to "⏮", 0xB2 to "⏹", 0xB3 to "⏯",
                0xBA to ";", 0xBB to "=", 0xBC to ",", 0xBD to "-", 0xBE to ".", 0xBF to "/",
                0xC0 to "`", 0xDB to "[", 0xDC to "\\", 0xDD to "]", 0xDE to "'",
            ),
        )
        for (i in 0..9) add((0x60 + i) to "Num $i")
        addAll(listOf(0x6A to "Num *", 0x6B to "Num +", 0x6D to "Num −", 0x6E to "Num .", 0x6F to "Num /"))
    }

    fun label(vk: Int): String = when (vk) {
        0x1B -> "⎋"
        0x09 -> "⇥"
        0x0D -> "↵"
        0x08 -> "⌫"
        0x2E -> "⌦"
        0x20 -> "␣"
        0xA0, 0xA1 -> "⇧"
        0xA2, 0xA3 -> "⌃"
        0xA4, 0xA5 -> "⌥"
        0x5B, 0x5C -> "❖"
        0x25 -> "←"
        0x26 -> "↑"
        0x27 -> "→"
        0x28 -> "↓"
        0x24 -> "⤒"
        0x23 -> "⤓"
        0x21 -> "⇞"
        0x22 -> "⇟"
        0x14 -> "⇪"
        0x90 -> "🔢"
        0xAD -> "🔇"
        0xAE -> "🔉"
        0xAF -> "🔊"
        0xB0 -> "⏭"
        0xB1 -> "⏮"
        0xB2 -> "⏹"
        0xB3 -> "⏯"
        else -> all.find { it.first == vk }?.second ?: "VK $vk"
    }
}

object OverlayLayouts {
    /** Preset 1: FPS / Aksi dengan Analog WASD, tombol gerak, reload, interaksi, mouse aim/fire. */
    fun fps(): List<OverlayItem> = listOf(
        OverlayItem("stick", OverlayKind.STICK_KEYS, keys = listOf(0x57, 0x53, 0x41, 0x44), x = 16f, y = 74f, size = 136f, radius = 68f, display = "WASD"),
        OverlayItem("shift", OverlayKind.KEY, 0xA0, x = 6f, y = 84f, size = 52f, radius = 26f, display = "⇧"),
        OverlayItem("ctrl", OverlayKind.KEY, 0xA2, x = 6f, y = 66f, size = 52f, radius = 26f, display = "⌃"),
        OverlayItem("space", OverlayKind.KEY, 0x20, x = 86f, y = 78f, size = 68f, radius = 34f, display = "␣"),
        OverlayItem("lmb", OverlayKind.MOUSE, 0, x = 86f, y = 58f, size = 56f, radius = 28f, display = "🖱L"),
        OverlayItem("rmb", OverlayKind.MOUSE, 1, x = 74f, y = 48f, size = 56f, radius = 28f, display = "🖱R"),
        OverlayItem("r", OverlayKind.KEY, 0x52, x = 74f, y = 68f, size = 48f, radius = 24f, display = "R"),
        OverlayItem("e", OverlayKind.KEY, 0x45, x = 86f, y = 38f, size = 48f, radius = 24f, display = "E"),
        OverlayItem("f", OverlayKind.KEY, 0x46, x = 64f, y = 38f, size = 48f, radius = 24f, display = "F"),
        OverlayItem("q", OverlayKind.KEY, 0x51, x = 16f, y = 46f, size = 46f, radius = 23f, display = "Q"),
        OverlayItem("esc", OverlayKind.KEY, 0x1B, x = 5f, y = 14f, size = 46f, radius = 23f, display = "⎋"),
        OverlayItem("tab", OverlayKind.KEY, 0x09, x = 5f, y = 28f, size = 46f, radius = 23f, display = "⇥"),
        OverlayItem("k1", OverlayKind.KEY, 0x31, x = 20f, y = 14f, size = 42f, radius = 21f, display = "1"),
        OverlayItem("k2", OverlayKind.KEY, 0x32, x = 28f, y = 14f, size = 42f, radius = 21f, display = "2"),
        OverlayItem("k3", OverlayKind.KEY, 0x33, x = 36f, y = 14f, size = 42f, radius = 21f, display = "3"),
        OverlayItem("k4", OverlayKind.KEY, 0x34, x = 44f, y = 14f, size = 42f, radius = 21f, display = "4"),
    )

    /** Preset 2: Gamepad Konsol Lengkap (Dual sticks, ABXY, D-Pad, Shoulder, Trigger). */
    fun gamepad(): List<OverlayItem> = listOf(
        OverlayItem("gsl", OverlayKind.GAMEPAD_STICK_L, 0, x = 18f, y = 72f, size = 136f, radius = 68f, display = "LS"),
        OverlayItem("gsr", OverlayKind.GAMEPAD_STICK_R, 1, x = 72f, y = 74f, size = 124f, radius = 62f, display = "RS"),
        OverlayItem("ga", OverlayKind.GAMEPAD_BUTTON, 0x1000, x = 88f, y = 68f, size = 52f, radius = 26f, display = "A"),
        OverlayItem("gb", OverlayKind.GAMEPAD_BUTTON, 0x2000, x = 95f, y = 58f, size = 52f, radius = 26f, display = "B"),
        OverlayItem("gx", OverlayKind.GAMEPAD_BUTTON, 0x4000, x = 81f, y = 58f, size = 52f, radius = 26f, display = "X"),
        OverlayItem("gy", OverlayKind.GAMEPAD_BUTTON, 0x8000, x = 88f, y = 48f, size = 52f, radius = 26f, display = "Y"),
        OverlayItem("gup", OverlayKind.GAMEPAD_BUTTON, 0x0001, x = 37f, y = 52f, size = 48f, radius = 24f, display = "↑"),
        OverlayItem("gdown", OverlayKind.GAMEPAD_BUTTON, 0x0002, x = 37f, y = 72f, size = 48f, radius = 24f, display = "↓"),
        OverlayItem("gleft", OverlayKind.GAMEPAD_BUTTON, 0x0004, x = 29f, y = 62f, size = 48f, radius = 24f, display = "←"),
        OverlayItem("gright", OverlayKind.GAMEPAD_BUTTON, 0x0008, x = 45f, y = 62f, size = 48f, radius = 24f, display = "→"),
        OverlayItem("glb", OverlayKind.GAMEPAD_BUTTON, 0x0100, x = 14f, y = 34f, size = 54f, radius = 27f, display = "LB"),
        OverlayItem("grb", OverlayKind.GAMEPAD_BUTTON, 0x0200, x = 86f, y = 34f, size = 54f, radius = 27f, display = "RB"),
        OverlayItem("glt", OverlayKind.GAMEPAD_TRIGGER_L, 0, x = 28f, y = 34f, size = 54f, radius = 27f, display = "LT"),
        OverlayItem("grt", OverlayKind.GAMEPAD_TRIGGER_R, 1, x = 72f, y = 34f, size = 54f, radius = 27f, display = "RT"),
        OverlayItem("gback", OverlayKind.GAMEPAD_BUTTON, 0x0020, x = 44f, y = 86f, size = 46f, radius = 23f, display = "⧏"),
        OverlayItem("gstart", OverlayKind.GAMEPAD_BUTTON, 0x0010, x = 56f, y = 86f, size = 46f, radius = 23f, display = "⧐"),
    )

    /** Preset 3: Desktop & Produktivitas (Function Row, Navigasi Panah, Pintasan Copy/Paste/CAD). */
    fun desktop(): List<OverlayItem> = buildList {
        // Barisan fungsi F1–F12 di atas
        for (i in 1..12) {
            add(OverlayItem("f$i", OverlayKind.KEY, 0x6F + i, x = 5f + (i - 1) * 7.8f, y = 14f, size = 42f, radius = 21f, display = "F$i"))
        }
        // Pintasan kombinasi di bawah
        add(OverlayItem("cad", OverlayKind.CHORD, keys = listOf(0xA2, 0xA4, 0x2E), x = 12f, y = 84f, size = 54f, radius = 27f, display = "⌃⌥⌦"))
        add(OverlayItem("cz", OverlayKind.CHORD, keys = listOf(0xA2, 0x5A), x = 22f, y = 84f, size = 50f, radius = 25f, display = "⌃Z"))
        add(OverlayItem("cc", OverlayKind.CHORD, keys = listOf(0xA2, 0x43), x = 32f, y = 84f, size = 50f, radius = 25f, display = "⌃C"))
        add(OverlayItem("cv", OverlayKind.CHORD, keys = listOf(0xA2, 0x56), x = 42f, y = 84f, size = 50f, radius = 25f, display = "⌃V"))
        add(OverlayItem("cs", OverlayKind.CHORD, keys = listOf(0xA2, 0x53), x = 52f, y = 84f, size = 50f, radius = 25f, display = "⌃S"))
        add(OverlayItem("atab", OverlayKind.CHORD, keys = listOf(0xA4, 0x09), x = 62f, y = 84f, size = 50f, radius = 25f, display = "⌥⇥"))
        add(OverlayItem("wind", OverlayKind.CHORD, keys = listOf(0x5B, 0x44), x = 72f, y = 84f, size = 50f, radius = 25f, display = "❖D"))
        // Navigasi panah
        add(OverlayItem("up", OverlayKind.KEY, 0x26, x = 88f, y = 70f, size = 48f, radius = 24f, display = "↑"))
        add(OverlayItem("down", OverlayKind.KEY, 0x28, x = 88f, y = 84f, size = 48f, radius = 24f, display = "↓"))
        add(OverlayItem("left", OverlayKind.KEY, 0x25, x = 80f, y = 84f, size = 48f, radius = 24f, display = "←"))
        add(OverlayItem("right", OverlayKind.KEY, 0x27, x = 96f, y = 84f, size = 48f, radius = 24f, display = "→"))
        // Tombol esensial
        add(OverlayItem("esc", OverlayKind.KEY, 0x1B, x = 5f, y = 30f, size = 48f, radius = 24f, display = "⎋"))
        add(OverlayItem("tab", OverlayKind.KEY, 0x09, x = 5f, y = 46f, size = 48f, radius = 24f, display = "⇥"))
        add(OverlayItem("enter", OverlayKind.KEY, 0x0D, x = 92f, y = 52f, size = 54f, radius = 27f, display = "↵"))
        add(OverlayItem("back", OverlayKind.KEY, 0x08, x = 92f, y = 36f, size = 48f, radius = 24f, display = "⌫"))
        add(OverlayItem("space", OverlayKind.KEY, 0x20, x = 48f, y = 68f, size = 68f, radius = 34f, display = "␣"))
    }

    /** Preset 4: Media & Navigasi Cepat. */
    fun media(): List<OverlayItem> = listOf(
        OverlayItem("play", OverlayKind.KEY, 0xB3, x = 50f, y = 76f, size = 64f, radius = 32f, display = "⏯"),
        OverlayItem("prev", OverlayKind.KEY, 0xB1, x = 38f, y = 76f, size = 52f, radius = 26f, display = "⏮"),
        OverlayItem("next", OverlayKind.KEY, 0xB0, x = 62f, y = 76f, size = 52f, radius = 26f, display = "⏭"),
        OverlayItem("mute", OverlayKind.KEY, 0xAD, x = 26f, y = 76f, size = 48f, radius = 24f, display = "🔇"),
        OverlayItem("voldn", OverlayKind.KEY, 0xAE, x = 74f, y = 76f, size = 48f, radius = 24f, display = "🔉"),
        OverlayItem("volup", OverlayKind.KEY, 0xAF, x = 86f, y = 76f, size = 48f, radius = 24f, display = "🔊"),
        OverlayItem("home", OverlayKind.KEY, 0x24, x = 26f, y = 60f, size = 48f, radius = 24f, display = "⤒"),
        OverlayItem("end", OverlayKind.KEY, 0x23, x = 38f, y = 60f, size = 48f, radius = 24f, display = "⤓"),
        OverlayItem("pgup", OverlayKind.KEY, 0x21, x = 62f, y = 60f, size = 48f, radius = 24f, display = "⇞"),
        OverlayItem("pgdn", OverlayKind.KEY, 0x22, x = 74f, y = 60f, size = 48f, radius = 24f, display = "⇟"),
        OverlayItem("space", OverlayKind.KEY, 0x20, x = 50f, y = 60f, size = 54f, radius = 27f, display = "␣"),
        OverlayItem("sw", OverlayKind.SCROLL_WHEEL, x = 14f, y = 68f, size = 96f, radius = 48f, display = "Scroll"),
    )

    fun mouse(): List<OverlayItem> = listOf(
        OverlayItem("lmb", OverlayKind.MOUSE, 0, x = 70f, y = 68f, size = 58f, radius = 29f, display = "🖱L"),
        OverlayItem("rmb", OverlayKind.MOUSE, 1, x = 84f, y = 68f, size = 58f, radius = 29f, display = "🖱R"),
        OverlayItem("mmb", OverlayKind.MOUSE, 2, x = 77f, y = 55f, size = 52f, radius = 26f, display = "🖱M"),
        OverlayItem("toggle", OverlayKind.TOGGLE, x = 77f, y = 84f, size = 52f, radius = 26f, display = "Touch"),
        OverlayItem("sw", OverlayKind.SCROLL_WHEEL, x = 16f, y = 76f, size = 92f, radius = 46f, display = "Scroll"),
        OverlayItem("scu", OverlayKind.SCROLL, 120, x = 28f, y = 68f, size = 50f, radius = 25f, display = "▲"),
        OverlayItem("scd", OverlayKind.SCROLL, -120, x = 28f, y = 84f, size = 50f, radius = 25f, display = "▼"),
    )

    fun functionRow(): List<OverlayItem> = (1..12).map { i ->
        OverlayItem("f$i", OverlayKind.KEY, 0x6F + i, x = 7f + (i - 1) * 7.8f, y = 38f, size = 44f, radius = 22f, display = "F$i")
    }

    fun numpad(): List<OverlayItem> {
        val out = ArrayList<OverlayItem>()
        fun key(id: String, vk: Int, label: String, x: Float, y: Float) =
            out.add(OverlayItem(id, OverlayKind.KEY, vk, x = x, y = y, size = 48f, radius = 24f, display = label))
        val xs = listOf(66f, 76f, 86f)
        val rows = listOf(listOf(7, 8, 9), listOf(4, 5, 6), listOf(1, 2, 3))
        rows.forEachIndexed { r, row -> row.forEachIndexed { c, n -> key("num$n", 0x60 + n, n.toString(), xs[c], 46f + r * 13f) } }
        key("num0", 0x60, "0", 66f, 85f)
        key("numdot", 0x6E, ".", 76f, 85f)
        key("numenter", 0x0D, "↵", 86f, 85f)
        listOf("/" to 0x6F, "*" to 0x6A, "−" to 0x6D, "+" to 0x6B).forEachIndexed { i, (label, vk) ->
            key("numop$i", vk, label, 56f, 46f + i * 13f)
        }
        return out
    }

    fun complete(): List<OverlayItem> =
        (fps() + desktop() + numpad() + gamepad() + mouse())
            .distinctBy { it.id }
            .take(MAX_OVERLAY_ITEMS)

    fun toJson(items: List<OverlayItem>): String {
        val a = JSONArray()
        items.take(MAX_OVERLAY_ITEMS).forEach { m ->
            a.put(
                JSONObject()
                    .put("id", m.id).put("kind", m.kind.name).put("code", m.code)
                    .put("keys", JSONArray(m.keys)).put("x", m.x.toDouble()).put("y", m.y.toDouble())
                    .put("size", m.size.toDouble()).put("radius", m.radius.toDouble())
                    .put("name", m.name).put("display", m.display),
            )
        }
        return a.toString()
    }

    fun fromJson(raw: String?): List<OverlayItem> {
        if (raw.isNullOrBlank()) return fps()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val kind = runCatching { OverlayKind.valueOf(o.optString("kind")) }.getOrNull() ?: return@mapNotNull null
                val keys = o.optJSONArray("keys")?.let { k -> (0 until k.length()).map { k.optInt(it) } } ?: emptyList()
                OverlayItem(
                    id = o.optString("id").ifBlank { "k$i" },
                    kind = kind,
                    code = o.optInt("code"),
                    keys = keys,
                    x = o.optDouble("x", 50.0).toFloat().coerceIn(0f, 100f),
                    y = o.optDouble("y", 50.0).toFloat().coerceIn(0f, 100f),
                    size = o.optDouble("size", 56.0).toFloat().coerceIn(34f, 180f),
                    radius = o.optDouble("radius", 28.0).toFloat().coerceIn(0f, 90f),
                    name = o.optString("name"),
                    display = o.optString("display"),
                )
            }.take(MAX_OVERLAY_ITEMS)
        }.getOrElse { fps() }
    }
}

private fun OverlayItem.label(): String = when (kind) {
    OverlayKind.KEY -> display.ifBlank { OverlayKeys.label(code) }
    OverlayKind.CHORD -> display.ifBlank { keys.joinToString("+") { OverlayKeys.label(it) } }
    OverlayKind.MOUSE -> display.ifBlank { listOf("🖱L", "🖱R", "🖱M", "🖱Back", "🖱Next").getOrElse(code) { "🖱" } }
    OverlayKind.SCROLL -> if (code > 0) "▲" else "▼"
    OverlayKind.SCROLL_X -> if (code > 0) "▶" else "◀"
    OverlayKind.SCROLL_WHEEL -> "Scroll"
    OverlayKind.TOGGLE -> "Touch"
    OverlayKind.STICK_KEYS -> display.ifBlank { "WASD" }
    OverlayKind.STICK_MOUSE -> "Mouse"
    OverlayKind.GAMEPAD_STICK_L -> display.ifBlank { "LS" }
    OverlayKind.GAMEPAD_STICK_R -> display.ifBlank { "RS" }
    OverlayKind.GAMEPAD_BUTTON -> display.ifBlank { "PAD" }
    OverlayKind.GAMEPAD_TRIGGER_L -> "LT"
    OverlayKind.GAMEPAD_TRIGGER_R -> "RT"
}

class OverlayHolds(private val send: (ByteArray) -> Unit) {
    private val owners = HashMap<String, OverlayItem>()
    private val counts = HashMap<Int, Int>()
    private val padButtons = HashMap<String, Int>()
    private var padLx = 0
    private var padLy = 0
    private var padRx = 0
    private var padRy = 0
    private var padLt = 0
    private var padRt = 0

    fun down(owner: String, m: OverlayItem) {
        owners[owner] = m
        if (m.kind == OverlayKind.GAMEPAD_BUTTON) {
            padButtons[owner] = m.code
            flushGamepad()
            return
        }
        actions(m).forEach { a ->
            val k = a.code
            val c = counts.getOrElse(k) { 0 }
            counts[k] = c + 1
            if (c == 0) {
                if (a.kind == OverlayKind.KEY) send(StreamXy.key(a.code, true))
                else if (a.kind == OverlayKind.MOUSE) send(StreamXy.button(a.code, true))
                else if (a.kind == OverlayKind.SCROLL) send(StreamXy.scroll(0, a.code))
                else if (a.kind == OverlayKind.SCROLL_X) send(StreamXy.scroll(a.code, 0))
            }
        }
    }

    fun up(owner: String) {
        val m = owners.remove(owner) ?: return
        if (m.kind == OverlayKind.GAMEPAD_BUTTON) {
            padButtons.remove(owner)
            flushGamepad()
            return
        }
        actions(m).forEach { a ->
            val k = a.code
            val c = counts.getOrElse(k) { 0 }
            if (c <= 1) {
                counts.remove(k)
                if (a.kind == OverlayKind.KEY) send(StreamXy.key(a.code, false))
                else if (a.kind == OverlayKind.MOUSE) send(StreamXy.button(a.code, false))
            } else counts[k] = c - 1
        }
    }

    fun gamepadButton(owner: String, bit: Int, down: Boolean) {
        if (bit == 0) return
        if (down) padButtons[owner] = bit else padButtons.remove(owner)
        flushGamepad()
    }

    fun gamepadStick(kind: OverlayKind, x: Float, y: Float) {
        if (kind == OverlayKind.GAMEPAD_STICK_L) {
            padLx = XyGamepad.axis(x); padLy = XyGamepad.axis(y)
        } else {
            padRx = XyGamepad.axis(x); padRy = XyGamepad.axis(y)
        }
        flushGamepad()
    }

    fun gamepadTrigger(kind: OverlayKind, value: Float) {
        if (kind == OverlayKind.GAMEPAD_TRIGGER_L) padLt = XyGamepad.trigger(value) else padRt = XyGamepad.trigger(value)
        flushGamepad()
    }

    fun reset() {
        owners.keys.toList().forEach { up(it) }
        padButtons.clear()
        padLt = 0; padRt = 0; padLx = 0; padLy = 0; padRx = 0; padRy = 0
        flushGamepad()
    }

    private fun actions(m: OverlayItem) =
        if (m.kind == OverlayKind.CHORD) m.keys.map { m.copy(kind = OverlayKind.KEY, code = it) } else listOf(m)

    private fun flushGamepad() {
        val buttons = padButtons.values.fold(0) { acc, bit -> acc or bit }
        send(StreamXy.gamepad(buttons, padLt, padRt, padLx, padLy, padRx, padRy))
    }
}

/**
 * Lapisan kontrol di atas layar dengan tema hitam gaming pekat, feedback animasi sentuh neon,
 * analog dengan cetakan W-A-S-D yang menyala saat digeser, serta varian gaya visual (Transparan, Gaming, Solid).
 */
@Composable
fun ControlOverlay(
    items: List<OverlayItem>,
    edit: Boolean,
    send: (ByteArray) -> Unit,
    onItems: (List<OverlayItem>) -> Unit,
    onToggleTouch: () -> Unit,
    onEdit: (Boolean) -> Unit,
    onSave: () -> Unit,
    /** 0 otomatis, 1 selalu tampil, 2 mati — lihat [OverlayRules]. */
    mode: Int = OverlayRules.MODE_AUTO,
    /** 0 Transparan border neon, 1 Gaming frame beveled, 2 Solid dark taktis */
    style: Int = ControlStyle.TRANSPARENT_BORDER,
    onStyle: (Int) -> Unit = {},
    /** Perangkat fisik yang sedang menempel; hanya memengaruhi mode otomatis. */
    hidKeyboard: Boolean = false,
    hidMouse: Boolean = false,
    hidGamepad: Boolean = false,
) {
    val holds = remember { OverlayHolds(send) }
    DisposableEffect(Unit) { onDispose { holds.reset() } }
    DisposableEffect(edit) {
        if (edit) holds.reset()
        onDispose { }
    }
    var selected by remember { mutableStateOf<String?>(null) }
    var library by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val terlihat = items.filter {
            OverlayRules.visible(it.kind.name, mode, hidKeyboard, hidMouse, hidGamepad, edit)
        }
        terlihat.forEach { m ->
            OverlayButton(
                m = m,
                edit = edit,
                selected = selected == m.id,
                style = style,
                parentW = w,
                parentH = h,
                holds = holds,
                send = send,
                onToggleTouch = onToggleTouch,
                onSelect = { selected = m.id; library = false },
                onMove = { x, y -> onItems(items.map { if (it.id == m.id) it.copy(x = x, y = y) else it }) },
            )
        }
        if (edit) {
            Column(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                    .shadow(16.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x1A000000), spotColor = Color(0x227C3AED))
                    .clip(RoundedCornerShape(16.dp)).background(Color(0xF8FFFFFF)).border(1.dp, Color(0xFFE4E4EC), RoundedCornerShape(16.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    DarkChip("Tambah", on = library) { library = !library; selected = null }
                    DarkChip("Simpan") { onSave(); onEdit(false) }
                    DarkChip("Selesai") { onEdit(false) }
                    DarkChip("Hapus", danger = true) {
                        val id = selected
                        if (id != null) { onItems(items.filter { it.id != id }); selected = null }
                    }
                    Box(Modifier.width(1.dp).height(20.dp).background(Color(0xFFE4E4EC)))
                    DarkChip("Transparan", on = style == ControlStyle.TRANSPARENT_BORDER) { onStyle(ControlStyle.TRANSPARENT_BORDER) }
                    DarkChip("Bingkai Game", on = style == ControlStyle.GAMING_FRAME) { onStyle(ControlStyle.GAMING_FRAME) }
                    DarkChip("Solid Gelap", on = style == ControlStyle.SOLID_DARK) { onStyle(ControlStyle.SOLID_DARK) }
                }
                if (library) LibraryPanel(items.size, onPreset = { onItems(it); library = false }) { add ->
                    if (items.size >= MAX_OVERLAY_ITEMS) return@LibraryPanel
                    onItems(items + add)
                    selected = add.id
                    library = false
                }
                val cur = items.find { it.id == selected }
                if (cur != null && !library) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        XyText("${cur.label()} · ${cur.size.roundToInt()}dp · geser untuk posisikan", Xy.caption.copy(color = Xy.accent))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DarkChip("−") { onItems(items.map { if (it.id == cur.id) it.copy(size = (it.size - 8f).coerceAtLeast(34f), radius = ((it.size - 8f) / 2).coerceAtLeast(0f)) else it }) }
                            DarkChip("+") { onItems(items.map { if (it.id == cur.id) it.copy(size = (it.size + 8f).coerceAtMost(180f), radius = ((it.size + 8f) / 2).coerceAtMost(90f)) else it }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.editTapSelect(edit: Boolean, onSelect: () -> Unit): Modifier =
    if (edit) clickable(remember { MutableInteractionSource() }, null, onClick = onSelect) else this

@Composable
private fun OverlayButton(
    m: OverlayItem,
    edit: Boolean,
    selected: Boolean,
    style: Int,
    parentW: Float,
    parentH: Float,
    holds: OverlayHolds,
    send: (ByteArray) -> Unit,
    onToggleTouch: () -> Unit,
    onSelect: () -> Unit,
    onMove: (Float, Float) -> Unit,
) {
    val density = LocalDensity.current
    val px = with(density) { m.size.dp.toPx() }
    val left = (m.x / 100f * parentW - px / 2).coerceIn(0f, parentW - px)
    val top = (m.y / 100f * parentH - px / 2).coerceIn(0f, parentH - px)

    when (m.kind) {
        OverlayKind.STICK_KEYS, OverlayKind.STICK_MOUSE -> {
            StickPad(m, edit, selected, style, left, top, px, parentW, parentH, holds, send, onSelect, onMove)
            return
        }
        OverlayKind.SCROLL_WHEEL -> {
            ScrollWheel(m, edit, selected, style, left, top, px, parentW, parentH, send, onSelect, onMove)
            return
        }
        OverlayKind.GAMEPAD_STICK_L, OverlayKind.GAMEPAD_STICK_R -> {
            GamepadStickPad(m, edit, selected, style, left, top, px, parentW, parentH, holds, onSelect, onMove)
            return
        }
        OverlayKind.GAMEPAD_TRIGGER_L, OverlayKind.GAMEPAD_TRIGGER_R -> {
            GamepadTriggerPad(m, edit, selected, style, left, top, px, parentW, parentH, holds, onSelect, onMove)
            return
        }
        else -> Unit
    }

    RoundControl(
        m = m,
        selected = selected,
        style = style,
        left = left,
        top = top,
        edit = edit,
        parentW = parentW,
        parentH = parentH,
        px = px,
        holds = holds,
        onToggleTouch = onToggleTouch,
        onSelect = onSelect,
        onMove = onMove,
    )
}

@Composable
private fun RoundControl(
    m: OverlayItem,
    selected: Boolean,
    style: Int,
    left: Float,
    top: Float,
    edit: Boolean,
    parentW: Float,
    parentH: Float,
    px: Float,
    holds: OverlayHolds,
    onToggleTouch: () -> Unit,
    onSelect: () -> Unit,
    onMove: (Float, Float) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = when {
            selected -> 1.06f
            pressed -> 0.91f
            else -> 1.0f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "btnScale",
    )

    val shape: Shape = when (style) {
        ControlStyle.GAMING_FRAME -> RoundedCornerShape((m.radius * 0.7f).dp.coerceAtLeast(10.dp))
        else -> CircleShape
    }

    val bgColor by animateColorAsState(
        targetValue = when {
            selected -> Xy.accent
            pressed -> Color(0xFF7C3AED)
            style == ControlStyle.TRANSPARENT_BORDER -> Color(0x33000000)
            style == ControlStyle.GAMING_FRAME -> Color(0xCC0E0E18)
            else -> Color(0xEA161622) // SOLID_DARK
        },
        animationSpec = tween(90),
        label = "btnBg",
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> Color(0xFFE879F9)
            pressed -> Color(0xFFC084FC)
            style == ControlStyle.TRANSPARENT_BORDER -> Color(0x77A78BFA)
            style == ControlStyle.GAMING_FRAME -> Color(0xFF8B5CF6)
            else -> Color(0x44FFFFFF) // SOLID_DARK
        },
        animationSpec = tween(90),
        label = "btnBorder",
    )

    val borderWidth = when {
        selected -> 2.5.dp
        pressed -> 2.5.dp
        style == ControlStyle.GAMING_FRAME -> 2.dp
        else -> 1.5.dp
    }

    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(m.size.dp)
            .scale(scale)
            .clip(shape)
            .background(bgColor)
            .border(borderWidth, borderColor, shape)
            .editTapSelect(edit, onSelect)
            .pointerInput(edit, m.id) {
                if (edit) {
                    var dragCenter = Offset.Zero
                    detectDragGestures(
                        onDragStart = { onSelect(); dragCenter = Offset(left + px / 2, top + px / 2) },
                        onDrag = { change, drag ->
                            change.consume()
                            dragCenter += drag
                            onMove((dragCenter.x / parentW * 100f).coerceIn(0f, 100f), (dragCenter.y / parentH * 100f).coerceIn(0f, 100f))
                        },
                    )
                } else detectTapGestures(
                    onPress = {
                        pressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (m.kind == OverlayKind.TOGGLE) {
                            onToggleTouch()
                            tryAwaitRelease()
                            pressed = false
                        } else {
                            holds.down(m.id, m)
                            tryAwaitRelease()
                            holds.up(m.id)
                            pressed = false
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val label = m.label()
        val isIconGlyph = label.length <= 2 || label.startsWith("⌃") || label.startsWith("⌥") || label.startsWith("❖")
        XyText(
            label,
            Xy.body.copy(
                fontSize = if (isIconGlyph) (m.size * 0.32f).coerceIn(12f, 24f).sp else 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected || pressed) Color.White else Color(0xFFF1F5F9),
            ),
        )
    }
}

/**
 * Analog / Joystick dengan tanda arah W A S D / Panah yang menyala saat digeser.
 */
@Composable
private fun StickPad(
    m: OverlayItem,
    edit: Boolean,
    selected: Boolean,
    style: Int,
    left: Float,
    top: Float,
    px: Float,
    parentW: Float,
    parentH: Float,
    holds: OverlayHolds,
    send: (ByteArray) -> Unit,
    onSelect: () -> Unit,
    onMove: (Float, Float) -> Unit,
) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    var mouseVector by remember { mutableStateOf(Offset.Zero) }
    val dirs = m.keys.ifEmpty { listOf(0x57, 0x53, 0x41, 0x44) } // W, S, A, D

    // Label cardinal arah: Atas (North), Bawah (South), Kiri (West), Kanan (East)
    val labelN = OverlayKeys.label(dirs.getOrElse(0) { 0x57 })
    val labelS = OverlayKeys.label(dirs.getOrElse(1) { 0x53 })
    val labelW = OverlayKeys.label(dirs.getOrElse(2) { 0x41 })
    val labelE = OverlayKeys.label(dirs.getOrElse(3) { 0x44 })

    val max = px * 0.42f
    val vx = if (max > 0) (knob.x / max).coerceIn(-1f, 1f) else 0f
    val vy = if (max > 0) (knob.y / max).coerceIn(-1f, 1f) else 0f

    val isNorthActive = vy < -0.22f
    val isSouthActive = vy > 0.22f
    val isWestActive = vx < -0.22f
    val isEastActive = vx > 0.22f

    LaunchedEffect(edit) {
        if (edit) {
            knob = Offset.Zero
            mouseVector = Offset.Zero
        }
    }

    fun updateStick(position: Offset) {
        val c = Offset(px / 2, px / 2)
        var raw = position - c
        val len = hypot(raw.x, raw.y)
        if (len > max) raw = raw / len * max
        knob = raw
        val curVx = (raw.x / max).coerceIn(-1f, 1f)
        val curVy = (raw.y / max).coerceIn(-1f, 1f)
        if (m.kind == OverlayKind.STICK_KEYS) {
            val pressed = listOf(curVy < -0.18f, curVy > 0.18f, curVx < -0.18f, curVx > 0.18f)
            pressed.forEachIndexed { i, on ->
                val owner = "stick:${m.id}:$i"
                if (on) holds.down(owner, OverlayItem(owner, OverlayKind.KEY, dirs.getOrElse(i) { 0 }, x = 0f, y = 0f, size = 8f, radius = 4f))
                else holds.up(owner)
            }
        } else {
            mouseVector = if (len > max * 0.06f) Offset(curVx, curVy) else Offset.Zero
            if (mouseVector != Offset.Zero) send(StreamXy.moveRel((curVx * 24).roundToInt(), (curVy * 24).roundToInt()))
        }
    }

    LaunchedEffect(edit, m.kind, mouseVector) {
        if (!edit && m.kind == OverlayKind.STICK_MOUSE && mouseVector != Offset.Zero) {
            while (true) {
                send(StreamXy.moveRel((mouseVector.x * 24).roundToInt(), (mouseVector.y * 24).roundToInt()))
                delay(16L)
            }
        }
    }

    val baseBg = when (style) {
        ControlStyle.TRANSPARENT_BORDER -> Color(0x33000000)
        ControlStyle.GAMING_FRAME -> Color(0xDD0A0A14)
        else -> Color(0xEA12121C)
    }

    val baseBorder = when {
        selected -> Color(0xFFE879F9)
        style == ControlStyle.GAMING_FRAME -> Color(0xFF8B5CF6)
        else -> Color(0x55A78BFA)
    }

    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(m.size.dp)
            .clip(CircleShape)
            .background(baseBg)
            .border(2.dp, baseBorder, CircleShape)
            .editTapSelect(edit, onSelect)
            .pointerInput(edit, m.id) {
                if (edit) {
                    var dragCenter = Offset.Zero
                    detectDragGestures(
                        onDragStart = { onSelect(); dragCenter = Offset(left + px / 2, top + px / 2) },
                        onDrag = { change, drag ->
                            change.consume()
                            dragCenter += drag
                            onMove(
                                (dragCenter.x / parentW * 100f).coerceIn(0f, 100f),
                                (dragCenter.y / parentH * 100f).coerceIn(0f, 100f),
                            )
                        },
                    )
                } else detectDragGestures(
                    onDragStart = { updateStick(it) },
                    onDrag = { change, _ ->
                        change.consume()
                        updateStick(change.position)
                    },
                    onDragEnd = {
                        knob = Offset.Zero
                        mouseVector = Offset.Zero
                        for (i in 0..3) holds.up("stick:${m.id}:$i")
                    },
                    onDragCancel = {
                        knob = Offset.Zero
                        mouseVector = Offset.Zero
                        for (i in 0..3) holds.up("stick:${m.id}:$i")
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Cetakan arah di dasar analog: W (North), S (South), A (West), D (East)
        if (m.kind == OverlayKind.STICK_KEYS) {
            // North
            CardinalText(labelN, isNorthActive, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            // South
            CardinalText(labelS, isSouthActive, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
            // West
            CardinalText(labelW, isWestActive, Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
            // East
            CardinalText(labelE, isEastActive, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp))
        } else {
            XyText("MOUSE", Xy.label.copy(fontSize = 10.sp, color = Color(0x66FFFFFF)), Modifier.align(Alignment.Center))
        }

        // Thumbstick knob yang digeser jari
        val knobSize = (m.size * 0.38f).dp
        Box(
            Modifier
                .size(knobSize)
                .offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFA78BFA), Color(0xFF7C3AED))))
                .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)))
        }
    }
}

@Composable
private fun CardinalText(text: String, active: Boolean, modifier: Modifier) {
    val color by animateColorAsState(if (active) Color(0xFF38BDF8) else Color(0x88CBD5E1), tween(80), label = "cardinalColor")
    val scale by animateFloatAsState(if (active) 1.25f else 1.0f, tween(80), label = "cardinalScale")
    XyText(
        text,
        Xy.label.copy(fontSize = 11.sp, fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold, color = color),
        modifier = modifier.scale(scale),
    )
}

@Composable
private fun GamepadStickPad(
    m: OverlayItem,
    edit: Boolean,
    selected: Boolean,
    style: Int,
    left: Float,
    top: Float,
    px: Float,
    parentW: Float,
    parentH: Float,
    holds: OverlayHolds,
    onSelect: () -> Unit,
    onMove: (Float, Float) -> Unit,
) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    val max = px * 0.42f
    val isStickL = m.kind == OverlayKind.GAMEPAD_STICK_L

    LaunchedEffect(edit) { if (edit) knob = Offset.Zero }

    fun updateAnalog(position: Offset) {
        val c = Offset(px / 2, px / 2)
        var raw = position - c
        val len = hypot(raw.x, raw.y)
        if (len > max) raw = raw / len * max
        knob = raw
        val vx = (raw.x / max).coerceIn(-1f, 1f)
        val vy = (raw.y / max).coerceIn(-1f, 1f)
        holds.gamepadStick(m.kind, vx, vy)
    }

    val baseBg = when (style) {
        ControlStyle.TRANSPARENT_BORDER -> Color(0x33000000)
        ControlStyle.GAMING_FRAME -> Color(0xDD0A0A14)
        else -> Color(0xEA12121C)
    }

    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(m.size.dp)
            .clip(CircleShape)
            .background(baseBg)
            .border(2.dp, if (selected) Color(0xFFE879F9) else Color(0x66A78BFA), CircleShape)
            .editTapSelect(edit, onSelect)
            .pointerInput(edit, m.id) {
                if (edit) {
                    var dragCenter = Offset.Zero
                    detectDragGestures(
                        onDragStart = { onSelect(); dragCenter = Offset(left + px / 2, top + px / 2) },
                        onDrag = { change, drag ->
                            change.consume()
                            dragCenter += drag
                            onMove(
                                (dragCenter.x / parentW * 100f).coerceIn(0f, 100f),
                                (dragCenter.y / parentH * 100f).coerceIn(0f, 100f),
                            )
                        },
                    )
                } else detectDragGestures(
                    onDragStart = { updateAnalog(it) },
                    onDrag = { change, _ ->
                        change.consume()
                        updateAnalog(change.position)
                    },
                    onDragEnd = { knob = Offset.Zero; holds.gamepadStick(m.kind, 0f, 0f) },
                    onDragCancel = { knob = Offset.Zero; holds.gamepadStick(m.kind, 0f, 0f) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        XyText(if (isStickL) "LS" else "RS", Xy.label.copy(fontSize = 11.sp, color = Color(0x66FFFFFF)), Modifier.align(Alignment.Center))

        Box(
            Modifier
                .size((m.size * 0.38f).dp)
                .offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF38BDF8), Color(0xFF0284C7))))
                .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)))
        }
    }
}

@Composable
private fun GamepadTriggerPad(
    m: OverlayItem,
    edit: Boolean,
    selected: Boolean,
    style: Int,
    left: Float,
    top: Float,
    px: Float,
    parentW: Float,
    parentH: Float,
    holds: OverlayHolds,
    onSelect: () -> Unit,
    onMove: (Float, Float) -> Unit,
) {
    RoundControl(
        m = m,
        selected = selected,
        style = style,
        left = left,
        top = top,
        edit = edit,
        parentW = parentW,
        parentH = parentH,
        px = px,
        holds = holds,
        onToggleTouch = {},
        onSelect = onSelect,
        onMove = onMove,
    )
}

@Composable
private fun ScrollWheel(
    m: OverlayItem,
    edit: Boolean,
    selected: Boolean,
    style: Int,
    left: Float,
    top: Float,
    px: Float,
    parentW: Float,
    parentH: Float,
    send: (ByteArray) -> Unit,
    onSelect: () -> Unit,
    onMove: (Float, Float) -> Unit,
) {
    var knob by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(edit) {
        if (edit) knob = 0f
    }
    val shape = RoundedCornerShape(m.radius.dp.coerceAtLeast(18.dp))
    val bg = when (style) {
        ControlStyle.TRANSPARENT_BORDER -> Color(0x33000000)
        ControlStyle.GAMING_FRAME -> Color(0xDD0A0A14)
        else -> Color(0xEA12121C)
    }

    Box(
        Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(width = (m.size * 0.72f).dp, height = m.size.dp)
            .clip(shape)
            .background(bg)
            .border(2.dp, if (selected) Color(0xFFE879F9) else Color(0x66A78BFA), shape)
            .editTapSelect(edit, onSelect)
            .pointerInput(edit, m.id) {
                if (edit) {
                    var dragCenter = Offset.Zero
                    detectDragGestures(
                        onDragStart = { onSelect(); dragCenter = Offset(left + px / 2, top + px / 2) },
                        onDrag = { change, drag ->
                            change.consume()
                            dragCenter += drag
                            onMove(
                                (dragCenter.x / parentW * 100f).coerceIn(0f, 100f),
                                (dragCenter.y / parentH * 100f).coerceIn(0f, 100f),
                            )
                        },
                    )
                } else detectDragGestures(
                    onDrag = { change, drag ->
                        change.consume()
                        knob = (knob + drag.y).coerceIn(-px * 0.28f, px * 0.28f)
                        val dy = XyScroll.delta(drag.y)
                        if (dy != 0) send(StreamXy.scroll(0, dy))
                    },
                    onDragEnd = { knob = 0f },
                    onDragCancel = { knob = 0f },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        XyText("⇅", Xy.label.copy(fontSize = 18.sp, color = Color(0x55FFFFFF)), Modifier.align(Alignment.Center))
        Box(
            Modifier.size((m.size * 0.44f).dp)
                .offset { IntOffset(0, knob.roundToInt()) }
                .clip(RoundedCornerShape(999.dp))
                .background(Xy.accent)
                .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(999.dp)),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryPanel(count: Int, onPreset: (List<OverlayItem>) -> Unit, add: (OverlayItem) -> Unit) {
    Column(Modifier.height(280.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        XyText("PRESET LENGKAP ($count/$MAX_OVERLAY_ITEMS)", Xy.label.copy(color = Xy.accent))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DarkChip("FPS & Aksi", on = true) { onPreset(OverlayLayouts.fps()) }
            DarkChip("Gamepad Konsol", on = true) { onPreset(OverlayLayouts.gamepad()) }
            DarkChip("Desktop & Pintasan", on = true) { onPreset(OverlayLayouts.desktop()) }
            DarkChip("Media & Navigasi", on = true) { onPreset(OverlayLayouts.media()) }
            DarkChip("Preset Mouse", on = true) { onPreset(OverlayLayouts.mouse()) }
            DarkChip("Semua Kontrol", on = true) { onPreset(OverlayLayouts.complete()) }
        }

        XyText("GERAK & ANALOG", Xy.label.copy(color = Xy.accent))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DarkChip("🕹 Stick WASD") { add(OverlayItem("stk${System.nanoTime()}", OverlayKind.STICK_KEYS, keys = listOf(0x57, 0x53, 0x41, 0x44), x = 20f, y = 70f, size = 136f, radius = 68f, display = "WASD")) }
            DarkChip("🕹 Stick Panah") { add(OverlayItem("stk${System.nanoTime()}", OverlayKind.STICK_KEYS, keys = listOf(0x26, 0x28, 0x25, 0x27), x = 20f, y = 70f, size = 136f, radius = 68f, display = "Panah")) }
            DarkChip("🖱 Stick Mouse") { add(OverlayItem("stm${System.nanoTime()}", OverlayKind.STICK_MOUSE, x = 80f, y = 70f, size = 136f, radius = 68f, display = "Mouse")) }
            DarkChip("🎮 Stick L (Gamepad)") { add(OverlayItem("gsl${System.nanoTime()}", OverlayKind.GAMEPAD_STICK_L, x = 18f, y = 72f, size = 136f, radius = 68f, display = "LS")) }
            DarkChip("🎮 Stick R (Gamepad)") { add(OverlayItem("gsr${System.nanoTime()}", OverlayKind.GAMEPAD_STICK_R, x = 72f, y = 72f, size = 124f, radius = 62f, display = "RS")) }
        }

        XyText("MOUSE & SCROLL", Xy.label.copy(color = Xy.accent))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DarkChip("🖱L Klik Kiri") { add(item(OverlayKind.MOUSE, 0, "🖱L")) }
            DarkChip("🖱R Klik Kanan") { add(item(OverlayKind.MOUSE, 1, "🖱R")) }
            DarkChip("🖱M Klik Tengah") { add(item(OverlayKind.MOUSE, 2, "🖱M")) }
            DarkChip("⇅ Scroll Wheel") { add(OverlayItem("sw${System.nanoTime()}", OverlayKind.SCROLL_WHEEL, x = 16f, y = 72f, size = 92f, radius = 46f, display = "Scroll")) }
            DarkChip("▲ Scroll Atas") { add(item(OverlayKind.SCROLL, 120, "▲")) }
            DarkChip("▼ Scroll Bawah") { add(item(OverlayKind.SCROLL, -120, "▼")) }
            DarkChip("Touch Mode") { add(item(OverlayKind.TOGGLE, 0, "Touch")) }
        }

        XyText("TOMBOL AKSI & SIMBOL VISUAL", Xy.label.copy(color = Xy.accent))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "⎋ Esc" to 0x1B, "⇥ Tab" to 0x09, "↵ Enter" to 0x0D, "⌫ Back" to 0x08,
                "␣ Spasi" to 0x20, "⇧ Shift" to 0xA0, "⌃ Ctrl" to 0xA2, "⌥ Alt" to 0xA4, "❖ Win" to 0x5B,
                "↑" to 0x26, "↓" to 0x28, "←" to 0x25, "→" to 0x27,
                "W" to 0x57, "A" to 0x41, "S" to 0x53, "D" to 0x44,
                "E" to 0x45, "R" to 0x52, "F" to 0x46, "Q" to 0x51, "C" to 0x43, "V" to 0x56,
            ).forEach { (n, vk) ->
                DarkChip(n) { add(item(OverlayKind.KEY, vk, n.substringBefore(" "))) }
            }
        }

        XyText("PINTASAN & KOMBO", Xy.label.copy(color = Xy.accent))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DarkChip("⌃C Copy") { add(OverlayItem("c${System.nanoTime()}", OverlayKind.CHORD, keys = listOf(0xA2, 0x43), x = 50f, y = 50f, size = 56f, radius = 28f, display = "⌃C")) }
            DarkChip("⌃V Paste") { add(OverlayItem("c${System.nanoTime()}", OverlayKind.CHORD, keys = listOf(0xA2, 0x56), x = 50f, y = 50f, size = 56f, radius = 28f, display = "⌃V")) }
            DarkChip("⌃Z Undo") { add(OverlayItem("c${System.nanoTime()}", OverlayKind.CHORD, keys = listOf(0xA2, 0x5A), x = 50f, y = 50f, size = 56f, radius = 28f, display = "⌃Z")) }
            DarkChip("⌃S Save") { add(OverlayItem("c${System.nanoTime()}", OverlayKind.CHORD, keys = listOf(0xA2, 0x53), x = 50f, y = 50f, size = 56f, radius = 28f, display = "⌃S")) }
            DarkChip("⌥⇥ Alt+Tab") { add(OverlayItem("c${System.nanoTime()}", OverlayKind.CHORD, keys = listOf(0xA4, 0x09), x = 50f, y = 50f, size = 56f, radius = 28f, display = "⌥⇥")) }
            DarkChip("⌥F4 Alt+F4") { add(OverlayItem("c${System.nanoTime()}", OverlayKind.CHORD, keys = listOf(0xA4, 0x73), x = 50f, y = 50f, size = 56f, radius = 28f, display = "⌥F4")) }
            DarkChip("❖D Desktop") { add(OverlayItem("c${System.nanoTime()}", OverlayKind.CHORD, keys = listOf(0x5B, 0x44), x = 50f, y = 50f, size = 56f, radius = 28f, display = "❖D")) }
            DarkChip("⌃⌥⌦ CAD") { add(OverlayItem("c${System.nanoTime()}", OverlayKind.CHORD, keys = listOf(0xA2, 0xA4, 0x2E), x = 50f, y = 50f, size = 56f, radius = 28f, display = "⌃⌥⌦")) }
        }

        XyText("F1–F12", Xy.label.copy(color = Xy.accent))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..12).forEach { i -> DarkChip("F$i") { add(item(OverlayKind.KEY, 0x6F + i, "F$i")) } }
        }

        XyText("GAMEPAD TOMBOL", Xy.label.copy(color = Xy.accent))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("A" to 0x1000, "B" to 0x2000, "X" to 0x4000, "Y" to 0x8000, "LB" to 0x0100, "RB" to 0x0200, "Start" to 0x0010, "Back" to 0x0020).forEach { (n, bit) ->
                DarkChip("Pad $n") { add(item(OverlayKind.GAMEPAD_BUTTON, bit, n)) }
            }
            DarkChip("LT Trigger") { add(item(OverlayKind.GAMEPAD_TRIGGER_L, 0, "LT")) }
            DarkChip("RT Trigger") { add(item(OverlayKind.GAMEPAD_TRIGGER_R, 1, "RT")) }
        }
    }
}

private fun item(kind: OverlayKind, code: Int, display: String = "") =
    OverlayItem("n${System.nanoTime()}", kind, code, x = 50f, y = 55f, size = 56f, radius = 28f, display = display)

@Composable
private fun DarkChip(text: String, on: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val bg = when {
        danger -> Color(0x1AF43F5E)
        on -> Xy.accent
        else -> Color(0xFFF1F1F6)
    }
    val border = when {
        danger -> Color(0x66F43F5E)
        on -> Xy.accent
        else -> Color(0xFFE4E4EC)
    }
    val fg = when {
        danger -> Color(0xFFE11D48)
        on -> Color.White
        else -> Xy.textHi
    }
    Box(
        Modifier
            .height(34.dp)
            .widthIn(min = 40.dp)
            .clip(RoundedCornerShape(Xy.pill))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(Xy.pill))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        XyText(text, Xy.label.copy(fontSize = 11.sp, letterSpacing = 0.sp, color = fg))
    }
}
