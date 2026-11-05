package id.xyverse.xydesk.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Pindai QR koneksi dari layar host. Panel host Windows menampilkan QR berisi
 * tautan https://remote.xydesk.my.id/connect?device=XXXXXXXXX — hasil pindai
 * cukup mengisi kolom ID perangkat, tanpa mengetik sama sekali.
 */

/** Ambil ID perangkat 9 digit dari tautan QR (atau dari teks yang hanya berisi 9 digit). */
fun parseDeviceLink(raw: String): String? {
    val s = raw.trim()
    Regex("""[?&]device=(\d{9})""").find(s)?.let { return it.groupValues[1] }
    val direct = s.filter { it.isDigit() }
    return if (direct.length == 9) direct else null
}

/** Peluncur pemindai QR; memanggil [onScanned] dengan ID 9 digit bila hasil valid. */
@Composable
fun rememberQrScanner(onScanned: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        parseDeviceLink(text)?.let(onScanned)
    }
    return {
        launcher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Arahkan kamera ke QR di layar host")
                .setBeepEnabled(false)
                .setOrientationLocked(true),
        )
    }
}
