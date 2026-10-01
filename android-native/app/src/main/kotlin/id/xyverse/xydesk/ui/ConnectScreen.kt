package id.xyverse.xydesk.ui

import id.xyverse.xydesk.ui.kit.XyIcon

import androidx.compose.ui.draw.clip

import androidx.compose.ui.Alignment

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.compose.foundation.horizontalScroll

import androidx.compose.foundation.clickable

import androidx.compose.foundation.background

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
    onQuick: (host: String) -> Unit = {},
    newsTitle: String? = null,
    onNews: () -> Unit = {},
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
        if (newsTitle != null) { NewsBanner(newsTitle, onNews); Spacer(Modifier.height(16.dp)) }
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
        if (devices.isEmpty()) XyEmpty(Icon.PLUG, "Host pertama kamu", "Pasang XyDesk Host di PC, salin ID 9 digit dan password host, lalu masukkan di atas.", image = R.drawable.empty_devices)
        else QuickConnect(devices.take(4), onQuick)
        Spacer(Modifier.height(96.dp))
    }
}

/** Pil host terakhir/favorit: satu ketuk langsung menyambung (password tersimpan) atau minta password. */
@Composable
private fun QuickConnect(devices: List<SessionRecord>, onQuick: (String) -> Unit) {
    val meta = LocalHostMeta.current
    XyText("SAMBUNG CEPAT", Xy.label)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        devices.forEach { d ->
            Row(
                Modifier.clip(RoundedCornerShape(Xy.pill)).background(Xy.overlay)
                    .clickable(remember { MutableInteractionSource() }, null) { onQuick(d.host) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                XyIcon(if (meta.favorite(d.host)) Icon.STAR else Icon.MONITOR, tint = Xy.accent, size = 16.dp)
                Spacer(Modifier.width(8.dp))
                XyText(hostTitle(d), Xy.label.copy(color = Xy.textHi), maxLines = 1)
            }
        }
    }
}
