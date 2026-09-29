package id.xyverse.xydesk.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import id.xyverse.xydesk.databinding.ActivityConnectBinding
import id.xyverse.xydesk.net.Api
import kotlinx.coroutines.launch

/** Login OTP email + form konek. JWT disimpan di SharedPreferences. */
class ConnectActivity : AppCompatActivity() {
    private lateinit var b: ActivityConnectBinding
    private val prefs by lazy { getSharedPreferences("xydesk", MODE_PRIVATE) }
    private var otpSent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityConnectBinding.inflate(layoutInflater)
        setContentView(b.root)
        render()

        b.loginBtn.setOnClickListener { if (otpSent) verify() else requestOtp() }
        b.logoutBtn.setOnClickListener { prefs.edit().clear().apply(); otpSent = false; render() }
        b.connectBtn.setOnClickListener { connect() }
    }

    private fun render() {
        val jwt = prefs.getString("jwt", null)
        val email = prefs.getString("email", null)
        b.loginBox.visibility = if (jwt == null) View.VISIBLE else View.GONE
        b.connectBox.visibility = if (jwt == null) View.GONE else View.VISIBLE
        b.account.text = if (jwt == null) "Masuk dulu untuk pairing" else "Masuk sebagai $email"
        b.hostId.setText(prefs.getString("lastHost", ""))
        b.status.text = ""
    }

    private fun requestOtp() {
        val email = b.email.text.toString().trim()
        if (!email.contains('@')) return status("Email tidak valid.")
        busy(true)
        lifecycleScope.launch {
            runCatching { Api.requestOtp(email) }
                .onSuccess {
                    otpSent = true
                    b.otp.visibility = View.VISIBLE
                    b.loginBtn.text = "Masuk"
                    status("Kode dikirim ke $email")
                }
                .onFailure { status("Gagal kirim kode: ${it.message}") }
            busy(false)
        }
    }

    private fun verify() {
        val email = b.email.text.toString().trim()
        val otp = b.otp.text.toString().trim()
        busy(true)
        lifecycleScope.launch {
            runCatching { Api.verifyOtp(email, otp) }
                .onSuccess { jwt ->
                    prefs.edit().putString("jwt", jwt).putString("email", email).apply()
                    render()
                }
                .onFailure { status("Kode salah atau kedaluwarsa: ${it.message}") }
            busy(false)
        }
    }

    private fun connect() {
        val host = b.hostId.text.toString().trim()
        val pin = b.pin.text.toString()
        if (host.replace(" ", "").length < 6) return status("ID perangkat belum lengkap.")
        prefs.edit().putString("lastHost", host).apply()
        startActivity(
            Intent(this, SessionActivity::class.java)
                .putExtra("jwt", prefs.getString("jwt", ""))
                .putExtra("host", host)
                .putExtra("pin", pin),
        )
    }

    private fun status(text: String) {
        b.status.text = text
    }

    private fun busy(on: Boolean) {
        b.loginBtn.isEnabled = !on
        b.connectBtn.isEnabled = !on
    }
}
