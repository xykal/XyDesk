package id.xyverse.xydesk.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "angle",
    )
    val shape = RoundedCornerShape(Xy.pill)

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .drawWithContent {
                drawContent()
                if (loading) {
                    val stroke = 3.dp.toPx()
                    rotate(spin) {
                        drawRoundRect(
                            brush = Brush.sweepGradient(listOf(Color.Transparent, Color.Transparent, Xy.accent, Color(0xFF4285F4), Color.Transparent)),
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(size.height / 2),
                            style = Stroke(stroke),
                        )
                    }
                }
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
                                if (offset.value > maxPx * 0.85f) { offset.animateTo(maxPx, tween(120)); onTrigger() }
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

/** Logo "G" Google digambar dengan empat busur (tanpa aset bitmap). */
@Composable
fun GoogleMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.2f
        val inset = stroke / 2
        val arc = Size(size.width - stroke, size.height - stroke)
        val tl = Offset(inset, inset)
        fun seg(color: Color, start: Float, sweep: Float) =
            drawArc(color, start, sweep, false, tl, arc, style = Stroke(stroke))
        seg(Color(0xFF4285F4), -10f, 80f)
        seg(Color(0xFF34A853), 70f, 80f)
        seg(Color(0xFFFBBC05), 150f, 70f)
        seg(Color(0xFFEA4335), 220f, 90f)
        drawLine(Color(0xFF4285F4), Offset(size.width / 2, size.height / 2), Offset(size.width - inset, size.height / 2), stroke)
    }
}
