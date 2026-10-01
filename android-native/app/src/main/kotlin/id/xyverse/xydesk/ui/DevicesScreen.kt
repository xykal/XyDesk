@file:OptIn(ExperimentalFoundationApi::class)

package id.xyverse.xydesk.ui

import android.graphics.BitmapFactory
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.core.HostSpecs
import id.xyverse.xydesk.core.Previews
import id.xyverse.xydesk.core.SessionRecord
import id.xyverse.xydesk.ui.kit.Icon
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyButton
import id.xyverse.xydesk.ui.kit.XyIcon
import id.xyverse.xydesk.ui.kit.XyText
import java.text.DateFormat
import java.util.Date

/** Kartu per host: cuplikan layar terakhir, nama, spesifikasi ringkas; ketuk untuk detail. */
@Composable
fun DevicesSection(devices: List<SessionRecord>, compact: Boolean = false, onLong: (host: String) -> Unit = {}, onPick: (host: String) -> Unit) {
    if (devices.isEmpty()) return
    XyText("PERANGKAT TERAKHIR", Xy.label)
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        devices.forEach { d -> if (compact) DeviceRow(d, { onLong(d.host) }) { onPick(d.host) } else DeviceCard(d, { onLong(d.host) }) { onPick(d.host) } }
    }
    if (!compact) { Spacer(Modifier.height(10.dp)); XyText("Tahan kartu untuk beri nama, favorit, atau lupakan host.", Xy.caption.copy(color = Xy.textLow)) }
}

/** Nama tampilan: alias dari pengguna menang atas nama host. */
@Composable
fun hostTitle(d: SessionRecord): String {
    val alias = LocalHostMeta.current.alias(d.host)
    return alias.ifEmpty { title(d) }
}

data class HostMeta(val alias: (String) -> String = { "" }, val favorite: (String) -> Boolean = { false })

val LocalHostMeta = compositionLocalOf { HostMeta() }

@Composable
private fun DeviceCard(d: SessionRecord, onLong: () -> Unit, onConnect: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val file = Previews.file(ctx, d.host)
    val stamp = file.lastModified()
    val preview = remember(stamp) { if (stamp > 0) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Xy.radiusL)).background(Xy.overlay)
            .combinedClickable(remember { MutableInteractionSource() }, null, onLongClick = onLong) { open = !open },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF1B1B22)), contentAlignment = Alignment.Center) {
            if (preview != null) Image(preview, null, Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Crop)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                XyIcon(Icon.MONITOR, tint = Color.White.copy(alpha = 0.5f), size = 34.dp)
                Spacer(Modifier.height(6.dp))
                XyText("Belum ada cuplikan", Xy.caption.copy(color = Color.White.copy(alpha = 0.6f)))
            }
            Row(Modifier.align(Alignment.TopStart).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Badge(pretty(d.host))
                Badge(relative(d.startedAt))
            }
            if (LocalHostMeta.current.favorite(d.host)) Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { XyIcon(Icon.STAR, tint = Color(0xFFFFC53D), size = 18.dp) }
        }
        Column(Modifier.padding(14.dp)) {
            XyText(hostTitle(d), Xy.title)
            XyText(subtitle(d.specs), Xy.caption)
            Spacer(Modifier.height(10.dp))
            AnimatedVisibility(open) { Column { SpecTable(d.specs); Spacer(Modifier.height(12.dp)) } }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                XyButton("Sambungkan", Modifier.weight(1f), onClick = onConnect)
                Box(
                    Modifier.size(54.dp).background(Color.White, CircleShape).border(1.dp, Xy.line, CircleShape)
                        .clickable(remember { MutableInteractionSource() }, null) { open = !open },
                    contentAlignment = Alignment.Center,
                ) { XyIcon(Icon.CHEVRON, tint = Xy.textMid, size = 20.dp) }
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
private fun Badge(text: String) {
    Box(Modifier.clip(RoundedCornerShape(Xy.pill)).background(Color(0x99000000)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        XyText(text, Xy.label.copy(color = Color.White))
    }
}

@Composable
fun HistorySection(history: List<SessionRecord>, onOpen: (SessionRecord) -> Unit = {}) {
    if (history.isEmpty()) return
    XyText("RIWAYAT SESI", Xy.label)
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        history.take(30).forEach { h ->
            val ok = h.outcome == "ok" || h.outcome == "putus" || h.outcome == "berjalan"
            val dot = when (h.outcome) { "berjalan" -> Xy.accent; "ok", "putus" -> Xy.success; else -> Xy.danger }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Xy.radiusS)).clickable(remember { MutableInteractionSource() }, null) { onOpen(h) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    XyText(hostTitle(h), Xy.body)
                    XyText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(h.startedAt)) + (if (ok) "" else " · " + outcomeLabel(h.outcome)), Xy.caption)
                }
                XyText(duration(h.durationSec), Xy.caption.copy(color = Xy.textLow))
            }
        }
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
