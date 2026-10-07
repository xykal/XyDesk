package id.xyverse.xydesk.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.xyverse.xydesk.core.Images
import id.xyverse.xydesk.core.Lang
import id.xyverse.xydesk.core.RemoteImage
import id.xyverse.xydesk.core.Sfx
import id.xyverse.xydesk.core.Store
import id.xyverse.xydesk.core.StreamXy
import id.xyverse.xydesk.net.Api
import id.xyverse.xydesk.ui.kit.Icon
import id.xyverse.xydesk.ui.kit.Social
import id.xyverse.xydesk.ui.kit.SocialMark
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyButton
import id.xyverse.xydesk.ui.kit.XyCard
import id.xyverse.xydesk.ui.kit.XyField
import id.xyverse.xydesk.ui.kit.XyIcon
import id.xyverse.xydesk.ui.kit.XyNotice
import id.xyverse.xydesk.ui.kit.XyRow
import id.xyverse.xydesk.ui.kit.XyText
import id.xyverse.xydesk.ui.kit.XyToggle
import kotlinx.coroutines.launch

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
    val store: Store? = null,
    val onTextSize: (Int) -> Unit = {},
)

private enum class Sub { ROOT, PROFILE, SECURITY, PERMISSIONS, SESSION, GENERAL, ABOUT }

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
    BackHandler(sub != Sub.ROOT) { sub = Sub.ROOT }
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
            Sub.PROFILE -> SubPage("Edit Profil", { sub = Sub.ROOT }) { ProfileBody(email, settings) }
            Sub.SECURITY -> SubPage("Keamanan", { sub = Sub.ROOT }) { SecurityBody(settings, onLogout) }
            Sub.PERMISSIONS -> SubPage("Izin", { sub = Sub.ROOT }) { PermissionsBody(settings) }
            Sub.ABOUT -> SubPage("Tentang", { sub = Sub.ROOT }) { AboutBody(settings.appVersion, onOpenUrl) }
            Sub.SESSION -> SubPage("Sesi", { sub = Sub.ROOT }) { settings.store?.let { SessionDefaults(it) } }
            Sub.GENERAL -> SubPage("Umum", { sub = Sub.ROOT }) { settings.store?.let { GeneralMorePrefs(it, settings.onTextSize) } }
        }
    }
}

