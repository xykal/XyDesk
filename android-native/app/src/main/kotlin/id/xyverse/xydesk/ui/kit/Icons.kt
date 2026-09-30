package id.xyverse.xydesk.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Ikon garis milik XyDesk sendiri, digambar di Canvas (tanpa set ikon sistem). */
enum class Icon { KEYBOARD, CONTROLS, SETTINGS, POWER, MONITOR, PHONE, SHIELD, KEY, PLUG, CLOCK, CHEVRON, CLOSE, TRASH, SEARCH }

@Composable
fun XyIcon(icon: Icon, modifier: Modifier = Modifier, tint: Color = Xy.textHi, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val s = Stroke(w * 0.085f, cap = StrokeCap.Round)
        when (icon) {
            Icon.KEYBOARD -> {
                rr(0.08f, 0.26f, 0.84f, 0.48f, 0.08f, tint, s)
                for (r in 0..1) for (c in 0..4) dot(0.2f + c * 0.15f, 0.4f + r * 0.14f, 0.025f, tint)
                line(0.3f, 0.64f, 0.7f, 0.64f, tint, s)
            }
            Icon.CONTROLS -> {
                for (i in 0..2) {
                    val y = 0.25f + i * 0.25f
                    line(0.12f, y, 0.88f, y, tint, s)
                    dot(listOf(0.62f, 0.32f, 0.72f)[i], y, 0.075f, tint, Color.White, s)
                }
            }
            Icon.SETTINGS -> {
                val c = Offset(w / 2, w / 2)
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4
                    line(0.5f + cos(a).toFloat() * 0.3f, 0.5f + sin(a).toFloat() * 0.3f, 0.5f + cos(a).toFloat() * 0.42f, 0.5f + sin(a).toFloat() * 0.42f, tint, s)
                }
                drawCircle(tint, w * 0.28f, c, style = s)
                drawCircle(tint, w * 0.1f, c, style = s)
            }
            Icon.POWER -> {
                drawArc(tint, -60f, 300f, false, Offset(w * 0.15f, w * 0.18f), Size(w * 0.7f, w * 0.7f), style = s)
                line(0.5f, 0.1f, 0.5f, 0.5f, tint, s)
            }
            Icon.MONITOR -> {
                rr(0.08f, 0.16f, 0.84f, 0.54f, 0.08f, tint, s)
                line(0.5f, 0.7f, 0.5f, 0.86f, tint, s)
                line(0.32f, 0.86f, 0.68f, 0.86f, tint, s)
            }
            Icon.PHONE -> {
                rr(0.26f, 0.08f, 0.48f, 0.84f, 0.1f, tint, s)
                dot(0.5f, 0.8f, 0.03f, tint)
            }
            Icon.SHIELD -> {
                val p = Path().apply {
                    moveTo(w * 0.5f, w * 0.1f); lineTo(w * 0.86f, w * 0.24f)
                    cubicTo(w * 0.86f, w * 0.6f, w * 0.7f, w * 0.82f, w * 0.5f, w * 0.92f)
                    cubicTo(w * 0.3f, w * 0.82f, w * 0.14f, w * 0.6f, w * 0.14f, w * 0.24f); close()
                }
                drawPath(p, tint, style = s)
                line(0.36f, 0.5f, 0.46f, 0.6f, tint, s); line(0.46f, 0.6f, 0.65f, 0.4f, tint, s)
            }
            Icon.KEY -> {
                drawCircle(tint, w * 0.16f, Offset(w * 0.32f, w * 0.68f), style = s)
                line(0.43f, 0.57f, 0.84f, 0.16f, tint, s)
                line(0.7f, 0.3f, 0.8f, 0.4f, tint, s); line(0.6f, 0.4f, 0.7f, 0.5f, tint, s)
            }
            Icon.PLUG -> {
                line(0.36f, 0.1f, 0.36f, 0.32f, tint, s); line(0.64f, 0.1f, 0.64f, 0.32f, tint, s)
                rr(0.24f, 0.32f, 0.52f, 0.3f, 0.06f, tint, s)
                line(0.5f, 0.62f, 0.5f, 0.9f, tint, s)
            }
            Icon.CLOCK -> {
                drawCircle(tint, w * 0.4f, Offset(w / 2, w / 2), style = s)
                line(0.5f, 0.28f, 0.5f, 0.52f, tint, s); line(0.5f, 0.52f, 0.66f, 0.62f, tint, s)
            }
            Icon.CHEVRON -> { line(0.4f, 0.28f, 0.62f, 0.5f, tint, s); line(0.62f, 0.5f, 0.4f, 0.72f, tint, s) }
            Icon.CLOSE -> { line(0.3f, 0.3f, 0.7f, 0.7f, tint, s); line(0.7f, 0.3f, 0.3f, 0.7f, tint, s) }
            Icon.TRASH -> {
                line(0.2f, 0.26f, 0.8f, 0.26f, tint, s); line(0.4f, 0.16f, 0.6f, 0.16f, tint, s)
                rr(0.27f, 0.26f, 0.46f, 0.6f, 0.05f, tint, s)
                line(0.43f, 0.4f, 0.43f, 0.72f, tint, s); line(0.57f, 0.4f, 0.57f, 0.72f, tint, s)
            }
            Icon.SEARCH -> {
                drawCircle(tint, w * 0.26f, Offset(w * 0.42f, w * 0.42f), style = s)
                line(0.62f, 0.62f, 0.86f, 0.86f, tint, s)
            }
        }
    }
}

private fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float, c: Color, s: Stroke) {
    val w = size.width
    drawLine(c, Offset(x1 * w, y1 * w), Offset(x2 * w, y2 * w), s.width, StrokeCap.Round)
}

private fun DrawScope.rr(x: Float, y: Float, rw: Float, rh: Float, r: Float, c: Color, s: Stroke) {
    val w = size.width
    drawRoundRect(c, Offset(x * w, y * w), Size(rw * w, rh * w), CornerRadius(r * w), style = s)
}

private fun DrawScope.dot(x: Float, y: Float, r: Float, c: Color, fill: Color? = null, s: Stroke? = null) {
    val w = size.width
    if (fill != null && s != null) {
        drawCircle(fill, r * w, Offset(x * w, y * w))
        drawCircle(c, r * w, Offset(x * w, y * w), style = s)
    } else drawCircle(c, r * w, Offset(x * w, y * w))
}
