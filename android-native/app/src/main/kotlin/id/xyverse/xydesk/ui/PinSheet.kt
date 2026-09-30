package id.xyverse.xydesk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyButton
import id.xyverse.xydesk.ui.kit.XyField
import id.xyverse.xydesk.ui.kit.XyText
import id.xyverse.xydesk.ui.kit.XyToggle
import id.xyverse.xydesk.ui.kit.t

/** Lembar bawah: minta password host saat menyambung dari riwayat, opsi ingat per host. */
@Composable
fun PinSheet(host: String?, remembered: Boolean, onDismiss: () -> Unit, onConnect: (pin: String, remember: Boolean) -> Unit) {
    AnimatedVisibility(host != null, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss))
    }
    AnimatedVisibility(host != null, enter = slideInVertically { it }, exit = slideOutVertically { it }, modifier = Modifier.fillMaxSize()) {
        var pin by remember { mutableStateOf("") }
        var keep by remember { mutableStateOf(remembered) }
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = Xy.radiusL, topEnd = Xy.radiusL)).background(Xy.bg)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .safeDrawingPadding().padding(Xy.pad),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).height(4.dp).fillMaxWidth(0.12f).clip(RoundedCornerShape(Xy.pill)).background(Xy.line))
                Spacer(Modifier.height(16.dp))
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
    }
}
