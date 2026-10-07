package id.xyverse.xydesk.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xyverse.xyadapt.ChatRules
import id.xyverse.xydesk.core.RemoteImage
import id.xyverse.xydesk.net.ChatClient
import id.xyverse.xydesk.net.ChatLink
import id.xyverse.xydesk.net.ChatMessage
import id.xyverse.xydesk.ui.kit.Icon
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyEmpty
import id.xyverse.xydesk.ui.kit.XyIcon
import id.xyverse.xydesk.ui.kit.XyText
import kotlinx.coroutines.delay
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Chat global: satu ruang untuk semua pengguna XyDesk yang sudah masuk.
 *
 * Mengutamakan rasa responsif & kenyamanan:
 * - **Tampil instan nol-delay:** pesan dimuat dari disk cache pada frame pertama tanpa kedipan loading.
 * - **Foto profil, nama & badge VIP:** semua pesan menampilkan identitas jelas dan bingkai emas berlapis untuk anggota VIP.
 * - **Swipe-to-reply:** geser gelembung ke kanan untuk membalas dengan tahanan elastis dan getaran haptic.
 * - **WhatsApp iOS-style Long-Press Focus:** tahan gelembung untuk mengangkatnya dengan latar belakang buram/frosted dan menu aksi melayang tepat di bawahnya.
 */
@Composable
fun ChatScreen(jwt: String) {
    val context = LocalContext.current
    val client = remember(jwt) { ChatClient.shared(context, jwt) }
    DisposableEffect(client) {
        client.start()
        onDispose { /* Pertahankan soket hidup saat berpindah tab */ }
    }

    val messages by client.messages.collectAsState()
    val link by client.link.collectAsState()
    val online by client.online.collectAsState()
    val me by client.me.collectAsState()
    val notice by client.notice.collectAsState()

    var activeReply by remember { mutableStateOf<ChatMessage?>(null) }
    var focusedMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var toastNotice by remember { mutableStateOf<String?>(null) }

    // Sambung ulang dengan jeda menanjak selama layar masih aktif.
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
    LaunchedEffect(toastNotice) {
        if (toastNotice != null) {
            delay(2000)
            toastNotice = null
        }
    }

    var draft by remember { mutableStateOf("") }
    var stamps by remember { mutableStateOf(listOf<Long>()) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    val offsetMs = remember { TimeZone.getDefault().rawOffset }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            ChatHeader(link, online, messages.isNotEmpty(), client.waitingSince)

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(Xy.pad), contentAlignment = Alignment.Center) {
                        XyEmpty(
                            Icon.NEWS,
                            "Belum ada obrolan",
                            "Ruang ini terbuka untuk semua pengguna XyDesk. Sapa duluan — pesan tersimpan untuk siapa pun yang masuk.",
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
                            SwipeableBubbleRow(
                                message = m,
                                head = head,
                                me = me,
                                offsetMs = offsetMs,
                                onSwipeReply = { activeReply = it },
                                onFocus = { focusedMessage = it },
                            )
                        }
                    }
                }
            }

            // Pesan peringatan / notifikasi singkat.
            val alert = notice ?: toastNotice
            AnimatedVisibility(
                visible = alert != null,
                enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { it / 2 },
                exit = fadeOut(tween(160)),
            ) {
                XyText(
                    alert.orEmpty(),
                    Xy.caption.copy(color = if (notice != null) Xy.danger else Xy.accent, textAlign = TextAlign.Center),
                    Modifier.fillMaxWidth().padding(horizontal = Xy.pad, vertical = 4.dp),
                )
            }

            // Kartu pratinjau pesan yang sedang dibalas.
            AnimatedVisibility(
                visible = activeReply != null,
                enter = fadeIn(tween(140)) + slideInVertically(tween(140)) { it / 2 },
                exit = fadeOut(tween(140)) + slideOutVertically(tween(140)) { it / 2 },
            ) {
                activeReply?.let { target ->
                    ReplyPreviewBar(
                        target = target,
                        me = me,
                        onCancel = { activeReply = null },
                    )
                }
            }

            ChatInput(
                draft = draft,
                onDraft = { draft = it },
                connected = link == ChatLink.ONLINE,
                stamps = stamps,
            ) {
                if (client.send(draft, replyTo = activeReply?.id)) {
                    val now = System.currentTimeMillis()
                    stamps = ChatRules.pruneStamps(stamps, now) + now
                    draft = ""
                    activeReply = null
                }
            }
            Spacer(Modifier.height(84.dp))
        }

        // Lapisan fokus WhatsApp iPhone bergaya frosted blur saat pesan ditahan.
        focusedMessage?.let { focused ->
            FocusOverlay(
                message = focused,
                me = me,
                offsetMs = offsetMs,
                onDismiss = { focusedMessage = null },
                onReply = {
                    activeReply = focused
                    focusedMessage = null
                },
                onCopy = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("XyDesk Chat", focused.text))
                    toastNotice = "Teks disalin"
                    focusedMessage = null
                },
            )
        }
    }
}

