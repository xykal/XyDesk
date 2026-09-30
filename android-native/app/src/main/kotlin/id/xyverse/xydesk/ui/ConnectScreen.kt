package id.xyverse.xydesk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.R
import id.xyverse.xydesk.core.SessionRecord
import id.xyverse.xydesk.ui.kit.Icon
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyEmpty
import id.xyverse.xydesk.ui.kit.XyButton
import id.xyverse.xydesk.ui.kit.XyCard
import id.xyverse.xydesk.ui.kit.XyField
import id.xyverse.xydesk.ui.kit.XyNotice
import id.xyverse.xydesk.ui.kit.XyText
import id.xyverse.xydesk.ui.kit.t

@Composable
fun ConnectScreen(
    email: String,
    initialHost: String,
    devices: List<SessionRecord>,
    history: List<SessionRecord> = emptyList(),
    onConnect: (host: String, pin: String) -> Unit,
) {
    var host by remember { mutableStateOf(initialHost) }
    var pin by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    val digits = host.filter(Char::isDigit)

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(Xy.pad),
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.height(12.dp))
        XyText("Sambungkan", Xy.display)
        XyText(email, Xy.caption)
        Spacer(Modifier.height(24.dp))
        XyCard {
            XyField(digits.chunked(3).joinToString(" "), { host = it }, "ID perangkat", keyboard = KeyboardType.Number, mono = true, hint = "000 000 000")
            Spacer(Modifier.height(Xy.gap))
            XyField(pin, { pin = it.take(32) }, "Password host", keyboard = KeyboardType.Password, hint = t("sesuai di aplikasi host"), transformation = PasswordVisualTransformation())
            Spacer(Modifier.height(Xy.gap))
            XyNotice(notice, Xy.warning)
            Spacer(Modifier.height(Xy.gap))
            XyButton("Hubungkan", enabled = digits.length >= 6 && pin.isNotEmpty()) {
                when {
                    digits.length < 6 -> notice = "ID perangkat belum lengkap."
                    pin.isEmpty() -> notice = "Password host wajib diisi."
                    else -> onConnect(digits, pin)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        UsageStrip(history)
        if (history.isNotEmpty()) Spacer(Modifier.height(24.dp))
        if (devices.isEmpty()) XyEmpty(Icon.PLUG, "Host pertama kamu", "Pasang XyDesk Host di PC, salin ID 9 digit dan password host, lalu masukkan di atas.", image = R.drawable.empty_devices)
        else DevicesSection(devices.take(3), compact = true) { host = it }
        Spacer(Modifier.height(96.dp))
    }
}
