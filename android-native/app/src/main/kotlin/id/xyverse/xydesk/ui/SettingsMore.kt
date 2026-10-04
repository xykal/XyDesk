package id.xyverse.xydesk.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.core.NewsWatch
import id.xyverse.xydesk.core.P
import id.xyverse.xydesk.core.Store
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyCard
import id.xyverse.xydesk.ui.kit.XyText
import id.xyverse.xydesk.ui.kit.XyToggle

/** Satu baris pilihan bersegmen yang langsung tersimpan ke `Store`. */
@Composable
fun Choice(store: Store, key: String, title: String, caption: String, options: List<String>, def: Int = 0, onChange: (Int) -> Unit = {}) {
    var v by remember { mutableIntStateOf(store.int(key, def)) }
    Column(Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            XyText(title, Xy.body, Modifier.weight(1f))
            Segmented(options.mapIndexed { i, l -> l to i }, v) { v = it; store.set(key, it); onChange(it) }
        }
        if (caption.isNotEmpty()) XyText(caption, Xy.caption)
    }
}

/** Saklar yang langsung tersimpan ke `Store`. */
@Composable
fun Switch(store: Store, key: String, title: String, caption: String, def: Boolean = false, onChange: (Boolean) -> Unit = {}) {
    var v by remember { mutableStateOf(store.bool(key, def)) }
    Box(Modifier.padding(horizontal = 6.dp)) { XyToggle(title, caption, v) { v = it; store.set(key, it); onChange(it) } }
}

/** Pengaturan tambahan di halaman Sesi: tampilan, audio awal, kursor lanjutan, HUD, dok. */
@Composable
fun SessionMorePrefs(store: Store) {
    XyCard {
        XyText("TAMPILAN SESI", Xy.label)
        XyText("Sesi dikunci lanskap. Menu lain bebas potret.", Xy.caption)
        Choice(store, P.RAIL_AUTOHIDE, "Rel otomatis sembunyi", "Rel kanan menyusut jadi garis bila tidak disentuh.", listOf("Mati", "5 d", "10 d", "20 d"))
        Choice(store, P.START_RES, "Resolusi awal", "Dikirim ke host saat tersambung; Auto mengikuti jaringan.", listOf("Auto", "720p", "1080p"))
        Choice(store, P.MAX_MBPS, "Bitrate maksimum", "Batas atas agar kuota aman; Auto = tanpa batas (≤ 50 Mbps).", listOf("Auto", "8", "12", "20", "30"))
        Switch(store, P.SESSION_HAPTIC, "Getaran di sesi", "Getar halus saat klik, modifier, dan gestur tiga jari.", true)
    }
    Spacer(Modifier.height(16.dp))
    XyCard {
        XyText("SAAT MULAI SESI", Xy.label)
        Switch(store, P.START_MUTED, "Audio PC bisu", "Suara PC tidak diputar sampai dinyalakan di panel Kontrol.")
        Switch(store, P.START_MIC, "Mic langsung nyala", "Butuh izin mikrofon dan host dengan XyDesk Virtual Microphone.")
        Switch(store, P.START_CLIP, "Clipboard dua arah", "Teks yang disalin di HP ikut ke PC dan sebaliknya.")
    }
    Spacer(Modifier.height(16.dp))
    XyCard {
        XyText("KURSOR LANJUTAN", Xy.label)
        Choice(store, P.ACCEL, "Percepatan kursor", "Gerak cepat melompat lebih jauh; Mati = linear.", listOf("Mati", "Sedang", "Kuat"), 1)
        Choice(store, P.SCROLL_SPEED, "Kecepatan scroll", "", listOf("Lambat", "Normal", "Cepat"), 1)
        Switch(store, P.TWO_FINGER_RIGHT, "Ketuk dua jari = klik kanan", "Matikan bila sering tidak sengaja.", true)
        Switch(store, P.SWIPE3, "Gestur tiga jari", "Geser kiri/kanan Alt+Tab, ke atas Win+Tab.", true)
    }
    Spacer(Modifier.height(16.dp))
    XyCard {
        XyText("HUD & DOK", Xy.label)
        Choice(store, P.HUD_SIZE, "Ukuran HUD", "", listOf("S", "M", "L"), 1)
        Switch(store, P.HUD_RIGHT, "HUD di kanan atas", "Bawaan kiri atas, menjauh dari rel.")
        Switch(store, P.DOCK_TOP, "Dok di atas", "Bawaan di bawah layar.")
    }
}

/** Pengaturan tambahan di halaman Akun: koneksi, privasi, data, tampilan. */
@Composable
fun GeneralMorePrefs(store: Store, onTextSize: (Int) -> Unit) {
    val ctx = LocalContext.current
    XyCard {
        XyText("KONEKSI", Xy.label)
        Switch(store, P.REMEMBER_DEFAULT, "Ingat perangkat secara bawaan", "Saklar di halaman Sambungkan menyala otomatis.", true)
        Switch(store, P.AUTO_LAST, "Sambung otomatis ke PC terakhir", "Saat aplikasi dibuka, langsung tersambung bila password tersimpan.")
        Switch(store, P.FORCE_RELAY, "Selalu lewat relay", "Menyembunyikan IP HP dari host; latensi sedikit naik.")
    }
    Spacer(Modifier.height(16.dp))
    XyCard {
        XyText("PRIVASI & DATA", Xy.label)
        Switch(store, P.SAVE_PREVIEW, "Simpan wallpaper PC", "Wallpaper kecil di kartu perangkat; hanya di HP ini.", true)
        Switch(store, P.SHOW_ID, "Tampilkan ID di kartu", "Sembunyikan bila sering merekam layar HP.", true)
        Choice(store, P.HISTORY_DAYS, "Hapus riwayat otomatis", "Sesi lebih lama dari ini dibuang saat aplikasi dibuka.", listOf("Tidak", "7 hr", "30 hr", "90 hr")) { store.pruneHistory() }
    }
    Spacer(Modifier.height(16.dp))
    XyCard {
        XyText("TAMPILAN & BERITA", Xy.label)
        Choice(store, P.TEXT_SIZE, "Ukuran teks", "Berlaku di seluruh aplikasi.", listOf("S", "M", "L"), 1, onTextSize)
        Choice(store, P.NEWS_HOURS, "Cek berita tiap", "Interval pemeriksaan artikel baru di latar.", listOf("3 jam", "6 jam", "12 jam"), 1) { NewsWatch.schedule(ctx) }
    }
}
