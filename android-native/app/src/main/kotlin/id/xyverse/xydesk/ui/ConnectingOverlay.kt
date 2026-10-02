package id.xyverse.xydesk.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.core.LocalLang
import id.xyverse.xydesk.core.tr
import id.xyverse.xydesk.rtc.Phase
import id.xyverse.xydesk.ui.kit.MorphLoader
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyButton
import id.xyverse.xydesk.ui.kit.XyError
import id.xyverse.xydesk.ui.kit.XyText

/** Keadaan layar sesi sebelum video tampil: memuat (morph HP → PC → Server) atau gagal. */
data class ConnectState(val phase: Phase, val message: String?, val hostId: String, val attempt: Int = 0, val reconnecting: Boolean = false)

private val failed = setOf(Phase.REJECTED, Phase.PEER_OFFLINE, Phase.BUSY, Phase.ERROR, Phase.ENDED)

@Composable
fun ConnectingOverlay(state: ConnectState, onRetry: () -> Unit, onBack: () -> Unit) {
    val lang = LocalLang.current
    val isError = state.phase in failed && !state.reconnecting
    Box(Modifier.fillMaxSize().background(Xy.bg).safeDrawingPadding(), contentAlignment = Alignment.Center) {
        AnimatedContent(isError, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) }, label = "conn") { err ->
            if (err) {
                Box(Modifier.padding(Xy.pad).widthIn(max = 420.dp)) {
                    XyError(
                        title = errorTitle(state.phase).tr(lang),
                        reason = (state.message?.takeIf { it.isNotBlank() } ?: errorReason(state.phase)).tr(lang),
                        retry = if (state.phase == Phase.REJECTED) null else "Coba lagi".tr(lang),
                        onRetry = onRetry,
                        back = "Kembali".tr(lang),
                        onBack = onBack,
                    )
                }
            } else {
                Column(Modifier.padding(Xy.pad), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    MorphLoader(size = 132.dp)
                    Spacer(Modifier.height(28.dp))
                    XyText(if (state.reconnecting) "Menyambung ulang…".tr(lang) else "Menyambung…".tr(lang), Xy.title.copy(textAlign = TextAlign.Center))
                    if (state.attempt > 0) XyText("Percobaan".tr(lang) + " ${state.attempt}/3", Xy.caption.copy(textAlign = TextAlign.Center))
                    Spacer(Modifier.height(4.dp))
                    XyText("PC " + state.hostId.chunked(3).joinToString(" "), Xy.caption.copy(textAlign = TextAlign.Center))
                    Spacer(Modifier.height(36.dp))
                    XyButton("Batal".tr(lang), Modifier.width(180.dp), ghost = true, onClick = onBack)
                }
            }
        }
    }
}

private fun phaseTitle(p: Phase) = when (p) {
    Phase.PAIRING -> "Menghubungi host…"
    Phase.NEGOTIATING -> "Pairing diterima, menyiapkan video…"
    else -> "Menyiapkan sesi…"
}

private fun errorTitle(p: Phase) = when (p) {
    Phase.REJECTED -> "Password host salah"
    Phase.PEER_OFFLINE -> "Host tidak online"
    Phase.BUSY -> "Host sedang dipakai"
    Phase.ENDED -> "Sesi berakhir"
    else -> "Gagal menyambung"
}

private fun errorReason(p: Phase) = when (p) {
    Phase.REJECTED -> "Cek password di aplikasi host, lalu coba lagi."
    Phase.PEER_OFFLINE -> "Pastikan XyDesk Host berjalan dan PC terhubung internet."
    Phase.BUSY -> "Ada perangkat lain yang sedang tersambung ke host ini."
    Phase.ENDED -> "Host menutup sesi atau koneksi terputus."
    else -> "Periksa jaringan dan coba lagi."
}
