package id.xyverse.xydesk.ui

import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xyverse.xydesk.R
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ease = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Master VO "XyDesk" di `res/raw/xydesk_vo.mp3`; hanya saat pertama kali atau diputar ulang. */
private class IntroVoice(ctx: android.content.Context) {
    private val player = runCatching {
        MediaPlayer.create(ctx, R.raw.xydesk_vo)?.apply { isLooping = false }
    }.getOrNull()

    private var started = false

    fun start() {
        val p = player ?: return
        if (started || p.isPlaying) return
        started = true
        runCatching { p.setVolume(1f, 1f); p.start() }
    }

    fun release() {
        runCatching { if (player?.isPlaying == true) player.stop() }
        runCatching { player?.release() }
    }
}

/** Splash sederhana: logo + wordmark berdampingan, masuk halus, keluar halus. Ketuk untuk lewati. */
@Composable
fun SplashScreen(playVoice: Boolean, short: Boolean = false, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val enter = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val voice = remember { if (playVoice) IntroVoice(ctx) else null }
    DisposableEffect(Unit) { onDispose { voice?.release() } }
    LaunchedEffect(playVoice) {
        delay(60)
        if (playVoice) voice?.start()
        launch { enter.animateTo(1f, tween(if (short) 520 else 760, easing = ease)) }
        delay(if (short) 900L else 2100L)
        voice?.release()
        exit.animateTo(1f, tween(320, easing = ease))
        onDone()
    }
    Box(
        Modifier.fillMaxSize().background(Xy.bg).graphicsLayer { alpha = 1f - exit.value }
            .clickable(remember { MutableInteractionSource() }, null) { voice?.release(); onDone() },
        contentAlignment = Alignment.Center,
    ) {
        val float = rememberInfiniteTransition(label = "orb")
        val oy by float.animateFloat(0f, 1f, infiniteRepeatable(tween(3800, easing = LinearEasing), RepeatMode.Reverse), label = "oy")
        Box(Modifier.size(22.dp).align(Alignment.TopEnd).padding(48.dp).graphicsLayer { translationY = (oy - 0.5f) * 18f }.clip(CircleShape).background(Xy.accent.copy(alpha = 0.35f)))
        Box(Modifier.size(12.dp).align(Alignment.BottomStart).padding(56.dp).graphicsLayer { translationY = (0.5f - oy) * 14f }.clip(CircleShape).background(Color.White.copy(alpha = 0.55f)))
        Row(
            Modifier.graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 14.dp.toPx()
                val s = 0.96f + 0.04f * enter.value
                scaleX = s; scaleY = s
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(R.drawable.logo_xy), null, Modifier.size(64.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                XyText("XyDesk", Xy.display.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp))
                XyText("XyVerse Technology Global", Xy.caption.copy(letterSpacing = 1.2.sp))
            }
        }
        XyText(
            "Remote gaming, dari HP.", Xy.caption,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).graphicsLayer { alpha = enter.value },
        )
        Spacer(Modifier.height(0.dp))
    }
}
