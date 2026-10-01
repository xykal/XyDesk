package id.xyverse.xydesk.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import id.xyverse.xydesk.core.Store
import id.xyverse.xydesk.ui.kit.Icon
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyRow
import id.xyverse.xydesk.ui.kit.t
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Baris "Ekspor riwayat": simpan JSON lewat pemilih berkas sistem, tanpa izin penyimpanan. */
@Composable
fun ExportHistoryRow(store: Store) {
    val ctx = LocalContext.current
    val saved = t("Riwayat tersimpan.")
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val body = JSONObject().put("app", "XyDesk").put("exportedAt", System.currentTimeMillis())
                .put("sessions", JSONArray(store.history.map { it.json() })).toString(2)
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) }
            Toast.makeText(ctx, saved, Toast.LENGTH_SHORT).show()
        }
    }
    XyRow(Icon.CLOCK, "Ekspor riwayat", "Simpan riwayat sesi sebagai berkas JSON.", tint = Xy.textMid) {
        launcher.launch("xydesk-riwayat-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".json")
    }
}
