package id.xyverse.xydesk.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.core.Store
import id.xyverse.xydesk.ui.kit.Icon
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyButton
import id.xyverse.xydesk.ui.kit.XyField
import id.xyverse.xydesk.ui.kit.XyRow
import id.xyverse.xydesk.ui.kit.XySheet
import id.xyverse.xydesk.ui.kit.XyText
import id.xyverse.xydesk.ui.kit.XyToggle

/** Tahan kartu host: beri nama panggilan, tandai favorit, atau lupakan host. */
@Composable
fun HostSheet(host: String?, store: Store, onDismiss: () -> Unit) {
    XySheet(host != null, onDismiss) {
        val h = host ?: ""
        var alias by remember(h) { mutableStateOf(store.alias(h)) }
        var fav by remember(h) { mutableStateOf(store.favorite(h)) }
        val hasPin = remember(h) { store.hostPin(h) != null }
        XyText("Atur host", Xy.title)
        XyText(h.chunked(3).joinToString(" "), Xy.caption)
        Spacer(Modifier.height(16.dp))
        XyField(alias, { alias = it.take(24) }, "Nama panggilan", hint = "PC Kamar, Kantor, …")
        Spacer(Modifier.height(8.dp))
        XyToggle("Favorit", "Selalu tampil paling atas di Perangkat dan Beranda.", fav) { fav = it }
        Spacer(Modifier.height(4.dp))
        XyRow(Icon.KEY, "Password tersimpan", if (hasPin) "Ada; dipakai otomatis saat ketuk sambung." else "Belum; akan ditanya saat menyambung.", chevron = false)
        Spacer(Modifier.height(12.dp))
        XyButton("Simpan") {
            store.setAlias(h, alias); store.setFavorite(h, fav); onDismiss()
        }
        Spacer(Modifier.height(8.dp))
        XyButton("Lupakan host ini", ghost = true) { store.forgetHost(h); onDismiss() }
    }
}
