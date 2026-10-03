package id.xyverse.xydesk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xyverse.xydesk.ui.kit.Icon
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyIcon
import id.xyverse.xydesk.ui.kit.XyText

/** Aksi yang tersedia di dalam sesi; semua dikirim lewat `libstreamxy`. */
class SessionActions(
    val keyboard: () -> Unit,
    val quality: (preset: Int) -> Unit,
    val resolution: (mode: Int) -> Unit,
    val fps: (target: Int) -> Unit = {},
    val bitrate: (mbps: Int) -> Unit = {},
    val display: (index: Int) -> Unit,
    val touchMode: (direct: Boolean) -> Unit = {},
    val trackpadSpeed: (speed: Float) -> Unit = {},
    val naturalScroll: (on: Boolean) -> Unit = {},
    val audioMute: (muted: Boolean) -> Unit = {},
    val mic: (on: Boolean) -> Unit = {},
    val clipboardSync: (on: Boolean) -> Unit = {},
    val sendQuickKey: (combo: QuickKey) -> Unit = {},
    val stats: (show: Boolean) -> Unit = {},
    val centerCursor: () -> Unit = {},
    val present: () -> Unit = {},
    val dockOn: (on: Boolean) -> Unit = {},
    val dockSize: (size: Int) -> Unit = {},
    val dockKeys: (ids: List<String>) -> Unit = {},
    val overlayEdit: () -> Unit = {},
    val disconnect: () -> Unit,
)

/** Nilai awal panel, diambil dari preferensi tersimpan. */
data class SessionPrefs(
    val quality: Int = 0,
    val fps: Int = 60,
    val directTouch: Boolean = false,
    val trackpadSpeed: Float = 1.4f,
    val naturalScroll: Boolean = false,
    val showStats: Boolean = true,
    val dockOn: Boolean = true,
    val dockSize: Int = 1,
    val dockKeys: List<String> = DockCatalog.default,
    val autohideMs: Long = 0,
)

enum class QuickKey(val label: String) {
    ESC("Esc"), TAB("Tab"), WIN("Win"), ENTER("Enter"), COPY("Ctrl+C"), PASTE("Ctrl+V"), UNDO("Ctrl+Z"),
    ALT_TAB("Alt+Tab"), ALT_F4("Alt+F4"), F11("F11"), PRTSC("PrtSc"), CAD("CAD"),
}

private enum class Panel { NONE, CONTROLS, DOCK, SETTINGS }

private val glass = Color(0xF2FFFFFF)

/**
 * Rel vertikal tetap di tepi kanan: Keyboard, Kontrol, Atur, Putus. Panel terbuka
 * ke kiri rel; pegangan kecil di atas menyembunyikan rel jadi satu garis tipis.
 */
