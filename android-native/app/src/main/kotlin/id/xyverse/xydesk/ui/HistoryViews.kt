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

/** Riwayat: satu baris per PC (sesi terakhir + jumlah sesi), saringan hasil. */
@Composable
fun HistoryBrowser(history: List<SessionRecord>, onOpen: (SessionRecord) -> Unit) {
    var filter by remember { mutableStateOf("Semua") }
    val shown = history.filter {
        when (filter) { "Berhasil" -> it.outcome in okOutcomes; "Gagal" -> it.outcome !in okOutcomes; else -> true }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Semua", "Berhasil", "Gagal").forEach { Chip(it, it == filter) { filter = it } }
    }
    Spacer(Modifier.height(16.dp))
    val perHost = shown.groupBy { it.host }.values.map { runs -> runs.maxBy { it.startedAt } to runs.size }.sortedByDescending { it.first.startedAt }
    if (perHost.isEmpty()) XyText("Tidak ada sesi untuk saringan ini.", Xy.caption)
    else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { perHost.forEach { (h, n) -> HistoryRow(h, n) { onOpen(h) } } }
}

@Composable
private fun HistoryRow(h: SessionRecord, count: Int, onPick: () -> Unit) {
    val ctx = LocalContext.current
    val file = Previews.file(ctx, h.host)
    val stamp = file.lastModified()
    val preview = remember(stamp) { if (stamp > 0) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null }
    val dot = when (h.outcome) { "berjalan" -> Xy.accent; "ok", "putus" -> Xy.success; else -> Xy.danger }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Xy.radiusM)).background(Xy.overlay)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onPick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(92.dp).aspectRatio(16f / 10f).clip(RoundedCornerShape(Xy.radiusS)).background(Color(0xFF1B1B22)), contentAlignment = Alignment.Center) {
            if (preview != null) Image(preview, null, Modifier.fillMaxWidth().aspectRatio(16f / 10f), contentScale = ContentScale.Crop)
            else XyIcon(Icon.MONITOR, tint = Color.White.copy(alpha = 0.4f), size = 22.dp)
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(8.dp).clip(CircleShape).background(dot))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            XyText(hostTitle(h), Xy.body, maxLines = 1)
            XyText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(h.startedAt)) + " · " + duration(h.durationSec), Xy.caption)
            XyText(if (h.outcome in okOutcomes) "$count sesi" else outcomeLabel(h.outcome) + " · $count sesi", Xy.caption.copy(color = if (h.outcome in okOutcomes) Xy.textLow else Xy.danger))
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
