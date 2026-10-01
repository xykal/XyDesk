package id.xyverse.xydesk.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import id.xyverse.xydesk.core.StreamXy
import id.xyverse.xydesk.core.Store
import id.xyverse.xydesk.ui.kit.Icon
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyCard
import id.xyverse.xydesk.ui.kit.XyRow
import id.xyverse.xydesk.ui.kit.XyText
import id.xyverse.xydesk.ui.kit.XyToggle

/** Preferensi umum: tab awal dan notifikasi berita. Pengaturan sesi ada di halaman Sesi. */
@Composable
fun PreferencesSection(store: Store) {
    val ctx = LocalContext.current
    var start by remember { mutableIntStateOf(store.startTab) }
    var notify by remember { mutableStateOf(store.newsNotify) }
    Row(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        XyText("Tab awal", Xy.body, Modifier.weight(1f))
        Segmented(listOf("Koneksi" to 0, "Perangkat" to 1, "Berita" to 2), start) { start = it; store.startTab = it }
    }
    Box(Modifier.padding(horizontal = 6.dp)) {
        XyToggle("Notifikasi berita", "Cek artikel baru di latar tiap beberapa jam.", notify) {
            notify = it; store.newsNotify = it
            if (it) NewsWatch.schedule(ctx) else NewsWatch.cancel(ctx)
        }
    }
}

/** Konfirmasi putus sesi; dipasang di halaman Sesi. */
@Composable
fun DisconnectPref(store: Store) {
    var confirm by remember { mutableStateOf(store.confirmDisconnect) }
    Box(Modifier.padding(horizontal = 6.dp)) {
        XyToggle("Tanya sebelum putus", "Tombol Kembali perlu ditekan dua kali untuk mengakhiri sesi.", confirm) { confirm = it; store.confirmDisconnect = it }
    }
}

/** Halaman Tentang: versi, catatan rilis, legal, lisensi; semuanya dibaca di dalam aplikasi. */
@Composable
fun AboutBody(appVersion: String, onOpenUrl: (String) -> Unit) {
    XyCard {
        XyText("APLIKASI", Xy.label)
        Spacer(Modifier.height(8.dp))
        XyRow(Icon.MONITOR, "XyDesk $appVersion", "libstreamxy ${StreamXy.version()} · XyVerse Technology Global", chevron = false)
        XyRow(Icon.NEWS, "Catatan rilis", "Apa yang berubah di tiap versi.", onClick = { Legal.open = Legal.CHANGELOG })
        XyRow(Icon.LINK, "Situs web", "xydesk.my.id", onClick = { onOpenUrl("https://xydesk.my.id") })
    }
    Spacer(Modifier.height(16.dp))
    XyCard {
        XyText("LEGAL", Xy.label)
        Spacer(Modifier.height(8.dp))
        XyRow(Icon.SHIELD, "Syarat & Ketentuan", "Dibaca di dalam aplikasi.", onClick = { Legal.open = Legal.TERMS })
        XyRow(Icon.KEY, "Kebijakan Privasi", "Dibaca di dalam aplikasi.", onClick = { Legal.open = Legal.PRIVACY })
        XyRow(Icon.LIST, "Lisensi pihak ketiga", "Pustaka sumber terbuka yang dipakai.", onClick = { Legal.open = Legal.LICENSES })
    }
}