@Composable
fun SessionToolbar(actions: SessionActions, prefs: SessionPrefs = SessionPrefs()) {
    var panel by remember { mutableStateOf(Panel.NONE) }
    var hidden by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf(prefs.quality) }
    var fps by remember { mutableStateOf(prefs.fps) }
    var res by remember { mutableStateOf(-1) }
    var mbps by remember { mutableStateOf(0) }
    var monitor by remember { mutableStateOf(0) }
    var directTouch by remember { mutableStateOf(prefs.directTouch) }
    var speed by remember { mutableFloatStateOf(prefs.trackpadSpeed) }
    var natural by remember { mutableStateOf(prefs.naturalScroll) }
    var muted by remember { mutableStateOf(false) }
    var mic by remember { mutableStateOf(false) }
    var clip by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(prefs.showStats) }
    var dockOn by remember { mutableStateOf(prefs.dockOn) }
    var dockSize by remember { mutableStateOf(prefs.dockSize) }
    var dockKeys by remember { mutableStateOf(prefs.dockKeys) }
    val qLabels = listOf("Auto", "Sedang", "Tinggi", "Ultra")
    var touched by remember { mutableStateOf(0L) }
    if (prefs.autohideMs > 0) LaunchedEffect(touched, panel, hidden) {
        if (!hidden && panel == Panel.NONE) { kotlinx.coroutines.delay(prefs.autohideMs); hidden = true }
    }

    Row(
        Modifier.padding(end = 8.dp).pointerInput(Unit) { awaitEachGesture { awaitFirstDown(false, PointerEventPass.Initial); touched = System.currentTimeMillis() } },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(!hidden && panel != Panel.NONE, enter = slideInHorizontally { it / 2 } + fadeIn(), exit = slideOutHorizontally { it / 2 } + fadeOut()) {
            Column(
                Modifier.width(232.dp).clip(RoundedCornerShape(Xy.radiusL)).background(glass).border(1.dp, Xy.line, RoundedCornerShape(Xy.radiusL)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (panel == Panel.DOCK) {
                    DockEditor(dockOn, dockSize, dockKeys,
                        onOn = { dockOn = it; actions.dockOn(it) },
                        onSize = { dockSize = it; actions.dockSize(it) },
                    ) { dockKeys = it; actions.dockKeys(it) }
                } else if (panel == Panel.CONTROLS) {
                    XyText("KONTROL", Xy.label)
                    Wrap { QuickKey.entries.forEach { k -> Pill(k.label) { actions.sendQuickKey(k) } } }
                    Wrap {
                        Pill("Pusatkan kursor") { actions.centerCursor() }
                        Pill("Mode presentasi") { panel = Panel.NONE; actions.present() }
                    }
                    XyText("MODE SENTUH", Xy.label)
                    Wrap {
                        Pill("Trackpad", accent = !directTouch) { directTouch = false; actions.touchMode(false) }
                        Pill("Sentuh langsung", accent = directTouch) { directTouch = true; actions.touchMode(true) }
                    }
                    XyText("TRACKPAD", Xy.label)
                    Wrap {
                        Pill("−") { speed = (speed - 0.2f).coerceAtLeast(0.6f); actions.trackpadSpeed(speed) }
                        Pill("Kecepatan %.1f".format(speed), accent = true) {}
                        Pill("+") { speed = (speed + 0.2f).coerceAtMost(3f); actions.trackpadSpeed(speed) }
                        Pill("Scroll alami", accent = natural) { natural = !natural; actions.naturalScroll(natural) }
                    }
                    XyText("Ketuk klik kiri · ketuk 2 jari klik kanan · geser 2 jari scroll · ketuk-ketuk-tahan seret · 3 jari geser Alt+Tab", Xy.caption)
                } else {
                    XyText("KUALITAS", Xy.label)
                    Wrap { qLabels.forEachIndexed { i, l -> Pill(l, accent = quality == i) { quality = i; actions.quality(i) } } }
                    XyText("FPS · RESOLUSI", Xy.label)
                    Wrap {
                        listOf(30, 60).forEach { f -> Pill("$f fps", accent = fps == f) { fps = f; actions.fps(f) } }
                        Pill("Auto", accent = res < 0) { res = -1; quality = 0; actions.quality(0) }
                        listOf("720p", "1080p").forEachIndexed { i, l -> Pill(l, accent = res == i) { res = i; actions.resolution(i) } }
                    }
                    if (quality != 0) {
                        XyText("BITRATE", Xy.label)
                        Wrap { listOf(4, 8, 12, 20, 30).forEach { m -> Pill("$m Mbps", accent = mbps == m) { mbps = m; actions.bitrate(m) } } }
                    }
                    XyText("MONITOR · SESI", Xy.label)
                    Wrap {
                        Pill("Monitor ${monitor + 1}") { monitor = (monitor + 1) % 4; actions.display(monitor) }
                        Pill(if (muted) "Audio bisu" else "Audio", accent = !muted) { muted = !muted; actions.audioMute(muted) }
                        Pill(if (mic) "Mic nyala" else "Mic", accent = mic) { mic = !mic; actions.mic(mic) }
                        Pill("Clipboard", accent = clip) { clip = !clip; actions.clipboardSync(clip) }
                        Pill("Stats", accent = stats) { stats = !stats; actions.stats(stats) }
                    }
                    if (quality == 0) XyText("Auto: resolusi dan bitrate mengikuti jaringan (libxyadapt).", Xy.caption)
                }
            }
        }
        if (hidden) {
            Box(
                Modifier.size(width = 14.dp, height = 56.dp).clip(RoundedCornerShape(Xy.pill)).background(glass).border(1.dp, Xy.line, RoundedCornerShape(Xy.pill))
                    .clickable(remember { MutableInteractionSource() }, null) { hidden = false },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(3.dp, 22.dp).clip(CircleShape).background(Xy.accent)) }
        } else {
            Column(
                Modifier.clip(RoundedCornerShape(Xy.pill)).background(glass).border(1.dp, Xy.line, RoundedCornerShape(Xy.pill)).padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier.size(width = 44.dp, height = 22.dp)
                        .clickable(remember { MutableInteractionSource() }, null) { hidden = true; panel = Panel.NONE },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(20.dp, 4.dp).clip(CircleShape).background(Xy.textLow)) }
                RailButton(Icon.KEYBOARD, "Keyboard") { actions.keyboard() }
                RailButton(Icon.CONTROLS, "Kontrol", active = panel == Panel.CONTROLS) { panel = if (panel == Panel.CONTROLS) Panel.NONE else Panel.CONTROLS }
                RailButton(Icon.GRID, "Dok", active = panel == Panel.DOCK) { actions.overlayEdit(); panel = Panel.NONE }
                RailButton(Icon.SETTINGS, "Atur", active = panel == Panel.SETTINGS) { panel = if (panel == Panel.SETTINGS) Panel.NONE else Panel.SETTINGS }
                RailButton(Icon.POWER, "Putus", danger = true) { actions.disconnect() }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun RailButton(icon: Icon, label: String, active: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val bg = when { danger -> Xy.danger.copy(alpha = 0.1f); active -> Xy.accent; else -> Xy.overlay }
    val fg = when { danger -> Xy.danger; active -> Color.White; else -> Xy.textHi }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(44.dp).background(bg, CircleShape).clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { XyIcon(icon, tint = fg, size = 21.dp) }
        XyText(label, Xy.label.copy(fontSize = 9.5.sp, letterSpacing = 0.sp, color = if (danger) Xy.danger else Xy.textMid))
    }
}

