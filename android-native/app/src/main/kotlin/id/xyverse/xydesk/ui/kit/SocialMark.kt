package id.xyverse.xydesk.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class Social { TIKTOK, INSTAGRAM, YOUTUBE, X, WEB }

/** Logo sosial digambar vektor (garis putih di atas lingkaran brand). */
@Composable
fun SocialMark(kind: Social, modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width
        val s = Stroke(w * 0.11f, cap = StrokeCap.Round)
        when (kind) {
            Social.X -> {
                drawLine(color, Offset(w * 0.22f, w * 0.2f), Offset(w * 0.78f, w * 0.8f), s.width, StrokeCap.Round)
                drawLine(color, Offset(w * 0.78f, w * 0.2f), Offset(w * 0.22f, w * 0.8f), s.width * 0.6f, StrokeCap.Round)
            }
            Social.YOUTUBE -> {
                drawRoundRect(color, Offset(w * 0.14f, w * 0.26f), Size(w * 0.72f, w * 0.48f), CornerRadius(w * 0.14f))
                val tri = Path().apply { moveTo(w * 0.42f, w * 0.38f); lineTo(w * 0.64f, w * 0.5f); lineTo(w * 0.42f, w * 0.62f); close() }
                drawPath(tri, Color(0xFFFF0000))
            }
            Social.INSTAGRAM -> {
                drawRoundRect(color, Offset(w * 0.2f, w * 0.2f), Size(w * 0.6f, w * 0.6f), CornerRadius(w * 0.18f), style = s)
                drawCircle(color, w * 0.14f, Offset(w / 2, w / 2), style = s)
                drawCircle(color, w * 0.045f, Offset(w * 0.68f, w * 0.32f))
            }
            Social.TIKTOK -> {
                val p = Path().apply {
                    moveTo(w * 0.52f, w * 0.2f); lineTo(w * 0.52f, w * 0.62f)
                    cubicTo(w * 0.52f, w * 0.8f, w * 0.28f, w * 0.8f, w * 0.28f, w * 0.64f)
                    cubicTo(w * 0.28f, w * 0.52f, w * 0.4f, w * 0.48f, w * 0.48f, w * 0.52f)
                }
                drawPath(p, color, style = s)
                val hook = Path().apply { moveTo(w * 0.52f, w * 0.26f); cubicTo(w * 0.56f, w * 0.4f, w * 0.66f, w * 0.44f, w * 0.76f, w * 0.44f) }
                drawPath(hook, color, style = s)
            }
            Social.WEB -> {
                drawCircle(color, w * 0.3f, Offset(w / 2, w / 2), style = s)
                drawLine(color, Offset(w * 0.2f, w / 2), Offset(w * 0.8f, w / 2), s.width * 0.7f)
                drawOval(color, Offset(w * 0.36f, w * 0.2f), Size(w * 0.28f, w * 0.6f), style = Stroke(s.width * 0.7f))
            }
        }
    }
}
