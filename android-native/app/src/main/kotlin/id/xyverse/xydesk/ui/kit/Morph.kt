package id.xyverse.xydesk.ui.kit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Satu bagian bentuk: kotak membulat dalam kotak 100×100. */
private data class Part(val cx: Float, val cy: Float, val w: Float, val h: Float, val r: Float, val fill: Boolean = false, val a: Float = 1f)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun Part.to(o: Part, t: Float) = Part(
    lerp(cx, o.cx, t), lerp(cy, o.cy, t), lerp(w, o.w, t), lerp(h, o.h, t), lerp(r, o.r, t),
    if (t < 0.5f) fill else o.fill, lerp(a, o.a, t),
)

private fun hidden(cx: Float, cy: Float) = Part(cx, cy, 2f, 2f, 1f, true, 0f)

private val phone = listOf(
    Part(50f, 50f, 38f, 70f, 9f),
    Part(50f, 21f, 12f, 3f, 1.5f, true),
    Part(50f, 79f, 5f, 5f, 2.5f, true),
    hidden(50f, 50f),
    hidden(50f, 50f),
)

private val pc = listOf(
    Part(50f, 42f, 76f, 48f, 7f),
    Part(50f, 72f, 10f, 10f, 2f, true),
    Part(50f, 82f, 36f, 4f, 2f, true),
    hidden(50f, 42f),
    hidden(50f, 42f),
)

private val server = listOf(
    Part(50f, 50f, 48f, 70f, 6f),
    Part(46f, 33f, 26f, 3f, 1.5f, true),
    Part(46f, 50f, 26f, 3f, 1.5f, true),
    Part(46f, 67f, 26f, 3f, 1.5f, true),
    Part(65f, 33f, 4f, 4f, 2f, true),
)

private val shapes = listOf(phone, pc, server)

private fun ease(x: Float): Float = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).let { it * it * it } / 2

/**
 * Loader morphing HP → PC → Server → HP, tanpa akhir. Durasi tidak ditentukan
 * pemanggil: loop terus sampai composable dilepas (mis. saat sesi tersambung).
 */
@Composable
fun MorphLoader(modifier: Modifier = Modifier, size: Dp = 120.dp, tint: Color = Xy.accent) {
    val t by rememberInfiniteTransition(label = "morph").animateFloat(
        0f, 3f, infiniteRepeatable(tween(5400, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    val k = t.toInt().coerceIn(0, 2)
    val u = t - k
    val p = if (u < 0.42f) 0f else ease((u - 0.42f) / 0.58f)
    val from = shapes[k]
    val to = shapes[(k + 1) % 3]
    val breath = 1f + 0.015f * kotlin.math.sin(t * 6.283f).toFloat()
    Canvas(modifier.size(size)) {
        val s = this.size.width / 100f
        val stroke = Stroke(3.2f * s, cap = StrokeCap.Round)
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.16f), Color.Transparent)), this.size.width * 0.55f * breath, center)
        from.indices.forEach { i ->
            val q = from[i].to(to[i], p)
            val w = q.w * s * breath
            val h = q.h * s * breath
            val tl = Offset(q.cx * s - w / 2, q.cy * s - h / 2)
            val c = tint.copy(alpha = q.a)
            if (q.fill) drawRoundRect(c, tl, Size(w, h), CornerRadius(q.r * s))
            else {
                drawRoundRect(Color.White.copy(alpha = 0.9f * q.a), tl, Size(w, h), CornerRadius(q.r * s))
                drawRoundRect(c, tl, Size(w, h), CornerRadius(q.r * s), style = stroke)
            }
        }
    }
}
