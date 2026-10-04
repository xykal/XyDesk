package id.xyverse.xydesk.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xyverse.xydesk.core.StreamXy
import id.xyverse.xydesk.core.XyGamepad
import id.xyverse.xydesk.core.XyScroll
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyText
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val MAX_OVERLAY_ITEMS = 96

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

/** Katalog aksi Windows VK — huruf, F1–F24, numpad, panah, media. */
object OverlayKeys {
    val all: List<Pair<Int, String>> = buildList {
        addAll(('A'..'Z').map { it.code to it.toString() })
        addAll(('0'..'9').map { it.code to it.toString() })
        for (i in 1..24) add((0x6F + i) to "F$i")
        addAll(
            listOf(
                0x20 to "Spasi", 0x0D to "Enter", 0x1B to "Esc", 0x09 to "Tab",
                0x08 to "Backspace", 0x2E to "Delete", 0x2D to "Insert",
                0xA0 to "Shift", 0xA1 to "Shift kanan", 0xA2 to "Ctrl", 0xA3 to "Ctrl kanan",
                0xA4 to "Alt", 0xA5 to "Alt kanan", 0x5B to "Win", 0x5C to "Win kanan", 0x5D to "Menu",
                0x25 to "←", 0x26 to "↑", 0x27 to "→", 0x28 to "↓",
                0x24 to "Home", 0x23 to "End", 0x21 to "PgUp", 0x22 to "PgDn",
                0x14 to "Caps", 0x90 to "NumLk", 0x91 to "ScrLk", 0x13 to "Pause", 0x2C to "PrtSc",
                0xAD to "Mute", 0xAE to "Vol −", 0xAF to "Vol +",
                0xB0 to "Next", 0xB1 to "Prev", 0xB2 to "Stop", 0xB3 to "Play",
                0xBA to ";", 0xBB to "=", 0xBC to ",", 0xBD to "-", 0xBE to ".", 0xBF to "/",
                0xC0 to "`", 0xDB to "[", 0xDC to "\\", 0xDD to "]", 0xDE to "'",
            ),
        )
        for (i in 0..9) add((0x60 + i) to "Num $i")
        addAll(listOf(0x6A to "Num *", 0x6B to "Num +", 0x6D to "Num −", 0x6E to "Num .", 0x6F to "Num /"))
    }
    fun label(vk: Int) = all.find { it.first == vk }?.second ?: "VK $vk"
}

object OverlayLayouts {
    fun mouse() = listOf(
        OverlayItem("lmb", OverlayKind.MOUSE, 0, x = 70f, y = 68f, size = 58f, radius = 29f),
        OverlayItem("rmb", OverlayKind.MOUSE, 1, x = 84f, y = 68f, size = 58f, radius = 29f),
        OverlayItem("mmb", OverlayKind.MOUSE, 2, x = 77f, y = 55f, size = 52f, radius = 26f, display = "Tengah"),
        OverlayItem("toggle", OverlayKind.TOGGLE, x = 77f, y = 84f, size = 52f, radius = 26f),
        OverlayItem("sw", OverlayKind.SCROLL_WHEEL, x = 16f, y = 76f, size = 92f, radius = 46f, display = "Scroll"),
        OverlayItem("scu", OverlayKind.SCROLL, 120, x = 28f, y = 68f, size = 50f, radius = 25f),
        OverlayItem("scd", OverlayKind.SCROLL, -120, x = 28f, y = 84f, size = 50f, radius = 25f),
    )

    fun fps() = listOf(
        OverlayItem("w", OverlayKind.KEY, 0x57, x = 14f, y = 44f, size = 52f, radius = 26f, display = "W"),
        OverlayItem("a", OverlayKind.KEY, 0x41, x = 7f, y = 60f, size = 52f, radius = 26f, display = "A"),
        OverlayItem("s", OverlayKind.KEY, 0x53, x = 14f, y = 60f, size = 52f, radius = 26f, display = "S"),
        OverlayItem("d", OverlayKind.KEY, 0x44, x = 21f, y = 60f, size = 52f, radius = 26f, display = "D"),
        OverlayItem("shift", OverlayKind.KEY, 0xA0, x = 8f, y = 82f, size = 56f, radius = 28f, display = "Shift"),
        OverlayItem("ctrl", OverlayKind.KEY, 0xA2, x = 22f, y = 82f, size = 56f, radius = 28f, display = "Ctrl"),
        OverlayItem("space", OverlayKind.KEY, 0x20, x = 83f, y = 78f, size = 64f, radius = 32f, display = "Spasi"),
        OverlayItem("stick", OverlayKind.STICK_KEYS, keys = listOf(0x57, 0x53, 0x41, 0x44), x = 18f, y = 78f, size = 120f, radius = 60f),
        OverlayItem("lmb", OverlayKind.MOUSE, 0, x = 68f, y = 54f, size = 56f, radius = 28f),
        OverlayItem("rmb", OverlayKind.MOUSE, 1, x = 84f, y = 54f, size = 56f, radius = 28f),
    )

