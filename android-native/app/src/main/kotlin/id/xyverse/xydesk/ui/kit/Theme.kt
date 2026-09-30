package id.xyverse.xydesk.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import id.xyverse.xydesk.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Token desain XyDesk — cermin `lib/core/tokens.dart`. Tidak memakai Material:
 * semua komponen digambar sendiri di atas Compose foundation.
 */
private fun manrope(w: FontWeight) = Font(R.font.manrope, w, variationSettings = FontVariation.Settings(w, androidx.compose.ui.text.font.FontStyle.Normal))

/** Manrope (OFL) dibundel di res/font; satu berkas variabel untuk semua bobot. */
val XyFont = FontFamily(manrope(FontWeight.Normal), manrope(FontWeight.Medium), manrope(FontWeight.SemiBold), manrope(FontWeight.Bold))

object Xy {
    val bg = Color(0xFFFFFFFF)
    val raised = Color(0xFFFFFFFF)
    val overlay = Color(0xFFF4F4F7)
    val input = Color(0xFFF6F6F9)
    val line = Color(0xFFE8E8ED)
    val accent = Color(0xFF7C3AED)
    val accentDeep = Color(0xFF6D28D9)
    val lavender = Color(0xFF7C3AED)
    val textHi = Color(0xFF18181B)
    val textMid = Color(0xFF6B6B76)
    val textLow = Color(0xFF9A9AA2)
    val success = Color(0xFF167347)
    val warning = Color(0xFF855400)
    val danger = Color(0xFFB42332)
    val shadow = Color(0x1A26125C)

    val accentBrush = Brush.linearGradient(listOf(accent, accentDeep))

    val radiusS = 10.dp
    val radiusM = 16.dp
    val radiusL = 24.dp
    val pill = 999.dp
    val gap = 12.dp
    val pad = 20.dp

    val display = TextStyle(fontFamily = XyFont, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, letterSpacing = (-0.8).sp, color = textHi)
    val title = TextStyle(fontFamily = XyFont, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = textHi)
    val body = TextStyle(fontFamily = XyFont, fontSize = 15.sp, color = textHi, lineHeight = 22.sp)
    val caption = TextStyle(fontFamily = XyFont, fontSize = 12.5.sp, color = textMid, lineHeight = 18.sp)
    val label = TextStyle(fontFamily = XyFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.6.sp, color = textLow)
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp, letterSpacing = 2.sp, color = textHi)
}

@Composable
fun XyTheme(content: @Composable () -> Unit) {
    val selection = TextSelectionColors(handleColor = Xy.accent, backgroundColor = Xy.accent.copy(alpha = 0.35f))
    CompositionLocalProvider(LocalTextSelectionColors provides selection) {
        Box(Modifier.fillMaxSize().background(Xy.bg)) { content() }
    }
}
