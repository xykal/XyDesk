package id.xyverse.xydesk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyText

/** Satu tombol dok. `vk` untuk tombol tunggal, `chord` untuk kombinasi, `mouse`/`scroll` untuk aksi tetikus, `sticky` untuk modifier yang ditahan. */
data class DockKey(
    val id: String,
    val label: String,
    val group: String,
    val vk: Int = 0,
    val chord: IntArray = intArrayOf(),
    val mouse: Int = -1,
    val scroll: Int = 0,
    val sticky: Boolean = false,
)

/** Katalog kontrol yang bisa dipasang ke dok; urutan = urutan di editor. */
object DockCatalog {
    val all: List<DockKey> = buildList {
        add(DockKey("lmb", "Klik kiri", "Tetikus", mouse = 0)); add(DockKey("rmb", "Klik kanan", "Tetikus", mouse = 1)); add(DockKey("mmb", "Klik tengah", "Tetikus", mouse = 2))
        add(DockKey("drag", "Seret", "Tetikus", sticky = true, mouse = 0))
        add(DockKey("sup", "Scroll ↑", "Tetikus", scroll = 1)); add(DockKey("sdn", "Scroll ↓", "Tetikus", scroll = -1))
        add(DockKey("esc", "Esc", "Tombol", vk = 0x1B)); add(DockKey("tab", "Tab", "Tombol", vk = 0x09)); add(DockKey("enter", "Enter", "Tombol", vk = 0x0D))
        add(DockKey("space", "Spasi", "Tombol", vk = 0x20)); add(DockKey("bksp", "Backspace", "Tombol", vk = 0x08)); add(DockKey("del", "Delete", "Tombol", vk = 0x2E))
        add(DockKey("win", "Win", "Tombol", vk = 0x5B)); add(DockKey("prtsc", "PrtSc", "Tombol", vk = 0x2C))
        add(DockKey("ctrl", "Ctrl", "Modifier", vk = 0xA2, sticky = true)); add(DockKey("shift", "Shift", "Modifier", vk = 0xA0, sticky = true)); add(DockKey("alt", "Alt", "Modifier", vk = 0xA4, sticky = true))
        add(DockKey("up", "↑", "Arah", vk = 0x26)); add(DockKey("down", "↓", "Arah", vk = 0x28)); add(DockKey("left", "←", "Arah", vk = 0x25)); add(DockKey("right", "→", "Arah", vk = 0x27))
        add(DockKey("home", "Home", "Arah", vk = 0x24)); add(DockKey("end", "End", "Arah", vk = 0x23)); add(DockKey("pgup", "PgUp", "Arah", vk = 0x21)); add(DockKey("pgdn", "PgDn", "Arah", vk = 0x22))
        for (i in 1..12) add(DockKey("f$i", "F$i", "Fungsi", vk = 0x6F + i))
        add(DockKey("nmlk", "NumLk", "Numpad", vk = 0x90))
        add(DockKey("ndiv", "Num /", "Numpad", vk = 0x6F)); add(DockKey("nmul", "Num *", "Numpad", vk = 0x6A))
        add(DockKey("nsub", "Num −", "Numpad", vk = 0x6D)); add(DockKey("nadd", "Num +", "Numpad", vk = 0x6B))
        add(DockKey("ndec", "Num .", "Numpad", vk = 0x6E))
        for (i in 0..9) add(DockKey("n$i", "Num $i", "Numpad", vk = 0x60 + i))
        add(DockKey("copy", "Ctrl+C", "Kombinasi", chord = intArrayOf(0xA2, 0x43))); add(DockKey("paste", "Ctrl+V", "Kombinasi", chord = intArrayOf(0xA2, 0x56)))
        add(DockKey("cut", "Ctrl+X", "Kombinasi", chord = intArrayOf(0xA2, 0x58))); add(DockKey("undo", "Ctrl+Z", "Kombinasi", chord = intArrayOf(0xA2, 0x5A)))
        add(DockKey("all", "Ctrl+A", "Kombinasi", chord = intArrayOf(0xA2, 0x41))); add(DockKey("save", "Ctrl+S", "Kombinasi", chord = intArrayOf(0xA2, 0x53)))
        add(DockKey("alttab", "Alt+Tab", "Kombinasi", chord = intArrayOf(0xA4, 0x09))); add(DockKey("altf4", "Alt+F4", "Kombinasi", chord = intArrayOf(0xA4, 0x73)))
        add(DockKey("wind", "Win+D", "Kombinasi", chord = intArrayOf(0x5B, 0x44))); add(DockKey("wine", "Win+E", "Kombinasi", chord = intArrayOf(0x5B, 0x45)))
        add(DockKey("winl", "Win+L", "Kombinasi", chord = intArrayOf(0x5B, 0x4C))); add(DockKey("taskmgr", "Task Manager", "Kombinasi", chord = intArrayOf(0xA2, 0xA0, 0x1B)))
        add(DockKey("cad", "Ctrl+Alt+Del", "Kombinasi", chord = intArrayOf(0xA2, 0xA4, 0x2E)))
        add(DockKey("volup", "Vol +", "Media", vk = 0xAF)); add(DockKey("voldn", "Vol −", "Media", vk = 0xAE)); add(DockKey("mute", "Bisu", "Media", vk = 0xAD))
        add(DockKey("play", "Putar/Jeda", "Media", vk = 0xB3)); add(DockKey("next", "Lagu ›", "Media", vk = 0xB0)); add(DockKey("prev", "‹ Lagu", "Media", vk = 0xB1))
    }
    val byId = all.associateBy { it.id }
    val default = listOf("lmb", "rmb", "sup", "sdn", "esc", "win", "alttab", "enter")
    val groups = all.map { it.group }.distinct()
}

/** Dok tombol di bawah layar: geser mendatar bila penuh; modifier/seret menyala saat ditahan. */
@Composable
fun ControlDock(ids: List<String>, held: Set<String>, size: Int, onTap: (DockKey) -> Unit) {
    val keys = ids.mapNotNull { DockCatalog.byId[it] }
    if (keys.isEmpty()) return
    val h = when (size) { 0 -> 34.dp; 2 -> 50.dp; else -> 42.dp }
    val fs = when (size) { 0 -> 11.sp; 2 -> 15.sp; else -> 13.sp }
    Row(
        Modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(Xy.pill)).background(Color(0xE6FFFFFF)).border(1.dp, Xy.line, RoundedCornerShape(Xy.pill))
            .padding(5.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        keys.forEach { k ->
            val on = k.id in held
            Box(
                Modifier.height(h).clip(RoundedCornerShape(Xy.pill)).background(if (on) Xy.accent else Xy.overlay)
                    .clickable(remember { MutableInteractionSource() }, null) { onTap(k) }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { XyText(k.label, Xy.label.copy(fontSize = fs, color = if (on) Color.White else Xy.textHi)) }
        }
    }
}
