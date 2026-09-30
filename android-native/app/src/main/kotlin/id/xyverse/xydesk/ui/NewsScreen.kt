package id.xyverse.xydesk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.core.News
import id.xyverse.xydesk.core.NewsFeed
import id.xyverse.xydesk.core.NewsItem
import id.xyverse.xydesk.ui.kit.Icon
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyButton
import id.xyverse.xydesk.ui.kit.XyCard
import id.xyverse.xydesk.ui.kit.XyIcon
import id.xyverse.xydesk.ui.kit.XyNotice
import id.xyverse.xydesk.ui.kit.XyText

/** Tab Berita: catatan rilis dan info produk dari repo, tetap terbaca saat offline. */
@Composable
fun NewsScreen(feed: NewsFeed?, appVersion: String, seenId: String, onOpenUrl: (String) -> Unit, onSeen: (String) -> Unit) {
    val ctx = LocalContext.current
    val shown = feed ?: remember { News.bundled(ctx) }
    LaunchedEffect(shown.items.firstOrNull()?.id) { shown.items.firstOrNull()?.let { onSeen(it.id) } }
    if (shown.latest.isNotEmpty() && News.newer(shown.latest, appVersion)) {
        UpdateBanner(shown.latest, appVersion) { onOpenUrl("https://github.com/xykal/XyDesk/releases") }
        Spacer(Modifier.height(16.dp))
    }
    if (feed == null) XyNotice("Memuat berita terbaru…", Xy.textLow)
    else if (!feed.fromNetwork) XyNotice("Offline: menampilkan berita bawaan aplikasi.", Xy.warning)
    Spacer(Modifier.height(8.dp))
    var tag by remember { mutableStateOf("Semua") }
    val tags = listOf("Semua") + shown.items.map { it.tag }.distinct()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { tags.forEach { Chip(it, it == tag) { tag = it } } }
    Spacer(Modifier.height(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        shown.items.filter { tag == "Semua" || it.tag == tag }.forEachIndexed { i, n ->
            NewsCard(n, fresh = i == 0 && n.id != seenId, onOpen = { if (n.url.isNotEmpty()) onOpenUrl(n.url) })
        }
    }
}

@Composable
private fun UpdateBanner(latest: String, installed: String, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Xy.radiusL)).background(Xy.accent).padding(18.dp)) {
        XyText("Versi $latest tersedia", Xy.title.copy(color = Color.White))
        XyText("Kamu memakai $installed. Unduh versi terbaru untuk fitur dan perbaikan terkini.", Xy.caption.copy(color = Color.White.copy(alpha = 0.85f)))
        Spacer(Modifier.height(12.dp))
        XyButton("Lihat unduhan", ghost = true, onClick = onOpen)
    }
}

@Composable
private fun NewsCard(n: NewsItem, fresh: Boolean, onOpen: () -> Unit) {
    XyCard(Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(n.tag, true, small = true) {}
            Spacer(Modifier.width(8.dp))
            XyText(n.date, Xy.caption, Modifier.weight(1f))
            if (fresh) Box(Modifier.clip(RoundedCornerShape(Xy.pill)).background(Xy.danger).padding(horizontal = 8.dp, vertical = 2.dp)) {
                XyText("BARU", Xy.label.copy(color = Color.White))
            }
        }
        Spacer(Modifier.height(10.dp))
        XyText(n.title, Xy.title)
        Spacer(Modifier.height(4.dp))
        XyText(n.body, Xy.caption.copy(color = Xy.textMid))
        if (n.url.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                XyIcon(Icon.LINK, tint = Xy.accent, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                XyText("Selengkapnya", Xy.label.copy(color = Xy.accent))
            }
        }
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
