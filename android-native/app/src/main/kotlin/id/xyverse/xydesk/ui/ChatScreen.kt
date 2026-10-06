package id.xyverse.xydesk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import id.xyverse.xyadapt.ChatRules
import id.xyverse.xydesk.net.ChatClient
import id.xyverse.xydesk.net.ChatLink
import id.xyverse.xydesk.net.ChatMessage
import id.xyverse.xydesk.ui.kit.Icon
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyEmpty
import id.xyverse.xydesk.ui.kit.XyText
import kotlinx.coroutines.delay
import java.util.TimeZone

/**
 * Chat global: satu ruang untuk semua pengguna XyDesk yang sudah masuk.
 *
 * Batas panjang, rem laju, pengelompokan gelembung, jam, dan penerjemahan
 * pesan kesalahan diambil dari `ChatRules` di modul xyadapt — modul JVM yang
 * punya tes — supaya tampilan tidak menyimpan angka batasnya sendiri dan
 * tidak pernah berbeda dari server.
 */
@Composable
fun ChatScreen(jwt: String) {
    val client = remember(jwt) { ChatClient(jwt) }
    DisposableEffect(client) {
        client.start()
        onDispose { client.stop() }
    }

    val messages by client.messages.collectAsState()
    val link by client.link.collectAsState()
    val online by client.online.collectAsState()
    val me by client.me.collectAsState()
    val notice by client.notice.collectAsState()

    // Sambung ulang dengan jeda menanjak selama layar masih terbuka.
    LaunchedEffect(link) {
        if (link == ChatLink.OFFLINE) {
            delay(client.retryDelayMs())
            client.reopen()
        }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2600)
            client.clearNotice()
        }
    }

    var draft by remember { mutableStateOf("") }
    var stamps by remember { mutableStateOf(listOf<Long>()) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    val offsetMs = remember { TimeZone.getDefault().rawOffset }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        ChatHeader(link, online)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(Xy.pad), contentAlignment = Alignment.Center) {
                    XyEmpty(
                        Icon.NEWS,
                        if (link == ChatLink.ONLINE) "Belum ada obrolan" else "Menyambung ke ruang",
                        if (link == ChatLink.ONLINE) {
                            "Ruang ini terbuka untuk semua pengguna XyDesk. Sapa duluan — 50 pesan terakhir tersimpan untuk siapa pun yang baru masuk."
                        } else {
                            "Sebentar, ruangnya sedang dibuka."
                        },
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Xy.pad, end = Xy.pad, top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(messages, key = { _, m -> m.id }) { index, m ->
                        val previous = messages.getOrNull(index - 1)
                        val head = ChatRules.startsGroup(previous?.from, previous?.at ?: 0L, m.from, m.at)
                        Bubble(m, head, me, offsetMs)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = notice != null,
            enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { it / 2 },
            exit = fadeOut(tween(160)),
        ) {
            XyText(
                notice.orEmpty(),
                Xy.caption.copy(color = Xy.danger, textAlign = TextAlign.Center),
                Modifier.fillMaxWidth().padding(horizontal = Xy.pad, vertical = 4.dp),
            )
        }

        ChatInput(
            draft = draft,
            onDraft = { draft = it },
            connected = link == ChatLink.ONLINE,
            stamps = stamps,
        ) {
            if (client.send(draft)) {
                val now = System.currentTimeMillis()
                stamps = ChatRules.pruneStamps(stamps, now) + now
                draft = ""
            }
        }
        // Ruang untuk bar bawah yang mengambang.
        Spacer(Modifier.height(84.dp))
    }
}

