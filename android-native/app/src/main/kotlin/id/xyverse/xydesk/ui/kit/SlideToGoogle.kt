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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * "Ular" cahaya: segmen garis yang merayap mengikuti tepi pil (bukan berputar
 * di tengah). Kepala tebal & pekat, ekor menipis lewat beberapa sub-segmen.
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

/** getSegment yang membungkus lewat titik nol path tertutup. */
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
 * "Geser ke kanan untuk lanjut dengan Google". Knob bulat berlogo G digeser ke
 * ujung; saat mencapai ujung `onTrigger` dipanggil, lalu selama `loading`
 * garis gradien berputar mengelilingi pil.
 */
@Composable
fun SlideToGoogle(loading: Boolean, enabled: Boolean = true, onTrigger: () -> Unit) {
    val density = LocalDensity.current
    val knob = 46.dp
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
            .height(54.dp)
            .drawWithContent {
                drawContent()
                if (loading) drawSnake(measure, travel)
            }
            .padding(2.dp)
            .clip(shape)
            .background(Xy.overlay),
    ) {
        val maxPx = with(density) { (maxWidth - knob - pad * 2).toPx() }
        val progress = (offset.value / maxPx).coerceIn(0f, 1f)
        Box(Modifier.fillMaxSize().padding(start = knob + 12.dp).alpha(1f - progress), contentAlignment = Alignment.CenterStart) {
            XyText(if (loading) "Menghubungkan ke Google…" else "Geser untuk lanjut dengan Google", Xy.caption.copy(color = Xy.textMid))
        }
        Box(
            Modifier
                .padding(pad)
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(knob)
                .shadow(6.dp, CircleShape, ambientColor = Xy.shadow, spotColor = Xy.shadow)
                .clip(CircleShape)
                .background(Color.White)
                .pointerInput(enabled, loading, maxPx) {
                    if (!enabled || loading) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offset.value > maxPx * 0.85f) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); offset.animateTo(maxPx, tween(120)); onTrigger() }
                                else offset.animateTo(0f, spring(stiffness = 500f))
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
        androidx.compose.runtime.LaunchedEffect(loading) { if (!loading && offset.value > 0f) offset.animateTo(0f, tween(300)) }
    }
}

