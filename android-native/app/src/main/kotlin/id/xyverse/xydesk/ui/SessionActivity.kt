package id.xyverse.xydesk.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ClipData
import android.content.ClipboardManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
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
import id.xyverse.xyadapt.FpsOptions
import id.xyverse.xyadapt.CaptureRules
import id.xyverse.xyadapt.PadKbm
import id.xyverse.xyadapt.InputFamily
import id.xyverse.xyadapt.OverlayRules
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
import kotlin.math.roundToInt

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
    private lateinit var hid: SessionHid
    private var hidMonitor: HidMonitor? = null
    private var hidPresent = HidMonitor.HidPresence()
    private var hidToastShown = false
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
    private var overlayItems by mutableStateOf(listOf<OverlayItem>())
    private var overlayEdit by mutableStateOf(false)
    private var overlayMode by mutableStateOf(OverlayRules.MODE_AUTO)
    /** Perangkat fisik yang menempel, dibaca ulang oleh Compose. */
    private var hidState by mutableStateOf(HidMonitor.HidPresence())
    /** Kosong = host sanggup menerima gamepad. */
    private var hostGamepadReason by mutableStateOf("")
    private var gamepadWarned = false
    private var capturedMouse: CapturedMouseView? = null
    private var connectState by mutableStateOf(ConnectState(Phase.PAIRING, null, ""))
    private val ui = Handler(Looper.getMainLooper())
    private val statsTick = object : Runnable {
        override fun run() { metrics.tick(); ui.postDelayed(this, 1000) }
    }
    private val previewTick = object : Runnable {
        override fun run() {
            if (store.bool(P.SAVE_PREVIEW, true)) session.requestWallpaperPreview()
            ui.postDelayed(this, 30_000)
        }
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
        requestHighestRefreshRate()
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
        overlayItems = OverlayLayouts.fromJson(store.overlayJson)
        overlayMode = OverlayRules.normalizeMode(store.overlayMode)
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
        metrics.targetFps = sessionFps()
        metrics.auto = store.quality == 0
        val tunedTrackpadSpeed = store.trackpadSpeed.coerceAtLeast(1.8f)
        if (store.trackpadSpeed < tunedTrackpadSpeed) store.trackpadSpeed = tunedTrackpadSpeed
        val cfg = TrackpadConfig(
            speed = tunedTrackpadSpeed,
            naturalScroll = store.naturalScroll,
            accel = listOf(0f, 0.35f, 0.8f)[store.int(P.ACCEL, 1)],
            scrollUnit = listOf(32, 52, 82)[store.int(P.SCROLL_SPEED, 1)],
            twoFingerTap = store.bool(P.TWO_FINGER_RIGHT, true),
            swipe3Px = if (store.bool(P.SWIPE3, true)) 90f else Float.MAX_VALUE,
        )
        val haptic = store.bool(P.SESSION_HAPTIC, true)
        touch = SessionTouch(cfg, session::send) {
            if (haptic) b.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        touch.directTouch = store.directTouch
        keys = SessionKeyboard(this, b.keyboardSink, session::send)
        hid = SessionHid(session::send).also {
            it.kbmMode = store.int(P.PAD_KBM, PadKbm.MODE_AUTO)
        }
        hidMonitor = HidMonitor(this) { p -> runOnUiThread { onHid(p) } }
        capturedMouse = CapturedMouseView(this, session::send).also {
            it.sensitivity = cfg.speed.coerceIn(0.2f, 5f)
            it.onEscape = { store.set(P.POINTER_CAPTURE, false); applyPointerCapture() }
            // Ukuran 1 px: ia hanya perlu bisa memegang fokus, bukan terlihat.
            addContentView(it, android.view.ViewGroup.LayoutParams(1, 1))
        }
        // Surface decode native juga harus tampil natural/aspect-fit, bukan stretch/crop.
        b.raw.setZOrderMediaOverlay(false)
        session.attach(b.video)
        applyDecodeMode(true)
        b.raw.setOnTouchListener { v, e -> touch.onTouch(v, e) }
        b.video.setOnTouchListener { v, e -> touch.onTouch(v, e) }
        connectState = ConnectState(Phase.PAIRING, null, hostId, attempt = intent.getIntExtra("attempt", 0))
        setupToolbar()
        setupPcKeys()
        setupOverlay()
        setupControlMapping()
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
        if (w <= 0 || h <= 0) return
        val parent = b.raw.parent as View
        if (parent.width == 0 || parent.height == 0) {
            b.raw.post { fitSurface(w, h) }
            return
        }
        val scale = minOf(parent.width.toFloat() / w, parent.height.toFloat() / h)
        val lp = b.raw.layoutParams as android.widget.FrameLayout.LayoutParams
        lp.width = (w * scale).roundToInt().coerceAtLeast(1)
        lp.height = (h * scale).roundToInt().coerceAtLeast(1)
        lp.gravity = android.view.Gravity.CENTER
        b.raw.layoutParams = lp
        b.raw.holder.setFixedSize(w, h)
    }

    private fun saveOverlayLayout() {
        store.overlayJson = OverlayLayouts.toJson(overlayItems)
    }

    private fun setOverlayEditMode(on: Boolean) {
        if (overlayEdit && !on) saveOverlayLayout()
        overlayEdit = on
        // Mengatur tata letak saat kontrol dimatikan tidak masuk akal:
        // tidak ada yang tergambar untuk dipindahkan.
        applyPointerCapture()
        if (on && overlayMode == OverlayRules.MODE_OFF) {
            overlayMode = OverlayRules.MODE_AUTO
            store.overlayMode = overlayMode
        }
        applyChrome()
        applyChrome()
    }

    private fun setupControlMapping() {
        (b.controlsLayer as? PassThroughComposeView)?.behind = b.video
        b.controlsLayer.setContent {
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
                    onEdit = { setOverlayEditMode(it) },
                    onSave = { saveOverlayLayout() },
                    mode = overlayMode,
                    hidKeyboard = hidState.keyboard,
                    hidMouse = hidState.mouse,
                    hidGamepad = hidState.gamepad,
                )
            }
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
        pcKeysOpen = on && !hidPresent.keyboard
        applyPointerCapture()
        b.pcKeys.visibility = if (pcKeysOpen && !presenting) View.VISIBLE else View.GONE
        applyChrome()
    }

    private fun onHid(p: HidMonitor.HidPresence) {
        hidPresent = p
        hidState = p
        applyPointerCapture()
        if (::hid.isInitialized) hid.padPresent = p.gamepad
        if (p.keyboard && pcKeysOpen) setPcKeys(false)
        applyChrome()
        val pesan = OverlayRules.detectionMessage(overlayMode, p.keyboard, p.mouse, p.gamepad)
        if (pesan.isNotEmpty() && connected && !hidToastShown) {
            hidToastShown = true
            Toast.makeText(this, pesan.tr(store.lang), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Menyalakan atau melepas tangkapan pointer sesuai keadaan sekarang.
     * Dipanggil dari setiap tempat yang bisa mengubah jawabannya; aturannya
     * sendiri ada di [CaptureRules] dan diuji di JVM.
     */
    private fun applyPointerCapture() {
        val v = capturedMouse ?: return
        val mau = CaptureRules.wanted(
            mousePresent = hidPresent.mouse,
            connected = connected,
            enabled = store.bool(P.POINTER_CAPTURE, true),
            imeOpen = b.keyboardSink.hasFocus() || pcKeysOpen,
            editing = overlayEdit,
            presenting = presenting,
        )
        v.wantCapture = mau
        if (mau) v.capture() else v.release()
    }

    /** Berputar: otomatis → selalu tampil → mati → otomatis. */
    private fun cycleOverlayMode() {
        overlayMode = OverlayRules.nextMode(overlayMode)
        store.overlayMode = overlayMode
        applyChrome()
        Toast.makeText(this, OverlayRules.modeLabel(overlayMode).tr(store.lang), Toast.LENGTH_SHORT).show()
    }

    /**
     * Lapisan kontrol dimatikan hanya kalau benar-benar tidak ada tombol yang
     * tampil. Dulu satu perangkat fisik apa pun — keyboard Bluetooth sekalipun —
     * menyembunyikan seluruh lapisan, termasuk gamepad virtual yang tidak
     * punya padanan fisik di mana pun, dan tidak ada cara mengembalikannya.
     * Sekarang penyaringan dilakukan per tombol di [ControlOverlay]; di sini
     * tinggal memutuskan apakah seluruh View-nya masih ada gunanya.
     */
    private fun applyChrome() {
        val adaIsi = OverlayRules.layerVisible(
            overlayItems.map { it.kind.name },
            overlayMode,
            hidPresent.keyboard,
            hidPresent.mouse,
            hidPresent.gamepad,
            overlayEdit,
        )
        val show = connected && !presenting && !pcKeysOpen && adaIsi
        b.controlsLayer.visibility = if (show) View.VISIBLE else View.GONE
    }

    /** Display aktif; `Activity.getDisplay()` baru ada di API 30. */
    @Suppress("DEPRECATION")
    private fun activeDisplay(): android.view.Display? =
        if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay

    /**
     * Refresh rate panel saat ini. Dipakai untuk memutuskan apakah 120/144 fps
     * pantas ditawarkan — meminta laju di atas kemampuan panel hanya membakar
     * bitrate dan baterai tanpa satu frame tambahan yang terlihat.
     */
    private fun panelRefreshHz(): Float = activeDisplay()?.refreshRate ?: 0f

    /**
     * Minta mode layar tercepat selama sesi, pada resolusi yang sama persis.
     * Tanpa ini panel 120 Hz di banyak HP tetap berjalan 60 Hz karena aplikasi
     * tidak pernah memintanya — 120 fps dari host akan dibuang separuhnya di
     * tahap tampil. Mode dengan resolusi berbeda sengaja tidak dipilih supaya
     * sistem tidak mengubah ukuran layar demi mengejar Hz.
     */
    private fun requestHighestRefreshRate() {
        val d = activeDisplay() ?: return
        val current = d.mode ?: return
        val best = d.supportedModes
            ?.filter {
                it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
            }
            ?.maxByOrNull { it.refreshRate } ?: return
        if (best.refreshRate <= current.refreshRate + 0.1f) return
        window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
    }

    /** Preferensi fps tersimpan, dijatuhkan ke yang masih masuk akal di panel ini. */
    private fun sessionFps(): Int {
        val fps = FpsOptions.clampToDisplay(store.targetFps, panelRefreshHz())
        if (fps != store.targetFps) store.targetFps = fps
        return fps
    }

    private fun setupToolbar() {
        val autohide = listOf(0L, 5000L, 10000L, 20000L)[store.int(P.RAIL_AUTOHIDE, 0)]
        val prefs = SessionPrefs(store.quality, sessionFps(), store.directTouch, store.trackpadSpeed,
            store.naturalScroll, store.showStats, autohide, store.bool(P.POINTER_CAPTURE, true),
            store.int(P.PAD_KBM, PadKbm.MODE_AUTO), PadKbm.modeLabel(store.int(P.PAD_KBM, PadKbm.MODE_AUTO)),
            FpsOptions.forDisplay(panelRefreshHz()))
        b.toolbar.setContent {
            CompositionLocalProvider(LocalLang provides store.lang) {
                SessionToolbar(
                    SessionActions(
                        keyboard = { keys.toggle() },
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
                        overlayEdit = { setOverlayEditMode(!overlayEdit) },
                        overlayMode = { cycleOverlayMode() },
                        pointerCapture = { on -> store.set(P.POINTER_CAPTURE, on); applyPointerCapture() },
                        padKbm = {
                            val next = PadKbm.nextMode(store.int(P.PAD_KBM, PadKbm.MODE_AUTO))
                            store.set(P.PAD_KBM, next)
                            hid.kbmMode = next
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
        applyPointerCapture()
        b.toolbar.visibility = if (on) View.GONE else View.VISIBLE
        if (on) setPcKeys(false)
        applyChrome()
        if (on) b.status.visibility = View.GONE
        if (on) Toast.makeText(this, "Mode presentasi. Tekan Kembali untuk menampilkan kontrol lagi.".tr(store.lang), Toast.LENGTH_LONG).show()
    }

    private fun micHint() = when (micReason) {
        "no-endpoint" -> "Driver mic virtual ada, tapi perangkat CABLE Input nonaktif di PC. Aktifkan di Pengaturan Suara Windows, lalu sambung ulang."
        "no-driver" -> "PC belum punya driver mic virtual. Di PC: Start Menu > XyDesk > Install Virtual Audio Driver (Run as administrator), lalu sambung ulang."
        else -> "PC belum punya input mic virtual. Perbarui XyDesk Host ke versi terbaru."
    }

    /** Orientasi, posisi HUD, dan ukuran HUD dari Pengaturan. */
    private fun applyLayoutPrefs() {
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        b.status.textSize = listOf(10f, 12f, 14.5f)[store.int(P.HUD_SIZE, 1)]
        (b.status.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let { lp ->
            val right = store.bool(P.HUD_RIGHT, false)
            lp.gravity = android.view.Gravity.TOP or (if (right) android.view.Gravity.END else android.view.Gravity.START)
            if (right) lp.marginEnd = (resources.displayMetrics.density * 72).toInt()
            b.status.layoutParams = lp
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

    /** Catat/perbarui sesi ini di riwayat; dipanggil ulang tiap ada info baru. */
    private fun record() = store.record(
        SessionRecord(
            hostId, hostName, startedAt, (System.currentTimeMillis() - startedAt) / 1000, outcome, specs,
            if (::metrics.isInitialized) metrics.trace.encode() else "",
        ),
    )

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!connected || event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        if (::hid.isInitialized && hid.gamepadKey(event)) return true
        if (KeyMap.isPhysical(event) && keys.physical(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (connected && ::hid.isInitialized) {
            val surface = if (lowLatency) b.raw else b.video
            if (hid.motion(event, surface)) return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (connected && ::hid.isInitialized && event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            val surface = if (lowLatency) b.raw else b.video
            if (hid.motion(event, surface)) return true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onHostWallpaper(jpeg: ByteArray) {
        if (!store.bool(P.SAVE_PREVIEW, true)) return
        Thread {
            Previews.saveJpeg(applicationContext, hostId, jpeg)
            ui.post { record() }
        }.start()
    }

    override fun onMicInput(available: Boolean, reason: String) { micInput = available; micReason = reason }

    /**
     * Peringatan sekali per sesi, dan hanya kalau pengguna benar-benar punya
     * tombol gamepad di layarnya: memberi tahu orang yang tidak memakai
     * gamepad bahwa gamepad tidak tersedia hanyalah kebisingan.
     */
    override fun onHostGamepad(available: Boolean, reason: String) = runOnUiThread {
        hostGamepadReason = if (available) "" else reason
        if (::hid.isInitialized) hid.hostGamepadAvailable = available
        val punyaTombolGamepad = overlayItems.any {
            OverlayRules.family(it.kind.name) == InputFamily.GAMEPAD
        }
        if (!available && reason.isNotEmpty() && punyaTombolGamepad && !gamepadWarned) {
            gamepadWarned = true
            Toast.makeText(this, "Gamepad tidak aktif di PC: $reason".tr(store.lang), Toast.LENGTH_LONG).show()
        }
    }

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
        if (!connected && ::hid.isInitialized) hid.releaseAll()
        if (phase == Phase.REJECTED) store.setHostPin(hostId, null)
        connectState = connectState.copy(phase = phase, message = message)
        b.overlay.visibility = if (connected) View.GONE else View.VISIBLE
        applyChrome()
        applyPointerCapture()
        b.status.visibility = View.GONE
        ui.removeCallbacks(statsTick); ui.removeCallbacks(previewTick)
        if (connected) {
            everConnected = true
            reconnect.reset()
            b.controlsLayer.bringToFront()
            b.toolbar.bringToFront()
            startService(Intent(this, SessionKeep::class.java))
            ui.postDelayed(statsTick, 1000)
            if (store.bool(P.SAVE_PREVIEW, true)) {
                listOf(1200L, 5000L, 12_000L).forEach { delay ->
                    ui.postDelayed({ session.requestWallpaperPreview() }, delay)
                }
            }
            ui.postDelayed(previewTick, 30_000)
            session.send(StreamXy.fps(metrics.targetFps))
            if (store.quality == 0) metrics.auto = true else session.send(StreamXy.quality(store.quality))
            applyStartPrefs()
        } else {
            stopService(Intent(this, SessionKeep::class.java))
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

    override fun onResume() {
        super.onResume()
        hidMonitor?.start()
    }

    override fun onStop() {
        super.onStop()
        // Aplikasi ke latar: tombol yang masih ditahan pemeta pad harus
        // dilepas, kalau tidak PC menerima W tertekan selamanya.
        if (::hid.isInitialized) hid.releaseAll()
    }

    override fun onPause() {
        if (overlayEdit) saveOverlayLayout()
        capturedMouse?.release()
        hidMonitor?.stop()
        super.onPause()
    }

    override fun onDestroy() {
        if (overlayEdit) saveOverlayLayout()
        capturedMouse?.release()
        hidMonitor?.stop()
        clipboard?.removePrimaryClipChangedListener(clipListener)
        if (outcome == "berjalan") outcome = "ok"
        record()
        ui.removeCallbacksAndMessages(null)
        touch.release()
        metrics.close()
        stopService(Intent(this, SessionKeep::class.java))
        session.stop()
        session.release()
        super.onDestroy()
    }
}
