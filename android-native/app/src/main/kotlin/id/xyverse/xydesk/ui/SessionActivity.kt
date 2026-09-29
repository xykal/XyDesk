package id.xyverse.xydesk.ui

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import id.xyverse.xydesk.databinding.ActivitySessionBinding
import id.xyverse.xydesk.rtc.InputCodec
import id.xyverse.xydesk.rtc.Phase
import id.xyverse.xydesk.rtc.RtcListener
import id.xyverse.xydesk.rtc.RtcSession

/**
 * Layar sesi: video penuh + mode trackpad.
 * Satu jari geser = gerak kursor relatif, ketuk = klik kiri,
 * ketuk dua jari = klik kanan, geser dua jari = scroll.
 */
class SessionActivity : AppCompatActivity(), RtcListener {
    private lateinit var b: ActivitySessionBinding
    private lateinit var session: RtcSession
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false
    private var downAt = 0L
    private var twoFinger = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        )
        session.attach(b.video)
        session.start()
        b.video.setOnTouchListener { _, e -> onTouch(e) }
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
                if (twoFinger) session.send(InputCodec.scroll(0, (-dy * 2).toInt()))
                else session.send(InputCodec.moveRel(dx.toInt(), dy.toInt()))
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
        session.send(InputCodec.button(button, true))
        b.video.postDelayed({ session.send(InputCodec.button(button, false)) }, 40)
    }

    override fun onPhase(phase: Phase, message: String?) = runOnUiThread {
        val text = when (phase) {
            Phase.PAIRING -> "Menghubungi host…"
            Phase.NEGOTIATING -> "Pairing diterima, menyiapkan video…"
            Phase.CONNECTED -> ""
            else -> message ?: phase.name
        }
        b.status.text = text
        b.status.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        if (phase in setOf(Phase.REJECTED, Phase.PEER_OFFLINE, Phase.BUSY, Phase.ERROR, Phase.ENDED)) {
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
        session.stop()
        session.release()
        super.onDestroy()
    }

    companion object {
        private const val SPEED = 1.4f
    }
}