    fun qwerty(): List<OverlayItem> {
        val out = ArrayList<OverlayItem>()
        fun key(id: String, vk: Int, label: String, x: Float, y: Float, size: Float = 42f) =
            out.add(OverlayItem(id, OverlayKind.KEY, vk, x = x, y = y, size = size, radius = size / 2f, display = label))
        "QWERTYUIOP".forEachIndexed { i, c -> key("q$c", c.code, c.toString(), 8f + i * 8.8f, 46f) }
        "ASDFGHJKL".forEachIndexed { i, c -> key("q$c", c.code, c.toString(), 12.5f + i * 8.8f, 59f) }
        "ZXCVBNM".forEachIndexed { i, c -> key("q$c", c.code, c.toString(), 21.5f + i * 8.8f, 72f) }
        key("tab", 0x09, "Tab", 4.5f, 59f, 44f)
        key("shift", 0xA0, "Shift", 8f, 72f, 48f)
        key("ctrl", 0xA2, "Ctrl", 12f, 86f, 46f)
        key("alt", 0xA4, "Alt", 24f, 86f, 46f)
        key("space", 0x20, "Space", 50f, 86f, 64f)
        key("enter", 0x0D, "Enter", 77f, 86f, 50f)
        key("back", 0x08, "⌫", 92f, 46f, 46f)
        key("esc", 0x1B, "Esc", 4.5f, 46f, 44f)
        return out
    }

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
        key("numenter", 0x0D, "Ent", 86f, 85f)
        listOf("/" to 0x6F, "*" to 0x6A, "−" to 0x6D, "+" to 0x6B).forEachIndexed { i, (label, vk) ->
            key("numop$i", vk, label, 56f, 46f + i * 13f)
        }
        return out
    }

    fun gamepad(): List<OverlayItem> = listOf(
        OverlayItem("gsl", OverlayKind.GAMEPAD_STICK_L, 0, x = 18f, y = 72f, size = 128f, radius = 64f, display = "L"),
        OverlayItem("gsr", OverlayKind.GAMEPAD_STICK_R, 1, x = 70f, y = 76f, size = 116f, radius = 58f, display = "R"),
        OverlayItem("ga", OverlayKind.GAMEPAD_BUTTON, 0x1000, x = 86f, y = 69f, size = 50f, radius = 25f, display = "A"),
        OverlayItem("gb", OverlayKind.GAMEPAD_BUTTON, 0x2000, x = 94f, y = 60f, size = 50f, radius = 25f, display = "B"),
        OverlayItem("gx", OverlayKind.GAMEPAD_BUTTON, 0x4000, x = 78f, y = 60f, size = 50f, radius = 25f, display = "X"),
        OverlayItem("gy", OverlayKind.GAMEPAD_BUTTON, 0x8000, x = 86f, y = 51f, size = 50f, radius = 25f, display = "Y"),
        OverlayItem("gup", OverlayKind.GAMEPAD_BUTTON, 0x0001, x = 37f, y = 55f, size = 46f, radius = 23f, display = "↑"),
        OverlayItem("gdown", OverlayKind.GAMEPAD_BUTTON, 0x0002, x = 37f, y = 73f, size = 46f, radius = 23f, display = "↓"),
        OverlayItem("gleft", OverlayKind.GAMEPAD_BUTTON, 0x0004, x = 30f, y = 64f, size = 46f, radius = 23f, display = "←"),
        OverlayItem("gright", OverlayKind.GAMEPAD_BUTTON, 0x0008, x = 44f, y = 64f, size = 46f, radius = 23f, display = "→"),
        OverlayItem("glb", OverlayKind.GAMEPAD_BUTTON, 0x0100, x = 14f, y = 36f, size = 54f, radius = 27f, display = "LB"),
        OverlayItem("grb", OverlayKind.GAMEPAD_BUTTON, 0x0200, x = 86f, y = 36f, size = 54f, radius = 27f, display = "RB"),
        OverlayItem("glt", OverlayKind.GAMEPAD_TRIGGER_L, 0, x = 28f, y = 36f, size = 54f, radius = 27f, display = "LT"),
        OverlayItem("grt", OverlayKind.GAMEPAD_TRIGGER_R, 1, x = 72f, y = 36f, size = 54f, radius = 27f, display = "RT"),
        OverlayItem("gback", OverlayKind.GAMEPAD_BUTTON, 0x0020, x = 45f, y = 86f, size = 46f, radius = 23f, display = "Back"),
        OverlayItem("gstart", OverlayKind.GAMEPAD_BUTTON, 0x0010, x = 55f, y = 86f, size = 46f, radius = 23f, display = "Start"),
    )

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
        }.getOrElse { mouse() }
    }
}