/** Header obrolan bebas kedipan loading. */
@Composable
private fun ChatHeader(link: ChatLink, online: Int, hasMessages: Boolean, waitingSince: Long) {
    val elapsed = System.currentTimeMillis() - waitingSince
    val isOnline = link == ChatLink.ONLINE
    val dot = when {
        isOnline -> Xy.success
        hasMessages -> Xy.textLow
        else -> Xy.warning
    }
    val pulse by animateFloatAsState(if (isOnline) 1f else 0.45f, tween(500), label = "pulse")
    val subtitle = ChatRules.statusLine(isOnline, online, hasMessages, elapsed)

    Column(Modifier.padding(start = Xy.pad, end = Xy.pad, top = 12.dp, bottom = 8.dp)) {
        XyText("Obrolan", Xy.display)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).alpha(pulse).background(dot, CircleShape))
            Spacer(Modifier.width(7.dp))
            XyText(subtitle, Xy.caption)
        }
    }
}

/** Baris gelembung dengan dukungan swipe-to-reply ke kanan. */
@Composable
private fun SwipeableBubbleRow(
    message: ChatMessage,
    head: Boolean,
    me: String,
    offsetMs: Int,
    onSwipeReply: (ChatMessage) -> Unit,
    onFocus: (ChatMessage) -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var dragDp by remember { mutableFloatStateOf(0f) }
    var armedTriggered by remember { mutableStateOf(false) }

    val animatedOffsetDp by animateFloatAsState(
        targetValue = dragDp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "bubbleSwipe",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragStart = { armedTriggered = false },
                    onDragEnd = {
                        if (ChatRules.swipeArmed(dragDp)) {
                            onSwipeReply(message)
                        }
                        dragDp = 0f
                        armedTriggered = false
                    },
                    onDragCancel = {
                        dragDp = 0f
                        armedTriggered = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        val deltaDp = with(density) { dragAmount.toDp().value }
                        if (deltaDp > 0 || dragDp > 0) {
                            change.consume()
                            val nextDp = ChatRules.swipeOffsetDp(dragDp + deltaDp)
                            dragDp = nextDp
                            if (!armedTriggered && ChatRules.swipeArmed(nextDp)) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                armedTriggered = true
                            }
                        }
                    },
                )
            },
    ) {
        if (animatedOffsetDp > 0.5f) {
            val iconAlpha = ChatRules.swipeIconAlpha(animatedOffsetDp)
            val iconScale = (0.6f + iconAlpha * 0.4f).coerceIn(0.6f, 1f)
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
                    .size(34.dp)
                    .scale(iconScale)
                    .alpha(iconAlpha)
                    .clip(CircleShape)
                    .background(Xy.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                XyIcon(Icon.REPLY, tint = Xy.accent, size = 18.dp)
            }
        }

        Box(
            Modifier
                .offset { IntOffset(with(density) { animatedOffsetDp.dp.roundToPx() }, 0) }
                .fillMaxWidth(),
        ) {
            Bubble(
                m = message,
                head = head,
                me = me,
                offsetMs = offsetMs,
                onFocus = { onFocus(message) },
            )
        }
    }
}

