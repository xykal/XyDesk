package id.xyverse.xydesk.ui

import android.media.MediaPlayer
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                p.playbackParams = p.playbackParams.setPitch(0.72f).setSpeed(0.92f)
            }
            p.setVolume(0.96f, 0.96f)
            p.start()
        }
    }

    fun release() {
        runCatching { if (player?.isPlaying == true) player.stop() }
        runCatching { player?.release() }
    }
}

/**
 * Splash: logo + wordmark berdampingan, masuk halus, keluar halus. Dekorasi
 * bermerek (awan, kilau, bola dunia) melayang di sudut dengan animasi lembut
 * dan delay berbeda per elemen. Ketuk untuk lewati.
 */
@Composable
fun SplashScreen(playVoice: Boolean, short: Boolean = false, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val enter = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val voice = remember { if (playVoice) IntroVoice(ctx) else null }
    DisposableEffect(Unit) { onDispose { voice?.release() } }

    // Tiga dekorasi muncul bergiliran: awan dulu, kilau 220 ms kemudian,
    // bola dunia 460 ms kemudian — masing-masing punya fase melayang sendiri.
    val cloudIn = remember { Animatable(0f) }
    val sparkIn = remember { Animatable(0f) }
    val globeIn = remember { Animatable(0f) }

    LaunchedEffect(playVoice) {
        delay(60)
        if (playVoice) voice?.start()
        launch { enter.animateTo(1f, tween(if (short) 520 else 760, easing = ease)) }
        launch { cloudIn.animateTo(1f, tween(if (short) 320 else 520, easing = ease)) }
        launch { delay(220); sparkIn.animateTo(1f, tween(if (short) 320 else 520, easing = ease)) }
        launch { delay(460); globeIn.animateTo(1f, tween(if (short) 320 else 520, easing = ease)) }
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
        val float = rememberInfiniteTransition(label = "aura")
        val bobCloud by float.animateFloat(0f, 1f, infiniteRepeatable(tween(3800, easing = LinearEasing), RepeatMode.Reverse), label = "bobCloud")
        val bobSpark by float.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse), label = "bobSpark")
        val bobGlobe by float.animateFloat(0f, 1f, infiniteRepeatable(tween(4600, easing = LinearEasing), RepeatMode.Reverse), label = "bobGlobe")

        // Awan — kiri atas, terapung paling lambat di antara ketiganya.
        Image(
            painterResource(R.drawable.float_cloud),
            null,
            Modifier
                .align(Alignment.TopStart)
                .offset(x = 18.dp, y = 72.dp)
                .graphicsLayer {
                    alpha = cloudIn.value
                    translationY = (bobCloud - 0.5f) * 18f
                }
                .size(112.dp),
        )

        // Kilau — kanan atas, berkilau sambil naik-turun paling cepat.
        Image(
            painterResource(R.drawable.float_sparkle),
            null,
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-22).dp, y = 88.dp)
                .graphicsLayer {
                    alpha = sparkIn.value * (0.62f + 0.38f * bobSpark)
                    translationY = (0.5f - bobSpark) * 14f
                }
                .size(72.dp),
        )

        // Bola dunia — kiri bawah, bergoyang paling pelan.
        Image(
            painterResource(R.drawable.float_globe),
            null,
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = 26.dp, y = (-96).dp)
                .graphicsLayer {
                    alpha = globeIn.value
                    translationY = (0.5f - bobGlobe) * 20f
                }
                .size(118.dp),
        )

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
            "Remote gaming, masuk ke bayangan.", Xy.caption,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).graphicsLayer { alpha = enter.value },
        )
    }
}
