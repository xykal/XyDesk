package id.xyverse.xydesk.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.core.SessionRecord
import id.xyverse.xydesk.core.StreamXy
import id.xyverse.xydesk.ui.kit.BottomNav
import id.xyverse.xydesk.ui.kit.Tab
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyButton
import id.xyverse.xydesk.ui.kit.XyCard
import id.xyverse.xydesk.ui.kit.XyText

@Composable
fun HomeShell(
    email: String,
    lastHost: String,
    history: List<SessionRecord>,
    onConnect: (host: String, pin: String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onLogout: () -> Unit,
) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var prefill by remember { mutableStateOf(lastHost) }
    val devices = history.distinctBy { it.host }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "tab") { t ->
            when (t) {
                Tab.HOME -> key(prefill) { ConnectScreen(email, prefill, devices, onConnect) }
                Tab.DEVICES -> Page("Perangkat", "Host yang pernah tersambung.") {
                    if (devices.isEmpty()) Empty("Belum ada perangkat. Sambungkan sekali, ia tersimpan di sini.")
                    DevicesSection(devices) { host -> prefill = host; tab = Tab.HOME }
                }
                Tab.HISTORY -> Page("Riwayat", "Sesi terakhir dan durasinya.") {
                    if (history.isEmpty()) Empty("Belum ada sesi.")
                    HistorySection(history)
                }
                Tab.ACCOUNT -> AccountScreen(email, onOpenUrl, onLogout)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).safeDrawingPadding()) { BottomNav(tab) { tab = it } }
    }
}

@Composable
private fun Page(title: String, caption: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Xy.pad)) {
        Spacer(Modifier.height(12.dp))
        XyText(title, Xy.display)
        XyText(caption, Xy.caption)
        Spacer(Modifier.height(24.dp))
        content()
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun Empty(text: String) {
    XyCard { XyText(text, Xy.body.copy(color = Xy.textMid)) }
    Spacer(Modifier.height(16.dp))
}

private data class Social(val name: String, val url: String, val color: Long)

private val socials = listOf(
    Social("TikTok", "https://tiktok.com/@xydesk", 0xFF010101),
    Social("Instagram", "https://instagram.com/xydesk", 0xFFE1306C),
    Social("YouTube", "https://youtube.com/@xydesk", 0xFFFF0000),
    Social("X", "https://x.com/xydesk", 0xFF000000),
    Social("Web", "https://xydesk.my.id", 0xFF7C3AED),
)

@Composable
private fun AccountScreen(email: String, onOpenUrl: (String) -> Unit, onLogout: () -> Unit) {
    Page("Akun", email) {
        XyCard {
            XyText("IKUTI XYDESK", Xy.label)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                socials.forEach { s ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(48.dp).background(Color(s.color), CircleShape).clickable { onOpenUrl(s.url) },
                            contentAlignment = Alignment.Center,
                        ) { XyText(s.name.take(1), Xy.title.copy(color = Color.White)) }
                        Spacer(Modifier.height(6.dp))
                        XyText(s.name, Xy.caption)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        XyCard {
            XyText("TENTANG", Xy.label)
            Spacer(Modifier.height(8.dp))
            XyText("XyDesk native · libstreamxy ${StreamXy.version()}", Xy.body)
            XyText("XyVerse Technology Global", Xy.caption)
        }
        Spacer(Modifier.height(16.dp))
        XyButton("Keluar", ghost = true, onClick = onLogout)
    }
}
