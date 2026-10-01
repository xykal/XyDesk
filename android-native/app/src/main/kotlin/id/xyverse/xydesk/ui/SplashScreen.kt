package id.xyverse.xydesk.ui

import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xyverse.xydesk.R
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val ease = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/**
 * Master stereo VO + intro sinematik "XyDesk" di `res/raw/xydesk_vo.mp3`.
 */
private class IntroVoice(ctx: android.content.Context) {
    private val player = runCatching { MediaPlayer.create(ctx, R.raw.xydesk_vo) }.getOrNull()

    fun start() = runCatching {
        player?.setVolume(1f, 1f)
        player?.start()
    }

    fun release() = runCatching { player?.release() }
}

private fun buildXContours(s: Float): List<Path> {
    fun c(v: Float) = v * s / 1024f
    val fg = Path().apply {
        moveTo(c(736f), c(113f))
        lineTo(c(876f), c(113f))
        cubicTo(c(922f), c(113f), c(942f), c(171f), c(911f), c(214f))
        cubicTo(c(703f), c(461f), c(485f), c(698f), c(259f), c(908f))
        lineTo(c(129f), c(908f))
        cubicTo(c(90f), c(908f), c(82f), c(849f), c(115f), c(806f))
        cubicTo(c(322f), c(557f), c(532f), c(324f), c(736f), c(113f))
        close()
    }
    val tl = Path().apply {
        moveTo(c(151f), c(113f))
        lineTo(c(297f), c(113f))
        cubicTo(c(371f), c(187f), c(449f), c(263f), c(521f), c(333f))
        lineTo(c(382f), c(505f))
        cubicTo(c(286f), c(400f), c(189f), c(296f), c(108f), c(196f))
        cubicTo(c(88f), c(168f), c(111f), c(113f), c(151f), c(113f))
        close()
    }
    val br = Path().apply {
        moveTo(c(638f), c(483f))
        cubicTo(c(730f), c(586f), c(828f), c(692f), c(916f), c(800f))
        cubicTo(c(940f), c(838f), c(918f), c(908f), c(876f), c(908f))
        lineTo(c(743f), c(908f))
        cubicTo(c(660f), c(824f), c(580f), c(743f), c(505f), c(665f))
        close()
    }
    val ridge = Path().apply {
        moveTo(c(301f), c(618f))
        cubicTo(c(377f), c(532f), c(519f), c(550f), c(629f), c(474f))
    }
    return listOf(fg, tl, br, ridge)
}

private fun DrawScope.drawStarGlint(center: Offset, radius: Float, alpha: Float) {
    if (alpha <= 0.02f || radius <= 0.5f) return
    val core = Color.White.copy(alpha = alpha.coerceIn(0f, 1f))
    val glow = Color(0xFFE9D5FF).copy(alpha = (alpha * 0.55f).coerceIn(0f, 1f))
    drawCircle(
        Brush.radialGradient(listOf(core, glow, Color.Transparent), center, radius * 1.15f),
        radius = radius * 1.15f,
        center = center,
    )
    val arm = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    drawPath(arm, core)
}

/**
 * Intro brand: garis kontur vektor X digambar presisi, lalu logo X 3D kaca-ungu
 * muncul dengan spring, sapuan cahaya specular + kilau bintang menyapu permukaan
 * logo, dan wordmark "XyDesk" meluncur keluar. Ketuk untuk melewati.
 */
