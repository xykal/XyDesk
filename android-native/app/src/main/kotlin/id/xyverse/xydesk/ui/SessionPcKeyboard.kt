package id.xyverse.xydesk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyText

private enum class KbLayer { ABC, NUM, FN, PAD, FULL }

/** Keyboard PC di HUD (bukan IME HP): huruf, angka, F1–F12, numpad — sama konsep dengan web. */
@Composable
fun SessionPcKeyboard(onKey: (vk: Int) -> Unit, onIme: () -> Unit, onClose: () -> Unit) {
    var layer by remember { mutableStateOf(KbLayer.ABC) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Color(0xF2FFFFFF)).border(1.dp, Xy.line, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(KbLayer.ABC to "ABC", KbLayer.NUM to "123", KbLayer.FN to "F1–F12", KbLayer.PAD to "Numpad", KbLayer.FULL to "Penuh").forEach { (l, t) ->
                Chip(t, layer == l) { layer = l }
            }
            Box(Modifier.weight(1f))
            Chip("IME HP") { onIme() }
            Chip("Tutup") { onClose() }
        }
        when (layer) {
            KbLayer.ABC -> Abc(onKey)
            KbLayer.NUM -> Num(onKey)
            KbLayer.FN -> Fn(onKey)
            KbLayer.PAD -> Pad(onKey)
            KbLayer.FULL -> Full(onKey)
        }
    }
}

@Composable
private fun Abc(onKey: (Int) -> Unit) {
    KeyRow("QWERTYUIOP".map { it.toString() to it.code }, onKey)
    KeyRow(listOf("Caps" to 0x14) + "ASDFGHJKL".map { it.toString() to it.code }, onKey)
    KeyRow(listOf("Shift" to 0xA0) + "ZXCVBNM".map { it.toString() to it.code } + listOf("⌫" to 0x08), onKey)
    KeyRow(listOf("Ctrl" to 0xA2, "Win" to 0x5B, "Alt" to 0xA4, "Spasi" to 0x20, "Enter" to 0x0D), onKey)
}

@Composable
private fun Num(onKey: (Int) -> Unit) {
    KeyRow((1..9).map { "$it" to (0x30 + it) } + listOf("0" to 0x30), onKey)
    KeyRow(listOf("-" to 0xBD, "=" to 0xBB, "[" to 0xDB, "]" to 0xDD, ";" to 0xBA, "'" to 0xDE, "," to 0xBC, "." to 0xBE, "/" to 0xBF, "⌫" to 0x08), onKey)
    KeyRow(listOf("Esc" to 0x1B, "Tab" to 0x09, "Shift" to 0xA0, "←" to 0x25, "↑" to 0x26, "↓" to 0x28, "→" to 0x27), onKey)
}

@Composable
private fun Fn(onKey: (Int) -> Unit) {
    KeyRow((1..12).map { "F$it" to (0x6F + it) }, onKey)
}

@Composable
private fun Pad(onKey: (Int) -> Unit) {
    KeyRow(listOf("NumLk" to 0x90, "/" to 0x6F, "*" to 0x6A, "-" to 0x6D), onKey)
    KeyRow(listOf("7" to 0x67, "8" to 0x68, "9" to 0x69, "+" to 0x6B), onKey)
    KeyRow(listOf("4" to 0x64, "5" to 0x65, "6" to 0x66, "Enter" to 0x0D), onKey)
    KeyRow(listOf("1" to 0x61, "2" to 0x62, "3" to 0x63, "." to 0x6E), onKey)
    KeyRow(listOf("0" to 0x60), onKey)
}

@Composable
private fun Full(onKey: (Int) -> Unit) {
    KeyRow(listOf("Esc" to 0x1B) + (1..12).map { "F$it" to (0x6F + it) } + listOf("⌫" to 0x08), onKey)
    Num(onKey)
    Abc(onKey)
}

@Composable
private fun KeyRow(keys: List<Pair<String, Int>>, onKey: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.forEach { (label, vk) ->
            val wide = label in setOf("Spasi", "Enter", "Shift", "Caps", "0")
            Box(
                Modifier.weight(if (wide) 1.6f else 1f).height(42.dp).clip(RoundedCornerShape(10.dp))
                    .background(Xy.overlay).clickable(remember { MutableInteractionSource() }, null) { onKey(vk) },
                contentAlignment = Alignment.Center,
            ) { XyText(label, Xy.label.copy(fontSize = 11.sp, color = Xy.textHi)) }
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.height(36.dp).widthIn(min = 48.dp).clip(RoundedCornerShape(Xy.pill))
            .background(if (on) Xy.accent else Xy.overlay)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { XyText(text, Xy.label.copy(fontSize = 10.sp, color = if (on) Color.White else Xy.textHi)) }
}
