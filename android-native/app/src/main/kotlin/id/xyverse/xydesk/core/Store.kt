package id.xyverse.xydesk.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

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

    var lang: Lang
        get() = if (prefs.getString("lang", "id") == "en") Lang.EN else Lang.ID
        set(v) = prefs.edit().putString("lang", if (v == Lang.EN) "en" else "id").apply()

    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(v) = prefs.edit().putBoolean("haptics", v).apply()

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) = prefs.edit().putBoolean("onboarded", v).apply()

    /** Riwayat sesi (terbaru dulu, maksimal 50). */
    var history: List<SessionRecord>
        get() = runCatching {
            val arr = JSONArray(prefs.getString("history", "[]"))
            (0 until arr.length()).map { SessionRecord.from(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        set(v) = prefs.edit().putString("history", JSONArray(v.take(50).map { it.json() }).toString()).apply()

    fun record(rec: SessionRecord) {
        history = listOf(rec) + history.filterNot { it.startedAt == rec.startedAt }
    }

    /** Host yang pernah tersambung, digabung per ID. */
    fun devices(): List<SessionRecord> = history.distinctBy { it.host }

    fun clear() = prefs.edit().clear().apply()
}

data class SessionRecord(
    val host: String,
    val name: String,
    val startedAt: Long,
    val durationSec: Long,
    val outcome: String,
) {
    fun json(): JSONObject = JSONObject()
        .put("host", host).put("name", name).put("startedAt", startedAt)
        .put("durationSec", durationSec).put("outcome", outcome)

    companion object {
        fun from(o: JSONObject) = SessionRecord(
            o.getString("host"), o.optString("name"), o.getLong("startedAt"),
            o.optLong("durationSec"), o.optString("outcome", "ok"),
        )
    }
}