@Composable
fun SplashScreen(playVoice: Boolean, short: Boolean = false, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val trace = remember { Animatable(0f) }
    val drop = remember { Animatable(0f) }
    val aura = remember { Animatable(0f) }
    val sheen = remember { Animatable(-0.55f) }
    val word = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val voice = remember { if (playVoice) IntroVoice(ctx) else null }
    DisposableEffect(Unit) { onDispose { voice?.release() } }

    LaunchedEffect(Unit) {
        delay(80)
        voice?.start()
        launch { trace.animateTo(1f, tween(if (short) 560 else 860, easing = ease)) }
        delay(if (short) 220 else 340)
        launch { drop.animateTo(1f, spring(dampingRatio = 0.66f, stiffness = 165f)) }
        launch { aura.animateTo(1f, tween(1350, easing = ease)) }
        delay(if (short) 280 else 440)
        launch { sheen.animateTo(1.45f, tween(if (short) 720 else 980, easing = ease)) }
        delay(160)
        launch { word.animateTo(1f, tween(680, easing = ease)) }
        delay(if (short) 740L else 1520L)
        exit.animateTo(1f, tween(380, easing = ease))
        onDone()
    }

    Box(
        Modifier.fillMaxSize().background(Xy.bg)
            .graphicsLayer { alpha = 1f - exit.value; translationY = -exit.value * 54.dp.toPx() }
            .clickable(remember { MutableInteractionSource() }, null) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2 - 34.dp.toPx())
            val r = 68.dp.toPx() + 148.dp.toPx() * aura.value
            drawCircle(
                Brush.radialGradient(
                    listOf(
                        Xy.accent.copy(alpha = 0.20f * (1f - aura.value * 0.65f)),
                        Color(0xFFD946EF).copy(alpha = 0.08f * (1f - aura.value * 0.75f)),
                        Color.Transparent,
                    ),
                    c,
                    r,
                ),
                r,
                c,
            )
            val ringR = 58.dp.toPx() + 195.dp.toPx() * aura.value
            drawCircle(Xy.accent.copy(alpha = 0.16f * (1f - aura.value)), ringR, c, style = Stroke(1.4.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(138.dp).graphicsLayer {
                    val p = (trace.value * 0.35f + drop.value * 0.65f).coerceIn(0f, 1f)
                    val s = 1.36f - 0.36f * p
                    scaleX = s; scaleY = s
                    rotationZ = -10f * (1f - p)
                    alpha = (trace.value * 1.6f).coerceIn(0f, 1f)
                    shadowElevation = 22.dp.toPx() * drop.value.coerceIn(0f, 1f)
                    shape = CircleShape; clip = false
                    ambientShadowColor = Xy.accent; spotShadowColor = Xy.accent
                },
                contentAlignment = Alignment.Center,
            ) {
                val logo = ImageBitmap.imageResource(R.drawable.logo_xy)
                Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                    val fillAlpha = ((drop.value - 0.08f) / 0.92f).coerceIn(0f, 1f)
                    if (fillAlpha > 0.01f) {
                        drawImage(
                            logo,
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                            alpha = fillAlpha,
                        )
                    }

                    val strokeAlpha = (1f - ((drop.value - 0.55f) / 0.45f).coerceIn(0f, 1f)) * (trace.value * 1.4f).coerceIn(0f, 1f)
                    if (strokeAlpha > 0.02f) {
                        val contours = buildXContours(size.width)
                        val measure = PathMeasure()
                        val strokeBrush = Brush.linearGradient(
                            listOf(
                                Color(0xFF7C3AED).copy(alpha = strokeAlpha),
                                Color(0xFFD946EF).copy(alpha = strokeAlpha),
                                Color(0xFFA78BFA).copy(alpha = strokeAlpha),
                            ),
                            Offset.Zero,
                            Offset(size.width, size.height),
                        )
                        val strokeStyle = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        contours.forEach { path ->
                            measure.setPath(path, false)
                            val len = measure.length
                            val stop = len * trace.value.coerceIn(0f, 1f)
                            if (stop > 1f) {
                                val seg = Path()
                                measure.getSegment(0f, stop, seg, true)
                                drawPath(seg, strokeBrush, style = strokeStyle)
                                if (trace.value < 0.96f) {
                                    val tip = measure.getPosition(stop)
                                    if (tip != Offset.Unspecified) {
                                        drawStarGlint(tip, 7.dp.toPx(), strokeAlpha * 0.9f)
                                    }
                                }
                            }
                        }
                    }

                    if (fillAlpha > 0.1f && sheen.value > -0.5f && sheen.value < 1.42f) {
                        val x = size.width * sheen.value
                        val band = size.width * 0.30f
                        drawRect(
                            Brush.linearGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0xFFF5D0FE).copy(alpha = 0.32f * fillAlpha),
                                    Color.White.copy(alpha = 0.82f * fillAlpha),
                                    Color(0xFFC4B5FD).copy(alpha = 0.28f * fillAlpha),
                                    Color.Transparent,
                                ),
                                Offset(x - band, 0f),
                                Offset(x + band, size.height * 0.85f),
                            ),
                            blendMode = BlendMode.SrcAtop,
                        )
                    }

                    if (fillAlpha > 0.2f) {
                        val g1 = (1f - abs(sheen.value - 0.24f) / 0.24f).coerceIn(0f, 1f)
                        val g2 = (1f - abs(sheen.value - 0.56f) / 0.24f).coerceIn(0f, 1f)
                        val g3 = (1f - abs(sheen.value - 0.88f) / 0.24f).coerceIn(0f, 1f)
                        drawStarGlint(Offset(size.width * 0.78f, size.height * 0.18f), 11.dp.toPx() * g1, g1 * fillAlpha)
                        drawStarGlint(Offset(size.width * 0.49f, size.height * 0.52f), 13.dp.toPx() * g2, g2 * fillAlpha)
                        drawStarGlint(Offset(size.width * 0.22f, size.height * 0.80f), 11.dp.toPx() * g3, g3 * fillAlpha)
                    }
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
