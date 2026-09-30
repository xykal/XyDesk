package id.xyverse.xydesk.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.R
import id.xyverse.xydesk.ui.kit.Xy
import kotlin.math.cos
import kotlin.math.sin

/**
 * Latar atas halaman login: gradasi ungu lembut, tiga perangkat 3D melayang
 * (controller, keyboard, mouse) dengan jejak motion blur, lalu gradasi putih
 * menimpa bagian bawah supaya kartu login "tumbuh" dari ilustrasi.
 */
@Composable
fun LoginHero(height: Dp = 340.dp) {
    val t by rememberInfiniteTransition(label = "hero").animateFloat(
        0f, 6.2832f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    BoxWithConstraints(Modifier.fillMaxWidth().height(height)) {
        val w = maxWidth
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFEDE7FF), Color(0xFFF6F2FF), Xy.bg))))
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(Xy.accent.copy(alpha = 0.22f), Color.Transparent)), size.width * 0.55f, Offset(size.width * 0.7f, size.height * 0.35f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF38BDF8).copy(alpha = 0.16f), Color.Transparent)), size.width * 0.4f, Offset(size.width * 0.15f, size.height * 0.7f))
        }
        Floating(R.drawable.float_keyboard, 150.dp, x = w * 0.02f, y = height * 0.12f, phase = t * 0.8f + 1.3f, amp = 10f, tilt = -12f, depth = 3.dp)
        Floating(R.drawable.float_mouse, 110.dp, x = w * 0.10f, y = height * 0.52f, phase = t * 1.1f + 3.1f, amp = 8f, tilt = 14f, depth = 1.5.dp)
        Floating(R.drawable.float_controller, 230.dp, x = w * 0.42f, y = height * 0.16f, phase = t, amp = 14f, tilt = -6f, depth = 0.dp)
        Box(
            Modifier.fillMaxWidth().height(height * 0.45f).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Xy.bg.copy(alpha = 0.85f), Xy.bg))),
        )
    }
}

@Composable
private fun BoxScope.Floating(res: Int, size: Dp, x: Dp, y: Dp, phase: Float, amp: Float, tilt: Float, depth: Dp) {
    val dy = sin(phase) * amp
    val vy = cos(phase) * amp
    val rot = tilt + sin(phase * 0.5f) * 3f
    val painter = painterResource(res)
    val base = Modifier.size(size).align(Alignment.TopStart)
    repeat(3) { i ->
        val k = (3 - i) * 0.32f
        Image(painter, null, base.offset(x, y + (dy - vy * k).dp).graphicsLayer { rotationZ = rot }.blur(depth + 6.dp).alpha(0.10f))
    }
    Image(painter, null, base.offset(x, y + dy.dp).graphicsLayer { rotationZ = rot }.blur(depth))
}
