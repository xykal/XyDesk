package id.xyverse.xydesk.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import id.xyverse.xydesk.core.tr
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import id.xyverse.xyadapt.ReconnectPolicy
import id.xyverse.xyadapt.TrackpadConfig
import id.xyverse.xyadapt.VideoCmd
import id.xyverse.xydesk.core.HostSpecs
import id.xyverse.xydesk.core.KeyMap
import id.xyverse.xydesk.core.LocalLang
import id.xyverse.xydesk.core.P
import id.xyverse.xydesk.core.Previews
import id.xyverse.xydesk.core.SessionRecord
import id.xyverse.xydesk.core.Store
import id.xyverse.xydesk.core.StreamXy
import id.xyverse.xydesk.databinding.ActivitySessionBinding
import id.xyverse.xydesk.rtc.Phase
import id.xyverse.xydesk.rtc.RtcListener
import id.xyverse.xydesk.rtc.RtcSession

/**
 * Layar sesi: video penuh, gestur trackpad/sentuh (`SessionTouch`), statistik +
 * kualitas adaptif (`SessionMetrics`), clipboard dua arah opsional, dan pencatatan
 * riwayat yang diperbarui langsung saat nama/spesifikasi host tiba.
 */
class SessionActivity : ComponentActivity(), RtcListener {
    private lateinit var b: ActivitySessionBinding
    private lateinit var session: RtcSession
    private lateinit var touch: SessionTouch
    private lateinit var metrics: SessionMetrics
    private lateinit var keys: SessionKeyboard
    private val store by lazy { Store(applicationContext) }
    private val hostId by lazy { intent.getStringExtra("host").orEmpty() }
    private val reconnect by lazy { ReconnectPolicy().also { p -> repeat(intent.getIntExtra("attempt", 0)) { p.nextDelayMs() } } }
    private var everConnected = false
    private var clipboard: ClipboardManager? = null
    private var lastSyncedClipboard = ""
    private var clipboardSync = false
    private var connected = false
    private var lowLatency = false
    private var showStats = true
    private var presenting = false
    private var pcKeysOpen = false
    private var lastBack = 0L
    private var micInput: Boolean? = null
    private var micReason = ""
    private var hostName = ""
    private var specs = HostSpecs()
    private var startedAt = 0L
    private var outcome = "berjalan"
    private var dockOn = true
    private var dockSize by mutableStateOf(1)
    private var dockKeys by mutableStateOf<List<String>>(emptyList())
    private var dockHeld by mutableStateOf<Set<String>>(emptySet())
    private var overlayItems by mutableStateOf(listOf<OverlayItem>())
    private var overlayEdit by mutableStateOf(false)
    private var connectState by mutableStateOf(ConnectState(Phase.PAIRING, null, ""))
    private val ui = Handler(Looper.getMainLooper())
    private val statsTick = object : Runnable {
        override fun run() { metrics.tick(); ui.postDelayed(this, 1000) }
    }
    private val previewTick = object : Runnable {
        override fun run() { capturePreview(); ui.postDelayed(this, 10_000) }
    }
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!connected || !clipboardSync) return@OnPrimaryClipChangedListener
        val text = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (text.isNotEmpty() && text != lastSyncedClipboard) {
            lastSyncedClipboard = text
            session.send(StreamXy.clipboardSet(text))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        getSystemService(AudioManager::class.java)?.mode = AudioManager.MODE_NORMAL
        clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.addPrimaryClipChangedListener(clipListener)
        b = ActivitySessionBinding.inflate(layoutInflater)
        setContentView(b.root)
        if (store.keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyLayoutPrefs()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = System.currentTimeMillis()
                when {
                    pcKeysOpen -> setPcKeys(false)
                    presenting -> setPresenting(false)
                    now - lastBack < 2000 || !store.confirmDisconnect -> { outcome = if (connected) "putus" else "batal"; finish() }
                    else -> { lastBack = now; Toast.makeText(this@SessionActivity, "Tekan sekali lagi untuk memutus sesi".tr(store.lang), Toast.LENGTH_SHORT).show() }
                }
            }
        })
        hideSystemBars()

        showStats = store.showStats
        dockOn = store.dockOn; dockSize = store.dockSize; dockKeys = store.dockKeys
        overlayItems = OverlayLayouts.fromJson(store.overlayJson)
        session = RtcSession(
            context = applicationContext,
            jwt = intent.getStringExtra("jwt").orEmpty(),
            hostId = hostId,
            pin = intent.getStringExtra("pin").orEmpty(),
            selfName = "${Build.MANUFACTURER} ${Build.MODEL}",
            listener = this,
            lowLatencySurface = { b.raw.holder.surface.takeIf { it.isValid } },
            onNativeDecode = { ms -> metrics.onNativeDecode(ms) },
            onNativeSize = { w, h -> runOnUiThread { fitSurface(w, h) } },
            onDecodeMode = { ll -> runOnUiThread { applyDecodeMode(ll) } },
            forceRelay = store.bool(P.FORCE_RELAY, false),
        )
        metrics = SessionMetrics(this, session, ::showHud, ::applyVideoCmd)
        metrics.targetFps = store.targetFps
        metrics.auto = store.quality == 0
        val cfg = TrackpadConfig(
            speed = store.trackpadSpeed,
            naturalScroll = store.naturalScroll,
            accel = listOf(0f, 0.6f, 1.2f)[store.int(P.ACCEL, 1)],
            scrollUnit = listOf(24, 40, 70)[store.int(P.SCROLL_SPEED, 1)],
            twoFingerTap = store.bool(P.TWO_FINGER_RIGHT, true),
            swipe3Px = if (store.bool(P.SWIPE3, true)) 90f else Float.MAX_VALUE,
        )
        val haptic = store.bool(P.SESSION_HAPTIC, true)
        touch = SessionTouch(cfg, session::send) {
            if (haptic) b.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        touch.directTouch = store.directTouch
        keys = SessionKeyboard(this, b.keyboardSink, session::send)
        session.attach(b.video)
        applyDecodeMode(true)
        b.raw.setOnTouchListener { v, e -> touch.onTouch(v, e) }
        b.video.setOnTouchListener { v, e -> touch.onTouch(v, e) }
        connectState = ConnectState(Phase.PAIRING, null, hostId, attempt = intent.getIntExtra("attempt", 0))
        setupToolbar()
        setupPcKeys()
        setupOverlay()
        setupDock()
        startedAt = System.currentTimeMillis()
        record()
        session.start()
    }

    private val maxMbps get() = P.MBPS[store.int(P.MAX_MBPS, 0)]
    private fun capped(mbps: Int) = if (maxMbps > 0) minOf(mbps, maxMbps) else mbps

    private fun applyVideoCmd(cmd: VideoCmd) {
        when (cmd) {
            is VideoCmd.Bitrate -> session.send(StreamXy.bitrate(capped(cmd.mbps)))
            is VideoCmd.Resolution -> session.send(StreamXy.resolution(cmd.mode))
            is VideoCmd.Fps -> session.send(StreamXy.fps(cmd.target))
        }
    }

    private fun showHud(items: List<Pair<String, String>>) = runOnUiThread {
        if (!connected || !showStats || presenting) return@runOnUiThread
        b.status.text = SessionHud.render(items.filter { it.first in store.hudItems && it.second.isNotEmpty() })
        b.status.visibility = View.VISIBLE
    }

    private fun applyDecodeMode(ll: Boolean) {
        lowLatency = ll
        metrics.lowLatency = ll
        b.raw.visibility = if (ll) View.VISIBLE else View.GONE
        b.video.visibility = if (ll) View.GONE else View.VISIBLE
    }

    /** Letterbox: SurfaceView mengikuti rasio frame host, bukan layar HP. */
    private fun fitSurface(w: Int, h: Int) {
        val parent = b.raw.parent as View
        if (parent.width == 0 || parent.height == 0) return
        val scale = minOf(parent.width.toFloat() / w, parent.height.toFloat() / h)
        val lp = b.raw.layoutParams as android.widget.FrameLayout.LayoutParams
        lp.width = (w * scale).toInt(); lp.height = (h * scale).toInt()
        lp.gravity = android.view.Gravity.CENTER
        b.raw.layoutParams = lp
        b.raw.holder.setFixedSize(w, h)
    }

    private fun setupDock() {
        (b.dock as? PassThroughComposeView)?.behind = b.video
        b.dock.setContent {
            CompositionLocalProvider(LocalLang provides store.lang) {
                ControlOverlay(
                    items = overlayItems,
                    edit = overlayEdit,
                    send = session::send,
                    onItems = { overlayItems = it },
                    onToggleTouch = {
                        store.directTouch = !store.directTouch
                        touch.directTouch = store.directTouch
                    },
                    onEdit = { overlayEdit = it },
                    onSave = { store.overlayJson = OverlayLayouts.toJson(overlayItems) },
                )
            }
        }
    }

    /** Tombol dok: modifier/seret bersifat tahan (ditekan sampai diketuk lagi), sisanya tekan-lepas. */
    private fun onDock(k: DockKey) {
        b.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        when {
            k.sticky -> {
                val down = k.id !in dockHeld
                dockHeld = if (down) dockHeld + k.id else dockHeld - k.id
                if (k.mouse >= 0) session.send(StreamXy.button(k.mouse, down)) else session.send(StreamXy.key(k.vk, down))
            }
            k.mouse >= 0 -> { session.send(StreamXy.button(k.mouse, true)); ui.postDelayed({ session.send(StreamXy.button(k.mouse, false)) }, 40) }
            k.scroll != 0 -> session.send(StreamXy.scroll(0, k.scroll * 240))
            k.chord.isNotEmpty() -> { k.chord.forEach { session.send(StreamXy.key(it, true)) }; k.chord.reversed().forEach { session.send(StreamXy.key(it, false)) } }
            else -> { session.send(StreamXy.key(k.vk, true)); session.send(StreamXy.key(k.vk, false)) }
        }
    }

    private fun setupOverlay() {
        b.overlay.setContent {
            CompositionLocalProvider(LocalLang provides store.lang) {
                ConnectingOverlay(
                    connectState,
                    onRetry = { outcome = "retry"; startActivity(intent); finish() },
                    onBack = { if (outcome == "berjalan") outcome = "batal"; finish() },
                )
            }
        }
    }

    private fun setupPcKeys() {
        b.pcKeys.setContent {
            CompositionLocalProvider(LocalLang provides store.lang) {
                SessionPcKeyboard(
                    onKey = { vk -> session.send(StreamXy.key(vk, true)); session.send(StreamXy.key(vk, false)) },
                    onIme = { keys.toggle() },
                    onClose = { setPcKeys(false) },
                )
            }
        }
    }

    private fun setPcKeys(on: Boolean) {
        pcKeysOpen = on
        b.pcKeys.visibility = if (on && !presenting) View.VISIBLE else View.GONE
        if (on) b.dock.visibility = View.GONE
        else if (connected && dockOn && !presenting) b.dock.visibility = View.VISIBLE
    }

    private fun setupToolbar() {
        val autohide = listOf(0L, 5000L, 10000L, 20000L)[store.int(P.RAIL_AUTOHIDE, 0)]
        val prefs = SessionPrefs(store.quality, store.targetFps, store.directTouch, store.trackpadSpeed, store.naturalScroll, store.showStats, dockOn, dockSize, dockKeys, autohide)
        b.toolbar.setContent {
            CompositionLocalProvider(LocalLang provides store.lang) {
                SessionToolbar(
                    SessionActions(
                        keyboard = { setPcKeys(!pcKeysOpen) },
                        quality = { store.quality = it; metrics.auto = it == 0; if (it > 0) session.send(StreamXy.quality(it)) },
                        resolution = { session.send(StreamXy.resolution(it)) },
                        fps = { store.targetFps = it; metrics.targetFps = it; session.send(StreamXy.fps(it)) },
                        bitrate = { metrics.auto = false; session.send(StreamXy.bitrate(capped(it))) },
                        display = { session.send(StreamXy.display(it)) },
                        touchMode = { store.directTouch = it; touch.directTouch = it },
                        trackpadSpeed = { store.trackpadSpeed = it; touch.config = touch.config.copy(speed = it) },
                        naturalScroll = { store.naturalScroll = it; touch.config = touch.config.copy(naturalScroll = it) },
                        audioMute = { session.setAudioMuted(it) },
                        mic = { on ->
                            when {
                                !on -> session.setMicEnabled(false)
                                micInput == false -> Toast.makeText(this, micHint().tr(store.lang), Toast.LENGTH_LONG).show()
                                hasMic() -> session.setMicEnabled(true)
                                else -> requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 7)
                            }
                        },
                        clipboardSync = { on -> clipboardSync = on; session.clipboardSync = on },
                        sendQuickKey = { keys.quick(it) },
                        stats = { store.showStats = it; showStats = it; if (!it) b.status.visibility = View.GONE },
                        centerCursor = { session.send(StreamXy.moveAbs(0.5f, 0.5f)) },
                        present = { setPresenting(true) },
                        dockOn = { store.dockOn = it; dockOn = it; b.dock.visibility = if (it && connected) View.VISIBLE else View.GONE },
                        dockSize = { store.dockSize = it; dockSize = it },
                        dockKeys = { store.dockKeys = it; dockKeys = it },
                        overlayEdit = {
                            overlayEdit = !overlayEdit
                            if (overlayEdit) { dockOn = true; store.dockOn = true; b.dock.visibility = View.VISIBLE }
                        },
                        disconnect = { outcome = "putus"; finish() },
                    ),
                    prefs,
                )
            }
        }
    }

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == 7) {
            val ok = res.firstOrNull() == PackageManager.PERMISSION_GRANTED
            session.setMicEnabled(ok)
            if (!ok) Toast.makeText(this, "Izin mikrofon ditolak.".tr(store.lang), Toast.LENGTH_SHORT).show()
        }
    }

    /** Mode presentasi: semua overlay disembunyikan; tombol Kembali mengembalikannya. */
    private fun setPresenting(on: Boolean) {
        presenting = on
        b.toolbar.visibility = if (on) View.GONE else View.VISIBLE
        if (on) setPcKeys(false)
        b.dock.visibility = if (!on && dockOn && connected) View.VISIBLE else View.GONE
        if (on) b.status.visibility = View.GONE
        if (on) Toast.makeText(this, "Mode presentasi. Tekan Kembali untuk menampilkan kontrol lagi.".tr(store.lang), Toast.LENGTH_LONG).show()
    }

    private fun micHint() = when (micReason) {
        "no-endpoint" -> "Driver mic virtual ada, tapi perangkat CABLE Input nonaktif di PC. Aktifkan di Pengaturan Suara Windows, lalu sambung ulang."
        "no-driver" -> "PC belum punya driver mic virtual. Di PC: Start Menu > XyDesk > Install Virtual Audio Driver (Run as administrator), lalu sambung ulang."
        else -> "PC belum punya input mic virtual. Perbarui XyDesk Host ke versi terbaru."
    }

    /** Orientasi, posisi HUD, ukuran HUD, dan posisi dok dari Pengaturan. */
    private fun applyLayoutPrefs() {
        requestedOrientation = when (store.int(P.ORIENTATION, 0)) {
            1 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            2 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        b.status.textSize = listOf(10f, 12f, 14.5f)[store.int(P.HUD_SIZE, 1)]
        (b.status.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let { lp ->
            val right = store.bool(P.HUD_RIGHT, false)
            lp.gravity = android.view.Gravity.TOP or (if (right) android.view.Gravity.END else android.view.Gravity.START)
            if (right) lp.marginEnd = (resources.displayMetrics.density * 72).toInt()
            b.status.layoutParams = lp
        }
        (b.dock.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let { lp ->
            val top = store.bool(P.DOCK_TOP, false)
            lp.gravity = (if (top) android.view.Gravity.TOP else android.view.Gravity.BOTTOM) or android.view.Gravity.CENTER_HORIZONTAL
            val m = (resources.displayMetrics.density * 10).toInt()
            lp.topMargin = if (top) m else 0; lp.bottomMargin = if (top) 0 else m
            b.dock.layoutParams = lp
        }
    }

    /** Preferensi "saat mulai sesi": audio bisu, mic, clipboard, resolusi awal, batas bitrate. */
    private fun applyStartPrefs() {
        if (store.bool(P.START_MUTED, false)) session.setAudioMuted(true)
        if (store.bool(P.START_MIC, false) && hasMic()) session.setMicEnabled(true)
        if (store.bool(P.START_CLIP, false)) { clipboardSync = true; session.clipboardSync = true }
        val res = store.int(P.START_RES, 0)
        if (res > 0) { metrics.auto = false; session.send(StreamXy.resolution(res - 1)) }
        if (maxMbps > 0 && metrics.auto) session.send(StreamXy.bitrate(maxMbps))
    }

    /** Salin frame yang sedang tampil ke bitmap kecil; jadi cuplikan kartu perangkat. */
    private fun capturePreview() {
        if (!connected || !store.bool(P.SAVE_PREVIEW, true)) return
        val view: SurfaceView = if (lowLatency) b.raw else b.video
        if (view.width == 0 || view.height == 0 || !view.holder.surface.isValid) return
        val w = 1280
        val bmp = Bitmap.createBitmap(w, (w * view.height / view.width).coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        runCatching {
            PixelCopy.request(view, bmp, { r -> if (r == PixelCopy.SUCCESS) Thread { Previews.save(applicationContext, hostId, bmp) }.start() }, ui)
        }
    }

    /** Catat/perbarui sesi ini di riwayat; dipanggil ulang tiap ada info baru. */
    private fun record() = store.record(
        SessionRecord(
            hostId, hostName, startedAt, (System.currentTimeMillis() - startedAt) / 1000, outcome, specs,
            if (::metrics.isInitialized) metrics.trace.encode() else "",
        ),
    )

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!connected || !KeyMap.isPhysical(event) || event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        return keys.physical(event) || super.dispatchKeyEvent(event)
    }

    override fun onMicInput(available: Boolean, reason: String) { micInput = available; micReason = reason }

    override fun onHostName(name: String) = runOnUiThread { hostName = name; record() }

    override fun onHostSpecs(specs: HostSpecs) = runOnUiThread { this.specs = specs; record() }

    override fun onRemoteClipboard(text: String) = runOnUiThread {
        if (text.isNotEmpty() && text != lastSyncedClipboard) {
            lastSyncedClipboard = text
            clipboard?.setPrimaryClip(ClipData.newPlainText("XyDesk", text))
        }
    }

    override fun onPhase(phase: Phase, message: String?) = runOnUiThread {
        connected = phase == Phase.CONNECTED
        if (phase == Phase.REJECTED) store.setHostPin(hostId, null)
        connectState = connectState.copy(phase = phase, message = message)
        b.overlay.visibility = if (connected) View.GONE else View.VISIBLE
        b.dock.visibility = if (connected && dockOn && !presenting) View.VISIBLE else View.GONE
        b.status.visibility = View.GONE
        ui.removeCallbacks(statsTick); ui.removeCallbacks(previewTick)
        if (connected) {
            everConnected = true
            reconnect.reset()
            ui.postDelayed(statsTick, 1000)
            ui.postDelayed(previewTick, 3000)
            session.send(StreamXy.fps(metrics.targetFps))
            if (store.quality == 0) metrics.auto = true else session.send(StreamXy.quality(store.quality))
            applyStartPrefs()
        }
        if (phase in setOf(Phase.REJECTED, Phase.PEER_OFFLINE, Phase.BUSY, Phase.ERROR, Phase.ENDED) && outcome == "berjalan") {
            outcome = phase.name.lowercase()
            if (everConnected && store.autoReconnect && ReconnectPolicy.retryable(outcome)) scheduleReconnect()
        }
        record()
    }

    /** Putus tak terduga setelah sempat tersambung: coba lagi dengan jeda mundur eksponensial. */
    private fun scheduleReconnect() {
        val delay = reconnect.nextDelayMs() ?: return
        connectState = connectState.copy(reconnecting = true, attempt = reconnect.attempt)
        ui.postDelayed({
            outcome = "retry"
            startActivity(intent.putExtra("attempt", reconnect.attempt))
            finish()
        }, delay)
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
    }

    override fun onDestroy() {
        clipboard?.removePrimaryClipChangedListener(clipListener)
        if (outcome == "berjalan") outcome = "ok"
        record()
        ui.removeCallbacksAndMessages(null)
        touch.release()
        metrics.close()
        session.stop()
        session.release()
        super.onDestroy()
    }
}
