package id.xyverse.xydesk.ui

import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xyverse.xydesk.R
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ease = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/**
 * VO "XyDesk": diperkeras +12 dB (LoudnessEnhancer) dan diberi ruang gema aula
 * besar lewat PresetReverb pada sesi audio pemutar — reverb, bukan echo/delay.
 */
private class IntroVoice(ctx: android.content.Context) {
    private val player = MediaPlayer.create(ctx, R.raw.xydesk_vo)
    private val loud = runCatching { LoudnessEnhancer(player.audioSessionId).apply { setTargetGain(1200); enabled = true } }.getOrNull()
    private val reverb = runCatching {
        PresetReverb(0, player.audioSessionId).apply { preset = PresetReverb.PRESET_LARGEHALL; enabled = true }
    }.getOrNull()

    fun start() = runCatching {
        reverb?.let { player.attachAuxEffect(it.id); player.setAuxEffectSendLevel(1f) }
        player.setVolume(1f, 1f)
        player.start()
    }

    fun release() {
        runCatching { player.release() }
        runCatching { loud?.release() }
        runCatching { reverb?.release() }
    }
}

/**
 * Intro: garis "X" tergambar dari dua goresan, denyut cahaya ungu dari pusat,
 * wordmark muncul mengembang, lalu VO "XyDesk". Ketuk untuk melewati.
 */
@Composable
fun SplashScreen(playVoice: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val stroke = remember { Animatable(0f) }
    val pulse = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val voice = remember { if (playVoice) IntroVoice(ctx) else null }
    DisposableEffect(Unit) { onDispose { voice?.release() } }

    LaunchedEffect(Unit) {
        launch { stroke.animateTo(1f, tween(900, easing = ease)) }
        delay(500)
        voice?.start()
        launch { pulse.animateTo(1f, tween(1400, easing = ease)) }
        delay(250)
        launch { word.animateTo(1f, tween(800, easing = ease)) }
        delay(1900)
        exit.animateTo(1f, tween(450, easing = ease))
        onDone()
    }

    Box(
        Modifier.fillMaxSize().background(Xy.bg).alpha(1f - exit.value)
            .clickable(remember { MutableInteractionSource() }, null) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2 - 24.dp.toPx())
            val r = size.minDimension * (0.25f + 0.9f * pulse.value)
            drawCircle(Brush.radialGradient(listOf(Xy.accent.copy(alpha = 0.18f * (1f - pulse.value)), Color.Transparent), c, r), r, c)
            val half = 34.dp.toPx()
            val a = Path().apply { moveTo(c.x - half, c.y - half); lineTo(c.x + half, c.y + half) }
            val bPath = Path().apply { moveTo(c.x + half, c.y - half); lineTo(c.x - half, c.y + half) }
            listOf(a, bPath).forEachIndexed { i, p ->
                val m = PathMeasure().apply { setPath(p, false) }
                val t = ((stroke.value - i * 0.25f) / 0.75f).coerceIn(0f, 1f)
                val seg = Path()
                m.getSegment(0f, m.length * t, seg, true)
                drawPath(seg, Xy.accentBrush, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Column(Modifier.scale(0.92f + 0.08f * word.value).alpha(word.value), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(110.dp))
            XyText("XyDesk", Xy.display.copy(fontSize = 40.sp, letterSpacing = (-1.5).sp))
            Spacer(Modifier.height(6.dp))
            XyText("XyVerse Technology Global", Xy.label)
        }
    }
}