private fun OverlayItem.label(): String = when (kind) {
    OverlayKind.KEY -> display.ifBlank { OverlayKeys.label(code) }
    OverlayKind.CHORD -> display.ifBlank { keys.joinToString("+") { OverlayKeys.label(it) } }
    OverlayKind.MOUSE -> display.ifBlank { listOf("Kiri", "Kanan", "Tengah", "Back", "Maju").getOrElse(code) { "Mouse" } }
    OverlayKind.SCROLL -> if (code > 0) "Scr ↑" else "Scr ↓"
    OverlayKind.SCROLL_X -> if (code > 0) "Scr →" else "Scr ←"
    OverlayKind.SCROLL_WHEEL -> "Scroll"
    OverlayKind.TOGGLE -> "Mode"
    OverlayKind.STICK_KEYS -> display.ifBlank { "WASD" }
    OverlayKind.STICK_MOUSE -> "Mouse"
    OverlayKind.GAMEPAD_BUTTON -> display.ifBlank { "Pad" }
    OverlayKind.GAMEPAD_STICK_L -> display.ifBlank { "L" }
    OverlayKind.GAMEPAD_STICK_R -> display.ifBlank { "R" }
    OverlayKind.GAMEPAD_TRIGGER_L -> display.ifBlank { "LT" }
    OverlayKind.GAMEPAD_TRIGGER_R -> display.ifBlank { "RT" }
}

class OverlayHolds(private val send: (ByteArray) -> Unit) {
    private val owners = mutableMapOf<String, OverlayItem>()
    private val counts = mutableMapOf<String, Int>()
    private val padButtons = mutableMapOf<String, Int>()
    private var padLt = 0
    private var padRt = 0
    private var padLx = 0
    private var padLy = 0
    private var padRx = 0
    private var padRy = 0

    fun down(owner: String, m: OverlayItem) {
        if (m.kind == OverlayKind.TOGGLE || m.kind == OverlayKind.STICK_KEYS || m.kind == OverlayKind.STICK_MOUSE || m.kind == OverlayKind.SCROLL_WHEEL) return
        if (m.kind == OverlayKind.SCROLL) { send(StreamXy.scroll(0, m.code)); return }
        if (m.kind == OverlayKind.SCROLL_X) { send(StreamXy.scroll(m.code, 0)); return }
        if (m.kind == OverlayKind.GAMEPAD_BUTTON) { gamepadButton(owner, m.code, true); return }
        if (m.kind == OverlayKind.GAMEPAD_TRIGGER_L || m.kind == OverlayKind.GAMEPAD_TRIGGER_R) {
            owners[owner] = m
            gamepadTrigger(m.kind, 1f)
            return
        }
        if (owners.containsKey(owner)) return
        owners[owner] = m
        actions(m).forEach { a ->
            val k = "${a.kind}:${a.code}"
            val n = counts[k] ?: 0
            counts[k] = n + 1
            if (n == 0) {
                if (a.kind == OverlayKind.KEY) send(StreamXy.key(a.code, true))
                else if (a.kind == OverlayKind.MOUSE) send(StreamXy.button(a.code, true))
            }
        }
    }

