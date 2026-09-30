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
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyButton
import id.xyverse.xydesk.ui.kit.XyText
import kotlinx.coroutines.launch

private class Page(val kicker: String, val title: String, val body: String, val tips: List<Pair<String, String>> = emptyList())

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
    val state = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        HorizontalPager(state, Modifier.weight(1f)) { i ->
            val p = pages[i]
            Column(Modifier.fillMaxSize().padding(Xy.pad), verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Xy.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    XyText("${i + 1}", Xy.display.copy(color = Xy.accent))
                }
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
