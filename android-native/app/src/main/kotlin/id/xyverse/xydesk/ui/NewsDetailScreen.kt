package id.xyverse.xydesk.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.core.News
import id.xyverse.xydesk.core.NewsComment
import id.xyverse.xydesk.core.NewsDetail
import id.xyverse.xydesk.core.NewsPost
import id.xyverse.xydesk.core.RemoteImage
import id.xyverse.xydesk.ui.kit.Icon
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyButton
import id.xyverse.xydesk.ui.kit.XyField
import id.xyverse.xydesk.ui.kit.XyIcon
import id.xyverse.xydesk.ui.kit.XyMarkdown
import id.xyverse.xydesk.ui.kit.XyNotice
import id.xyverse.xydesk.ui.kit.XyText
import id.xyverse.xydesk.ui.kit.t
import kotlinx.coroutines.launch

/** Artikel penuh seperti di web: sampul, isi markdown, suka, komentar + balasan, bagikan. */
@Composable
fun NewsDetailScreen(post: NewsPost, fp: String, onShare: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    var detail by remember(post.slug) { mutableStateOf<NewsDetail?>(null) }
    var error by remember { mutableStateOf("") }
    var liked by remember { mutableStateOf(false) }
    var likes by remember { mutableStateOf(post.likeCount) }
    var comments by remember { mutableStateOf<List<NewsComment>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<NewsComment?>(null) }
    var sending by remember { mutableStateOf(false) }
    LaunchedEffect(post.slug) {
        runCatching { News.detail(post.slug, fp) }
            .onSuccess { detail = it; liked = it.liked; likes = it.post.likeCount; comments = it.comments }
            .onFailure { error = it.message ?: "Gagal memuat" }
    }
    val shown = detail?.post ?: post
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Round(Icon.CHEVRON, rotate = 180f, onClick = onBack)
            Spacer(Modifier.weight(1f))
            Round(Icon.LINK) { onShare(News.shareUrl(post.slug)) }
        }
        if (shown.cover.isNotBlank()) RemoteImage(shown.cover, Modifier.padding(horizontal = Xy.pad).fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(Xy.radiusL)))
        Column(Modifier.padding(Xy.pad)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(shown.category.replaceFirstChar(Char::uppercase), true, small = true) {}
                XyText(shown.author.ifBlank { "XyDesk" } + " · " + shortDate(shown.createdAt), Xy.caption)
            }
            Spacer(Modifier.height(10.dp))
            XyText(shown.title, Xy.display)
            Spacer(Modifier.height(16.dp))
            if (error.isNotEmpty()) XyNotice(error, Xy.danger)
            XyMarkdown(shown.content)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(if (liked) "Disukai · $likes" else "Suka · $likes", liked) {
                    scope.launch { runCatching { News.like(post.slug, fp) }.onSuccess { (l, n) -> liked = l; likes = n } }
                }
                Pill("Bagikan", false) { onShare(News.shareUrl(post.slug)) }
            }
            Spacer(Modifier.height(28.dp))
            XyText("KOMENTAR · ${comments.size}", Xy.label)
            Spacer(Modifier.height(10.dp))
            val roots = comments.filter { it.parentId == null }
            if (detail != null && roots.isEmpty()) XyText("Belum ada komentar. Jadilah yang pertama.", Xy.caption)
            roots.forEach { c ->
                CommentRow(c) { replyTo = c }
                comments.filter { it.parentId == c.id }.forEach { r -> Box(Modifier.padding(start = 36.dp)) { CommentRow(r) { replyTo = c } } }
            }
            Spacer(Modifier.height(16.dp))
            replyTo?.let { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    XyText(t("Membalas") + " ${r.author}", Xy.caption.copy(color = Xy.accent), Modifier.weight(1f))
                    Box(Modifier.clickable(remember { MutableInteractionSource() }, null) { replyTo = null }) { XyIcon(Icon.CLOSE, tint = Xy.textMid, size = 16.dp) }
                }
                Spacer(Modifier.height(6.dp))
            }
            XyField(draft, { draft = it.take(1000) }, "Tulis sebagai ${News.displayName(fp)}", hint = t("Komentar minimal 2 huruf"))
            Spacer(Modifier.height(10.dp))
            XyButton(if (sending) "Mengirim…" else "Kirim komentar", enabled = draft.trim().length >= 2 && !sending) {
                sending = true
                scope.launch {
                    runCatching { News.comment(post.slug, fp, News.displayName(fp), draft.trim(), replyTo?.id) }
                        .onSuccess { comments = comments + it; draft = ""; replyTo = null }
                        .onFailure { error = it.message ?: "Gagal mengirim" }
                    sending = false
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun CommentRow(c: NewsComment, onReply: () -> Unit) {
    Row(Modifier.padding(vertical = 8.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(if (c.official) Xy.accent else Xy.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            XyText(c.author.take(1).uppercase(), Xy.label.copy(color = if (c.official) Color.White else Xy.accent))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                XyText(c.author, Xy.body.copy(color = Xy.textHi))
                if (c.official) Chip("XyVerse", true, small = true) {}
                XyText(shortDate(c.createdAt), Xy.caption)
            }
            XyText(c.content, Xy.caption.copy(color = Xy.textMid))
            Box(Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onReply).padding(top = 4.dp)) { XyText("Balas", Xy.label.copy(color = Xy.accent)) }
        }
    }
}

@Composable
private fun Round(icon: Icon, rotate: Float = 0f, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(Xy.overlay).clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { XyIcon(icon, Modifier.rotate(rotate), tint = Xy.textHi, size = 18.dp) }
}

@Composable
private fun Pill(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(Xy.pill)).background(if (active) Xy.accent else Xy.overlay)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    ) { XyText(text, Xy.label.copy(color = if (active) Color.White else Xy.textHi)) }
}
