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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xyverse.xydesk.R
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ease = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

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
 * Intro minimal: huruf "XyDesk" muncul satu per satu dari bawah dengan blur
 * halus, garis tipis membentang di bawahnya, satu titik ungu meluncur di garis
 * itu, tagline menyusul. Putih bersih, tanpa ornamen. Ketuk untuk melewati.
 */
@Composable
fun SplashScreen(playVoice: Boolean, short: Boolean = false, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val letters = "XyDesk".toList()
    val reveal = remember { letters.map { Animatable(0f) } }
    val line = remember { Animatable(0f) }
    val dot = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val voice = remember { if (playVoice) IntroVoice(ctx) else null }
    DisposableEffect(Unit) { onDispose { voice?.release() } }

    LaunchedEffect(Unit) {
        delay(150)
        voice?.start()
        reveal.forEachIndexed { i, a -> launch { delay(i * 70L); a.animateTo(1f, tween(650, easing = ease)) } }
        delay(520)
        launch { line.animateTo(1f, tween(700, easing = ease)) }
        delay(250)
        launch { dot.animateTo(1f, tween(900, easing = ease)) }
        launch { tagline.animateTo(1f, tween(600, easing = ease)) }
        delay(if (short) 700L else 1500L)
        exit.animateTo(1f, tween(400, easing = ease))
        onDone()
    }

    Box(
        Modifier.fillMaxSize().background(Xy.bg).alpha(1f - exit.value).scale(1f + 0.04f * exit.value)
            .clickable(remember { MutableInteractionSource() }, null) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                letters.forEachIndexed { i, ch ->
                    val v = reveal[i].value
                    XyText(
                        ch.toString(),
                        Xy.display.copy(fontSize = 44.sp, letterSpacing = (-1.5).sp),
                        Modifier.graphicsLayer { alpha = v; translationY = (1f - v) * 28.dp.toPx(); renderEffect = if (v < 1f) BlurEffect(6f * (1f - v), 6f * (1f - v)) else null },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Canvas(Modifier.width(120.dp).height(6.dp)) {
                val w = size.width * line.value
                val x0 = (size.width - w) / 2
                drawLine(Xy.line, Offset(x0, size.height / 2), Offset(x0 + w, size.height / 2), 1.5.dp.toPx(), StrokeCap.Round)
                if (dot.value > 0f && dot.value < 1f) {
                    val x = x0 + w * dot.value
                    drawCircle(Xy.accent.copy(alpha = 0.25f), 5.dp.toPx(), Offset(x, size.height / 2))
                    drawCircle(Xy.accent, 2.5.dp.toPx(), Offset(x, size.height / 2))
                }
            }
            Spacer(Modifier.height(14.dp))
            XyText("XyVerse Technology Global", Xy.label, Modifier.alpha(tagline.value))
        }
    }
}
