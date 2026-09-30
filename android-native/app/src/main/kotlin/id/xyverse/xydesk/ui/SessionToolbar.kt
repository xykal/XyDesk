package id.xyverse.xydesk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xyverse.xydesk.ui.kit.Icon
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyIcon
import id.xyverse.xydesk.ui.kit.XyText
import kotlin.math.roundToInt

/** Aksi yang tersedia di dalam sesi; semua dikirim lewat `libstreamxy`. */
class SessionActions(
    val keyboard: () -> Unit,
    val quality: (preset: Int) -> Unit,
    val resolution: (mode: Int) -> Unit,
    val display: (index: Int) -> Unit,
    val touchMode: (direct: Boolean) -> Unit = {},
    val audioMute: (muted: Boolean) -> Unit = {},
    val clipboardSync: (on: Boolean) -> Unit = {},
    val sendQuickKey: (combo: QuickKey) -> Unit = {},
    val toggleStats: () -> Unit,
    val disconnect: () -> Unit,
)

enum class QuickKey(val label: String) { ESC("Esc"), TAB("Tab"), WIN("Win"), COPY("Ctrl+C"), PASTE("Ctrl+V"), CAD("CAD") }

private enum class Panel { NONE, CONTROLS, SETTINGS }

private val glass = Color(0xF2FFFFFF)

/**
 * Rel vertikal di tepi kanan: Keyboard, Kontrol, Pengaturan, Putus. Bisa digeser
 * naik-turun; panel terbuka ke kiri rel. Ketuk pegangan kecil untuk menyembunyikan.
 */
@Composable
fun SessionToolbar(actions: SessionActions) {
    var panel by remember { mutableStateOf(Panel.NONE) }
    var hidden by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var quality by remember { mutableStateOf(0) }
    var res by remember { mutableStateOf(0) }
    var monitor by remember { mutableStateOf(0) }
    var directTouch by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var clip by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(true) }
    val qLabels = listOf("Auto", "Sedang", "Tinggi", "Ultra")

    Row(
        Modifier.offset { IntOffset(0, dragY.roundToInt()) }.padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(!hidden && panel != Panel.NONE, enter = slideInHorizontally { it / 2 } + fadeIn(), exit = slideOutHorizontally { it / 2 } + fadeOut()) {
            Column(
                Modifier.width(232.dp).clip(RoundedCornerShape(Xy.radiusL)).background(glass).border(1.dp, Xy.line, RoundedCornerShape(Xy.radiusL)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (panel == Panel.CONTROLS) {
                    XyText("KONTROL", Xy.label)
                    Wrap { QuickKey.entries.forEach { k -> Pill(k.label) { actions.sendQuickKey(k) } } }
                    XyText("MODE SENTUH", Xy.label)
                    Wrap {
                        Pill("Trackpad", accent = !directTouch) { directTouch = false; actions.touchMode(false) }
                        Pill("Sentuh langsung", accent = directTouch) { directTouch = true; actions.touchMode(true) }
                    }
                } else {
                    XyText("KUALITAS", Xy.label)
                    Wrap { qLabels.forEachIndexed { i, l -> Pill(l, accent = quality == i) { quality = i; actions.quality(i) } } }
                    XyText("RESOLUSI · MONITOR", Xy.label)
                    Wrap {
                        listOf("720p", "1080p").forEachIndexed { i, l -> Pill(l, accent = res == i) { res = i; actions.resolution(i) } }
                        Pill("Monitor ${monitor + 1}") { monitor = (monitor + 1) % 4; actions.display(monitor) }
                    }
                    XyText("SESI", Xy.label)
                    Wrap {
                        Pill(if (muted) "Audio bisu" else "Audio", accent = !muted) { muted = !muted; actions.audioMute(muted) }
                        Pill("Clipboard", accent = clip) { clip = !clip; actions.clipboardSync(clip) }
                        Pill("Stats", accent = stats) { stats = !stats; actions.toggleStats() }
                    }
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
                    Modifier.size(width = 44.dp, height = 22.dp).pointerInput(Unit) { detectDragGestures { c, d -> c.consume(); dragY += d.y } }
                        .clickable(remember { MutableInteractionSource() }, null) { hidden = true; panel = Panel.NONE },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(20.dp, 4.dp).clip(CircleShape).background(Xy.textLow)) }
                RailButton(Icon.KEYBOARD, "Keyboard") { actions.keyboard() }
                RailButton(Icon.CONTROLS, "Kontrol", active = panel == Panel.CONTROLS) { panel = if (panel == Panel.CONTROLS) Panel.NONE else Panel.CONTROLS }
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
