package id.xyverse.xydesk.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Tab(val label: String) { HOME("Beranda"), DEVICES("Perangkat"), HISTORY("Riwayat"), NEWS("Berita"), ACCOUNT("Akun") }

/** Bar bawah mengambang berbentuk pil; ikon garis digambar sendiri. */
@Composable
fun BottomNav(current: Tab, badge: Tab? = null, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .shadow(20.dp, RoundedCornerShape(Xy.pill), ambientColor = Xy.shadow, spotColor = Xy.shadow)
            .clip(RoundedCornerShape(Xy.pill))
            .background(Color.White)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            val active = t == current
            val bg by animateColorAsState(if (active) Xy.accent.copy(alpha = 0.12f) else Color.Transparent, label = "bg")
            val fg by animateColorAsState(if (active) Xy.accent else Xy.textLow, label = "fg")
            val w by animateDpAsState(if (active) 104.dp else 44.dp, label = "w")
            Row(
                Modifier
                    .width(w)
                    .height(44.dp)
                    .clip(RoundedCornerShape(Xy.pill))
                    .background(bg)
                    .clickable(remember { MutableInteractionSource() }, null) { onSelect(t) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Canvas(Modifier.size(20.dp)) { icon(t, fg) }
                    if (t == badge && !active) Box(Modifier.align(Alignment.TopEnd).size(7.dp).background(Xy.danger, CircleShape))
                }
                if (active) {
                    Spacer(Modifier.width(8.dp))
                    XyText(t.label, Xy.label.copy(color = fg, letterSpacing = 0.2.sp))
                }
            }
        }
    }
}

private fun DrawScope.icon(t: Tab, c: Color) {
    val s = Stroke(1.8.dp.toPx())
    val w = size.width
    when (t) {
        Tab.HOME -> {
            drawRoundRect(c, Offset(w * 0.1f, w * 0.15f), Size(w * 0.8f, w * 0.55f), CornerRadius(w * 0.1f), style = s)
            drawLine(c, Offset(w * 0.3f, w * 0.88f), Offset(w * 0.7f, w * 0.88f), s.width)
        }
        Tab.DEVICES -> {
            drawRoundRect(c, Offset(w * 0.1f, w * 0.1f), Size(w * 0.5f, w * 0.8f), CornerRadius(w * 0.1f), style = s)
            drawRoundRect(c, Offset(w * 0.55f, w * 0.35f), Size(w * 0.35f, w * 0.55f), CornerRadius(w * 0.08f), style = s)
        }
        Tab.HISTORY -> {
            drawCircle(c, w * 0.4f, Offset(w / 2, w / 2), style = s)
            drawLine(c, Offset(w / 2, w * 0.28f), Offset(w / 2, w / 2), s.width)
            drawLine(c, Offset(w / 2, w / 2), Offset(w * 0.68f, w * 0.62f), s.width)
        }
        Tab.NEWS -> {
            drawRoundRect(c, Offset(w * 0.1f, w * 0.14f), Size(w * 0.8f, w * 0.72f), CornerRadius(w * 0.08f), style = s)
            drawLine(c, Offset(w * 0.28f, w * 0.38f), Offset(w * 0.72f, w * 0.38f), s.width)
            drawLine(c, Offset(w * 0.28f, w * 0.54f), Offset(w * 0.72f, w * 0.54f), s.width)
            drawLine(c, Offset(w * 0.28f, w * 0.7f), Offset(w * 0.56f, w * 0.7f), s.width)
        }
        Tab.ACCOUNT -> {
            drawCircle(c, w * 0.18f, Offset(w / 2, w * 0.32f), style = s)
            drawArc(c, 200f, 140f, false, Offset(w * 0.15f, w * 0.55f), Size(w * 0.7f, w * 0.7f), style = s)
        }
    }
}

