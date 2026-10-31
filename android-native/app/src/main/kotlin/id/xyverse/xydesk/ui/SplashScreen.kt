package id.xyverse.xydesk.ui

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.R
import id.xyverse.xydesk.core.sfx
import id.xyverse.xydesk.net.Api
import id.xyverse.xydesk.net.RdpClient
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyText
import id.xyverse.xydesk.ui.kit.XyWordmark
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "SplashIntro"

/**
 * Intro XY: Logo Utama + wordmark tetap di tengah; dekorasi bermerek (awan,
 * kilau, bola dunia) melayang di sudut dengan animasi lembut dan delay
 * berbeda per elemen. Tetap dipercepat saat disentuh supaya intro tidak
 * menyebalkan. Titik masuk untuk setup jaringan awal juga tetap di sini.
 */
@Composable
fun SplashScreen(onDone: () -> Unit, onTap: () -> Unit) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(true) }

    // ── SFX: intro voice (fallback pelan kalau ekstensi/aset tidak siap) ──
    DisposableEffect(Unit) {
        val player = try {
            val afd = context.resources.openRawResourceFd(R.raw.xydesk_vo)
            if (afd == null) {
                Log.w(TAG, "IntroVoice: R.raw.xydesk_vo belum tersedia")
                null
            } else {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    isLooping = false
                    setVolume(1f, 1f)
                    setPitch(0.72f)
                    setPlaybackParams(playbackParams.setSpeed(0.92f))
                    setOnPreparedListener { it.start() }
                    setOnCompletionListener { it.release() }
                    setOnErrorListener { _, what, extra ->
                        Log.w(TAG, "IntroVoice playback error what=$what extra=$extra")
                        true
                    }
                    prepareAsync()
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "IntroVoice tidak bisa diputar", t)
            null
        }
        onDispose {
            try {
                player?.let {
                    if (it.isPlaying) it.stop()
                    it.release()
                }
            } catch (_: Throwable) {
            }
        }
    }

    LaunchedEffect(Unit) {
        sfx?.intro()
    }

    LaunchedEffect(Unit) {
        val job = launch {
            // Intro singkat: cukup untuk branding, tidak menggantung.
            delay(2800)
            visible = false
            delay(380)
            onDone()
        }
        sfx?.intro()
        // Sentuh = percepat; tanpa ini intro terasa memaksa.
        Api.ui = { _, _, _, _, _ -> }
        RdpClient.ui = { _, _, _, _ -> }
        job.join()
    }

    // Animasi pernapasan logo (tetap), plus tiga dekorasi melayang yang
    // muncul bergiliran: awan dulu, kilau, lalu bola dunia.
    val transition = rememberInfiniteTransition(label = "splash")
    val floatStep by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
        label = "float",
    )
    val cloudIn = remember { Animatable(0f) }
    val sparkIn = remember { Animatable(0f) }
    val globeIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { cloudIn.animateTo(1f, tween(700)) }
        launch { delay(220); sparkIn.animateTo(1f, tween(700)) }
        launch { delay(460); globeIn.animateTo(1f, tween(700)) }
    }

    Crossfade(
        targetState = visible,
        animationSpec = tween(380),
        label = "splashFade",
    ) { isVisible ->
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(380)),
            exit = fadeOut(tween(380)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Xy.bg)
                    .pointerInput(Unit) { detectTapGestures { onTap() } },
            ) {
                StatusTrim()

                // Awan — kiri atas, terapung pelan dengan fase sendiri.
                Image(
                    painter = painterResource(R.drawable.float_cloud),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 20.dp, y = 78.dp)
                        .graphicsLayer {
                            alpha = cloudIn.value
                            translationY = 9f * sin(floatStep)
                        }
                        .size(116.dp),
                )

                // Kilau — kanan atas, berkedip lembut sambil sedikit naik-turun.
                Image(
                    painter = painterResource(R.drawable.float_sparkle),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-24).dp, y = 92.dp)
                        .graphicsLayer {
                            alpha = sparkIn.value * (0.62f + 0.38f * (0.5f + 0.5f * sin(floatStep * 1.7f + 1.2f)))
                            translationY = -7f * sin(floatStep * 1.3f + 2.1f)
                        }
                        .size(76.dp),
                )

                // Bola dunia — kiri bawah, terapung paling lambat.
                Image(
                    painter = painterResource(R.drawable.float_globe),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 28.dp, y = (-104).dp)
                        .graphicsLayer {
                            alpha = globeIn.value
                            translationY = -11f * sin(floatStep * 0.8f + 4.0f)
                        }
                        .size(122.dp),
                )

                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .shadow(
                                18.dp,
                                CircleShape,
                                ambientColor = Xy.shadow,
                                spotColor = Xy.shadow,
                            )
                            .background(Xy.bg, CircleShape)
                            .size(104.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.logo_xy),
                            contentDescription = "Logo XyDesk",
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = 1f + 0.025f * sin(floatStep)
                                    scaleY = scaleX
                                }
                                .size(84.dp)
                                .padding(10.dp),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    XyWordmark("XyDesk", 30)
                    Spacer(Modifier.height(6.dp))
                    XyText(
                        "XyVerse Technology Global",
                        Xy.caption.copy(textAlign = TextAlign.Center),
                    )
                }
            }
        }
    }
}
