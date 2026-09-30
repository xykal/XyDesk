package id.xyverse.xydesk.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xyverse.xydesk.core.Lang
import id.xyverse.xydesk.core.StreamXy
import id.xyverse.xydesk.ui.kit.Icon
import id.xyverse.xydesk.ui.kit.Social
import id.xyverse.xydesk.ui.kit.SocialMark
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyButton
import id.xyverse.xydesk.ui.kit.XyCard
import id.xyverse.xydesk.ui.kit.XyIcon
import id.xyverse.xydesk.ui.kit.XyRow
import id.xyverse.xydesk.ui.kit.XyText
import id.xyverse.xydesk.ui.kit.XyToggle

/** Preferensi dan aksi yang bisa diubah dari tab Akun. */
class Settings(
    val lang: Lang,
    val onLang: (Lang) -> Unit,
    val haptics: Boolean,
    val onHaptics: (Boolean) -> Unit,
    val onReplayIntro: () -> Unit,
    val deviceLabel: String = "",
    val appVersion: String = "",
    val historyCount: Int = 0,
    val onClearHistory: () -> Unit = {},
    val onOpenAppSettings: () -> Unit = {},
)

private enum class Sub { ROOT, PROFILE, SECURITY, PERMISSIONS }

private data class SocialLink(val kind: Social, val name: String, val url: String, val color: Long)

private val socials = listOf(
    SocialLink(Social.TIKTOK, "TikTok", "https://tiktok.com/@xydesk", 0xFF010101),
    SocialLink(Social.INSTAGRAM, "Instagram", "https://instagram.com/xydesk", 0xFFE1306C),
    SocialLink(Social.YOUTUBE, "YouTube", "https://youtube.com/@xydesk", 0xFFFF0000),
    SocialLink(Social.X, "X", "https://x.com/xydesk", 0xFF000000),
    SocialLink(Social.WEB, "Web", "https://xydesk.my.id", 0xFF7C3AED),
)

@Composable
fun AccountScreen(email: String, onOpenUrl: (String) -> Unit, settings: Settings, onLogout: () -> Unit) {
    var sub by remember { mutableStateOf(Sub.ROOT) }
    AnimatedContent(
        sub,
        transitionSpec = {
            val fwd = targetState != Sub.ROOT
            (slideInHorizontally { if (fwd) it / 3 else -it / 3 } + fadeIn(tween(220))) togetherWith (slideOutHorizontally { if (fwd) -it / 3 else it / 3 } + fadeOut(tween(160)))
        },
        label = "akun",
    ) { s ->
        when (s) {
            Sub.ROOT -> Root(email, onOpenUrl, settings, onLogout) { sub = it }
            Sub.PROFILE -> SubPage("Profil", { sub = Sub.ROOT }) { ProfileBody(email, settings) }
            Sub.SECURITY -> SubPage("Keamanan", { sub = Sub.ROOT }) { SecurityBody(settings, onLogout) }
            Sub.PERMISSIONS -> SubPage("Izin", { sub = Sub.ROOT }) { PermissionsBody(settings) }
        }
    }
}

