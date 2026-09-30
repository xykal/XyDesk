package id.xyverse.xydesk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.net.Api
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyButton
import id.xyverse.xydesk.ui.kit.XyCard
import id.xyverse.xydesk.ui.kit.XyField
import id.xyverse.xydesk.ui.kit.XyNotice
import id.xyverse.xydesk.ui.kit.XyText
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onGoogle: suspend () -> String?, onLoggedIn: (jwt: String, email: String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var tone by remember { mutableStateOf(Xy.textMid) }
    val scope = rememberCoroutineScope()

    fun run(block: suspend () -> Unit) {
        busy = true
        scope.launch {
            runCatching { block() }.onFailure { notice = it.message ?: "Gagal"; tone = Xy.danger }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(Xy.pad),
        verticalArrangement = Arrangement.Center,
    ) {
        XyText("XyDesk", Xy.display)
        XyText("Kendalikan PC dari mana saja — latensi rendah, jalur langsung.", Xy.caption)
        Spacer(Modifier.height(28.dp))
        XyCard {
            XyField(email, { email = it.trim() }, "Email", keyboard = KeyboardType.Email, hint = "nama@domain.com")
            if (sent) {
                Spacer(Modifier.height(Xy.gap))
                XyField(otp, { otp = it.filter(Char::isDigit).take(6) }, "Kode OTP", keyboard = KeyboardType.Number, mono = true, hint = "••••••")
            }
            Spacer(Modifier.height(Xy.gap))
            XyNotice(notice, tone)
            Spacer(Modifier.height(Xy.gap))
            XyButton(if (sent) "Masuk" else "Kirim kode", enabled = !busy && email.contains('@')) {
                if (!sent) run {
                    Api.requestOtp(email); sent = true; notice = "Kode dikirim ke $email"; tone = Xy.success
                } else run {
                    val jwt = Api.verifyOtp(email, otp); onLoggedIn(jwt, email)
                }
            }
            Spacer(Modifier.height(10.dp))
            XyButton("Lanjut dengan Google", ghost = true, enabled = !busy) {
                run {
                    val idToken = onGoogle() ?: return@run
                    val res = Api.googleLogin(idToken)
                    onLoggedIn(res.getString("token"), res.optJSONObject("user")?.optString("email").orEmpty())
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        XyText("XyVerse Technology Global", Xy.label)
    }
}
