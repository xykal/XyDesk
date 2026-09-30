package id.xyverse.xydesk.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import id.xyverse.xydesk.core.Lang
import id.xyverse.xydesk.ui.kit.Social
import id.xyverse.xydesk.ui.kit.SocialMark
import id.xyverse.xydesk.ui.kit.XyToggle
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
    settings: Settings,
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
                Tab.ACCOUNT -> AccountScreen(email, onOpenUrl, settings, onLogout)
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

/** Preferensi yang bisa diubah dari tab Akun. */
class Settings(
    val lang: Lang,
    val onLang: (Lang) -> Unit,
    val haptics: Boolean,
    val onHaptics: (Boolean) -> Unit,
    val onReplayIntro: () -> Unit,
)

private data class SocialLink(val kind: Social, val name: String, val url: String, val color: Long)

private val socials = listOf(
    SocialLink(Social.TIKTOK, "TikTok", "https://tiktok.com/@xydesk", 0xFF010101),
    SocialLink(Social.INSTAGRAM, "Instagram", "https://instagram.com/xydesk", 0xFFE1306C),
    SocialLink(Social.YOUTUBE, "YouTube", "https://youtube.com/@xydesk", 0xFFFF0000),
    SocialLink(Social.X, "X", "https://x.com/xydesk", 0xFF000000),
    SocialLink(Social.WEB, "Web", "https://xydesk.my.id", 0xFF7C3AED),
)

@Composable
private fun AccountScreen(email: String, onOpenUrl: (String) -> Unit, settings: Settings, onLogout: () -> Unit) {
    Page("Akun", email) {
        XyCard {
            XyText("PENGATURAN", Xy.label)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                XyText("Bahasa", Xy.body, Modifier.weight(1f))
                Segmented(listOf("ID" to Lang.ID, "EN" to Lang.EN), settings.lang, settings.onLang)
            }
            Spacer(Modifier.height(4.dp))
            XyToggle("Getaran halus", "Umpan balik saat geser dan tekan.", settings.haptics, settings.onHaptics)
            Spacer(Modifier.height(8.dp))
            XyButton("Putar intro lagi", ghost = true, onClick = settings.onReplayIntro)
        }
        Spacer(Modifier.height(16.dp))
        XyCard {
            XyText("IKUTI XYDESK", Xy.label)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                socials.forEach { s ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(48.dp).background(Color(s.color), CircleShape).border(1.dp, Xy.line, CircleShape).clickable { onOpenUrl(s.url) },
                            contentAlignment = Alignment.Center,
                        ) { SocialMark(s.kind, Modifier.size(22.dp)) }
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

@Composable
private fun <T> Segmented(items: List<Pair<String, T>>, value: T, onChange: (T) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(Xy.pill)).background(Xy.overlay).padding(3.dp)) {
        items.forEach { (label, v) ->
            val on = v == value
            Box(
                Modifier.clip(RoundedCornerShape(Xy.pill)).background(if (on) Color.White else Color.Transparent)
                    .clickable { onChange(v) }.padding(horizontal = 14.dp, vertical = 6.dp),
            ) { XyText(label, Xy.label.copy(color = if (on) Xy.accent else Xy.textMid)) }
        }
    }
}
