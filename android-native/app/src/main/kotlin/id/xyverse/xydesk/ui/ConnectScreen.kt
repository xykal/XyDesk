package id.xyverse.xydesk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
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
import id.xyverse.xydesk.core.StreamXy
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyButton
import id.xyverse.xydesk.ui.kit.XyCard
import id.xyverse.xydesk.ui.kit.XyField
import id.xyverse.xydesk.ui.kit.XyNotice
import id.xyverse.xydesk.ui.kit.XyText
import id.xyverse.xydesk.ui.kit.XyToggle

@Composable
fun ConnectScreen(
    email: String,
    initialHost: String,
    initialLowLatency: Boolean,
    onConnect: (host: String, pin: String, lowLatency: Boolean) -> Unit,
    onLogout: () -> Unit,
) {
    var host by remember { mutableStateOf(initialHost) }
    var pin by remember { mutableStateOf("") }
    var lowLatency by remember { mutableStateOf(initialLowLatency) }
    var notice by remember { mutableStateOf("") }
    val digits = host.filter(Char::isDigit)

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(Xy.pad),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                XyText("Sambungkan", Xy.display)
                XyText(email, Xy.caption)
            }
            XyText("keluar", Xy.label, Modifier.padding(bottom = 6.dp).clickableText(onLogout), color = Xy.lavender)
        }
        Spacer(Modifier.height(24.dp))
        XyCard {
            XyField(digits.chunked(3).joinToString(" "), { host = it }, "ID perangkat", keyboard = KeyboardType.Number, mono = true, hint = "000 000 000")
            Spacer(Modifier.height(Xy.gap))
            XyField(pin, { pin = it.take(12) }, "PIN (opsional)", keyboard = KeyboardType.NumberPassword, transformation = PasswordVisualTransformation())
            Spacer(Modifier.height(Xy.gap))
            XyToggle("Decoder low-latency", "H.264 langsung ke MediaCodec, tanpa antrian render.", lowLatency) { lowLatency = it }
            Spacer(Modifier.height(Xy.gap))
            XyNotice(notice, Xy.warning)
            Spacer(Modifier.height(Xy.gap))
            XyButton("Hubungkan", enabled = digits.length >= 6) {
                if (digits.length < 6) notice = "ID perangkat belum lengkap." else onConnect(digits, pin, lowLatency)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            XyText("native", Xy.label)
            Spacer(Modifier.width(2.dp))
            XyText(StreamXy.version(), Xy.label, color = Xy.textLow)
        }
    }
}

private fun Modifier.clickableText(onClick: () -> Unit): Modifier =
    androidx.compose.foundation.clickable(this, onClick = onClick)
