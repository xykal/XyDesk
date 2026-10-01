package id.xyverse.xydesk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import id.xyverse.xydesk.core.NewsPost
import id.xyverse.xydesk.core.RemoteImage
import id.xyverse.xydesk.ui.kit.Icon
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyIcon
import id.xyverse.xydesk.ui.kit.XyNotice
import id.xyverse.xydesk.ui.kit.XyText
import id.xyverse.xydesk.ui.kit.t

/** Tab Berita: daftar artikel dari news.xydesk.my.id, kategori sama dengan web. */
@Composable
fun NewsScreen(posts: List<NewsPost>?, offline: Boolean, seenSlug: String, onOpen: (NewsPost) -> Unit) {
    var cat by remember { mutableStateOf("semua") }
    if (posts == null) XyNotice("Memuat berita terbaru…", Xy.textLow)
    else if (offline) XyNotice("Offline: menampilkan berita yang terakhir tersimpan.", Xy.warning)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("semua", "rilis", "teknik", "umum").forEach { Chip(it.replaceFirstChar(Char::uppercase), it == cat) { cat = it } }
    }
    Spacer(Modifier.height(14.dp))
    val shown = (posts ?: emptyList()).filter { cat == "semua" || it.category == cat }
    if (posts != null && shown.isEmpty()) XyText("Belum ada berita di kategori ini.", Xy.caption)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        shown.forEachIndexed { i, p -> NewsCard(p, featured = i == 0 && cat == "semua", fresh = i == 0 && p.slug != seenSlug) { onOpen(p) } }
    }
}

@Composable
private fun NewsCard(p: NewsPost, featured: Boolean, fresh: Boolean, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(Xy.radiusL)
    if (featured) Column(Modifier.fillMaxWidth().clip(shape).background(Xy.overlay).clickable(remember { MutableInteractionSource() }, null, onClick = onOpen)) {
        if (p.cover.isNotBlank()) RemoteImage(p.cover, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Column(Modifier.padding(14.dp)) {
            Meta(p, fresh)
            Spacer(Modifier.height(8.dp))
            XyText(p.title, Xy.title)
            Spacer(Modifier.height(4.dp))
            XyText(p.excerpt, Xy.caption.copy(color = Xy.textMid), maxLines = 3)
        }
    } else Row(
        Modifier.fillMaxWidth().clip(shape).background(Xy.overlay).clickable(remember { MutableInteractionSource() }, null, onClick = onOpen).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Meta(p, fresh)
            Spacer(Modifier.height(6.dp))
            XyText(p.title, Xy.body.copy(color = Xy.textHi), maxLines = 2)
            Spacer(Modifier.height(2.dp))
            XyText(p.excerpt, Xy.caption, maxLines = 2)
        }
        if (p.cover.isNotBlank()) {
            Spacer(Modifier.width(12.dp))
            RemoteImage(p.cover, Modifier.width(96.dp).aspectRatio(1f).clip(RoundedCornerShape(Xy.radiusM)))
        }
    }
}

@Composable
private fun Meta(p: NewsPost, fresh: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(p.category.replaceFirstChar(Char::uppercase), true, small = true) {}
        XyText(shortDate(p.createdAt), Xy.caption)
        Spacer(Modifier.weight(1f))
        Count(Icon.STAR, p.likeCount); Count(Icon.NEWS, p.commentCount)
        if (fresh) Box(Modifier.clip(RoundedCornerShape(Xy.pill)).background(Xy.danger).padding(horizontal = 8.dp, vertical = 2.dp)) {
            XyText(t("BARU"), Xy.label.copy(color = Color.White))
        }
    }
}

@Composable
private fun Count(icon: Icon, n: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        XyIcon(icon, tint = Xy.textLow, size = 13.dp); Spacer(Modifier.width(3.dp)); XyText("$n", Xy.label.copy(color = Xy.textLow))
    }
}

@Composable
fun Chip(text: String, active: Boolean, small: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(Xy.pill)).background(if (active) Xy.accent.copy(alpha = 0.12f) else Xy.overlay)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = if (small) 9.dp else 14.dp, vertical = if (small) 3.dp else 8.dp),
    ) { XyText(text, Xy.label.copy(color = if (active) Xy.accent else Xy.textMid)) }
}

/** Pita kecil di beranda saat ada berita yang belum dibaca. */
@Composable
fun NewsBanner(title: String, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Xy.pill)).background(Xy.accent.copy(alpha = 0.1f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onOpen).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XyIcon(Icon.NEWS, tint = Xy.accent, size = 18.dp)
        Spacer(Modifier.width(10.dp))
        XyText(t("Berita baru") + ": " + title, Xy.caption.copy(color = Xy.accentDeep), Modifier.weight(1f), maxLines = 1)
        XyIcon(Icon.CHEVRON, tint = Xy.accent, size = 16.dp)
    }
}

internal fun shortDate(iso: String): String = iso.take(10).split("-").let { if (it.size == 3) "${it[2]}/${it[1]}/${it[0]}" else iso }
