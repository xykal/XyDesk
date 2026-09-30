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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
 * Intro brand: logo X kaca-ungu jatuh ke posisi dengan spring (skala 1.7→1,
 * rotasi -14°→0), aura ungu memuai di belakangnya, kilau specular menyapu
 * permukaan logo, lalu wordmark "XyDesk" meluncur keluar dari balik logo.
 * Keluar: seluruh komposisi terangkat dan memudar. Ketuk untuk melewati.
 */
@Composable
fun SplashScreen(playVoice: Boolean, short: Boolean = false, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val drop = remember { Animatable(0f) }
    val aura = remember { Animatable(0f) }
    val sheen = remember { Animatable(-1f) }
    val word = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val voice = remember { if (playVoice) IntroVoice(ctx) else null }
    DisposableEffect(Unit) { onDispose { voice?.release() } }

    LaunchedEffect(Unit) {
        delay(120)
        voice?.start()
        launch { drop.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 170f)) }
        delay(180)
        launch { aura.animateTo(1f, tween(1400, easing = ease)) }
        delay(420)
        launch { sheen.animateTo(1.4f, tween(900, easing = ease)) }
        delay(200)
        launch { word.animateTo(1f, tween(700, easing = ease)) }
        delay(if (short) 800L else 1600L)
        exit.animateTo(1f, tween(420, easing = ease))
        onDone()
    }

    Box(
        Modifier.fillMaxSize().background(Xy.bg)
            .graphicsLayer { alpha = 1f - exit.value; translationY = -exit.value * 60.dp.toPx() }
            .clickable(remember { MutableInteractionSource() }, null) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2 - 30.dp.toPx())
            val r = 70.dp.toPx() + 140.dp.toPx() * aura.value
            drawCircle(Brush.radialGradient(listOf(Xy.accent.copy(alpha = 0.22f * (1f - aura.value * 0.7f)), Color.Transparent), c, r), r, c)
            val ringR = 60.dp.toPx() + 190.dp.toPx() * aura.value
            drawCircle(Xy.accent.copy(alpha = 0.18f * (1f - aura.value)), ringR, c, style = Stroke(1.5.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(132.dp).graphicsLayer {
                    val s = 1.7f - 0.7f * drop.value
                    scaleX = s; scaleY = s
                    rotationZ = -14f * (1f - drop.value)
                    alpha = drop.value.coerceIn(0f, 1f)
                    shadowElevation = 24.dp.toPx() * drop.value
                    shape = CircleShape; clip = false
                    ambientShadowColor = Xy.accent; spotShadowColor = Xy.accent
                },
                contentAlignment = Alignment.Center,
            ) {
                val logo = ImageBitmap.imageResource(R.drawable.logo_xy)
                Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                    drawImage(logo, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                    val x = size.width * sheen.value
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.75f), Color.Transparent),
                            Offset(x - size.width * 0.25f, 0f), Offset(x + size.width * 0.25f, size.height),
                        ),
                        blendMode = BlendMode.SrcAtop,
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Box(Modifier.clip(RectangleShape)) {
                XyText(
                    "XyDesk",
                    Xy.display.copy(fontSize = 38.sp, letterSpacing = (-1.2).sp),
                    Modifier.graphicsLayer { translationY = (1f - word.value) * (-46).dp.toPx(); alpha = word.value },
                )
            }
            Spacer(Modifier.height(6.dp))
            XyText("Kendalikan PC dari mana saja", Xy.caption.copy(letterSpacing = 0.4.sp), Modifier.alpha(word.value))
            Spacer(Modifier.height(18.dp))
            Box(Modifier.width(56.dp).height(3.dp).clip(RoundedCornerShape(Xy.pill)).background(Xy.line).alpha(word.value)) {
                Box(Modifier.fillMaxWidth(sheen.value.coerceIn(0f, 1f)).height(3.dp).background(Xy.accentBrush))
            }
        }
        XyText("XyVerse Technology Global", Xy.label, Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp).alpha(word.value))
    }
}
