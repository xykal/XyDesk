package id.xyverse.xydesk.ui

import id.xyverse.xydesk.core.XyScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyText
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class OverlayKind { KEY, MOUSE, SCROLL, SCROLL_X, SCROLL_WHEEL, CHORD, TOGGLE, STICK_KEYS, STICK_MOUSE }

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
        OverlayItem("lmb", OverlayKind.MOUSE, 0, x = 70f, y = 68f, size = 56f, radius = 28f),
        OverlayItem("rmb", OverlayKind.MOUSE, 1, x = 84f, y = 68f, size = 56f, radius = 28f),
        OverlayItem("toggle", OverlayKind.TOGGLE, x = 77f, y = 84f, size = 52f, radius = 26f),
        OverlayItem("scu", OverlayKind.SCROLL, 120, x = 16f, y = 70f, size = 52f, radius = 26f),
        OverlayItem("scd", OverlayKind.SCROLL, -120, x = 16f, y = 84f, size = 52f, radius = 26f),
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
    fun toJson(items: List<OverlayItem>): String {
        val a = JSONArray()
        items.take(24).forEach { m ->
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
        if (raw.isNullOrBlank()) return mouse()
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
                    size = o.optDouble("size", 56.0).toFloat().coerceIn(36f, 160f),
                    radius = o.optDouble("radius", 28.0).toFloat().coerceIn(0f, 80f),
                    name = o.optString("name"),
                    display = o.optString("display"),
                )
            }.take(24)
        }.getOrElse { mouse() }
    }
}

private fun OverlayItem.label(): String = when (kind) {
    OverlayKind.KEY -> display.ifBlank { OverlayKeys.label(code) }
    OverlayKind.CHORD -> display.ifBlank { keys.joinToString("+") { OverlayKeys.label(it) } }
    OverlayKind.MOUSE -> listOf("Kiri", "Kanan", "Tengah", "Back", "Maju").getOrElse(code) { "Mouse" }
    OverlayKind.SCROLL -> if (code > 0) "Scr ↑" else "Scr ↓"
    OverlayKind.SCROLL_X -> if (code > 0) "Scr →" else "Scr ←"
    OverlayKind.SCROLL_WHEEL -> "Scroll"
    OverlayKind.TOGGLE -> "Mode"
    OverlayKind.STICK_KEYS -> display.ifBlank { "WASD" }
    OverlayKind.STICK_MOUSE -> "Mouse"
}

