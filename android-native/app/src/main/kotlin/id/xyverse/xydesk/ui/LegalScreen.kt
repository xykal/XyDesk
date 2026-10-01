package id.xyverse.xydesk.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.ui.kit.Icon
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyIcon
import id.xyverse.xydesk.ui.kit.XyMarkdown
import id.xyverse.xydesk.ui.kit.XyText

/**
 * Dokumen legal dibaca dari aset `legal.md` (salinan `docs/LEGAL.md` saat build)
 * dan ditampilkan di dalam aplikasi, bukan di peramban.
 */
object Legal {
    const val TERMS = "syarat"
    const val PRIVACY = "privasi"
    var open by mutableStateOf<String?>(null)

    fun title(kind: String) = if (kind == TERMS) "Syarat & Ketentuan" else "Kebijakan Privasi"

    fun section(markdown: String, kind: String): String {
        val head = if (kind == TERMS) "## 3." else "## 2."
        val start = markdown.indexOf(head).takeIf { it >= 0 } ?: return markdown
        val body = markdown.substring(markdown.indexOf('\n', start) + 1)
        val end = body.indexOf("\n## ").takeIf { it >= 0 } ?: body.length
        return body.substring(0, end).lines().filterNot { it.startsWith("|---") || it.trim() == "---" }
            .joinToString("\n") { l -> if (l.startsWith("|")) "- " + l.trim('|').split("|").joinToString(" — ") { it.trim() } else l }
    }
}

@Composable
fun LegalOverlay() {
    val kind = Legal.open ?: return
    val ctx = LocalContext.current
    val text = remember(kind) {
        val md = runCatching { ctx.assets.open("legal.md").bufferedReader().readText() }.getOrDefault("Dokumen belum tersedia.")
        Legal.section(md, kind)
    }
    BackHandler { Legal.open = null }
    Column(Modifier.fillMaxSize().background(Xy.bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Xy.pad)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(Xy.overlay, CircleShape).clickable(remember { MutableInteractionSource() }, null) { Legal.open = null },
                contentAlignment = Alignment.Center,
            ) { XyIcon(Icon.CHEVRON, Modifier.rotate(180f), tint = Xy.textHi, size = 20.dp) }
            Spacer(Modifier.width(12.dp))
            XyText(Legal.title(kind), Xy.display)
        }
        XyText("DRAFT · XyVerse Technology Global", Xy.label)
        Spacer(Modifier.height(20.dp))
        XyMarkdown(text)
        Spacer(Modifier.height(48.dp))
    }
}
