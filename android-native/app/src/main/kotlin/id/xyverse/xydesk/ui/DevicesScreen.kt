@file:OptIn(ExperimentalFoundationApi::class)

package id.xyverse.xydesk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.core.HostSpecs
import id.xyverse.xydesk.core.Previews
import id.xyverse.xydesk.core.P
import id.xyverse.xydesk.core.SessionRecord
import id.xyverse.xydesk.core.Store
import id.xyverse.xydesk.ui.kit.Icon
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyButton
import id.xyverse.xydesk.ui.kit.XyIcon
import id.xyverse.xydesk.ui.kit.XyText
import java.text.DateFormat
import java.util.Date

/** Kartu per host: wallpaper terakhir, nama, spesifikasi ringkas; ketuk panah untuk detail dan riwayat sesi. */
@Composable
fun DevicesSection(
    devices: List<SessionRecord>,
    compact: Boolean = false,
    grid: Boolean = false,
    onLong: (host: String) -> Unit = {},
    onSession: (SessionRecord) -> Unit = {},
    onPick: (host: String) -> Unit,
) {
    if (devices.isEmpty()) return
    XyText(if (compact) "PERANGKAT TERAKHIR" else "PERANGKAT", Xy.label)
    Spacer(Modifier.height(8.dp))
    when {
        compact -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { devices.forEach { d -> DeviceRow(d, { onLong(d.host) }) { onPick(d.host) } } }
        grid -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            devices.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { d -> Box(Modifier.weight(1f)) { DeviceTile(d, { onLong(d.host) }) { onPick(d.host) } } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { devices.forEach { d -> DeviceCard(d, { onLong(d.host) }, onSession) { onPick(d.host) } } }
    }
    if (!compact) { Spacer(Modifier.height(10.dp)); XyText("Tahan kartu untuk beri nama, favorit, atau lupakan host.", Xy.caption.copy(color = Xy.textLow)) }
}

/** Nama tampilan: alias dari pengguna menang atas nama host. */
@Composable
fun hostTitle(d: SessionRecord): String {
    val alias = LocalHostMeta.current.alias(d.host)
    return alias.ifEmpty { title(d) }
}

data class HostMeta(
    val alias: (String) -> String = { "" },
    val favorite: (String) -> Boolean = { false },
    val online: (String) -> Boolean? = { null },
    val sessions: (String) -> List<SessionRecord> = { emptyList() },
)

val LocalHostMeta = compositionLocalOf { HostMeta() }

@Composable
private fun PreviewBox(d: SessionRecord, ratio: Float, iconSize: Dp, caption: Boolean, content: @Composable BoxScope.() -> Unit = {}) {
    val preview by Previews.rememberPreview(d.host, d.durationSec)
    Box(Modifier.fillMaxWidth().aspectRatio(ratio).background(Color(0xFF1B1B22)), contentAlignment = Alignment.Center) {
        val img = preview
        if (img != null) Image(img, null, Modifier.fillMaxWidth().aspectRatio(ratio), contentScale = ContentScale.Fit, filterQuality = FilterQuality.Low)
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            XyIcon(Icon.MONITOR, tint = Color.White.copy(alpha = 0.5f), size = iconSize)
            if (caption) { Spacer(Modifier.height(6.dp)); XyText("Belum ada wallpaper", Xy.caption.copy(color = Color.White.copy(alpha = 0.6f))) }
        }
        content()
    }
}

@Composable
private fun DeviceCard(d: SessionRecord, onLong: () -> Unit, onSession: (SessionRecord) -> Unit, onConnect: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val meta = LocalHostMeta.current
    val spin by animateFloatAsState(if (open) 90f else 0f, label = "chevron")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Xy.radiusL)).background(Xy.overlay)
            .combinedClickable(remember { MutableInteractionSource() }, null, onLongClick = onLong) { open = !open },
    ) {
        PreviewBox(d, 16f / 9f, 34.dp, caption = true) {
            Row(Modifier.align(Alignment.TopStart).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (Store(LocalContext.current).bool(P.SHOW_ID, true)) Badge(pretty(d.host))
                Badge(relative(d.startedAt))
                meta.online(d.host)?.let { on -> Badge(if (on) "● Online" else "○ Offline", if (on) Color(0xCC167347) else null) }
            }
            if (meta.favorite(d.host)) Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { XyIcon(Icon.STAR, tint = Color(0xFFFFC53D), size = 18.dp) }
        }
        Column(Modifier.padding(14.dp)) {
            XyText(hostTitle(d), Xy.title)
            XyText(subtitle(d.specs), Xy.caption)
            Spacer(Modifier.height(10.dp))
            AnimatedVisibility(open) {
                Column {
                    SpecTable(d.specs)
                    Spacer(Modifier.height(12.dp))
                    SessionRows(meta.sessions(d.host), onSession)
                    Spacer(Modifier.height(12.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                XyButton("Sambungkan", Modifier.weight(1f), onClick = onConnect)
                Box(
                    Modifier.size(54.dp).background(Color.White, CircleShape).border(1.dp, Xy.line, CircleShape)
                        .clickable(remember { MutableInteractionSource() }, null) { open = !open },
                    contentAlignment = Alignment.Center,
                ) { XyIcon(Icon.CHEVRON, Modifier.rotate(spin), tint = Xy.textMid, size = 20.dp) }
            }
        }
    }
}

@Composable
private fun DeviceTile(d: SessionRecord, onLong: () -> Unit, onPick: () -> Unit) {
    val meta = LocalHostMeta.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Xy.radiusM)).background(Xy.overlay)
            .combinedClickable(remember { MutableInteractionSource() }, null, onLongClick = onLong, onClick = onPick),
    ) {
        PreviewBox(d, 16f / 10f, 26.dp, caption = false) {
            meta.online(d.host)?.let { on -> Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(9.dp).clip(CircleShape).background(if (on) Xy.success else Xy.textLow)) }
            if (meta.favorite(d.host)) Box(Modifier.align(Alignment.TopStart).padding(8.dp)) { XyIcon(Icon.STAR, tint = Color(0xFFFFC53D), size = 14.dp) }
        }
        Column(Modifier.padding(10.dp)) {
            XyText(hostTitle(d), Xy.body, maxLines = 1)
            XyText(pretty(d.host) + " · " + relative(d.startedAt), Xy.caption)
        }
    }
}

