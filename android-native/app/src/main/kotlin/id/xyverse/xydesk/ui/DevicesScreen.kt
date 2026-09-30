package id.xyverse.xydesk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.core.SessionRecord
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyText
import java.text.DateFormat
import java.util.Date

/** Daftar host yang pernah tersambung; ketuk untuk mengisi form konek. */
@Composable
fun DevicesSection(devices: List<SessionRecord>, onPick: (host: String) -> Unit) {
    if (devices.isEmpty()) return
    XyText("PERANGKAT TERAKHIR", Xy.label)
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        devices.take(5).forEach { d ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Xy.radiusM))
                    .background(Xy.raised)
                    .clickable(remember { MutableInteractionSource() }, null) { onPick(d.host) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Xy.accent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    XyText(d.name.take(1).ifEmpty { "#" }.uppercase(), Xy.title.copy(color = Xy.lavender))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    XyText(d.name.ifEmpty { "Host ${pretty(d.host)}" }, Xy.body)
                    XyText(pretty(d.host) + " · " + relative(d.startedAt), Xy.caption)
                }
                XyText("›", Xy.title.copy(color = Xy.textLow))
            }
        }
    }
}

@Composable
fun HistorySection(history: List<SessionRecord>) {
    if (history.isEmpty()) return
    XyText("RIWAYAT SESI", Xy.label)
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        history.take(20).forEach { h ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (h.outcome == "ok" || h.outcome == "putus") Xy.success else Xy.warning))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    XyText(h.name.ifEmpty { pretty(h.host) }, Xy.body)
                    XyText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(h.startedAt)), Xy.caption)
                }
                XyText(duration(h.durationSec), Xy.caption.copy(color = Xy.textLow))
            }
        }
    }
}

private fun pretty(host: String) = host.filter(Char::isDigit).chunked(3).joinToString(" ")

private fun duration(sec: Long): String = if (sec < 60) "${sec}d" else "${sec / 60}m ${sec % 60}d"

private fun relative(at: Long): String {
    val m = (System.currentTimeMillis() - at) / 60000
    return when {
        m < 1 -> "baru saja"
        m < 60 -> "$m mnt lalu"
        m < 1440 -> "${m / 60} jam lalu"
        else -> "${m / 1440} hari lalu"
    }
}
