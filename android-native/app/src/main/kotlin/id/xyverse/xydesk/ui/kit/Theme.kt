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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Token desain XyDesk — cermin `lib/core/tokens.dart`. Tidak memakai Material:
 * semua komponen digambar sendiri di atas Compose foundation.
 */
object Xy {
    val bg = Color(0xFF131315)
    val raised = Color(0xFF1B1B1E)
    val overlay = Color(0xFF232326)
    val input = Color(0xFF202023)
    val line = Color(0x14FFFFFF)
    val accent = Color(0xFF7C3AED)
    val accentDeep = Color(0xFF5B21B6)
    val lavender = Color(0xFFA78BFA)
    val textHi = Color(0xFFEDEDEF)
    val textMid = Color(0xFFA0A0A8)
    val textLow = Color(0xFF6B6B73)
    val success = Color(0xFF4FA97A)
    val warning = Color(0xFFC9963F)
    val danger = Color(0xFFD9646E)

    val accentBrush = Brush.linearGradient(listOf(accent, accentDeep))
    val glowBrush = Brush.radialGradient(listOf(Color(0x337C3AED), Color.Transparent))

    val radiusS = 10.dp
    val radiusM = 16.dp
    val radiusL = 24.dp
    val gap = 12.dp
    val pad = 20.dp

    val display = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-0.5).sp, color = textHi)
    val title = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = textHi)
    val body = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, color = textHi, lineHeight = 22.sp)
    val caption = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.5.sp, color = textMid, lineHeight = 18.sp)
    val label = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.6.sp, color = textLow)
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp, letterSpacing = 2.sp, color = textHi)
}

@Composable
fun XyTheme(content: @Composable () -> Unit) {
    val selection = TextSelectionColors(handleColor = Xy.accent, backgroundColor = Xy.accent.copy(alpha = 0.35f))
    CompositionLocalProvider(LocalTextSelectionColors provides selection) {
        Box(Modifier.fillMaxSize().background(Xy.bg)) {
            Box(Modifier.fillMaxSize().background(Xy.glowBrush))
            content()
        }
    }
}
