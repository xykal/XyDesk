package id.xyverse.xydesk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xyverse.xydesk.core.LocalLang
import id.xyverse.xydesk.core.Sfx
import id.xyverse.xydesk.core.tr
import id.xyverse.xydesk.net.Api
import id.xyverse.xydesk.ui.kit.SlideToGoogle
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyButton
import id.xyverse.xydesk.ui.kit.XyCard
import id.xyverse.xydesk.ui.kit.XyDeskMark
import id.xyverse.xydesk.ui.kit.XyField
import id.xyverse.xydesk.ui.kit.XyNotice
import id.xyverse.xydesk.ui.kit.XyText
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoginScreen(
    onGoogle: suspend () -> String?,
    onOpenUrl: (String) -> Unit = {},
    onLoggedIn: (jwt: String, email: String) -> Unit,
) {
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

    fun runAction(block: suspend () -> Unit) {
        busy = true
        notice = ""
        scope.launch {
            runCatching { block() }.onFailure {
                notice = it.message ?: "Gagal memproses permintaan."
                tone = Xy.danger
            }
            busy = false
        }
    }

    Box(Modifier.fillMaxSize().background(Xy.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))

            // Logo resmi XyDesk di posisi paling atas
            XyDeskMark(size = 72.dp)
            Spacer(Modifier.height(18.dp))

            XyText("XyDesk", Xy.display.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(4.dp))
            XyText(
                "Remote play & kendali PC latensi ultra-rendah.",
                Xy.caption.copy(color = Xy.textMid, textAlign = TextAlign.Center),
            )

            Spacer(Modifier.height(28.dp))

            XyCard {
                XyField(
                    value = email,
                    onValueChange = { email = it.trim(); if (notice.isNotBlank()) notice = "" },
                    label = "Alamat Email",
                    keyboard = KeyboardType.Email,
                    hint = "nama@domain.com",
                    enabled = !busy && !sent,
                )

                AnimatedVisibility(
                    visible = sent,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    Column {
                        Spacer(Modifier.height(Xy.gap))
                        XyField(
                            value = otp,
                            onValueChange = { otp = it.filter(Char::isDigit).take(6); if (notice.isNotBlank()) notice = "" },
                            label = "Kode OTP (6 Digit)",
                            keyboard = KeyboardType.Number,
                            mono = true,
                            hint = "••••••",
                            enabled = !busy,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            XyText(
                                "Ganti email",
                                Xy.label.copy(color = Xy.accent, fontWeight = FontWeight.SemiBold),
                                Modifier.clickable(remember { MutableInteractionSource() }, null) {
                                    sent = false
                                    otp = ""
                                    notice = ""
                                },
                            )
                            XyText(
                                "Kirim ulang kode",
                                Xy.label.copy(color = Xy.accent, fontWeight = FontWeight.SemiBold),
                                Modifier.clickable(remember { MutableInteractionSource() }, null) {
                                    runAction {
                                        Api.requestOtp(email)
                                        notice = "Kode baru telah dikirim ke $email"
                                        tone = Xy.success
                                    }
                                },
                            )
                        }
                    }
                }

                if (notice.isNotBlank()) {
                    Spacer(Modifier.height(Xy.gap))
                    XyNotice(notice, tone)
                }

                Spacer(Modifier.height(16.dp))

                val canSubmit = if (!sent) {
                    !busy && email.contains('@') && email.length >= 5
                } else {
                    !busy && otp.length == 6
                }

                XyButton(
                    label = if (sent) "Masuk" else "Kirim Kode Masuk",
                    enabled = canSubmit,
                ) {
                    if (!sent) {
                        runAction {
                            Api.requestOtp(email)
                            sent = true
                            notice = "${t0("Kode dikirim ke")} $email"
                            tone = Xy.success
                        }
                    } else {
                        runAction {
                            val res = Api.verifyOtp(email, otp)
                            val token = res.getString("token")
                            sfx.confirm()
                            onLoggedIn(token, email)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(1.dp).background(Xy.line))
                    XyText("atau", Xy.label.copy(color = Xy.textLow), Modifier.padding(horizontal = 12.dp))
                    Box(Modifier.weight(1f).height(1.dp).background(Xy.line))
                }
                Spacer(Modifier.height(16.dp))

                SlideToGoogle(loading = googleBusy, enabled = !busy) {
                    sfx.confirm()
                    googleBusy = true
                    notice = ""
                    scope.launch {
                        runCatching {
                            val idToken = onGoogle() ?: throw IllegalStateException(t0("Login Google dibatalkan."))
                            val res = Api.googleLogin(idToken)
                            val token = res.getString("token")
                            val userEmail = res.optJSONObject("user")?.optString("email").orEmpty().ifEmpty { email }
                            onLoggedIn(token, userEmail)
                        }.onFailure {
                            notice = it.message ?: "Login Google belum berhasil."
                            tone = Xy.danger
                        }
                        googleBusy = false
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                XyText("Dengan masuk, kamu menyetujui", Xy.caption.copy(color = Xy.textLow))
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LegalLink("Syarat & Ketentuan") { Legal.open = Legal.TERMS }
                    LegalLink("Privasi") { Legal.open = Legal.PRIVACY }
                    LegalLink("Cookie") { Legal.open = Legal.COOKIE }
                    LegalLink("Lisensi") { Legal.open = Legal.LICENSES }
                }
                Spacer(Modifier.height(20.dp))
                XyText("XyVerse Technology Global", Xy.label.copy(color = Xy.textLow))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LegalLink(text: String, onClick: () -> Unit) {
    XyText(
        text,
        Xy.caption.copy(color = Xy.accent, fontWeight = FontWeight.Medium),
        Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
    )
}
