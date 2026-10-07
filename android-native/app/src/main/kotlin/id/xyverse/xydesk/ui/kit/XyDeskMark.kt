package id.xyverse.xydesk.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Logo resmi XyDesk (geometric 3D crossed-ribbon mark).
 *
 * Digambar di Canvas dengan gradasi violet-magenta-lavender khas XyDesk,
 * kontur pita saling silang, dan kilau bintang glint di titik persilangan.
 */
@Composable
fun XyDeskMark(
    size: Dp = 56.dp,
    modifier: Modifier = Modifier,
    elevated: Boolean = true,
) {
    val shape = RoundedCornerShape(size * 0.26f)
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (elevated) {
                    Modifier.shadow(
                        elevation = size * 0.16f,
                        shape = shape,
                        ambientColor = Xy.accent.copy(alpha = 0.35f),
                        spotColor = Xy.accent.copy(alpha = 0.45f),
                    )
                } else Modifier,
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF8B5CF6),
                        Color(0xFF7C3AED),
                        Color(0xFF6D28D9),
                    ),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size * 0.72f)) {
            val w = this.size.width
            val h = this.size.height

            // Gradasi pita utama (depan)
            val ribbonGrad = Brush.linearGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFF5D0FE), Color(0xFFE9D5FF)),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            )
            // Gradasi lengan belakang
            val backGrad = Brush.linearGradient(
                colors = listOf(Color(0xFFDDD6FE), Color(0xFFC4B5FD)),
                start = Offset(0f, h),
                end = Offset(w, 0f),
            )

            val stroke = w * 0.18f

            // Lengan belakang 1: kiri-atas ke tengah
            val pathBack1 = Path().apply {
                moveTo(w * 0.20f, h * 0.20f)
                cubicTo(w * 0.36f, h * 0.36f, w * 0.44f, h * 0.44f, w * 0.52f, h * 0.52f)
            }
            drawPath(pathBack1, backGrad, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Lengan belakang 2: tengah ke kanan-bawah
            val pathBack2 = Path().apply {
                moveTo(w * 0.52f, h * 0.52f)
                cubicTo(w * 0.60f, h * 0.60f, w * 0.68f, h * 0.68f, w * 0.80f, h * 0.80f)
            }
            drawPath(pathBack2, backGrad, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Pita utama depan (diagonal kanan-atas ke kiri-bawah)
            val pathFront = Path().apply {
                moveTo(w * 0.80f, h * 0.20f)
                cubicTo(w * 0.65f, h * 0.38f, w * 0.35f, h * 0.62f, w * 0.20f, h * 0.80f)
            }
            drawPath(pathFront, ribbonGrad, style = Stroke(stroke * 1.08f, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Kilau glint putih pada titik silang
            val starCenter = Offset(w * 0.50f, h * 0.48f)
            drawCircle(Color.White, radius = stroke * 0.38f, center = starCenter)
            drawGlint(this, starCenter, w * 0.18f)
        }
    }
}

private fun drawGlint(scope: DrawScope, center: Offset, r: Float) {
    val path = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x, center.y, center.x + r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + r)
        quadraticTo(center.x, center.y, center.x - r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - r)
        close()
    }
    scope.drawPath(path, Color.White, style = Fill)
}
