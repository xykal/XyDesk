package id.xyverse.xydesk.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import id.xyverse.xydesk.core.HostSpecs
import id.xyverse.xydesk.core.LocalLang
import id.xyverse.xydesk.core.Previews
import id.xyverse.xydesk.core.KeyMap
import id.xyverse.xydesk.core.LatencyRing
import id.xyverse.xydesk.core.SessionRecord
import id.xyverse.xydesk.core.Store
import id.xyverse.xydesk.core.StreamXy
import id.xyverse.xydesk.databinding.ActivitySessionBinding
import id.xyverse.xydesk.rtc.Phase
import id.xyverse.xydesk.rtc.RtcListener
import id.xyverse.xydesk.rtc.RtcSession

/**
 * Layar sesi: video penuh + mode trackpad / sentuh langsung + sinkronisasi clipboard dua arah.
 * Mode trackpad: satu jari geser = gerak kursor relatif, ketuk = klik kiri,
 * ketuk dua jari = klik kanan, geser dua jari = scroll.
 * Mode sentuh langsung: ketuk/geser langsung memetakan koordinat 0..1 ke host (`moveAbs`).
 */
class SessionActivity : ComponentActivity(), RtcListener {
    private lateinit var b: ActivitySessionBinding
    private lateinit var session: RtcSession
    private var clipboard: ClipboardManager? = null
    private var lastSyncedClipboard = ""
    private var clipboardSync = false
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!connected || !clipboardSync) return@OnPrimaryClipChangedListener
        val text = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (text.isNotEmpty() && text != lastSyncedClipboard) {
            lastSyncedClipboard = text
            session.send(StreamXy.clipboardSet(text))
        }
    }
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false
    private var downAt = 0L
    private var twoFinger = false
    private var directTouch = false
    private val decodeRing = LatencyRing()
    private val rttRing = LatencyRing()
    private var lastFrames = 0L
    private var lastDecodeSec = 0.0
    private val statsTick = object : Runnable {
        override fun run() {
            session.stats { fps, frames, decodeSec, rttMs, relay ->
                val shownFps = if (lowLatency) (nativeFrames - lastNativeFrames).toDouble() else fps
                lastNativeFrames = nativeFrames
                val d = frames - lastFrames
                if (!lowLatency && d > 0) decodeRing.push(((decodeSec - lastDecodeSec) / d * 1000).toFloat())
                lastFrames = frames; lastDecodeSec = decodeSec
                if (rttMs > 0) rttRing.push(rttMs.toFloat())
                val text = "%s · %.0f fps · dekode %.1f ms (p95 %.1f) · RTT %.0f ms · %s".format(
                    if (lowLatency) "LL" else "std", shownFps, decodeRing.p(50f), decodeRing.p(95f), rttRing.p(50f),
                    if (relay) "relay" else "langsung",
                )
                runOnUiThread { if (connected && showStats) { b.status.text = text; b.status.visibility = View.VISIBLE } }
            }
            b.video.postDelayed(this, 1000)
        }
    }
    private var connected = false
    private var lowLatency = false
    private var showStats = true
    private var hostName = ""
    private var specs = HostSpecs()
    private var connectState by mutableStateOf(ConnectState(Phase.PAIRING, null, ""))
    private val previewTick = object : Runnable {
        override fun run() {
            capturePreview()
            b.video.postDelayed(this, 10_000)
        }
    }
    private var startedAt = 0L
    private var outcome = "ok"
    private val store by lazy { Store(applicationContext) }
    private var nativeFrames = 0L
    private var lastNativeFrames = 0L

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        getSystemService(AudioManager::class.java)?.mode = AudioManager.MODE_NORMAL
        clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.addPrimaryClipChangedListener(clipListener)

        b = ActivitySessionBinding.inflate(layoutInflater)
        setContentView(b.root)
        hideSystemBars()

        session = RtcSession(
            context = applicationContext,
            jwt = intent.getStringExtra("jwt").orEmpty(),
            hostId = intent.getStringExtra("host").orEmpty(),
            pin = intent.getStringExtra("pin").orEmpty(),
            selfName = "${Build.MANUFACTURER} ${Build.MODEL}",
            listener = this,
            lowLatencySurface = { b.raw.holder.surface.takeIf { it.isValid } },
            onNativeDecode = { ms -> decodeRing.push(ms); nativeFrames++ },
            onNativeSize = { w, h -> runOnUiThread { fitSurface(w, h) } },
            onDecodeMode = { ll -> runOnUiThread { applyDecodeMode(ll) } },
        )
        session.attach(b.video)
        applyDecodeMode(true)
        b.raw.setOnTouchListener { v, e -> onTouch(v, e) }
        b.video.setOnTouchListener { v, e -> onTouch(v, e) }
        connectState = ConnectState(Phase.PAIRING, null, intent.getStringExtra("host").orEmpty())
        setupToolbar()
        setupOverlay()
        setupKeyboard()
        startedAt = System.currentTimeMillis()
        session.start()
    }

    /** Jalur decode dipilih otomatis oleh decoder; UI hanya mengikuti. */
    private fun applyDecodeMode(ll: Boolean) {
        lowLatency = ll
        b.raw.visibility = if (ll) View.VISIBLE else View.GONE
        b.video.visibility = if (ll) View.GONE else View.VISIBLE
    }

    /** Letterbox: SurfaceView mengikuti rasio frame host (mis. 720p), bukan layar HP. */
    private fun fitSurface(w: Int, h: Int) {
        val parent = b.raw.parent as View
        val pw = parent.width
        val ph = parent.height
        if (pw == 0 || ph == 0) return
        val scale = minOf(pw.toFloat() / w, ph.toFloat() / h)
        val lp = b.raw.layoutParams as android.widget.FrameLayout.LayoutParams
        lp.width = (w * scale).toInt()
        lp.height = (h * scale).toInt()
        lp.gravity = android.view.Gravity.CENTER
        b.raw.layoutParams = lp
        b.raw.holder.setFixedSize(w, h)
        b.status.text = "${w}x$h"
    }

    private fun onTouch(view: View, e: MotionEvent): Boolean {
        if (directTouch && e.pointerCount == 1) {
            val nx = (e.x / view.width.coerceAtLeast(1)).coerceIn(0f, 1f)
            val ny = (e.y / view.height.coerceAtLeast(1)).coerceIn(0f, 1f)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downAt = System.currentTimeMillis()
                    moved = false
                    lastX = e.x; lastY = e.y
                    session.send(StreamXy.moveAbs(nx, ny))
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.x - lastX; val dy = e.y - lastY
                    if (dx * dx + dy * dy > 36f) moved = true
                    session.send(StreamXy.moveAbs(nx, ny))
                }
                MotionEvent.ACTION_UP -> {
                    session.send(StreamXy.moveAbs(nx, ny))
                    if (!moved) click(if (System.currentTimeMillis() - downAt > 420) 1 else 0)
                }
            }
            return true
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x; lastY = e.y; moved = false; twoFinger = false
                downAt = System.currentTimeMillis()
            }
            MotionEvent.ACTION_POINTER_DOWN -> twoFinger = true
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.x - lastX) * SPEED
                val dy = (e.y - lastY) * SPEED
                if (dx * dx + dy * dy > 1f) moved = true
                if (twoFinger) session.send(StreamXy.scroll(0, (-dy * 2).toInt()))
                else session.send(StreamXy.moveRel(dx.toInt(), dy.toInt()))
                lastX = e.x; lastY = e.y
            }
            MotionEvent.ACTION_UP -> {
                val quick = System.currentTimeMillis() - downAt < 250
                if (!moved && quick) click(if (twoFinger) 1 else 0)
            }
        }
        return true
    }

    private fun click(button: Int) {
        session.send(StreamXy.button(button, true))
        b.video.postDelayed({ session.send(StreamXy.button(button, false)) }, 40)
    }

    private fun sendKeyTap(vk: Int) {
        session.send(StreamXy.key(vk, true))
        session.send(StreamXy.key(vk, false))
    }

    private fun sendChord(vararg vks: Int) {
        vks.forEach { session.send(StreamXy.key(it, true)) }
        vks.reversed().forEach { session.send(StreamXy.key(it, false)) }
    }

    private fun setupOverlay() {
        b.overlay.setContent {
            CompositionLocalProvider(LocalLang provides store.lang) {
                ConnectingOverlay(
                    connectState,
                    onRetry = { outcome = "retry"; startActivity(intent); finish() },
                    onBack = { if (outcome == "ok") outcome = "batal"; finish() },
                )
            }
        }
    }

    /** Salin frame yang sedang tampil ke bitmap kecil; jadi cuplikan kartu perangkat. */
    private fun capturePreview() {
        if (!connected) return
        val view: SurfaceView = if (lowLatency) b.raw else b.video
        if (view.width == 0 || view.height == 0 || !view.holder.surface.isValid) return
        val w = 480
        val h = (w * view.height / view.width).coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val host = intent.getStringExtra("host").orEmpty()
        runCatching {
            PixelCopy.request(view, bmp, { r ->
                if (r == PixelCopy.SUCCESS) Thread { Previews.save(applicationContext, host, bmp) }.start()
            }, Handler(Looper.getMainLooper()))
        }
    }

    private fun setupToolbar() {
        b.toolbar.setContent {
            CompositionLocalProvider(LocalLang provides store.lang) { SessionToolbar(
                SessionActions(
                    keyboard = { toggleKeyboard() },
                    quality = { session.send(StreamXy.quality(it)) },
                    resolution = { session.send(StreamXy.resolution(it)) },
                    display = { session.send(StreamXy.display(it)) },
                    touchMode = { directTouch = it },
                    audioMute = { session.setAudioMuted(it) },
                    clipboardSync = { on -> clipboardSync = on; session.clipboardSync = on },
                    sendQuickKey = { combo ->
                        when (combo) {
                            QuickKey.ESC -> sendKeyTap(0x1B)
                            QuickKey.TAB -> sendKeyTap(0x09)
                            QuickKey.WIN -> sendKeyTap(0x5B)
                            QuickKey.COPY -> sendChord(0xA2, 0x43)
                            QuickKey.PASTE -> sendChord(0xA2, 0x56)
                            QuickKey.CAD -> sendChord(0xA2, 0xA4, 0x2E)
                        }
                    },
                    toggleStats = { showStats = !showStats; if (!showStats) b.status.visibility = View.GONE },
                    disconnect = { outcome = "putus"; finish() },
                ),
            ) }
        }
    }

    /** EditText tak terlihat menampung IME; setiap perubahan teks dikirim sebagai Text/Key ke host. */
    private fun setupKeyboard() {
        b.keyboardSink.setOnKeyListener { _, code, ev ->
            if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            val vk = when (code) {
                KeyEvent.KEYCODE_DEL -> 0x08
                KeyEvent.KEYCODE_ENTER -> 0x0D
                KeyEvent.KEYCODE_TAB -> 0x09
                KeyEvent.KEYCODE_ESCAPE -> 0x1B
                else -> return@setOnKeyListener false
            }
            sendKeyTap(vk)
            true
        }
        b.keyboardSink.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > before && s != null) session.send(StreamXy.text(s.substring(start + before, start + count)))
                else if (before > count) repeat(before - count) {
                    sendKeyTap(0x08)
                }
            }
        })
    }

    private fun toggleKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java)
        b.keyboardSink.setText("")
        b.keyboardSink.requestFocus()
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    /** Keyboard fisik/Bluetooth: kirim VK langsung (tekan & lepas, termasuk modifier). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!connected || !KeyMap.isPhysical(event) || event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        val vk = KeyMap.vk(event.keyCode) ?: return super.dispatchKeyEvent(event)
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0 || vk !in 0xA0..0xA5) session.send(StreamXy.key(vk, true))
            KeyEvent.ACTION_UP -> session.send(StreamXy.key(vk, false))
        }
        return true
    }

    override fun onHostName(name: String) {
        hostName = name
    }

    override fun onHostSpecs(specs: HostSpecs) {
        this.specs = specs
    }

    override fun onRemoteClipboard(text: String) = runOnUiThread {
        if (text.isNotEmpty() && text != lastSyncedClipboard) {
            lastSyncedClipboard = text
            clipboard?.setPrimaryClip(ClipData.newPlainText("XyDesk", text))
        }
    }

    override fun onPhase(phase: Phase, message: String?) = runOnUiThread {
        connected = phase == Phase.CONNECTED
        connectState = connectState.copy(phase = phase, message = message)
        b.overlay.visibility = if (connected) View.GONE else View.VISIBLE
        b.status.visibility = View.GONE
        if (connected) {
            b.video.postDelayed(statsTick, 1000)
            b.video.postDelayed(previewTick, 3000)
        } else {
            b.video.removeCallbacks(statsTick)
            b.video.removeCallbacks(previewTick)
        }
        if (phase in setOf(Phase.REJECTED, Phase.PEER_OFFLINE, Phase.BUSY, Phase.ERROR, Phase.ENDED) && outcome == "ok") {
            outcome = phase.name.lowercase()
        }
    }

    private fun hideSystemBars() {
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
        store.record(
            SessionRecord(
                host = intent.getStringExtra("host").orEmpty(),
                name = hostName,
                startedAt = startedAt,
                durationSec = (System.currentTimeMillis() - startedAt) / 1000,
                outcome = outcome,
                specs = specs,
            ),
        )
        b.video.removeCallbacks(statsTick)
        b.video.removeCallbacks(previewTick)
        decodeRing.close()
        rttRing.close()
        session.stop()
        session.release()
        super.onDestroy()
    }

    companion object {
        private const val SPEED = 1.4f
    }
}