@Composable
private fun Root(email: String, onOpenUrl: (String) -> Unit, settings: Settings, onLogout: () -> Unit, go: (Sub) -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Xy.pad)) {
        Spacer(Modifier.height(12.dp))
        XyText("Akun", Xy.display)
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Xy.radiusL)).background(Xy.accentBrush)
                .clickable(remember { MutableInteractionSource() }, null) { go(Sub.PROFILE) }.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(email, 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                XyText(email.substringBefore("@").ifEmpty { "XyDesk" }, Xy.title.copy(color = Color.White))
                XyText(email, Xy.caption.copy(color = Color.White.copy(alpha = 0.8f)))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.clip(RoundedCornerShape(Xy.pill)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                    XyText("Akun Google", Xy.label.copy(color = Color.White))
                }
            }
            XyIcon(Icon.CHEVRON, tint = Color.White, size = 20.dp)
        }
        Spacer(Modifier.height(16.dp))
        XyCard {
            XyText("PENGATURAN", Xy.label)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                XyText("Bahasa", Xy.body, Modifier.weight(1f))
                Segmented(listOf("ID" to Lang.ID, "EN" to Lang.EN), settings.lang, settings.onLang)
            }
            Box(Modifier.padding(horizontal = 6.dp)) { XyToggle("Getaran halus", "Umpan balik saat geser dan tekan.", settings.haptics, settings.onHaptics) }
            XyRow(Icon.PHONE, "Putar intro lagi", "Splash dan panduan awal.", onClick = settings.onReplayIntro)
        }
        Spacer(Modifier.height(16.dp))
        XyCard {
            XyText("AKUN & PRIVASI", Xy.label)
            Spacer(Modifier.height(8.dp))
            XyRow(Icon.SHIELD, "Keamanan", "Token, password host, riwayat.", onClick = { go(Sub.SECURITY) })
            XyRow(Icon.KEY, "Izin", "Apa saja yang diakses aplikasi.", onClick = { go(Sub.PERMISSIONS) })
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
        XyText("XyDesk ${settings.appVersion} · libstreamxy ${StreamXy.version()} · XyVerse Technology Global", Xy.caption.copy(color = Xy.textLow))
        Spacer(Modifier.height(12.dp))
        XyButton("Keluar", ghost = true, onClick = onLogout)
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun SubPage(title: String, onBack: () -> Unit, body: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Xy.pad)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(Xy.overlay, CircleShape).clickable(remember { MutableInteractionSource() }, null, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { XyIcon(Icon.CHEVRON, Modifier.rotate(180f), tint = Xy.textHi, size = 20.dp) }
            Spacer(Modifier.width(12.dp))
            XyText(title, Xy.display.copy(fontSize = 26.sp))
        }
        Spacer(Modifier.height(20.dp))
        body()
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun ProfileBody(email: String, settings: Settings) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(email, 88.dp)
        Spacer(Modifier.height(12.dp))
        XyText(email.substringBefore("@").ifEmpty { "XyDesk" }, Xy.title)
        XyText(email, Xy.caption)
    }
    Spacer(Modifier.height(20.dp))
    XyCard {
        XyText("MASUK DENGAN", Xy.label)
        Spacer(Modifier.height(8.dp))
        XyRow(Icon.KEY, "Google", email, chevron = false)
    }
    Spacer(Modifier.height(16.dp))
    XyCard {
        XyText("PERANGKAT INI", Xy.label)
        Spacer(Modifier.height(8.dp))
        XyRow(Icon.PHONE, settings.deviceLabel, "Nama yang dilihat host saat pairing.", chevron = false)
        XyRow(Icon.CLOCK, "Riwayat sesi", "${settings.historyCount} sesi tersimpan di HP ini.", chevron = false)
        XyRow(Icon.MONITOR, "Versi aplikasi", "XyDesk ${settings.appVersion}", chevron = false)
    }
}

@Composable
private fun SecurityBody(settings: Settings, onLogout: () -> Unit) {
    XyCard {
        XyText("DATA DI HP INI", Xy.label)
        Spacer(Modifier.height(8.dp))
        XyRow(Icon.SHIELD, "Token login terenkripsi", "AES-256, hanya bisa dibuka oleh aplikasi ini.", chevron = false, tint = Xy.success)
        XyRow(Icon.KEY, "Password host tidak disimpan", "Diminta setiap kali menyambung.", chevron = false, tint = Xy.success)
        XyRow(Icon.MONITOR, "Cuplikan layar host", "Disimpan lokal untuk kartu perangkat; tidak diunggah.", chevron = false, tint = Xy.success)
    }
    Spacer(Modifier.height(16.dp))
    XyCard {
        XyText("TINDAKAN", Xy.label)
        Spacer(Modifier.height(8.dp))
        XyRow(Icon.TRASH, "Hapus riwayat & cuplikan", "${settings.historyCount} sesi akan dihapus.", tint = Xy.warning, onClick = settings.onClearHistory)
        XyRow(Icon.POWER, "Keluar dari HP ini", "Token dihapus, harus login ulang.", tint = Xy.danger, onClick = onLogout)
    }
}

@Composable
private fun PermissionsBody(settings: Settings) {
    XyCard {
        XyText("DIPAKAI", Xy.label)
        Spacer(Modifier.height(8.dp))
        XyRow(Icon.PLUG, "Internet", "Signaling dan jalur WebRTC ke host.", value = "Aktif", chevron = false, tint = Xy.success)
        XyRow(Icon.CONTROLS, "Clipboard", "Hanya saat kamu menyalakannya di dalam sesi.", value = "Opsional", chevron = false)
        XyRow(Icon.MONITOR, "Penyimpanan internal", "Riwayat dan cuplikan; tidak menyentuh galeri.", value = "Aktif", chevron = false, tint = Xy.success)
    }
    Spacer(Modifier.height(16.dp))
    XyCard {
        XyText("TIDAK DIMINTA", Xy.label)
        Spacer(Modifier.height(8.dp))
        XyRow(Icon.SEARCH, "Mikrofon, kamera, lokasi, kontak", "XyDesk tidak meminta izin ini.", chevron = false, tint = Xy.textLow)
    }
    Spacer(Modifier.height(16.dp))
    XyButton("Buka pengaturan aplikasi", ghost = true, onClick = settings.onOpenAppSettings)
}

@Composable
private fun Avatar(email: String, size: Dp) {
    Box(Modifier.size(size).background(Color.White, CircleShape).border(2.dp, Xy.accent.copy(alpha = 0.35f), CircleShape), contentAlignment = Alignment.Center) {
        XyText(email.take(1).ifEmpty { "X" }.uppercase(), Xy.display.copy(fontSize = (size.value * 0.42f).sp, color = Xy.accent))
    }
}

@Composable
fun <T> Segmented(items: List<Pair<String, T>>, value: T, onChange: (T) -> Unit) {
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
