package id.xyverse.xydesk.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import id.xyverse.xydesk.core.News
import id.xyverse.xydesk.core.NewsFeed
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
    history: List<SessionRecord>,
    onConnect: (host: String, pin: String) -> Unit,
    onOpenUrl: (String) -> Unit,
    settings: Settings,
    onLogout: () -> Unit,
) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var prefill by remember { mutableStateOf(lastHost) }
    var asking by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }
    val store = settings.store
    val metaTick by (store?.observeHostMeta() ?: remember { MutableStateFlow(0) }).collectAsState()
    val meta = remember(metaTick, store) { HostMeta({ store?.alias(it).orEmpty() }, { store?.favorite(it) ?: false }) }
    var grid by remember { mutableStateOf(store?.historyGrid ?: false) }
    var feed by remember { mutableStateOf<NewsFeed?>(null) }
    var seen by remember { mutableStateOf(store?.newsSeen.orEmpty()) }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { feed = News.load(ctx) }
    val devices = remember(history, metaTick) { history.distinctBy { it.host }.sortedByDescending { meta.favorite(it.host) } }
    val unread = (feed?.items?.firstOrNull()?.id ?: "").let { it.isNotEmpty() && it != seen }
    fun quickConnect(host: String) {
        val saved = store?.hostPin(host)
        if (saved != null) onConnect(host, saved) else asking = host
    }
    CompositionLocalProvider(LocalHostMeta provides meta) { Box(Modifier.fillMaxSize()) {
        AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "tab") { t ->
            when (t) {
                Tab.HOME -> key(prefill) { ConnectScreen(email, prefill, devices, history, onConnect) }
                Tab.DEVICES -> Page("Perangkat", "Host yang pernah tersambung, lengkap dengan cuplikan dan spesifikasinya.") {
                    if (devices.isEmpty()) {
                        XyEmpty(Icon.MONITOR, "Belum ada perangkat", "Sambungkan sekali, host tersimpan di sini beserta spesifikasinya.", "Sambungkan", image = R.drawable.empty_devices) { tab = Tab.HOME }
                    } else DevicesSection(devices.take(10), onLong = { editing = it }) { host -> quickConnect(host) }
                }
                Tab.HISTORY -> Page("Riwayat", "Sesi terakhir, durasi, dan hasilnya.") {
                    if (history.isEmpty()) XyEmpty(Icon.CLOCK, "Belum ada sesi", "Riwayat muncul setelah sesi pertama selesai.")
                    else HistoryBrowser(history, grid, { grid = it; store?.historyGrid = it }) { host -> quickConnect(host) }
                }
                Tab.NEWS -> Page("Berita", "Rilis, fitur baru, dan info XyDesk.") {
                    NewsScreen(feed, settings.appVersion, seen, onOpenUrl) { seen = it; store?.newsSeen = it }
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