@Composable
private fun ChatHeader(link: ChatLink, online: Int) {
    val dot = when (link) {
        ChatLink.ONLINE -> Xy.success
        ChatLink.CONNECTING -> Xy.warning
        ChatLink.OFFLINE -> Xy.danger
    }
    val pulse by animateFloatAsState(if (link == ChatLink.ONLINE) 1f else 0.45f, tween(500), label = "pulse")
    Column(Modifier.padding(start = Xy.pad, end = Xy.pad, top = 12.dp, bottom = 8.dp)) {
        XyText("Obrolan", Xy.display)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).alpha(pulse).background(dot, CircleShape))
            Spacer(Modifier.width(7.dp))
            XyText(
                when (link) {
                    ChatLink.ONLINE -> if (online > 0) "$online orang di ruang ini" else "Tersambung"
                    ChatLink.CONNECTING -> "Menyambung…"
                    ChatLink.OFFLINE -> "Terputus — mencoba lagi"
                },
                Xy.caption,
            )
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage, head: Boolean, me: String, offsetMs: Int) {
    val mine = m.mine || (me.isNotEmpty() && m.from == me)
    Row(
        Modifier.fillMaxWidth().padding(top = if (head) 10.dp else 2.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        // Kolom avatar selalu memesan tempat, juga untuk pesan lanjutan,
        // supaya gelembung satu orang tetap lurus dan tidak bergeser.
        if (!mine) {
            Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
                if (head) Avatar(m.from, m.hue, m.vip)
            }
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (head && !mine) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 3.dp)) {
                    XyText(m.from, Xy.label.copy(color = Xy.textMid, fontWeight = FontWeight.SemiBold), maxLines = 1)
                    if (m.vip) {
                        Spacer(Modifier.width(5.dp))
                        VipBadge()
                    }
                    Spacer(Modifier.width(6.dp))
                    XyText(ChatRules.clock(m.at, offsetMs), Xy.label, maxLines = 1)
                }
            }
            Box(
                Modifier
                    .widthIn(max = 268.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = if (mine || head) Xy.radiusM else 6.dp,
                            topEnd = if (mine && !head) 6.dp else Xy.radiusM,
                            bottomStart = Xy.radiusM,
                            bottomEnd = Xy.radiusM,
                        ),
                    )
                    .background(if (mine) Xy.accent else Xy.overlay)
                    .padding(horizontal = 13.dp, vertical = 9.dp),
            ) {
                XyText(m.text, Xy.body, color = if (mine) Color.White else Xy.textHi)
            }
            if (head && mine) {
                XyText(
                    ChatRules.clock(m.at, offsetMs),
                    Xy.label,
                    Modifier.padding(top = 2.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Avatar bulat berisi inisial. Anggota VIP mendapat cincin emas berlapis —
 * satu lingkaran gelap tipis di luar supaya cincinnya tetap terbaca di atas
 * latar putih maupun warna avatar yang terang.
 */
@Composable
private fun Avatar(name: String, hue: Int, vip: Boolean) {
    val size = if (vip) 30.dp else 28.dp
    Box(contentAlignment = Alignment.Center) {
        if (vip) {
            Box(
                Modifier
                    .size(size)
                    .background(Brush.linearGradient(listOf(kVipLight, kVipDeep)), CircleShape)
                    .border(0.8.dp, kVipDeep.copy(alpha = 0.55f), CircleShape),
            )
        }
        Box(
            Modifier
                .size(if (vip) size - 5.dp else size)
                .background(hueColor(hue), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            XyText(ChatRules.initial(name), Xy.label, color = Color.White, maxLines = 1)
        }
    }
}

/** Lencana kecil di sebelah nama. Sengaja teks, bukan gambar: ikut skala font. */
@Composable
private fun VipBadge() {
    Box(
        Modifier
            .clip(RoundedCornerShape(Xy.pill))
            .background(Brush.linearGradient(listOf(kVipLight, kVipDeep)))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        XyText("VIP", Xy.label.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp), color = Color.White, maxLines = 1)
    }
}

@Composable
private fun ChatInput(
    draft: String,
    onDraft: (String) -> Unit,
    connected: Boolean,
    stamps: List<Long>,
    onSend: () -> Unit,
) {
    val canSend = ChatRules.canSend(draft, stamps, System.currentTimeMillis(), connected)
    Column(Modifier.padding(horizontal = Xy.pad)) {
        if (ChatRules.showCounter(draft)) {
            XyText(
                "${ChatRules.remaining(draft)} karakter lagi",
                Xy.label,
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                color = if (ChatRules.remaining(draft) <= 0) Xy.danger else Xy.textLow,
                maxLines = 1,
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            val shape = RoundedCornerShape(Xy.radiusL)
            Box(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(Xy.input)
                    .border(1.5.dp, Xy.line, shape)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (draft.isEmpty()) {
                    XyText(if (connected) "Tulis pesan" else "Menyambung…", Xy.body, color = Xy.textLow, maxLines = 1)
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { onDraft(it.take(ChatRules.MAX_TEXT)) },
                    textStyle = Xy.body,
                    maxLines = 4,
                    cursorBrush = SolidColor(Xy.lavender),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            SendButton(canSend, onSend)
        }
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val fade by animateFloatAsState(if (enabled) 1f else 0.4f, tween(160), label = "send")
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(48.dp)
            .alpha(fade)
            .clip(CircleShape)
            .background(Xy.accent)
            .clickable(source, null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        XyText("→", Xy.title, color = Color.White, maxLines = 1)
    }
}

// Emas VIP. Dua titik gradasi supaya cincinnya tidak terlihat datar.
private val kVipLight = Color(0xFFF7C948)
private val kVipDeep = Color(0xFFB07400)

/** Warna avatar dari hue yang dikirim server; saturasi dan terang dikunci. */
private fun hueColor(hue: Int): Color {
    val h = (((hue % 360) + 360) % 360) / 60f
    val c = 0.42f
    val x = c * (1f - kotlin.math.abs(h % 2f - 1f))
    val rgb = when (h.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = 0.34f
    return Color(rgb.first + m, rgb.second + m, rgb.third + m)
}
