package id.xyverse.xydesk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyText

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

enum class QuickKey(val label: String) {
    ESC("Esc"),
    TAB("Tab"),
    WIN("Win"),
    COPY("Ctrl+C"),
    PASTE("Ctrl+V"),
    CAD("CAD"),
}

/**
 * Pil mengambang di bawah layar: satu titik kecil saat tersembunyi, ketuk untuk
 * membuka. Tidak memakai widget sistem; muncul/hilang dengan slide + fade.
 */
@Composable
fun SessionToolbar(actions: SessionActions) {
    var open by remember { mutableStateOf(false) }
    var keysOpen by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf(0) }
    var clip by remember { mutableStateOf(false) }
    var res by remember { mutableStateOf(0) }
    var monitor by remember { mutableStateOf(0) }
    var directTouch by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    val labels = listOf("Auto", "Sedang", "Tinggi", "Ultra")
    val resLabels = listOf("720p", "1080p")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(bottom = 10.dp),
    ) {
        AnimatedVisibility(open && keysOpen, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(Xy.radiusL))
                    .background(Color(0xF2FFFFFF))
                    .border(1.dp, Xy.line, RoundedCornerShape(Xy.radiusL))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuickKey.entries.forEach { k ->
                    Pill(k.label) { actions.sendQuickKey(k) }
                }
            }
        }
        AnimatedVisibility(open, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(Xy.radiusL))
                    .background(Color(0xF2FFFFFF))
                    .border(1.dp, Xy.line, RoundedCornerShape(Xy.radiusL))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill("Keyboard") { actions.keyboard() }
                Pill("Tombol", accent = keysOpen) { keysOpen = !keysOpen }
                Pill(if (directTouch) "Sentuh" else "Trackpad", accent = directTouch) {
                    directTouch = !directTouch
                    actions.touchMode(directTouch)
                }
                Pill(if (muted) "Bisu" else "Audio", accent = !muted) {
                    muted = !muted
                    actions.audioMute(muted)
                }
                Pill(if (clip) "Clipboard on" else "Clipboard", accent = clip) {
                    clip = !clip
                    actions.clipboardSync(clip)
                }
                Pill(labels[quality], accent = true) {
                    quality = (quality + 1) % labels.size
                    actions.quality(quality)
                }
                Pill(resLabels[res], accent = true) {
                    res = (res + 1) % resLabels.size
                    actions.resolution(res)
                }
                Pill("Monitor ${monitor + 1}") {
                    monitor = (monitor + 1) % 4
                    actions.display(monitor)
                }
                Pill("Stats") { actions.toggleStats() }
                Pill("Putus", danger = true) { actions.disconnect() }
                Pill("×") { open = false }
            }
        }
        if (!open) {
            Box(
                Modifier
                    .width(56.dp)
                    .height(22.dp)
                    .clickable(remember { MutableInteractionSource() }, null) { open = true },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(36.dp).height(5.dp).clip(CircleShape).background(Color(0x99FFFFFF)))
            }
        }
    }
}

@Composable
private fun Pill(text: String, accent: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val bg = when {
        danger -> Xy.danger.copy(alpha = 0.18f)
        accent -> Xy.accent.copy(alpha = 0.25f)
        else -> Xy.overlay
    }
    val fg = when {
        danger -> Xy.danger
        accent -> Xy.lavender
        else -> Xy.textHi
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(Xy.radiusM))
            .background(bg)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        XyText(text, Xy.caption.copy(color = fg))
    }
}
