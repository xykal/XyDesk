package id.xyverse.xydesk.core

import androidx.compose.runtime.compositionLocalOf

/** Teks UI dua bahasa. Kunci = bahasa Indonesia; nilai = Inggris. */
enum class Lang { ID, EN }

val LocalLang = compositionLocalOf { Lang.ID }

private val en = mapOf(
    "Kirim kode" to "Send code",
    "Masuk" to "Sign in",
    "Kode OTP" to "OTP code",
    "Kode dikirim ke" to "Code sent to",
    "Geser untuk lanjut dengan Google" to "Slide to continue with Google",
    "Menghubungkan ke Google…" to "Connecting to Google…",
    "Login Google dibatalkan." to "Google sign-in cancelled.",
    "atau" to "or",
    "Kendalikan PC dari mana saja — latensi rendah, jalur langsung." to "Control your PC from anywhere — low latency, direct path.",
    "Sambungkan" to "Connect",
    "ID perangkat" to "Device ID",
    "Password host" to "Host password",
    "sesuai di aplikasi host" to "as set in the host app",
    "Hubungkan" to "Connect",
    "ID perangkat belum lengkap." to "Device ID is incomplete.",
    "Password host wajib diisi." to "Host password is required.",
    "PERANGKAT TERAKHIR" to "RECENT DEVICES",
    "RIWAYAT SESI" to "SESSION HISTORY",
    "Beranda" to "Home",
    "Perangkat" to "Devices",
    "Riwayat" to "History",
    "Akun" to "Account",
    "Host yang pernah tersambung." to "Hosts you have connected to.",
    "Belum ada perangkat. Sambungkan sekali, ia tersimpan di sini." to "No devices yet. Connect once and it is saved here.",
    "Sesi terakhir dan durasinya." to "Recent sessions and their duration.",
    "Belum ada sesi." to "No sessions yet.",
    "IKUTI XYDESK" to "FOLLOW XYDESK",
    "PENGATURAN" to "SETTINGS",
    "Bahasa" to "Language",
    "Getaran halus" to "Subtle haptics",
    "Umpan balik saat geser dan tekan." to "Feedback on slide and tap.",
    "Putar intro lagi" to "Replay intro",
    "TENTANG" to "ABOUT",
    "Keluar" to "Sign out",
    "Lewati" to "Skip",
    "Lanjut" to "Next",
    "Mulai" to "Start",
)

fun String.tr(lang: Lang): String = if (lang == Lang.EN) en[this] ?: this else this