/** Gelembung pesan tunggal: avatar, nama, kutipan balasan, teks, dan status VIP. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    m: ChatMessage,
    head: Boolean,
    me: String,
    offsetMs: Int,
    onFocus: () -> Unit,
) {
    val mine = m.mine || (me.isNotEmpty() && m.from == me)
    val haptic = LocalHapticFeedback.current

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = if (head) 10.dp else 2.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) {
            Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                if (head) Avatar(m.from, m.hue, m.vip, m.photo)
            }
            Spacer(Modifier.width(6.dp))
        }

        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (head) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 3.dp)) {
                    if (!mine) {
                        XyText(m.from, Xy.label.copy(color = Xy.textMid, fontWeight = FontWeight.SemiBold), maxLines = 1)
                        if (m.vip) {
                            Spacer(Modifier.width(5.dp))
                            VipBadge()
                        }
                        Spacer(Modifier.width(6.dp))
                        XyText(ChatRules.clock(m.at, offsetMs), Xy.label, maxLines = 1)
                    } else {
                        XyText(ChatRules.clock(m.at, offsetMs), Xy.label, maxLines = 1)
                        if (m.vip) {
                            Spacer(Modifier.width(5.dp))
                            VipBadge()
                        }
                        Spacer(Modifier.width(6.dp))
                        XyText("Saya", Xy.label.copy(color = Xy.accent, fontWeight = FontWeight.SemiBold), maxLines = 1)
                    }
                }
            }

            Box(
                Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = if (mine || head) Xy.radiusM else 6.dp,
                            topEnd = if (mine && !head) 6.dp else Xy.radiusM,
                            bottomStart = Xy.radiusM,
                            bottomEnd = Xy.radiusM,
                        ),
                    )
                    .background(if (mine) Xy.accent else Xy.overlay)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onFocus()
                        },
                        onClick = {},
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Column {
                    if (m.reply != null) {
                        ReplyQuoteCard(
                            reply = m.reply,
                            mine = mine,
                        )
                        Spacer(Modifier.height(5.dp))
                    }

                    XyText(
                        m.text,
                        Xy.body,
                        color = if (mine) Color.White else Xy.textHi,
                    )
                }
            }
        }

        if (mine) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                if (head) Avatar(m.from, m.hue, m.vip, m.photo)
            }
        }
    }
}

/** Kotak kutipan balasan di dalam bubble. */
@Composable
private fun ReplyQuoteCard(reply: id.xyverse.xydesk.net.ChatReply, mine: Boolean) {
    val barColor = if (mine) Color.White else Xy.accent
    val bgTint = if (mine) Color(0x28FFFFFF) else Color(0x0E000000)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgTint)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(28.dp)
                .background(barColor, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            XyText(
                reply.from,
                Xy.label.copy(fontWeight = FontWeight.Bold),
                color = if (mine) Color.White else Xy.accent,
                maxLines = 1,
            )
            XyText(
                reply.text,
                Xy.caption,
                color = if (mine) Color.White.copy(alpha = 0.9f) else Xy.textMid,
                maxLines = 1,
            )
        }
    }
}

/** Baris pratinjau pesan sebelum dikirim. */
@Composable
private fun ReplyPreviewBar(
    target: ChatMessage,
    me: String,
    onCancel: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Xy.pad, vertical = 4.dp)
            .clip(RoundedCornerShape(Xy.radiusM))
            .background(Xy.overlay)
            .border(1.dp, Xy.line, RoundedCornerShape(Xy.radiusM))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.5.dp)
                .height(30.dp)
                .background(Xy.accent, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(10.dp))
        XyIcon(Icon.REPLY, tint = Xy.accent, size = 16.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            XyText(
                ChatRules.replyHeader(target.from, me),
                Xy.label.copy(fontWeight = FontWeight.Bold, color = Xy.accent),
                maxLines = 1,
            )
            XyText(
                ChatRules.replySnippet(target.text),
                Xy.caption.copy(color = Xy.textMid),
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(remember { MutableInteractionSource() }, null, onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            XyIcon(Icon.CLOSE, tint = Xy.textMid, size = 16.dp)
        }
    }
}

