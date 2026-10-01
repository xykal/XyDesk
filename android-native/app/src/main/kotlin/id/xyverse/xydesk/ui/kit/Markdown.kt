package id.xyverse.xydesk.ui.kit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.core.RemoteImage

/** Perender markdown ringan untuk isi berita: judul, paragraf, tebal, miring, daftar, gambar. */
@Composable
fun XyMarkdown(text: String) {
    val blocks = text.replace("\r", "").split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
    Column {
        blocks.forEach { b ->
            val img = Regex("^!\\[[^\\]]*]\\((\\S+)\\)$").find(b)
            when {
                img != null -> RemoteImage(img.groupValues[1], Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(Xy.radiusM)))
                b.startsWith("#") -> XyText(b.trimStart('#').trim(), Xy.title)
                b.lines().all { it.trimStart().startsWith("- ") || it.trimStart().startsWith("* ") } -> b.lines().forEach { l ->
                    Row { XyText("•", Xy.body, Modifier.width(18.dp)); BasicText(inline(l.trimStart().drop(2)), style = Xy.body) }
                }
                else -> BasicText(inline(b.replace("\n", " ")), style = Xy.body)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun inline(s: String): AnnotatedString = buildAnnotatedString {
    val re = Regex("\\*\\*(.+?)\\*\\*|\\*(.+?)\\*|`(.+?)`|\\[([^\\]]+)]\\([^)]+\\)")
    var i = 0
    re.findAll(s).forEach { m ->
        append(s.substring(i, m.range.first))
        when {
            m.groupValues[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(m.groupValues[1]) }
            m.groupValues[2].isNotEmpty() -> withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { append(m.groupValues[2]) }
            m.groupValues[3].isNotEmpty() -> withStyle(SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)) { append(m.groupValues[3]) }
            else -> withStyle(SpanStyle(color = Xy.accent)) { append(m.groupValues[4]) }
        }
        i = m.range.last + 1
    }
    append(s.substring(i))
}
