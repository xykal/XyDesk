package id.xyverse.xydesk.ui

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import id.xyverse.xydesk.core.SessionRecord
import id.xyverse.xydesk.core.Store
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import id.xyverse.xydesk.databinding.ActivitySessionBinding
import id.xyverse.xydesk.core.LatencyRing
import id.xyverse.xydesk.core.StreamXy
import id.xyverse.xydesk.rtc.Phase
import id.xyverse.xydesk.rtc.RtcListener
import id.xyverse.xydesk.rtc.RtcSession

/**
 * Layar sesi: video penuh + mode trackpad.
 * Satu jari geser = gerak kursor relatif, ketuk = klik kiri,
 * ketuk dua jari = klik kanan, geser dua jari = scroll.
 */
class SessionActivity : ComponentActivity(), RtcListener {
    private lateinit var b: ActivitySessionBinding
    private lateinit var session: RtcSession
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false
    private var downAt = 0L
    private var twoFinger = false
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
    private var startedAt = 0L
    private var outcome = "ok"
    private val store by lazy { Store(applicationContext) }
    private var nativeFrames = 0L
    private var lastNativeFrames = 0L

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySessionBinding.inflate(layoutInflater)
        setContentView(b.root)
        hideSystemBars()

        lowLatency = intent.getBooleanExtra("lowLatency", false)
        session = RtcSession(
            context = applicationContext,
            jwt = intent.getStringExtra("jwt").orEmpty(),
            hostId = intent.getStringExtra("host").orEmpty(),
            pin = intent.getStringExtra("pin").orEmpty(),
            selfName = "${Build.MANUFACTURER} ${Build.MODEL}",
            listener = this,
            lowLatencySurface = if (lowLatency) ({ b.raw.holder.surface.takeIf { it.isValid } }) else null,
            onNativeDecode = { ms -> decodeRing.push(ms); nativeFrames++ },
            onNativeSize = { w, h -> runOnUiThread { fitSurface(w, h) } },
        )
        if (lowLatency) {
            b.raw.visibility = View.VISIBLE
            b.video.visibility = View.GONE
        } else {
            session.attach(b.video)
        }
        val touchTarget: View = if (lowLatency) b.raw else b.video
        touchTarget.setOnTouchListener { _, e -> onTouch(e) }
        setupToolbar()
        setupKeyboard()
        startedAt = System.currentTimeMillis()
        session.start()
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

    private fun onTouch(e: MotionEvent): Boolean {
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

    private fun setupToolbar() {
        b.toolbar.setContent {
            SessionToolbar(
                SessionActions(
                    keyboard = { toggleKeyboard() },
                    quality = { session.send(StreamXy.quality(it)) },
                    toggleStats = { showStats = !showStats; if (!showStats) b.status.visibility = View.GONE },
                    disconnect = { outcome = "putus"; finish() },
                ),
            )
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
            session.send(StreamXy.key(vk, true)); session.send(StreamXy.key(vk, false))
            true
        }
        b.keyboardSink.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > before && s != null) session.send(StreamXy.text(s.substring(start + before, start + count)))
                else if (before > count) repeat(before - count) {
                    session.send(StreamXy.key(0x08, true)); session.send(StreamXy.key(0x08, false))
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

    override fun onHostName(name: String) {
        hostName = name
    }

    override fun onPhase(phase: Phase, message: String?) = runOnUiThread {
        val text = when (phase) {
            Phase.PAIRING -> "Menghubungi host…"
            Phase.NEGOTIATING -> "Pairing diterima, menyiapkan video…"
            Phase.CONNECTED -> ""
            else -> message ?: phase.name
        }
        connected = phase == Phase.CONNECTED
        if (connected) b.video.postDelayed(statsTick, 1000) else b.video.removeCallbacks(statsTick)
        b.status.text = text
        b.status.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        if (phase in setOf(Phase.REJECTED, Phase.PEER_OFFLINE, Phase.BUSY, Phase.ERROR, Phase.ENDED)) {
            if (outcome == "ok") outcome = phase.name.lowercase()
            b.status.setOnClickListener { finish() }
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
        store.record(
            SessionRecord(
                host = intent.getStringExtra("host").orEmpty(),
                name = hostName,
                startedAt = startedAt,
                durationSec = (System.currentTimeMillis() - startedAt) / 1000,
                outcome = outcome,
            ),
        )
        b.video.removeCallbacks(statsTick)
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
