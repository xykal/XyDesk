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
import id.xyverse.xydesk.ui.kit.XyToggle
import id.xyverse.xydesk.ui.kit.t

/** Minta password host saat menyambung dari riwayat, dengan opsi ingat per host. */
@Composable
fun PinSheet(host: String?, remembered: Boolean, onDismiss: () -> Unit, onConnect: (pin: String, remember: Boolean) -> Unit) {
    XySheet(host != null, onDismiss) {
        var pin by remember { mutableStateOf("") }
        var keep by remember { mutableStateOf(remembered) }
        XyText("Sambung ke host", Xy.title)
        XyText((host ?: "").chunked(3).joinToString(" "), Xy.caption)
        Spacer(Modifier.height(16.dp))
        XyField(pin, { pin = it.take(32) }, "Password host", keyboard = KeyboardType.Password, hint = t("sesuai di aplikasi host"), transformation = PasswordVisualTransformation())
        Spacer(Modifier.height(8.dp))
        XyToggle("Ingat password host ini", "Disimpan terenkripsi di HP ini; sekali ketuk untuk sambung berikutnya.", keep) { keep = it }
        Spacer(Modifier.height(12.dp))
        XyButton("Hubungkan", enabled = pin.isNotEmpty()) { onConnect(pin, keep) }
        Spacer(Modifier.height(8.dp))
        XyButton("Batal", ghost = true, onClick = onDismiss)
    }
}
