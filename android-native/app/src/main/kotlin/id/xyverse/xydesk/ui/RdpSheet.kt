package id.xyverse.xydesk.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyButton
import id.xyverse.xydesk.ui.kit.XyField
import id.xyverse.xydesk.ui.kit.XySheet
import id.xyverse.xydesk.ui.kit.XyText
import id.xyverse.xydesk.ui.kit.t

/**
 * Koneksi RDP dari HP — PC yang sedang dipasangkan membuka `mstsc` ke target
 * dari dalam jaringannya (kanal data `rdp`, end-to-end). IP target tidak pernah
 * terekspos; kata sandi hanya hidup selama permintaan berjalan.
 */
@Composable
fun RdpSheet(
    visible: Boolean,
    busy: Boolean,
    status: Pair<Boolean, String>?,
    onDismiss: () -> Unit,
    onOpen: (host: String, port: Int, user: String, pass: String) -> Unit,
) {
    XySheet(visible, onDismiss) {
        var host by remember { mutableStateOf("") }
        var port by remember { mutableStateOf("3389") }
        var user by remember { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }
        XyText(t("Koneksi RDP"), Xy.title)
        XyText(t("Dibuka dari PC yang tersambung — IP target tidak terekspos."), Xy.caption)
        Spacer(Modifier.height(16.dp))
        XyField(host, { host = it }, t("Alamat host RDP"), hint = t("nama PC atau IP"))
        XyField(port, { port = it.filter(Char::isDigit).take(5) }, t("Port"), keyboard = KeyboardType.Number)
        XyField(user, { user = it }, t("Username"), hint = t("bisa DOMAIN\\username"))
        XyField(
            pass,
            { pass = it },
            t("Kata sandi"),
            keyboard = KeyboardType.Password,
            transformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(6.dp))
        when {
            busy -> XyText(t("Mengirim permintaan…"), Xy.caption)
            status != null && status.first -> XyText(t("Perintah diterima. Jendela RDP muncul di layar PC."), Xy.caption)
            status != null -> XyText(t("Gagal") + ": " + status.second.ifBlank { t("tidak diketahui") }, Xy.caption)
        }
        Spacer(Modifier.height(10.dp))
        XyButton(t("Buka RDP dari PC"), enabled = !busy && host.isNotBlank() && user.isNotBlank()) {
            onOpen(host, port.toIntOrNull() ?: 3389, user, pass)
        }
        Spacer(Modifier.height(8.dp))
        XyButton(t("Batal"), ghost = true, onClick = onDismiss)
    }
}