@Composable
private fun Root(email: String, onOpenUrl: (String) -> Unit, settings: Settings, onLogout: () -> Unit, go: (Sub) -> Unit) {
    val store = settings.store
    val displayName = store?.userName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@").ifEmpty { "XyDesk" }
    val photoUrl = store?.userPhoto.orEmpty()

    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Xy.pad)) {
        Spacer(Modifier.height(12.dp))
        XyText("Akun", Xy.display)
        Spacer(Modifier.height(20.dp))

        // Kartu Profil Utama: Menampilkan nama, foto, dan email dengan tombol edit
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Xy.radiusL))
                .background(Xy.accentBrush)
                .clickable(remember { MutableInteractionSource() }, null) { go(Sub.PROFILE) }
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccountAvatar(name = displayName, photoUrl = photoUrl, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                XyText(displayName, Xy.title.copy(color = Color.White, fontWeight = FontWeight.Bold), maxLines = 1)
                XyText(email, Xy.caption.copy(color = Color.White.copy(alpha = 0.85f)), maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(Xy.pill))
                            .background(Color.White.copy(alpha = 0.22f))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        XyText("Edit Profil", Xy.label.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
            XyIcon(Icon.CHEVRON, tint = Color.White, size = 20.dp)
        }

        Spacer(Modifier.height(16.dp))
        XyCard {
            XyText("UMUM", Xy.label)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                XyText("Bahasa", Xy.body, Modifier.weight(1f))
                Segmented(listOf("ID" to Lang.ID, "EN" to Lang.EN), settings.lang, settings.onLang)
            }
            Box(Modifier.padding(horizontal = 6.dp)) { XyToggle("Getaran halus", "Umpan balik saat geser dan tekan.", settings.haptics, settings.onHaptics) }
            settings.store?.let { PreferencesSection(it) }
        }
        Spacer(Modifier.height(16.dp))
        XyCard {
            XyText("SESI", Xy.label)
            Spacer(Modifier.height(8.dp))
            XyRow(Icon.CONTROLS, "Video, kursor, HUD", "Kualitas, fps, trackpad, HUD, panduan gestur.", onClick = { go(Sub.SESSION) })
            XyRow(Icon.SETTINGS, "Umum", "Koneksi, privasi, data, ukuran teks, berita.", onClick = { go(Sub.GENERAL) })
            XyText("Kontrol mapping lengkap (joystick analog, WASD, mouse, F1–F12, QWERTY, numpad, shortcut) diatur langsung di dalam sesi lewat tombol Mapping.", Xy.caption, Modifier.padding(horizontal = 6.dp, vertical = 6.dp))
        }
        Spacer(Modifier.height(16.dp))
        XyCard {
            XyText("PRIVASI & KEAMANAN", Xy.label)
            Spacer(Modifier.height(8.dp))
            XyRow(Icon.SHIELD, "Keamanan", "Token, password host tersimpan, hapus riwayat.", onClick = { go(Sub.SECURITY) })
            XyRow(Icon.KEY, "Izin", "Mikrofon, notifikasi, dan yang tidak diminta.", onClick = { go(Sub.PERMISSIONS) })
        }
        Spacer(Modifier.height(16.dp))
        XyCard {
            XyText("TENTANG", Xy.label)
            Spacer(Modifier.height(8.dp))
            XyRow(Icon.NEWS, "Tentang XyDesk", "Versi, catatan rilis, S&K, privasi, lisensi.", onClick = { go(Sub.ABOUT) })
            XyRow(Icon.PHONE, "Putar intro lagi", "Splash dan panduan awal.", onClick = settings.onReplayIntro)
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

/**
 * Halaman Edit Profil: Pengguna dapat mengubah nama tampilan dan foto profil,
 * lalu menyimpannya ke server Cloudflare dan penyimpanan lokal.
 */
@Composable
private fun ProfileBody(email: String, settings: Settings) {
    val store = settings.store
    val jwt = store?.jwt.orEmpty()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sfx = remember { Sfx(ctx) }
    DisposableEffect(Unit) { onDispose { sfx.release() } }

    var nameInput by remember { mutableStateOf(store?.userName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")) }
    var photoInput by remember { mutableStateOf(store?.userPhoto.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var tone by remember { mutableStateOf(Xy.textMid) }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AccountAvatar(name = nameInput, photoUrl = photoInput, size = 96.dp)
        Spacer(Modifier.height(12.dp))
        XyText(nameInput.ifBlank { "Pengguna XyDesk" }, Xy.title.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold))
        XyText(email, Xy.caption.copy(color = Xy.textMid))
    }

    Spacer(Modifier.height(20.dp))

    XyCard {
        XyText("EDIT PROFIL", Xy.label)
        Spacer(Modifier.height(12.dp))

        XyField(
            value = nameInput,
            onValueChange = { nameInput = it; if (notice.isNotBlank()) notice = "" },
            label = "Nama Tampilan",
            hint = "Nama kamu di ruang obrolan",
            enabled = !busy,
        )

        Spacer(Modifier.height(14.dp))

        XyField(
            value = photoInput,
            onValueChange = { photoInput = it.trim(); if (notice.isNotBlank()) notice = "" },
            label = "URL Foto Profil (Opsional)",
            hint = "https://lh3.googleusercontent.com/...",
            enabled = !busy,
        )

        if (notice.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            XyNotice(notice, tone)
        }

        Spacer(Modifier.height(18.dp))

        val canSave = !busy && nameInput.trim().length >= 2 && nameInput.trim() != (store?.userName.orEmpty()) || photoInput != store?.userPhoto.orEmpty()

        XyButton(
            label = if (busy) "Menyimpan…" else "Simpan Perubahan",
            enabled = canSave && nameInput.trim().length >= 2,
        ) {
            busy = true
            notice = ""
            scope.launch {
                runCatching {
                    val cleanName = nameInput.trim()
                    val cleanPhoto = photoInput.trim()
                    if (jwt.isNotBlank()) {
                        Api.updateProfile(jwt, cleanName, cleanPhoto.ifBlank { null })
                    }
                    store?.userName = cleanName
                    store?.userPhoto = cleanPhoto.ifBlank { null }
                    sfx.confirm()
                    notice = "Profil berhasil disimpan!"
                    tone = Xy.success
                }.onFailure {
                    notice = it.message ?: "Gagal memperbarui profil."
                    tone = Xy.danger
                }
                busy = false
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    XyCard {
        XyText("INFORMASI AKUN", Xy.label)
        Spacer(Modifier.height(8.dp))
        XyRow(Icon.KEY, "Alamat Email", email, chevron = false)
        XyRow(Icon.PHONE, "Perangkat Ini", settings.deviceLabel, chevron = false)
        XyRow(Icon.CLOCK, "Riwayat Sesi", "${settings.historyCount} sesi di perangkat ini", chevron = false)
        XyRow(Icon.MONITOR, "Versi XyDesk", "v${settings.appVersion}", chevron = false)
    }
}

@Composable
private fun SecurityBody(settings: Settings, onLogout: () -> Unit) {
    XyCard {
        XyText("DATA DI HP INI", Xy.label)
        Spacer(Modifier.height(8.dp))
        XyRow(Icon.SHIELD, "Token login terenkripsi", "AES-256, hanya bisa dibuka oleh aplikasi ini.", chevron = false, tint = Xy.success)
        XyRow(Icon.KEY, "Password host", "Tidak disimpan kecuali kamu memilih \"Ingat\" saat menyambung dari riwayat.", chevron = false, tint = Xy.success)
        XyRow(Icon.MONITOR, "Wallpaper host", "Disimpan lokal untuk kartu perangkat; tidak diunggah.", chevron = false, tint = Xy.success)
    }
    Spacer(Modifier.height(16.dp))
    XyCard {
        XyText("TINDAKAN", Xy.label)
        Spacer(Modifier.height(8.dp))
        settings.store?.let { ExportHistoryRow(it) }
        XyRow(Icon.TRASH, "Hapus riwayat & wallpaper", "${settings.historyCount} sesi akan dihapus.", tint = Xy.warning, onClick = settings.onClearHistory)
        XyRow(Icon.KEY, "Lupakan password host tersimpan", "Semua host akan minta password lagi.", tint = Xy.warning, onClick = { settings.store?.forgetAllPins() })
        XyRow(Icon.POWER, "Keluar dari HP ini", "Token dihapus, harus login ulang.", tint = Xy.danger, onClick = onLogout)
    }
}

@Composable
private fun PermissionsBody(settings: Settings) {
    val ctx = LocalContext.current
    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    var mic by remember { mutableStateOf(granted(Manifest.permission.RECORD_AUDIO)) }
    var notif by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { mic = it }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notif = it }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                mic = granted(Manifest.permission.RECORD_AUDIO)
                notif = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        lifecycle.lifecycle.addObserver(obs); onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }
    XyCard {
        XyText("DIPAKAI", Xy.label)
        Spacer(Modifier.height(8.dp))
        XyRow(Icon.PLUG, "Internet", "Signaling dan jalur WebRTC ke host.", value = "Aktif", chevron = false, tint = Xy.success)
        XyRow(Icon.KEYBOARD, "Mikrofon", "Suara HP dikirim ke PC saat tombol Mic di sesi dinyalakan.",
            value = if (mic) "Diizinkan" else "Minta izin", chevron = !mic, tint = if (mic) Xy.success else Xy.accent,
            onClick = if (mic) null else ({ askMic.launch(Manifest.permission.RECORD_AUDIO) }))
        XyRow(Icon.NEWS, "Notifikasi", "Satu pemberitahuan saat ada berita baru.",
            value = if (notif) "Diizinkan" else "Minta izin", chevron = !notif, tint = if (notif) Xy.success else Xy.accent,
            onClick = if (notif || Build.VERSION.SDK_INT < 33) null else ({ askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }))
        XyRow(Icon.CONTROLS, "Clipboard", "Hanya saat kamu menyalakannya di dalam sesi.", value = "Opsional", chevron = false)
        XyRow(Icon.MONITOR, "Penyimpanan internal", "Riwayat dan wallpaper; tidak menyentuh galeri.", value = "Aktif", chevron = false, tint = Xy.success)
    }
    Spacer(Modifier.height(16.dp))
    XyCard {
        XyText("TIDAK DIMINTA", Xy.label)
        Spacer(Modifier.height(8.dp))
        XyRow(Icon.SEARCH, "Kamera, lokasi, kontak, galeri", "XyDesk tidak meminta izin ini.", chevron = false, tint = Xy.textLow)
    }
    Spacer(Modifier.height(16.dp))
    XyButton("Buka pengaturan aplikasi", ghost = true, onClick = settings.onOpenAppSettings)
}

/**
 * Avatar akun: menampilkan foto profil asli bila tersedia, atau inisial nama berlatar ungu.
 */
@Composable
private fun AccountAvatar(name: String, photoUrl: String, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Xy.accent)
            .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUrl.isNotBlank() && photoUrl.startsWith("https://")) {
            RemoteImage(url = photoUrl, modifier = Modifier.fillMaxSize())
        } else {
            val initial = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "X"
            XyText(
                initial,
                Xy.display.copy(fontSize = (size.value * 0.40f).sp, color = Color.White, fontWeight = FontWeight.Bold),
            )
        }
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