/** Baris pil yang membungkus ke baris berikutnya bila penuh. */
@OptIn(ExperimentalLayoutApi::class)
/** Editor dok: nyala/mati, ukuran, dan pilihan tombol per kelompok; urutan = urutan dipilih. */
@Composable
private fun DockEditor(on: Boolean, size: Int, ids: List<String>, onOn: (Boolean) -> Unit, onSize: (Int) -> Unit, onIds: (List<String>) -> Unit) {
    Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        XyText("DOK KONTROL", Xy.label)
        Wrap {
            Pill(if (on) "Dok tampil" else "Dok sembunyi", accent = on) { onOn(!on) }
            listOf("S", "M", "L").forEachIndexed { i, l -> Pill(l, accent = size == i) { onSize(i) } }
            Pill("Reset") { onIds(DockCatalog.default) }
        }
        XyText("${ids.size} tombol dipilih · ketuk untuk tambah/hapus; urutan mengikuti saat dipilih.", Xy.caption)
        DockCatalog.groups.forEach { g ->
            XyText(g.uppercase(), Xy.label)
            Wrap { DockCatalog.all.filter { it.group == g }.forEach { k -> Pill(k.label, accent = k.id in ids) { onIds(if (k.id in ids) ids - k.id else ids + k.id) } } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Wrap(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
private fun Pill(text: String, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(Xy.pill)).background(if (accent) Xy.accent else Xy.overlay)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    ) { XyText(text, Xy.caption.copy(color = if (accent) Color.White else Xy.textHi)) }
}
