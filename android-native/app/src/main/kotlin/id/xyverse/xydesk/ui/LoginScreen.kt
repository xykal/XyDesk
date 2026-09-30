package id.xyverse.xydesk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.core.LocalLang
import id.xyverse.xydesk.core.Sfx
import id.xyverse.xydesk.core.tr
import id.xyverse.xydesk.net.Api
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyButton
import id.xyverse.xydesk.ui.kit.XyCard
import id.xyverse.xydesk.ui.kit.XyField
import id.xyverse.xydesk.ui.kit.XyNotice
import id.xyverse.xydesk.ui.kit.XyText
import id.xyverse.xydesk.ui.kit.SlideToGoogle
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onGoogle: suspend () -> String?, onOpenUrl: (String) -> Unit = {}, onLoggedIn: (jwt: String, email: String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var tone by remember { mutableStateOf(Xy.textMid) }
    var googleBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val lang = LocalLang.current
    fun t0(k: String) = k.tr(lang)
    val sfx = remember { Sfx(ctx) }
    DisposableEffect(Unit) { onDispose { sfx.release() } }

    fun run(block: suspend () -> Unit) {
        busy = true
        scope.launch {
            runCatching { block() }.onFailure { notice = it.message ?: "Gagal"; tone = Xy.danger }
            busy = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        LoginHero()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).safeDrawingPadding().imePadding().padding(Xy.pad)) {
        Spacer(Modifier.height(300.dp))
        XyText("XyDesk", Xy.display)
        XyText("Kendalikan PC dari mana saja — latensi rendah, jalur langsung.", Xy.caption)
        Spacer(Modifier.height(20.dp))
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
                    Api.requestOtp(email); sent = true; notice = t0("Kode dikirim ke") + " " + email; tone = Xy.success
                } else run {
                    val jwt = Api.verifyOtp(email, otp); onLoggedIn(jwt, email)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(Xy.line))
                XyText("atau", Xy.label, Modifier.padding(horizontal = 10.dp))
                Box(Modifier.weight(1f).height(1.dp).background(Xy.line))
            }
            Spacer(Modifier.height(14.dp))
            SlideToGoogle(loading = googleBusy, enabled = !busy) {
                sfx.confirm()
                googleBusy = true
                scope.launch {
                    runCatching {
                        val idToken = onGoogle() ?: throw IllegalStateException(t0("Login Google dibatalkan."))
                        val res = Api.googleLogin(idToken)
                        onLoggedIn(res.getString("token"), res.optJSONObject("user")?.optString("email").orEmpty())
                    }.onFailure { notice = it.message ?: "Gagal"; tone = Xy.danger }
                    googleBusy = false
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            XyText("Dengan masuk, kamu menyetujui:", Xy.caption)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LegalPill("Syarat & Ketentuan") { onOpenUrl("https://xydesk.my.id/legal#syarat") }
                LegalPill("Kebijakan Privasi") { onOpenUrl("https://xydesk.my.id/legal#privasi") }
            }
            Spacer(Modifier.height(20.dp))
            XyText("XyVerse Technology Global", Xy.label)
        }
        Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LegalPill(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(Xy.pill)).background(Xy.overlay).border(1.dp, Xy.line, RoundedCornerShape(Xy.pill))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    ) { XyText(text, Xy.caption.copy(color = Xy.accent)) }
}
