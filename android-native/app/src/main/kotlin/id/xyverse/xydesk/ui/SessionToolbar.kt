package id.xyverse.xydesk.ui

import id.xyverse.xyadapt.DisplayRules
import id.xyverse.xyadapt.FpsOptions
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xyverse.xydesk.ui.kit.Icon
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xyadapt.PadKbm
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
    val overlayEdit: () -> Unit = {},
    /** Putar mode kontrol di layar: otomatis → selalu tampil → mati. */
    val overlayMode: () -> Unit = {},
    /** Tangkap mouse fisik: gerak relatif tak terbatas, kursor HP hilang. */
    val pointerCapture: (on: Boolean) -> Unit = {},
    /** Putar mode pad→keyboard/mouse: otomatis → selalu → mati. */
    val padKbm: () -> Unit = {},
    /** Buka pemilih berkas dan kirim ke PC. */
    val sendFile: () -> Unit = {},
    val disconnect: () -> Unit,
)

/**
 * Monitor host apa adanya, sebagaimana dilaporkan pesan `meta`.
 */
data class DisplayState(
    val displays: List<DisplayRules.HostDisplay> = emptyList(),
    val active: Int = 0,
)

/** Nilai awal panel, diambil dari preferensi tersimpan. */
data class SessionPrefs(
    val quality: Int = 0,
    val fps: Int = 60,
    val directTouch: Boolean = false,
    val trackpadSpeed: Float = 1.8f,
    val naturalScroll: Boolean = false,
    val showStats: Boolean = true,
    val autohideMs: Long = 0,
    /** Tangkapan pointer menyala untuk mouse fisik. */
    val pointerCapture: Boolean = true,
    /** Mode pemetaan gamepad fisik → keyboard/mouse (lihat PadKbm). */
    val padKbmMode: Int = 2,
    val padKbmLabel: String = "Otomatis",
    /** Anak tangga fps yang pantas untuk panel HP ini (lihat [FpsOptions]). */
    val fpsOptions: List<Int> = FpsOptions.BASE,
)

enum class QuickKey(val label: String) {
    ESC("⎋ Esc"), TAB("⇥ Tab"), WIN("❖ Win"), ENTER("↵ Enter"),
    COPY("⌃C"), PASTE("⌃V"), UNDO("⌃Z"), SAVE("⌃S"),
    ALT_TAB("⌥⇥"), ALT_F4("⌥F4"), F11("F11"), PRTSC("PrtSc"), CAD("⌃⌥⌦ CAD"),
}

private val kDarkGlass = Color(0xF008080E)
private val kDarkPillBg = Color(0x33262638)
private val kDarkBorder = Color(0x33A78BFA)

/**
 * Rel vertikal tetap di tepi kanan: Keyboard, Mapping, Tampil, Atur, Putus.
 * Desain hitam pekat gaming dengan neon aksen, tanpa duplikasi kontrol yang sudah ada di overlay.
 */