/** Riwayat sesi satu host di dalam detail kartu; ketuk baris untuk grafik sesi. */
@Composable
private fun SessionRows(sessions: List<SessionRecord>, onOpen: (SessionRecord) -> Unit) {
    if (sessions.isEmpty()) return
    XyText("RIWAYAT SESI · ${sessions.size}", Xy.label)
    Spacer(Modifier.height(6.dp))
    Column(Modifier.clip(RoundedCornerShape(Xy.radiusM)).background(Color.White).padding(vertical = 4.dp)) {
        sessions.take(8).forEach { h ->
            val ok = h.outcome in okOutcomes
            val dot = when (h.outcome) { "berjalan" -> Xy.accent; "ok", "putus" -> Xy.success; else -> Xy.danger }
            Row(
                Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null) { onOpen(h) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(10.dp))
                XyText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(h.startedAt)) + (if (ok) "" else " · " + outcomeLabel(h.outcome)), Xy.caption.copy(color = if (ok) Xy.textMid else Xy.danger), Modifier.weight(1f))
                XyText(duration(h.durationSec), Xy.caption.copy(color = Xy.textLow))
            }
        }
    }
}

@Composable
private fun DeviceRow(d: SessionRecord, onLong: () -> Unit, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Xy.radiusM)).background(Xy.overlay)
            .combinedClickable(remember { MutableInteractionSource() }, null, onLongClick = onLong, onClick = onPick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Xy.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            XyIcon(Icon.MONITOR, tint = Xy.accent, size = 18.dp)
            LocalHostMeta.current.online(d.host)?.let { on -> Box(Modifier.align(Alignment.BottomEnd).size(10.dp).clip(CircleShape).background(if (on) Xy.success else Xy.textLow)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            XyText(hostTitle(d), Xy.body)
            XyText(pretty(d.host) + " · " + relative(d.startedAt), Xy.caption)
        }
        if (LocalHostMeta.current.favorite(d.host)) { XyIcon(Icon.STAR, tint = Color(0xFFFFC53D), size = 16.dp); Spacer(Modifier.width(8.dp)) }
        XyIcon(Icon.CHEVRON, tint = Xy.textLow, size = 18.dp)
    }
}

@Composable
private fun SpecTable(s: HostSpecs) {
    val rows = listOf(
        "Motherboard" to s.motherboard, "CPU" to s.cpu, "GPU" to s.gpu,
        "RAM" to s.ram, "Penyimpanan" to s.storage, "Sistem" to s.os, "Nama mesin" to s.hostname,
    )
    Column(Modifier.clip(RoundedCornerShape(Xy.radiusM)).background(Color.White).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (k, v) ->
            Row {
                XyText(k, Xy.caption, Modifier.width(96.dp))
                XyText(v.ifEmpty { "Tidak terdeteksi" }, Xy.caption.copy(color = if (v.isEmpty()) Xy.textLow else Xy.textHi), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color? = null) {
    Box(Modifier.clip(RoundedCornerShape(Xy.pill)).background(color ?: Color(0x99000000)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        XyText(text, Xy.label.copy(color = Color.White))
    }
}

internal fun title(d: SessionRecord) = d.name.ifEmpty { d.specs.hostname }.ifEmpty { "Host ${pretty(d.host)}" }

private fun subtitle(s: HostSpecs) = listOf(s.cpu, s.gpu).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { s.os.ifEmpty { "Spesifikasi belum terbaca" } }

internal fun outcomeLabel(o: String) = when (o) {
    "rejected" -> "password salah"; "batal" -> "dibatalkan"; "retry" -> "coba ulang"; "peer_offline" -> "host offline"; "busy" -> "host sibuk"; "error" -> "gagal"; else -> o
}

internal fun pretty(host: String) = host.filter(Char::isDigit).chunked(3).joinToString(" ")

internal fun duration(sec: Long): String = if (sec < 60) "${sec}d" else "${sec / 60}m ${sec % 60}d"

internal fun relative(at: Long): String {
    val m = (System.currentTimeMillis() - at) / 60000
    return when {
        m < 1 -> "baru saja"
        m < 60 -> "$m mnt lalu"
        m < 1440 -> "${m / 60} jam lalu"
        else -> "${m / 1440} hari lalu"
    }
}
