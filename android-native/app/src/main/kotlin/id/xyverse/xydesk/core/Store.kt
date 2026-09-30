package id.xyverse.xydesk.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Penyimpanan sesi terenkripsi (JWT, email, host terakhir). */
class Store(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "xydesk.secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var jwt: String?
        get() = prefs.getString("jwt", null)
        set(v) = prefs.edit().putString("jwt", v).apply()

    var email: String?
        get() = prefs.getString("email", null)
        set(v) = prefs.edit().putString("email", v).apply()

    var lastHost: String
        get() = prefs.getString("lastHost", "").orEmpty()
        set(v) = prefs.edit().putString("lastHost", v).apply()

    var lowLatency: Boolean
        get() = prefs.getBoolean("lowLatency", true)
        set(v) = prefs.edit().putBoolean("lowLatency", v).apply()

    fun clear() = prefs.edit().clear().apply()
}
