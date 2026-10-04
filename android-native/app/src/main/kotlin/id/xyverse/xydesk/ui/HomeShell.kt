package id.xyverse.xydesk.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.DisposableEffect
import id.xyverse.xydesk.core.Presence
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import id.xyverse.xydesk.core.News
import id.xyverse.xydesk.core.NewsPost
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.R
import id.xyverse.xydesk.core.SessionRecord
import id.xyverse.xydesk.ui.kit.BottomNav
import id.xyverse.xydesk.ui.kit.Icon
import id.xyverse.xydesk.ui.kit.Tab
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyEmpty
import id.xyverse.xydesk.ui.kit.XyText

@Composable
fun HomeShell(
    email: String,
    lastHost: String,
    startTab: Tab = Tab.HOME,
    history: List<SessionRecord>,
    onConnect: (host: String, pin: String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onShare: (String) -> Unit = onOpenUrl,
    googleToken: (suspend () -> String?)? = null,
    settings: Settings,
    onLogout: () -> Unit,
) {
    var tab by remember { mutableStateOf(startTab) }
    BackHandler(tab != Tab.HOME) { tab = Tab.HOME }
    var prefill by remember { mutableStateOf(lastHost) }
    var asking by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<SessionRecord?>(null) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("Terakhir") }
    val store = settings.store
    var grid by remember { mutableStateOf(store?.devicesGrid ?: false) }
    val metaTick by (store?.observeHostMeta() ?: remember { MutableStateFlow(0) }).collectAsState()
    val knownHosts = remember { mutableStateOf<List<String>>(emptyList()) }
    val presence = remember { store?.jwt?.let { Presence(it) { knownHosts.value } } }
    DisposableEffect(presence) { presence?.start(); onDispose { presence?.stop() } }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, presence) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) presence?.refresh() }
        lifecycle.lifecycle.addObserver(obs); onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }
    val online by (presence?.online ?: remember { MutableStateFlow<Set<String>?>(null) }).collectAsState()
    val meta = remember(metaTick, store, online, history) {
        HostMeta(
            alias = { store?.alias(it).orEmpty() },
            favorite = { store?.favorite(it) ?: false },
            online = { h -> online?.let { Presence.hostKey(h) in it } },
            sessions = { h -> history.filter { it.host == h } },
        )
    }
    var posts by remember { mutableStateOf<List<NewsPost>?>(null) }
    var offline by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf<NewsPost?>(null) }
    var seen by remember { mutableStateOf(store?.newsSeen.orEmpty()) }
    LaunchedEffect(Unit) {
        val cached = store?.newsCache.orEmpty()
        if (cached.isNotEmpty()) posts = runCatching { News.parseList(cached) }.getOrNull()
        runCatching { News.list() }.onSuccess { list -> posts = list; offline = false; store?.newsCache = org.json.JSONObject().put("posts", org.json.JSONArray(list.map { p -> postJson(p) })).toString() }
            .onFailure { offline = true; if (posts == null) posts = emptyList() }
    }
    val devices = remember(history, metaTick) { history.distinctBy { it.host }.sortedByDescending { meta.favorite(it.host) } }
    LaunchedEffect(devices) { knownHosts.value = devices.map { it.host }; presence?.refresh() }
    val unread = (posts?.firstOrNull()?.slug ?: "").let { it.isNotEmpty() && it != seen }
    fun quickConnect(host: String) {
        val saved = store?.hostPin(host)
        if (saved != null) onConnect(host, saved) else asking = host
    }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(tab) {
        if (tab == Tab.NEWS && Build.VERSION.SDK_INT >= 33) askNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    CompositionLocalProvider(LocalHostMeta provides meta) { Box(Modifier.fillMaxSize()) {
        AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "tab") { t ->
            when (t) {
                Tab.HOME -> key(prefill) { ConnectScreen(prefill, devices, { quickConnect(it) }, if (unread) posts?.firstOrNull()?.title else null, { tab = Tab.NEWS }) { host, pin, keep ->
                    store?.setHostPin(host, if (keep) pin else null)
                    onConnect(host, pin)
                } }
                Tab.DEVICES -> Page("Perangkat", "Semua PC yang pernah tersambung: wallpaper, spesifikasi, dan riwayat sesinya.") {
                    if (devices.isEmpty()) {
                        XyEmpty(Icon.MONITOR, "Belum ada perangkat", "Sambungkan sekali, PC tersimpan di sini beserta spesifikasi dan riwayat sesinya.", "Sambungkan", image = R.drawable.float_pc_sleep) { tab = Tab.HOME }
                    } else {
                        DeviceSearch(query) { query = it }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Terakhir", "Nama", "Favorit").forEach { Chip(it, it == sort) { sort = it } }
                            }
                            ViewSwitch(grid) { grid = it; store?.devicesGrid = it }
                        }
                        Spacer(Modifier.height(12.dp))
                        UsageStrip(history)
                        Spacer(Modifier.height(16.dp))
                        val filtered = devices.filter { d -> query.isBlank() || hostTitle(d).contains(query, true) || d.host.contains(query.filter(Char::isDigit).ifEmpty { "\u0000" }) }
                        val names = filtered.associate { it.host to hostTitle(it) }
                        val shown = when (sort) {
                            "Nama" -> filtered.sortedBy { names[it.host]?.lowercase() }
                            "Favorit" -> filtered.sortedByDescending { meta.favorite(it.host) }
                            else -> filtered.sortedByDescending { it.startedAt }
                        }
                        if (shown.isEmpty()) XyText("Tidak ada host yang cocok.", Xy.caption)
                        else DevicesSection(shown.take(30), grid = grid, onLong = { editing = it }, onSession = { detail = it }) { host -> quickConnect(host) }
                    }
                }
                Tab.NEWS -> reading?.let { p -> NewsDetailScreen(p, store?.newsFp.orEmpty(), email, googleToken, onShare) { reading = null } }
                    ?: Page("Berita", "Rilis, fitur baru, dan info XyDesk.") {
                        NewsScreen(posts, offline, seen) { p -> reading = p; seen = p.slug; store?.newsSeen = p.slug }
                    }
                Tab.ACCOUNT -> AccountScreen(email, onOpenUrl, settings, onLogout)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).safeDrawingPadding()) { BottomNav(tab, badge = if (unread) Tab.NEWS else null) { tab = it } }
        PinSheet(asking, remembered = false, onDismiss = { asking = null }) { pin, keep ->
            val host = asking ?: return@PinSheet
            store?.setHostPin(host, if (keep) pin else null)
            asking = null
            onConnect(host, pin)
        }
        store?.let { HostSheet(editing, it) { editing = null } }
        SessionDetailSheet(detail, onDismiss = { detail = null }) { host -> detail = null; quickConnect(host) }
    } }
}

@Composable
private fun Page(title: String, caption: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Xy.pad)) {
        Spacer(Modifier.height(12.dp))
        XyText(title, Xy.display)
        XyText(caption, Xy.caption)
        Spacer(Modifier.height(24.dp))
        content()
        Spacer(Modifier.height(96.dp))
    }
}

private fun postJson(p: NewsPost) = org.json.JSONObject()
    .put("slug", p.slug).put("title", p.title).put("excerpt", p.excerpt).put("content", p.content).put("cover", p.cover)
    .put("category", p.category).put("author", p.author).put("createdAt", p.createdAt).put("likeCount", p.likeCount).put("commentCount", p.commentCount)
