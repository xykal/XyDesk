package id.xyverse.xydesk.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import id.xyverse.xydesk.core.Previews
import id.xyverse.xydesk.core.SessionRecord
import id.xyverse.xydesk.ui.kit.Icon
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyIcon
import id.xyverse.xydesk.ui.kit.XyText
import java.text.DateFormat
import java.util.Date

/** Riwayat dengan dua tampilan (grid kartu / daftar) dan saringan hasil. */
@Composable
fun HistoryBrowser(history: List<SessionRecord>, grid: Boolean, onGrid: (Boolean) -> Unit, onOpen: (SessionRecord) -> Unit) {
    var filter by remember { mutableStateOf("Semua") }
    val shown = history.filter {
        when (filter) { "Berhasil" -> it.outcome in okOutcomes; "Gagal" -> it.outcome !in okOutcomes; else -> true }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Semua", "Berhasil", "Gagal").forEach { Chip(it, it == filter) { filter = it } }
        }
        ViewSwitch(grid, onGrid)
    }
    Spacer(Modifier.height(16.dp))
    if (shown.isEmpty()) XyText("Tidak ada sesi untuk saringan ini.", Xy.caption)
    else if (grid) HistoryGrid(shown.take(30), onOpen)
    else HistorySection(shown, onOpen)
}

@Composable
private fun ViewSwitch(grid: Boolean, onGrid: (Boolean) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(Xy.pill)).background(Xy.overlay).padding(3.dp)) {
        listOf(false to Icon.LIST, true to Icon.GRID).forEach { (g, ic) ->
            val on = g == grid
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(if (on) Color.White else Color.Transparent)
                    .clickable(remember { MutableInteractionSource() }, null) { onGrid(g) },
                contentAlignment = Alignment.Center,
            ) { XyIcon(ic, tint = if (on) Xy.accent else Xy.textLow, size = 17.dp) }
        }
    }
}

@Composable
private fun HistoryGrid(items: List<SessionRecord>, onOpen: (SessionRecord) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { h -> Box(Modifier.weight(1f)) { HistoryTile(h) { onOpen(h) } } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HistoryTile(h: SessionRecord, onPick: () -> Unit) {
    val ctx = LocalContext.current
    val file = Previews.file(ctx, h.host)
    val stamp = file.lastModified()
    val preview = remember(stamp) { if (stamp > 0) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null }
    val dot = when (h.outcome) { "berjalan" -> Xy.accent; "ok", "putus" -> Xy.success; else -> Xy.danger }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Xy.radiusM)).background(Xy.overlay)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onPick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).background(Color(0xFF1B1B22)), contentAlignment = Alignment.Center) {
            if (preview != null) Image(preview, null, Modifier.fillMaxWidth().aspectRatio(16f / 10f), contentScale = ContentScale.Crop)
            else XyIcon(Icon.MONITOR, tint = Color.White.copy(alpha = 0.4f), size = 26.dp)
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(9.dp).clip(CircleShape).background(dot))
        }
        Column(Modifier.padding(10.dp)) {
            XyText(hostTitle(h), Xy.body, maxLines = 1)
            XyText(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(h.startedAt)) + " · " + duration(h.durationSec), Xy.caption)
            if (h.outcome !in okOutcomes) XyText(outcomeLabel(h.outcome), Xy.caption.copy(color = Xy.danger))
        }
    }
}

/** Ringkasan pemakaian di beranda: jumlah sesi, total jam, host paling sering; ketuk ke Riwayat. */
@Composable
fun UsageStrip(history: List<SessionRecord>, onOpen: () -> Unit = {}) {
    if (history.isEmpty()) return
    val secs = history.sumOf { it.durationSec }
    val hours = if (secs < 3600) "${secs / 60} mnt" else "%.1f jam".format(secs / 3600f)
    val top = history.groupBy { it.host }.maxByOrNull { it.value.size }?.value?.first()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Xy.radiusM)).background(Xy.overlay)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onOpen).padding(vertical = 12.dp),
    ) {
        Stat("${history.size}", "sesi", Modifier.weight(1f))
        Stat(hours, "total", Modifier.weight(1f))
        Stat(top?.let { hostTitle(it) } ?: "—", "paling sering", Modifier.weight(1.4f))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        XyText(value, Xy.title, maxLines = 1)
        XyText(label.uppercase(), Xy.label.copy(color = Xy.textLow))
    }
}

internal val okOutcomes = setOf("ok", "putus", "berjalan")