class OverlayHolds(private val send: (ByteArray) -> Unit) {
    private val owners = mutableMapOf<String, OverlayItem>()
    private val counts = mutableMapOf<String, Int>()
    fun down(owner: String, m: OverlayItem) {
        if (m.kind == OverlayKind.TOGGLE || m.kind == OverlayKind.STICK_KEYS || m.kind == OverlayKind.STICK_MOUSE || m.kind == OverlayKind.SCROLL_WHEEL) return
        if (m.kind == OverlayKind.SCROLL) { send(StreamXy.scroll(0, m.code)); return }
        if (m.kind == OverlayKind.SCROLL_X) { send(StreamXy.scroll(m.code, 0)); return }
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
        val m = owners.remove(owner) ?: return
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
    fun reset() { owners.keys.toList().forEach { up(it) } }
    private fun actions(m: OverlayItem) =
        if (m.kind == OverlayKind.CHORD) m.keys.map { m.copy(kind = OverlayKind.KEY, code = it) } else listOf(m)
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
                    if (items.size >= 24) return@LibraryPanel
                    onItems(items + add)
                    selected = add.id
                    library = false
                }
                val cur = items.find { it.id == selected }
                if (cur != null && !library) {
                    XyText("${cur.label()} · ${cur.size.roundToInt()}px", Xy.caption)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Chip("−") { onItems(items.map { if (it.id == cur.id) it.copy(size = (it.size - 8f).coerceAtLeast(36f), radius = ((it.size - 8f) / 2).coerceAtLeast(0f)) else it }) }
                        Chip("+") { onItems(items.map { if (it.id == cur.id) it.copy(size = (it.size + 8f).coerceAtMost(160f), radius = ((it.size + 8f) / 2).coerceAtMost(80f)) else it }) }
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
    if (m.kind == OverlayKind.STICK_KEYS || m.kind == OverlayKind.STICK_MOUSE) {
        StickPad(m, edit, left, top, px, parentW, parentH, holds, send, onSelect, onMove)
        return
    }
    if (m.kind == OverlayKind.SCROLL_WHEEL) {
        ScrollWheel(m, edit, left, top, px, parentW, parentH, send, onSelect, onMove)
        return
    }
    Box(
        Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(m.size.dp)
            .clip(RoundedCornerShape(m.radius.dp))
            .background(if (selected) Xy.accent else Color(0xE6FFFFFF))
            .border(1.dp, if (selected) Xy.accent else Xy.line, RoundedCornerShape(m.radius.dp))
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
    val dirs = m.keys.ifEmpty { listOf(0x57, 0x53, 0x41, 0x44) } // W S A D
    Box(
        Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(m.size.dp)
            .clip(CircleShape)
            .background(Color(0x99FFFFFF))
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
                    onDragStart = { },
                    onDrag = { change, _ ->
                        change.consume()
                        val c = Offset(px / 2, px / 2)
                        var v = change.position - c
                        val len = hypot(v.x, v.y).coerceAtLeast(1f)
                        v = v / len
                        knob = v * (px * 0.28f)
                        if (m.kind == OverlayKind.STICK_KEYS) {
                            val pressed = listOf(v.y < -0.28f, v.y > 0.28f, v.x < -0.28f, v.x > 0.28f)
                            pressed.forEachIndexed { i, on ->
                                val owner = "stick:${m.id}:$i"
                                if (on) holds.down(owner, OverlayItem(owner, OverlayKind.KEY, dirs.getOrElse(i) { 0 }, x = 0f, y = 0f, size = 8f, radius = 4f))
                                else holds.up(owner)
                            }
                        } else {
                            send(StreamXy.moveRel((v.x * 14).roundToInt(), (v.y * 14).roundToInt()))
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
        Box(Modifier.size((m.size * 0.28f).dp).offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }.clip(CircleShape).background(Xy.accent))
        XyText(if (m.kind == OverlayKind.STICK_KEYS) "KEY" else "MOUSE", Xy.label.copy(fontSize = 9.sp), modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp))
    }
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
            .background(Color(0xE6FFFFFF))
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
    Column(Modifier.height(240.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        XyText("Preset & kontrol ($count/24)", Xy.caption)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Preset mouse") { onPreset(OverlayLayouts.mouse()) }
            Chip("Preset FPS") { onPreset(OverlayLayouts.fps()) }
            Chip("Stick WASD") {
                add(OverlayItem("stk${System.nanoTime()}", OverlayKind.STICK_KEYS, keys = listOf(0x57, 0x53, 0x41, 0x44), x = 20f, y = 70f, size = 128f, radius = 64f))
            }
            Chip("Stick panah") {
            add(OverlayItem("stk${System.nanoTime()}", OverlayKind.STICK_KEYS, keys = listOf(0x26, 0x28, 0x25, 0x27), x = 20f, y = 70f, size = 128f, radius = 64f, display = "Panah"))
        }
        Chip("Stick mouse") {
            add(OverlayItem("stm${System.nanoTime()}", OverlayKind.STICK_MOUSE, x = 80f, y = 70f, size = 128f, radius = 64f))
        }
        Chip("Klik kiri") { add(item(OverlayKind.MOUSE, 0)) }
        Chip("Klik kanan") { add(item(OverlayKind.MOUSE, 1)) }
        Chip("Klik tengah") { add(item(OverlayKind.MOUSE, 2)) }
        Chip("Scroll geser") { add(OverlayItem("sw${System.nanoTime()}", OverlayKind.SCROLL_WHEEL, x = 16f, y = 72f, size = 88f, radius = 28f, display = "Scroll")) }
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