@Composable
fun SessionToolbar(
    actions: SessionActions,
    prefs: SessionPrefs = SessionPrefs(),
    /** Monitor host; `null` untuk pratinjau dan uji yang tidak punya sesi. */
    displayState: State<DisplayState>? = null,
) {
    var settingsOpen by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf(prefs.quality) }
    var fps by remember { mutableStateOf(prefs.fps) }
    var res by remember { mutableStateOf(-1) }
    var mbps by remember { mutableStateOf(0) }
    var directTouch by remember { mutableStateOf(prefs.directTouch) }
    var speed by remember { mutableFloatStateOf(prefs.trackpadSpeed) }
    var natural by remember { mutableStateOf(prefs.naturalScroll) }
    var muted by remember { mutableStateOf(false) }
    var mic by remember { mutableStateOf(false) }
    var clip by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(prefs.showStats) }
    var capture by remember { mutableStateOf(prefs.pointerCapture) }
    var padMode by remember { mutableStateOf(prefs.padKbmMode) }
    var padLabel by remember { mutableStateOf(prefs.padKbmLabel) }
    val qLabels = listOf("Auto", "Seimbang", "Lebih halus", "Paling halus")
    var touched by remember { mutableStateOf(0L) }
    if (prefs.autohideMs > 0) LaunchedEffect(touched, settingsOpen, hidden) {
        if (!hidden && !settingsOpen) { kotlinx.coroutines.delay(prefs.autohideMs); hidden = true }
    }

    Row(
        Modifier.padding(end = 8.dp).pointerInput(Unit) { awaitEachGesture { awaitFirstDown(false, PointerEventPass.Initial); touched = System.currentTimeMillis() } },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(!hidden && settingsOpen, enter = slideInHorizontally { it / 2 } + fadeIn(), exit = slideOutHorizontally { it / 2 } + fadeOut()) {
            Column(
                Modifier.width(248.dp).clip(RoundedCornerShape(Xy.radiusL)).background(kDarkGlass).border(1.dp, kDarkBorder, RoundedCornerShape(Xy.radiusL)).padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                XyText("KUALITAS & STREAM", Xy.label.copy(color = Xy.accent))
                Wrap { qLabels.forEachIndexed { i, l -> DarkPill(l, accent = quality == i) { quality = i; actions.quality(i) } } }

                XyText("FPS · RESOLUSI", Xy.label.copy(color = Xy.accent))
                Wrap {
                    prefs.fpsOptions.forEach { f -> DarkPill("$f fps", accent = fps == f) { fps = f; actions.fps(f) } }
                    DarkPill("Auto", accent = res < 0) { res = -1; quality = 0; actions.quality(0) }
                    listOf("720p", "1080p").forEachIndexed { i, l -> DarkPill(l, accent = res == i) { res = i; actions.resolution(i) } }
                }

                if (quality != 0) {
                    XyText("BITRATE", Xy.label.copy(color = Xy.accent))
                    Wrap { listOf(4, 8, 12, 20, 30).forEach { m -> DarkPill("$m Mbps", accent = mbps == m) { mbps = m; actions.bitrate(m) } } }
                }

                val layar = displayState?.value ?: DisplayState()
                XyText("LAYAR PC", Xy.label.copy(color = Xy.accent))
                if (DisplayRules.shouldOffer(layar.displays)) {
                    Wrap {
                        layar.displays.forEachIndexed { i, d ->
                            DarkPill(DisplayRules.label(d, i), accent = d.index == layar.active) {
                                DisplayRules.request(layar.displays, layar.active, d.index)?.let(actions.display)
                            }
                        }
                    }
                } else if (layar.displays.size == 1) {
                    XyText("PC ini hanya punya satu layar.", Xy.caption.copy(color = Color.White.copy(alpha = 0.6f)))
                } else {
                    XyText("Daftar layar belum diterima dari PC.", Xy.caption.copy(color = Color.White.copy(alpha = 0.6f)))
                }

                XyText("SENTUH & TRACKPAD", Xy.label.copy(color = Xy.accent))
                Wrap {
                    DarkPill("Trackpad", accent = !directTouch) { directTouch = false; actions.touchMode(false) }
                    DarkPill("Sentuh langsung", accent = directTouch) { directTouch = true; actions.touchMode(true) }
                }
                Wrap {
                    DarkPill("−") { speed = (speed - 0.2f).coerceAtLeast(0.6f); actions.trackpadSpeed(speed) }
                    DarkPill("Kecepatan %.1f".format(speed), accent = true) {}
                    DarkPill("+") { speed = (speed + 0.2f).coerceAtMost(3f); actions.trackpadSpeed(speed) }
                    DarkPill("Scroll alami", accent = natural) { natural = !natural; actions.naturalScroll(natural) }
                }

                XyText("FITUR SESI", Xy.label.copy(color = Xy.accent))
                Wrap {
                    DarkPill(if (muted) "Audio bisu" else "Audio", accent = !muted) { muted = !muted; actions.audioMute(muted) }
                    DarkPill(if (mic) "Mic nyala" else "Mic", accent = mic) { mic = !mic; actions.mic(mic) }
                    DarkPill("Clipboard", accent = clip) { clip = !clip; actions.clipboardSync(clip) }
                    DarkPill("Stats HUD", accent = stats) { stats = !stats; actions.stats(stats) }
                    DarkPill("Tangkap mouse", accent = capture) { capture = !capture; actions.pointerCapture(capture) }
                    DarkPill("Pad → kibor: $padLabel", accent = padMode == 1) {
                        padMode = PadKbm.nextMode(padMode)
                        padLabel = PadKbm.modeLabel(padMode)
                        actions.padKbm()
                    }
                }

                XyText("ALAT SESI", Xy.label.copy(color = Xy.accent))
                Wrap {
                    DarkPill("Pusatkan kursor") { actions.centerCursor() }
                    DarkPill("Kirim berkas") { settingsOpen = false; actions.sendFile() }
                    DarkPill("Mode presentasi") { settingsOpen = false; actions.present() }
                }
            }
        }
        if (hidden) {
            Box(
                Modifier.size(width = 16.dp, height = 58.dp).clip(RoundedCornerShape(Xy.pill)).background(kDarkGlass).border(1.dp, kDarkBorder, RoundedCornerShape(Xy.pill))
                    .clickable(remember { MutableInteractionSource() }, null) { hidden = false },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(3.dp, 22.dp).clip(CircleShape).background(Xy.accent)) }
        } else {
            Column(
                Modifier.clip(RoundedCornerShape(Xy.pill)).background(kDarkGlass).border(1.dp, kDarkBorder, RoundedCornerShape(Xy.pill)).padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier.size(width = 44.dp, height = 22.dp)
                        .clickable(remember { MutableInteractionSource() }, null) { hidden = true; settingsOpen = false },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(20.dp, 4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f))) }
                RailButton(Icon.KEYBOARD, "Kibor") { actions.keyboard() }
                RailButton(Icon.GRID, "Mapping") { actions.overlayEdit(); settingsOpen = false }
                RailButton(Icon.CONTROLS, "Tampil") { actions.overlayMode(); settingsOpen = false }
                RailButton(Icon.SETTINGS, "Atur", active = settingsOpen) { settingsOpen = !settingsOpen }
                RailButton(Icon.POWER, "Putus", danger = true) { actions.disconnect() }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun RailButton(icon: Icon, label: String, active: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val bg = when { danger -> Xy.danger.copy(alpha = 0.18f); active -> Xy.accent; else -> Color(0x2E1E1E2C) }
    val fg = when { danger -> Xy.danger; active -> Color.White; else -> Color(0xFFE2E8F0) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(44.dp).background(bg, CircleShape).border(1.dp, if (active) Color.White.copy(alpha = 0.6f) else Color(0x22FFFFFF), CircleShape)
                .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { XyIcon(icon, tint = fg, size = 20.dp) }
        XyText(label, Xy.label.copy(fontSize = 9.5.sp, letterSpacing = 0.sp, color = if (danger) Xy.danger else Color(0xFF94A3B8)))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Wrap(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
private fun DarkPill(text: String, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(Xy.pill)).background(if (accent) Xy.accent else kDarkPillBg)
            .border(1.dp, if (accent) Color.White.copy(alpha = 0.5f) else Color(0x22FFFFFF), RoundedCornerShape(Xy.pill))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick).padding(horizontal = 11.dp, vertical = 7.dp),
    ) { XyText(text, Xy.caption.copy(color = if (accent) Color.White else Color(0xFFE2E8F0))) }
}