/**
 * Overlay fokus saat gelembung ditahan: latar belakang gelap/blur bergaya WhatsApp iPhone,
 * gelembung terangkat membesar sedikit dengan bayangan lembut, dan menu aksi melayang tepat di bawahnya.
 */
@Composable
private fun FocusOverlay(
    message: ChatMessage,
    me: String,
    offsetMs: Int,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xBB05050A))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 340.dp)
                .clickable(remember { MutableInteractionSource() }, null) {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Gelembung yang difokuskan terangkat 1.04x dengan border glowing
            Box(
                Modifier
                    .scale(ChatRules.FOCUS_SCALE)
                    .clip(RoundedCornerShape(Xy.radiusL))
                    .background(Color(0xF012121A))
                    .border(1.5.dp, Color(0x66A78BFA), RoundedCornerShape(Xy.radiusL))
                    .padding(16.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(message.from, message.hue, message.vip, message.photo)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                XyText(message.from, Xy.title.copy(fontSize = 15.sp, color = Color.White), maxLines = 1)
                                if (message.vip) {
                                    Spacer(Modifier.width(6.dp))
                                    VipBadge()
                                }
                            }
                            XyText(ChatRules.clock(message.at, offsetMs), Xy.label.copy(color = Color(0xFFA1A1AA)))
                        }
                    }

                    if (message.reply != null) {
                        Spacer(Modifier.height(10.dp))
                        ReplyQuoteCard(message.reply, mine = false)
                    }

                    Spacer(Modifier.height(12.dp))
                    XyText(message.text, Xy.body.copy(color = Color(0xFFF4F4F5)))
                }
            }

            Spacer(Modifier.height(18.dp))

            // Menu aksi melayang ala WhatsApp iPhone
            Row(
                Modifier
                    .clip(RoundedCornerShape(Xy.pill))
                    .background(Color(0xF21C1C26))
                    .border(1.dp, Color(0x33A78BFA), RoundedCornerShape(Xy.pill))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FocusActionButton(icon = Icon.REPLY, label = "Balas", onClick = onReply)
                Box(Modifier.width(1.dp).height(20.dp).background(Color(0x33FFFFFF)))
                FocusActionButton(icon = Icon.COPY, label = "Salin", onClick = onCopy)
                Box(Modifier.width(1.dp).height(20.dp).background(Color(0x33FFFFFF)))
                FocusActionButton(icon = Icon.CLOSE, label = "Tutup", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun FocusActionButton(icon: Icon, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(Xy.pill))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XyIcon(icon, tint = Xy.accent, size = 17.dp)
        Spacer(Modifier.width(6.dp))
        XyText(label, Xy.label.copy(fontWeight = FontWeight.SemiBold, color = Color.White))
    }
}

/** Avatar bulat: foto profil asli bila ada, inisial huruf, dan cincin emas berlapis untuk VIP. */
@Composable
private fun Avatar(name: String, hue: Int, vip: Boolean, photo: String = "") {
    val size = if (vip) 32.dp else 28.dp
    val innerSize = if (vip) size - 4.dp else size
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
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
                .size(innerSize)
                .clip(CircleShape)
                .background(hueColor(hue)),
            contentAlignment = Alignment.Center,
        ) {
            if (photo.isNotBlank()) {
                RemoteImage(url = photo, modifier = Modifier.fillMaxSize())
            } else {
                XyText(ChatRules.initial(name), Xy.label.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1)
            }
        }
    }
}

/** Lencana VIP kecil. */
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
                    XyText("Tulis pesan", Xy.body, color = Xy.textLow, maxLines = 1)
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

private val kVipLight = Color(0xFFF7C948)
private val kVipDeep = Color(0xFFB07400)

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
