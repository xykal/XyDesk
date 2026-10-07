package id.xyverse.xydesk.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * "Ular" cahaya: garis gradien yang merayap mengikuti tepi pil saat loading.
 */
private fun DrawScope.drawSnake(measure: PathMeasure, t: Float) {
    val stroke = 3.dp.toPx()
    val rect = Path().apply {
        addRoundRect(RoundRect(stroke / 2, stroke / 2, size.width - stroke / 2, size.height - stroke / 2, CornerRadius(size.height / 2)))
    }
    measure.setPath(rect, true)
    val len = measure.length
    val body = len * 0.32f
    val head = t * len
    val steps = 6
    for (i in 0 until steps) {
        val a = head - body * (i + 1) / steps
        val b = head - body * i / steps
        val seg = Path()
        segment(measure, len, a, b, seg)
        val alpha = 1f - i / steps.toFloat()
        drawPath(seg, Xy.accent.copy(alpha = alpha), style = Stroke(stroke * (1f - 0.5f * i / steps), cap = StrokeCap.Round))
    }
}

private fun segment(m: PathMeasure, len: Float, from: Float, to: Float, out: Path) {
    var a = from
    var b = to
    while (a < 0f) { a += len; b += len }
    if (b <= len) { m.getSegment(a, b, out, true); return }
    m.getSegment(a, len, out, true)
    val rest = Path()
    m.getSegment(0f, b - len, rest, true)
    out.addPath(rest)
}

/**
 * Tombol interaktif Google: dapat ditekan langsung (tap) maupun digeser (slide).
 * Memberikan respons instan tanpa membingungkan pengguna atau terjebak loading.
 */
@Composable
fun SlideToGoogle(loading: Boolean, enabled: Boolean = true, onTrigger: () -> Unit) {
    val density = LocalDensity.current
    val knob = 44.dp
    val pad = 4.dp
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val travel by rememberInfiniteTransition(label = "snake").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    val measure = remember { PathMeasure() }
    val shape = RoundedCornerShape(Xy.pill)
    val haptic = LocalHapticFeedback.current

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .drawWithContent {
                drawContent()
                if (loading) drawSnake(measure, travel)
            }
            .clip(shape)
            .background(Xy.bg)
            .border(1.5.dp, Xy.line, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled && !loading,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTrigger()
                },
            ),
    ) {
        val maxPx = with(density) { (maxWidth - knob - pad * 2).toPx() }
        val progress = (offset.value / maxPx).coerceIn(0f, 1f)

        // Indikator swipe ungu muda di belakang
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Xy.accent.copy(alpha = 0.08f + 0.32f * progress)),
        )

        // Label teks di tengah tombol
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                XyText("Menghubungkan ke Google…", Xy.body.copy(fontWeight = FontWeight.Medium, color = Xy.accent))
            } else {
                Spacer(Modifier.width(knob / 2))
                XyText("Lanjutkan dengan Google", Xy.body.copy(fontWeight = FontWeight.SemiBold, color = Xy.textHi))
            }
        }

        // Knob bulat logo G yang bisa digeser atau ditekan
        if (!loading) {
            Box(
                Modifier
                    .padding(pad)
                    .offset { IntOffset(offset.value.roundToInt(), 0) }
                    .size(knob)
                    .shadow(4.dp, CircleShape, ambientColor = Xy.accent.copy(alpha = 0.2f), spotColor = Xy.accent.copy(alpha = 0.3f))
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Xy.line, CircleShape)
                    .pointerInput(enabled, loading, maxPx) {
                        if (!enabled || loading) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (offset.value > maxPx * 0.65f) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        offset.animateTo(maxPx, tween(120))
                                        onTrigger()
                                    } else {
                                        offset.animateTo(0f, spring(stiffness = 500f))
                                    }
                                }
                            },
                            onDragCancel = { scope.launch { offset.animateTo(0f) } },
                        ) { change, dx ->
                            change.consume()
                            scope.launch { offset.snapTo((offset.value + dx).coerceIn(0f, maxPx)) }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                GoogleMark(Modifier.size(22.dp))
            }
        }

        LaunchedEffect(loading) {
            if (!loading && offset.value > 0f) offset.animateTo(0f, tween(300))
        }
    }
}
