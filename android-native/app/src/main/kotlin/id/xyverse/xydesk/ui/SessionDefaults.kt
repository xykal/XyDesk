package id.xyverse.xydesk.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.core.Store
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyCard
import id.xyverse.xydesk.ui.kit.XyText
import id.xyverse.xydesk.ui.kit.XyToggle

/** Nilai awal sesi; tiap perubahan langsung tersimpan dan dipakai sesi berikutnya. */
@Composable
fun SessionDefaults(store: Store) {
    var quality by remember { mutableIntStateOf(store.quality) }
    var fps by remember { mutableIntStateOf(store.targetFps) }
    var speed by remember { mutableFloatStateOf(store.trackpadSpeed) }
    var natural by remember { mutableStateOf(store.naturalScroll) }
    var direct by remember { mutableStateOf(store.directTouch) }
    var stats by remember { mutableStateOf(store.showStats) }
    var retry by remember { mutableStateOf(store.autoReconnect) }
    var awake by remember { mutableStateOf(store.keepAwake) }
    XyCard {
        XyText("VIDEO", Xy.label)
        Spacer(Modifier.height(8.dp))
        Line("Kualitas") {
            Segmented(listOf("Auto" to 0, "Sedang" to 1, "Tinggi" to 2, "Ultra" to 3), quality) { quality = it; store.quality = it }
        }
        Line("FPS") { Segmented(listOf("30" to 30, "60" to 60), fps) { fps = it; store.targetFps = it } }
        Box(Modifier.padding(horizontal = 6.dp)) {
            XyToggle("Statistik di pojok", "fps, ms, jalur, jaringan tanpa latar.", stats) { stats = it; store.showStats = it }
        }
        XyText("Auto menaikkan/menurunkan resolusi dan bitrate mengikuti jaringan (libxyadapt).", Xy.caption, Modifier.padding(horizontal = 6.dp))
    }
    Spacer(Modifier.height(16.dp))
    XyCard {
        XyText("KURSOR", Xy.label)
        Spacer(Modifier.height(8.dp))
        Line("Mode awal") {
            Segmented(listOf("Trackpad" to false, "Sentuh" to true), direct) { direct = it; store.directTouch = it }
        }
        Line("Kecepatan %.1f".format(speed)) {
            Segmented(listOf("−" to -1, "+" to 1), 0) {
                speed = (speed + it * 0.2f).coerceIn(0.6f, 3f); store.trackpadSpeed = speed
            }
        }
        Box(Modifier.padding(horizontal = 6.dp)) {
            XyToggle("Scroll alami", "Dua jari ke bawah = halaman ikut ke bawah.", natural) { natural = it; store.naturalScroll = it }
        }
    }
    Spacer(Modifier.height(16.dp))
    XyCard {
        XyText("KONEKSI", Xy.label)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.padding(horizontal = 6.dp)) {
            XyToggle("Sambung ulang otomatis", "Coba lagi sampai 3 kali bila sesi putus mendadak.", retry) { retry = it; store.autoReconnect = it }
        }
        Box(Modifier.padding(horizontal = 6.dp)) {
            XyToggle("Layar tetap menyala", "HP tidak mengunci selama sesi berjalan.", awake) { awake = it; store.keepAwake = it }
        }
    }
    Spacer(Modifier.height(16.dp))
    GestureGuide()
}

/** Contekan gestur trackpad supaya fitur sesi mudah ditemukan. */
@Composable
fun GestureGuide() {
    val rows = listOf(
        "Ketuk 1 jari" to "Klik kiri", "Tahan 1 jari" to "Klik kanan", "Ketuk 2x lalu geser" to "Seret / blok teks",
        "Geser 2 jari" to "Scroll", "Cubit 2 jari" to "Zoom (Ctrl + roda)", "Geser 3 jari kiri/kanan" to "Alt+Tab",
        "Geser 3 jari ke atas" to "Win+Tab (semua jendela)", "Tombol kanan" to "Keyboard, tombol cepat, kualitas, putus",
    )
    XyCard {
        XyText("GESTUR", Xy.label)
        Spacer(Modifier.height(8.dp))
        rows.forEach { (g, a) ->
            Row(Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                XyText(g, Xy.body, Modifier.weight(1f))
                XyText(a, Xy.caption.copy(color = Xy.textMid))
            }
        }
    }
}

@Composable
private fun Line(label: String, control: @Composable () -> Unit) {
    Row(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        XyText(label, Xy.body, Modifier.weight(1f))
        control()
    }
}