    fun up(owner: String) {
        padButtons.remove(owner)?.let { flushGamepad(); return }
        val m = owners.remove(owner) ?: return
        if (m.kind == OverlayKind.GAMEPAD_TRIGGER_L || m.kind == OverlayKind.GAMEPAD_TRIGGER_R) { gamepadTrigger(m.kind, 0f); return }
        actions(m).asReversed().forEach { a ->
            val k = "${a.kind}:${a.code}"
            val n = (counts[k] ?: 1) - 1
            if (n > 0) counts[k] = n else {
                counts.remove(k)
                if (a.kind == OverlayKind.KEY) send(StreamXy.key(a.code, false))
                else if (a.kind == OverlayKind.MOUSE) send(StreamXy.button(a.code, false))
            }
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

@Composable
fun ControlOverlay(
    items: List<OverlayItem>,
    edit: Boolean,
    send: (ByteArray) -> Unit,
    onItems: (List<OverlayItem>) -> Unit,
    onToggleTouch: () -> Unit,
    onEdit: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    val holds = remember { OverlayHolds(send) }
    DisposableEffect(Unit) { onDispose { holds.reset() } }
    var selected by remember { mutableStateOf<String?>(null) }
    var library by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        items.forEach { m ->
            OverlayButton(
                m = m,
                edit = edit,
                selected = selected == m.id,
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
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Color(0xF7FFFFFF)).padding(10.dp),
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Tambah", library) { library = !library; selected = null }
                    Chip("Simpan") { onSave(); onEdit(false) }
                    Chip("Selesai") { onEdit(false) }
                    Chip("Hapus") {
                        val id = selected
                        if (id != null) { onItems(items.filter { it.id != id }); selected = null }
                    }
                }
                if (library) LibraryPanel(items.size, onPreset = { onItems(it); library = false }) { add ->
                    if (items.size >= MAX_OVERLAY_ITEMS) return@LibraryPanel
                    onItems(items + add)
                    selected = add.id
                    library = false
                }
                val cur = items.find { it.id == selected }
                if (cur != null && !library) {
                    XyText("${cur.label()} · ${cur.size.roundToInt()}px · geser untuk posisi, tahan saat bermain", Xy.caption)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Chip("−") { onItems(items.map { if (it.id == cur.id) it.copy(size = (it.size - 8f).coerceAtLeast(34f), radius = ((it.size - 8f) / 2).coerceAtLeast(0f)) else it }) }
                        Chip("+") { onItems(items.map { if (it.id == cur.id) it.copy(size = (it.size + 8f).coerceAtMost(180f), radius = ((it.size + 8f) / 2).coerceAtMost(90f)) else it }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayButton(
    m: OverlayItem,
    edit: Boolean,
    selected: Boolean,
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
            StickPad(m, edit, left, top, px, parentW, parentH, holds, send, onSelect, onMove)
            return
        }
        OverlayKind.SCROLL_WHEEL -> {
            ScrollWheel(m, edit, left, top, px, parentW, parentH, send, onSelect, onMove)
            return
        }
        OverlayKind.GAMEPAD_STICK_L, OverlayKind.GAMEPAD_STICK_R -> {
            GamepadStickPad(m, edit, left, top, px, parentW, parentH, holds, onSelect, onMove)
            return
        }
        OverlayKind.GAMEPAD_TRIGGER_L, OverlayKind.GAMEPAD_TRIGGER_R -> {
            GamepadTriggerPad(m, edit, left, top, px, parentW, parentH, holds, onSelect, onMove)
            return
        }
        else -> Unit
    }
    RoundControl(
        m = m,
        selected = selected,
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
    Box(
        Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(m.size.dp)
            .clip(CircleShape)
            .background(if (selected) Xy.accent else Color(0xDDFDFDFD))
            .border(2.dp, if (selected) Xy.accent else Color.White.copy(alpha = 0.85f), CircleShape)
            .border(1.dp, if (selected) Color.White.copy(alpha = 0.55f) else Xy.line, CircleShape)
            .pointerInput(edit, m.id) {
                if (edit) detectDragGestures(
                    onDragStart = { onSelect() },
                    onDrag = { change, drag ->
                        change.consume()
                        val nx = ((left + px / 2 + drag.x) / parentW * 100f).coerceIn(0f, 100f)
                        val ny = ((top + px / 2 + drag.y) / parentH * 100f).coerceIn(0f, 100f)
                        onMove(nx, ny)
                    },
                ) else detectTapGestures(
                    onPress = {
                        if (m.kind == OverlayKind.TOGGLE) onToggleTouch()
                        else {
                            holds.down(m.id, m)
                            tryAwaitRelease()
                            holds.up(m.id)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        XyText(m.label(), Xy.body.copy(fontSize = 12.sp, color = if (selected) Color.White else Xy.textHi))
    }
}

@Composable
private fun StickPad(
    m: OverlayItem,
    edit: Boolean,
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
    val dirs = m.keys.ifEmpty { listOf(0x57, 0x53, 0x41, 0x44) }
    Box(
        Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(m.size.dp)
            .clip(CircleShape)
            .background(Color(0xBFFFFFFF))
            .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape)
            .border(1.dp, Xy.line, CircleShape)
            .pointerInput(edit, m.id) {
                if (edit) detectDragGestures(
                    onDragStart = { onSelect() },
                    onDrag = { change, drag ->
                        change.consume()
                        onMove(
                            ((left + px / 2 + drag.x) / parentW * 100f).coerceIn(0f, 100f),
                            ((top + px / 2 + drag.y) / parentH * 100f).coerceIn(0f, 100f),
                        )
                    },
                ) else detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        val c = Offset(px / 2, px / 2)
                        val max = px * 0.42f
                        var raw = change.position - c
                        val len = hypot(raw.x, raw.y)
                        if (len > max) raw = raw / len * max
                        knob = raw
                        val vx = (raw.x / max).coerceIn(-1f, 1f)
                        val vy = (raw.y / max).coerceIn(-1f, 1f)
                        if (m.kind == OverlayKind.STICK_KEYS) {
                            val pressed = listOf(vy < -0.32f, vy > 0.32f, vx < -0.32f, vx > 0.32f)
                            pressed.forEachIndexed { i, on ->
                                val owner = "stick:${m.id}:$i"
                                if (on) holds.down(owner, OverlayItem(owner, OverlayKind.KEY, dirs.getOrElse(i) { 0 }, x = 0f, y = 0f, size = 8f, radius = 4f))
                                else holds.up(owner)
                            }
                        } else if (len > max * 0.10f) {
                            send(StreamXy.moveRel((vx * 18).roundToInt(), (vy * 18).roundToInt()))
                        }
                    },
                    onDragEnd = {
                        knob = Offset.Zero
                        for (i in 0..3) holds.up("stick:${m.id}:$i")
                    },
                    onDragCancel = {
                        knob = Offset.Zero
                        for (i in 0..3) holds.up("stick:${m.id}:$i")
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size((m.size * 0.30f).dp).offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }.clip(CircleShape).background(Xy.accent))
        XyText(if (m.kind == OverlayKind.STICK_KEYS) m.label() else "MOUSE", Xy.label.copy(fontSize = 9.sp), modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp))
    }
}

@Composable
private fun GamepadStickPad(
    m: OverlayItem,
    edit: Boolean,
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
    Box(
        Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(m.size.dp)
            .clip(CircleShape)
            .background(Color(0xBFFFFFFF))
            .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape)
            .border(1.dp, Xy.line, CircleShape)
            .pointerInput(edit, m.id) {
                if (edit) detectDragGestures(
                    onDragStart = { onSelect() },
                    onDrag = { change, drag ->
                        change.consume()
                        onMove(
                            ((left + px / 2 + drag.x) / parentW * 100f).coerceIn(0f, 100f),
                            ((top + px / 2 + drag.y) / parentH * 100f).coerceIn(0f, 100f),
                        )
                    },
                ) else detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        val center = Offset(px / 2, px / 2)
                        val max = px * 0.42f
                        var raw = change.position - center
                        val len = hypot(raw.x, raw.y)
                        if (len > max) raw = raw / len * max
                        knob = raw
                        val x = (raw.x / max).coerceIn(-1f, 1f)
                        val y = (-raw.y / max).coerceIn(-1f, 1f)
                        holds.gamepadStick(m.kind, x, y)
                    },
                    onDragEnd = { knob = Offset.Zero; holds.gamepadStick(m.kind, 0f, 0f) },
                    onDragCancel = { knob = Offset.Zero; holds.gamepadStick(m.kind, 0f, 0f) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size((m.size * 0.30f).dp).offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }.clip(CircleShape).background(Xy.accent))
        XyText(m.label(), Xy.label.copy(fontSize = 10.sp), modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp))
    }
}

@Composable
private fun GamepadTriggerPad(
    m: OverlayItem,
    edit: Boolean,
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
        selected = false,
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
    left: Float,
    top: Float,
    px: Float,
    parentW: Float,
    parentH: Float,
    send: (ByteArray) -> Unit,
    onSelect: () -> Unit,
    onMove: (Float, Float) -> Unit,
) {
    var knob by remember { mutableStateOf(0f) }
    Box(
        Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(width = (m.size * 0.72f).dp, height = m.size.dp)
            .clip(RoundedCornerShape(m.radius.dp.coerceAtLeast(18.dp)))
            .background(Color(0xDDFDFDFD))
            .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(m.radius.dp.coerceAtLeast(18.dp)))
            .border(1.dp, Xy.line, RoundedCornerShape(m.radius.dp.coerceAtLeast(18.dp)))
            .pointerInput(edit, m.id) {
                if (edit) detectDragGestures(
                    onDragStart = { onSelect() },
                    onDrag = { change, drag ->
                        change.consume()
                        onMove(
                            ((left + px / 2 + drag.x) / parentW * 100f).coerceIn(0f, 100f),
                            ((top + px / 2 + drag.y) / parentH * 100f).coerceIn(0f, 100f),
                        )
                    },
                ) else detectDragGestures(
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
        Box(
            Modifier.size((m.size * 0.42f).dp)
                .offset { IntOffset(0, knob.roundToInt()) }
                .clip(RoundedCornerShape(999.dp))
                .background(Xy.accent),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryPanel(count: Int, onPreset: (List<OverlayItem>) -> Unit, add: (OverlayItem) -> Unit) {
    Column(Modifier.height(260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        XyText("Preset & kontrol ($count/$MAX_OVERLAY_ITEMS)", Xy.caption)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Preset FPS") { onPreset(OverlayLayouts.fps()) }
            Chip("Preset mouse") { onPreset(OverlayLayouts.mouse()) }
            Chip("Preset QWERTY") { onPreset(OverlayLayouts.qwerty()) }
            Chip("Preset F1–F12") { onPreset(OverlayLayouts.functionRow()) }
            Chip("Preset numpad") { onPreset(OverlayLayouts.numpad()) }
            Chip("Preset gamepad") { onPreset(OverlayLayouts.gamepad()) }
            Chip("Stick WASD") { add(OverlayItem("stk${System.nanoTime()}", OverlayKind.STICK_KEYS, keys = listOf(0x57, 0x53, 0x41, 0x44), x = 20f, y = 70f, size = 128f, radius = 64f)) }
            Chip("Stick panah") { add(OverlayItem("stk${System.nanoTime()}", OverlayKind.STICK_KEYS, keys = listOf(0x26, 0x28, 0x25, 0x27), x = 20f, y = 70f, size = 128f, radius = 64f, display = "Panah")) }
            Chip("Stick mouse") { add(OverlayItem("stm${System.nanoTime()}", OverlayKind.STICK_MOUSE, x = 80f, y = 70f, size = 128f, radius = 64f)) }
            Chip("Joystick L") { add(OverlayItem("gsl${System.nanoTime()}", OverlayKind.GAMEPAD_STICK_L, x = 18f, y = 72f, size = 128f, radius = 64f, display = "L")) }
            Chip("Joystick R") { add(OverlayItem("gsr${System.nanoTime()}", OverlayKind.GAMEPAD_STICK_R, x = 72f, y = 72f, size = 116f, radius = 58f, display = "R")) }
            Chip("Klik kiri") { add(item(OverlayKind.MOUSE, 0)) }
            Chip("Klik kanan") { add(item(OverlayKind.MOUSE, 1)) }
            Chip("Klik tengah") { add(item(OverlayKind.MOUSE, 2)) }
            Chip("Scroll geser") { add(OverlayItem("sw${System.nanoTime()}", OverlayKind.SCROLL_WHEEL, x = 16f, y = 72f, size = 88f, radius = 44f, display = "Scroll")) }
            Chip("Scroll ↑") { add(item(OverlayKind.SCROLL, 120)) }
            Chip("Scroll ↓") { add(item(OverlayKind.SCROLL, -120)) }
            Chip("Scroll ←") { add(item(OverlayKind.SCROLL_X, -120)) }
            Chip("Scroll →") { add(item(OverlayKind.SCROLL_X, 120)) }
            Chip("Mode sentuh") { add(item(OverlayKind.TOGGLE, 0)) }
            listOf("W" to 0x57, "A" to 0x41, "S" to 0x53, "D" to 0x44, "Spasi" to 0x20, "Shift" to 0xA0, "Ctrl" to 0xA2, "Alt" to 0xA4, "Esc" to 0x1B, "Enter" to 0x0D, "Tab" to 0x09, "Win" to 0x5B).forEach { (n, vk) ->
                Chip(n) { add(item(OverlayKind.KEY, vk, n)) }
            }
            (1..12).forEach { i -> Chip("F$i") { add(item(OverlayKind.KEY, 0x6F + i, "F$i")) } }
            (0..9).forEach { i -> Chip("Num $i") { add(item(OverlayKind.KEY, 0x60 + i, "N$i")) } }
            listOf("A" to 0x1000, "B" to 0x2000, "X" to 0x4000, "Y" to 0x8000, "LB" to 0x0100, "RB" to 0x0200, "Start" to 0x0010, "Back" to 0x0020).forEach { (n, bit) ->
                Chip("Pad $n") { add(item(OverlayKind.GAMEPAD_BUTTON, bit, n)) }
            }
            Chip("LT") { add(item(OverlayKind.GAMEPAD_TRIGGER_L, 0, "LT")) }
            Chip("RT") { add(item(OverlayKind.GAMEPAD_TRIGGER_R, 1, "RT")) }
            Chip("Ctrl+C") { add(OverlayItem("c${System.nanoTime()}", OverlayKind.CHORD, keys = listOf(0xA2, 0x43), x = 50f, y = 50f, size = 56f, radius = 28f, display = "Copy")) }
            Chip("Ctrl+V") { add(OverlayItem("c${System.nanoTime()}", OverlayKind.CHORD, keys = listOf(0xA2, 0x56), x = 50f, y = 50f, size = 56f, radius = 28f, display = "Paste")) }
            Chip("Ctrl+Z") { add(OverlayItem("c${System.nanoTime()}", OverlayKind.CHORD, keys = listOf(0xA2, 0x5A), x = 50f, y = 50f, size = 56f, radius = 28f, display = "Undo")) }
            Chip("Alt+Tab") { add(OverlayItem("c${System.nanoTime()}", OverlayKind.CHORD, keys = listOf(0xA4, 0x09), x = 50f, y = 50f, size = 56f, radius = 28f, display = "Alt+Tab")) }
            Chip("Alt+F4") { add(OverlayItem("c${System.nanoTime()}", OverlayKind.CHORD, keys = listOf(0xA4, 0x73), x = 50f, y = 50f, size = 56f, radius = 28f, display = "Alt+F4")) }
            Chip("↑") { add(item(OverlayKind.KEY, 0x26, "↑")) }
            Chip("↓") { add(item(OverlayKind.KEY, 0x28, "↓")) }
            Chip("←") { add(item(OverlayKind.KEY, 0x25, "←")) }
            Chip("→") { add(item(OverlayKind.KEY, 0x27, "→")) }
        }
    }
}

private fun item(kind: OverlayKind, code: Int, display: String = "") =
    OverlayItem("n${System.nanoTime()}", kind, code, x = 50f, y = 55f, size = 56f, radius = 28f, display = display)

@Composable
private fun Chip(text: String, on: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.height(36.dp).widthIn(min = 44.dp).clip(RoundedCornerShape(Xy.pill))
            .background(if (on) Xy.accent else Xy.overlay)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { XyText(text, Xy.label.copy(fontSize = 12.sp, letterSpacing = 0.sp, color = if (on) Color.White else Xy.textHi)) }
}
