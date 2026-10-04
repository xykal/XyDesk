package id.xyverse.xydesk.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.R
import id.xyverse.xydesk.core.Lang
import id.xyverse.xydesk.core.LocalLang
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyButton
import id.xyverse.xydesk.ui.kit.XyText
import kotlinx.coroutines.launch

private class Page(val kicker: String, val title: String, val body: String, val tips: List<Pair<String, String>> = emptyList())

private val pagesEn = listOf(
    Page("WELCOME", "Your PC, in your hand.", "XyDesk is built for games: landscape, low latency, a direct path from GPU to phone."),
    Page("STEP 1", "Install the host on your PC.", "Download XyDesk Host from xydesk.my.id, run it, note the 9-digit device ID and set a password there."),
    Page("STEP 2", "Connect.", "Sign in with email or Google, type the device ID and host password, tap Connect. Hosts you connect to are saved automatically."),
    Page(
        "HOW TO PLAY", "Gestures.", "Your phone screen works like a precision trackpad.",
        listOf("1-finger drag" to "move cursor", "Tap" to "left click", "Long press" to "right click", "2-finger drag" to "scroll", "Mapping" to "analog joystick, WASD, QWERTY, numpad"),
    ),
)

private val pages = listOf(
    Page("SELAMAT DATANG", "PC kamu, di genggaman.", "XyDesk untuk game: lanskap terkunci, latensi rendah, jalur langsung GPU ke HP."),
    Page("LANGKAH 1", "Pasang host di PC.", "Unduh XyDesk Host dari xydesk.my.id, jalankan, lalu catat ID perangkat 9 digit dan atur password di sana."),
    Page("LANGKAH 2", "Hubungkan.", "Masuk dengan email atau Google, ketik ID perangkat dan password host, tekan Hubungkan. Host yang pernah tersambung tersimpan otomatis."),
    Page(
        "CARA MAIN", "Gestur di layar.", "Layar HP bekerja seperti trackpad presisi.",
        listOf("1 jari geser" to "gerakkan kursor", "Ketuk" to "klik kiri", "Tekan lama" to "klik kanan", "2 jari geser" to "scroll", "Mapping" to "joystick analog, WASD, QWERTY, numpad"),
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
                Illustration(i, Modifier.fillMaxWidth().height(200.dp))
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

/** Ilustrasi tiap halaman memakai hero + beberapa aset float agar jelas berganti. */
@Composable
private fun Illustration(page: Int, modifier: Modifier) {
    val heroes = listOf(R.drawable.onboard_welcome, R.drawable.onboard_host, R.drawable.onboard_connect, R.drawable.onboard_gestures)
    val floats = listOf(
        listOf(R.drawable.float_pc_mascot, R.drawable.float_phone, R.drawable.float_sparkle),
        listOf(R.drawable.float_monitor, R.drawable.float_padlock, R.drawable.float_wifi),
        listOf(R.drawable.float_cloud, R.drawable.float_cursor, R.drawable.float_globe),
        listOf(R.drawable.float_gamepad, R.drawable.float_keycap, R.drawable.float_headset),
    )
    val bg = listOf(
        listOf(Color(0xFFEDE7FF), Color(0xFFFFFFFF)),
        listOf(Color(0xFFE9F0FF), Color(0xFFFFFFFF)),
        listOf(Color(0xFFE7FFF6), Color(0xFFFFFFFF)),
        listOf(Color(0xFFFFF0E7), Color(0xFFFFFFFF)),
    )[page.coerceIn(0, 3)]
    Box(
        modifier.clip(RoundedCornerShape(28.dp)).background(Brush.radialGradient(bg)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(heroes.getOrElse(page) { heroes[0] }),
            null,
            Modifier.fillMaxSize().padding(8.dp),
            contentScale = ContentScale.Fit,
        )
        val pack = floats.getOrElse(page) { floats[0] }
        Image(painterResource(pack[0]), null, Modifier.align(Alignment.TopStart).padding(16.dp).size(56.dp).graphicsLayer { rotationZ = -10f }, contentScale = ContentScale.Fit)
        Image(painterResource(pack[1]), null, Modifier.align(Alignment.TopEnd).padding(18.dp).size(52.dp).graphicsLayer { rotationZ = 9f }, contentScale = ContentScale.Fit)
        Image(painterResource(pack[2]), null, Modifier.align(Alignment.BottomEnd).padding(20.dp).size(46.dp).graphicsLayer { rotationZ = -6f }, contentScale = ContentScale.Fit)
    }
}
