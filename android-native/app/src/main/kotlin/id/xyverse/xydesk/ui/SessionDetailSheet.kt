package id.xyverse.xydesk.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import id.xyverse.xyadapt.SessionTrace
import id.xyverse.xydesk.core.SessionRecord
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyButton
import id.xyverse.xydesk.ui.kit.XySheet
import id.xyverse.xydesk.ui.kit.XyText
import java.text.DateFormat
import java.util.Date

/** Detail satu sesi: grafik fps dan RTT sepanjang sesi, rata-rata, hasil; tombol sambung lagi. */
@Composable
fun SessionDetailSheet(rec: SessionRecord?, onDismiss: () -> Unit, onConnect: (host: String) -> Unit) {
    XySheet(rec != null, onDismiss) {
        val r = rec ?: return@XySheet
        val pts = remember(r.trace) { SessionTrace.decode(r.trace) }
        XyText(hostTitle(r), Xy.title)
        XyText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(r.startedAt)) + " · " + pretty(r.host), Xy.caption)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Xy.radiusM)).background(Xy.overlay).padding(vertical = 12.dp)) {
            Cell(duration(r.durationSec), "durasi", Modifier.weight(1f))
            Cell(if (pts.isEmpty()) "—" else "${pts.map { it.first }.average().toInt()}", "fps rata", Modifier.weight(1f))
            Cell(if (pts.isEmpty()) "—" else "${pts.map { it.second }.filter { it > 0 }.average().takeIf { !it.isNaN() }?.toInt() ?: 0} ms", "rtt rata", Modifier.weight(1f))
            Cell(if (r.outcome in okOutcomes) "oke" else outcomeLabel(r.outcome), "hasil", Modifier.weight(1.2f), if (r.outcome in okOutcomes) Xy.success else Xy.danger)
        }
        Spacer(Modifier.height(16.dp))
        if (pts.size >= 2) {
            Legend()
            Spacer(Modifier.height(8.dp))
            Sparkline(pts)
        } else XyText("Grafik muncul untuk sesi yang berjalan lebih dari beberapa detik.", Xy.caption)
        Spacer(Modifier.height(20.dp))
        XyButton("Sambungkan lagi") { onConnect(r.host) }
        Spacer(Modifier.height(8.dp))
        XyButton("Tutup", ghost = true, onClick = onDismiss)
    }
}

@Composable
private fun Cell(value: String, label: String, modifier: Modifier, tint: Color = Xy.textHi) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        XyText(value, Xy.title.copy(color = tint), maxLines = 1)
        XyText(label.uppercase(), Xy.label.copy(color = Xy.textLow))
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Xy.accent to "FPS", Xy.warning to "RTT ms").forEach { (c, l) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(8.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(6.dp)); XyText(l, Xy.label)
            }
        }
    }
}

@Composable
private fun Sparkline(pts: List<Pair<Int, Int>>) {
    val fps = pts.map { it.first.toFloat() }; val rtt = pts.map { it.second.toFloat() }
    val fpsMax = (fps.maxOrNull() ?: 60f).coerceAtLeast(30f); val rttMax = (rtt.maxOrNull() ?: 100f).coerceAtLeast(50f)
    val accent = Xy.accent; val warn = Xy.warning; val line = Xy.line
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val w = size.width; val h = size.height
        for (i in 0..2) drawLine(line, Offset(0f, h * i / 2), Offset(w, h * i / 2), 1.dp.toPx())
        fun path(v: List<Float>, max: Float): Path = Path().apply {
            v.forEachIndexed { i, y ->
                val x = w * i / (v.size - 1).coerceAtLeast(1); val yy = h - (y / max) * h * 0.92f - h * 0.04f
                if (i == 0) moveTo(x, yy) else lineTo(x, yy)
            }
        }
        drawPath(path(rtt, rttMax), warn.copy(alpha = 0.9f), style = Stroke(2.dp.toPx()))
        drawPath(path(fps, fpsMax), accent, style = Stroke(2.5.dp.toPx()))
    }
}
