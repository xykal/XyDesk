package id.xyverse.xydesk.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.R
import id.xyverse.xydesk.ui.kit.Xy
import kotlin.math.sin

/**
 * Latar atas halaman login: ilustrasi penuh (render PC + HP + controller, palet
 * ungu-putih) dengan gerak "napas" sangat pelan, lalu gradasi putih menimpa
 * bagian bawah supaya kartu login tumbuh dari ilustrasi.
 */
@Composable
fun LoginHero(height: Dp = 400.dp) {
    val t by rememberInfiniteTransition(label = "hero").animateFloat(
        0f, 6.2832f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    Box(Modifier.fillMaxWidth().height(height)) {
        Image(
            painterResource(R.drawable.hero_login), null,
            Modifier.fillMaxSize().graphicsLayer {
                val s = 1.06f + 0.02f * sin(t)
                scaleX = s; scaleY = s
                translationY = 8.dp.toPx() * sin(t * 0.5f)
            },
            alignment = Alignment.TopCenter,
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxWidth().height(height * 0.42f).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Xy.bg.copy(alpha = 0.9f), Xy.bg))),
        )
    }
}
