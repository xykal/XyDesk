package id.xyverse.xydesk.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.core.Lang
import id.xyverse.xydesk.core.LocalLang
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyButton
import id.xyverse.xydesk.ui.kit.XyText
import kotlinx.coroutines.launch

private class Page(val kicker: String, val title: String, val body: String, val tips: List<Pair<String, String>> = emptyList())

private val pagesEn = listOf(
    Page("WELCOME", "Your PC, in your hand.", "XyDesk streams your PC screen to your phone over a low-latency direct path — for work, and for play."),
    Page("STEP 1", "Install the host on your PC.", "Download XyDesk Host from xydesk.my.id, run it, note the 9-digit device ID and set a password there."),
    Page("STEP 2", "Connect.", "Sign in with email or Google, type the device ID and host password, tap Connect. Hosts you connect to are saved automatically."),
    Page(
        "HOW TO PLAY", "Gestures.", "Your phone screen works like a precision trackpad.",
        listOf("1-finger drag" to "move cursor", "Tap" to "left click", "Long press" to "right click", "2-finger drag" to "scroll", "Bottom handle" to "keyboard, quality, stats, disconnect"),
    ),
)

private val pages = listOf(
    Page("SELAMAT DATANG", "PC kamu, di genggaman.", "XyDesk menyalurkan layar PC ke HP lewat jalur langsung berlatensi rendah — untuk kerja, dan untuk main."),
    Page("LANGKAH 1", "Pasang host di PC.", "Unduh XyDesk Host dari xydesk.my.id, jalankan, lalu catat ID perangkat 9 digit dan atur password di sana."),
    Page("LANGKAH 2", "Hubungkan.", "Masuk dengan email atau Google, ketik ID perangkat dan password host, tekan Hubungkan. Host yang pernah tersambung tersimpan otomatis."),
    Page(
        "CARA MAIN", "Gestur di layar.", "Layar HP bekerja seperti trackpad presisi.",
        listOf("1 jari geser" to "gerakkan kursor", "Ketuk" to "klik kiri", "Tekan lama" to "klik kanan", "2 jari geser" to "scroll", "Garis di bawah" to "keyboard, kualitas, stats, putus"),
    ),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pages = if (LocalLang.current == Lang.EN) pagesEn else pages
    val state = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        HorizontalPager(state, Modifier.weight(1f)) { i ->
            val p = pages[i]
            Column(Modifier.fillMaxSize().padding(Xy.pad), verticalArrangement = Arrangement.Center) {
                Illustration(i, Modifier.fillMaxWidth().height(180.dp))
                Spacer(Modifier.height(28.dp))
                XyText(p.kicker, Xy.label.copy(color = Xy.accent))
                Spacer(Modifier.height(8.dp))
                XyText(p.title, Xy.display)
                Spacer(Modifier.height(12.dp))
                XyText(p.body, Xy.body.copy(color = Xy.textMid))
                if (p.tips.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    p.tips.forEach { (g, a) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Xy.overlay).padding(horizontal = 10.dp, vertical = 6.dp)) { XyText(g, Xy.caption.copy(color = Xy.textHi)) }
                            Spacer(Modifier.width(12.dp))
                            XyText(a, Xy.body)
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(horizontal = Xy.pad), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(pages.size) { i ->
                val w by animateDpAsState(if (i == state.currentPage) 22.dp else 6.dp, label = "dot")
                Box(Modifier.height(6.dp).width(w).clip(CircleShape).background(if (i == state.currentPage) Xy.accent else Xy.line))
            }
            Spacer(Modifier.weight(1f))
            if (state.currentPage < pages.lastIndex) XyText("Lewati", Xy.label, Modifier.clickableText(onDone), color = Xy.textMid)
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.padding(horizontal = Xy.pad, vertical = 8.dp)) {
            val last = state.currentPage == pages.lastIndex
            XyButton(if (last) "Mulai" else "Lanjut") {
                if (last) onDone() else scope.launch { state.animateScrollToPage(state.currentPage + 1) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun Modifier.clickableText(onClick: () -> Unit): Modifier = clickable(onClick = onClick)

/** Ilustrasi vektor sederhana per halaman: layar PC, HP, dan jalur di antaranya. */
@Composable
private fun Illustration(page: Int, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx())
        val pc = androidx.compose.ui.geometry.Rect(w * 0.08f, h * 0.12f, w * 0.58f, h * 0.68f)
        val phone = androidx.compose.ui.geometry.Rect(w * 0.70f, h * 0.22f, w * 0.92f, h * 0.88f)
        drawRoundRect(Xy.accent.copy(alpha = 0.10f), androidx.compose.ui.geometry.Offset(0f, h * 0.05f), androidx.compose.ui.geometry.Size(w, h * 0.9f), androidx.compose.ui.geometry.CornerRadius(28.dp.toPx()))
        drawRoundRect(Xy.textHi, pc.topLeft, pc.size, androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()), style = stroke)
        drawLine(Xy.textHi, androidx.compose.ui.geometry.Offset(pc.center.x - w * 0.08f, h * 0.8f), androidx.compose.ui.geometry.Offset(pc.center.x + w * 0.08f, h * 0.8f), stroke.width)
        drawLine(Xy.textHi, androidx.compose.ui.geometry.Offset(pc.center.x, pc.bottom), androidx.compose.ui.geometry.Offset(pc.center.x, h * 0.8f), stroke.width)
        drawRoundRect(Xy.textHi, phone.topLeft, phone.size, androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()), style = stroke)
        if (page >= 1) drawRoundRect(Xy.accent, androidx.compose.ui.geometry.Offset(pc.left + 12.dp.toPx(), pc.top + 12.dp.toPx()), androidx.compose.ui.geometry.Size(pc.width - 24.dp.toPx(), pc.height - 24.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
        if (page >= 2) {
            drawRoundRect(Xy.accent, androidx.compose.ui.geometry.Offset(phone.left + 8.dp.toPx(), phone.top + 10.dp.toPx()), androidx.compose.ui.geometry.Size(phone.width - 16.dp.toPx(), phone.height - 20.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(pc.right, pc.center.y); cubicTo(w * 0.64f, pc.center.y, w * 0.64f, phone.center.y, phone.left, phone.center.y)
            }
            drawPath(path, Xy.accent, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
        }
        if (page == 3) {
            drawCircle(Color.White, 10.dp.toPx(), phone.center)
            drawCircle(Color.White.copy(alpha = 0.4f), 18.dp.toPx(), phone.center, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
    }
}
