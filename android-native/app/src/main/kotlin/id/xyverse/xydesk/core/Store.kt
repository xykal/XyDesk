package id.xyverse.xydesk.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    var trackpadSpeed: Float
        get() = prefs.getFloat("trackpadSpeed", 1.8f)
        set(v) = prefs.edit().putFloat("trackpadSpeed", v).apply()

    var naturalScroll: Boolean
        get() = prefs.getBoolean("naturalScroll", false)
        set(v) = prefs.edit().putBoolean("naturalScroll", v).apply()

    /** 0 = Auto (adaptif), 1..3 = preset tetap. */
    var quality: Int
        get() = prefs.getInt("quality", 0)
        set(v) = prefs.edit().putInt("quality", v).apply()

    var targetFps: Int
        get() = prefs.getInt("targetFps", 60)
        set(v) = prefs.edit().putInt("targetFps", v).apply()

    var showStats: Boolean
        get() = prefs.getBoolean("showStats", true)
        set(v) = prefs.edit().putBoolean("showStats", v).apply()
    var devicesGrid: Boolean
        get() = prefs.getBoolean("devicesGrid", false)
        set(v) = prefs.edit().putBoolean("devicesGrid", v).apply()
    /** Mode kontrol di layar: 0 otomatis, 1 selalu tampil, 2 mati. */
    var overlayMode: Int
        get() = prefs.getInt("overlayMode", 0)
        set(v) = prefs.edit().putInt("overlayMode", v).apply()
    var overlayJson: String
        get() = prefs.getString("overlayJson", null).orEmpty()
        set(v) = prefs.edit().putString("overlayJson", v).apply()
    fun int(key: String, def: Int) = prefs.getInt(key, def)
    fun bool(key: String, def: Boolean) = prefs.getBoolean(key, def)
    fun set(key: String, v: Int) = prefs.edit().putInt(key, v).apply()
    fun set(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()

    /** Pangkas riwayat lebih tua dari `historyDays` (0 = simpan semua). Dipanggil saat aplikasi dibuka. */
    fun pruneHistory() {
        val days = int(P.HISTORY_DAYS, 0)
        if (days <= 0) return
        val cut = System.currentTimeMillis() - days * 86_400_000L
        history = history.filter { it.startedAt >= cut || it.outcome == "berjalan" }
    }

    var hudItems: Set<String>
        get() = prefs.getStringSet("hudItems", null) ?: HUD_DEFAULT
        set(v) = prefs.edit().putStringSet("hudItems", v).apply()

    var directTouch: Boolean
        get() = prefs.getBoolean("directTouch", false)
        set(v) = prefs.edit().putBoolean("directTouch", v).apply()

    /** Password host tersimpan per ID (opt-in, terenkripsi bersama token). */
    var autoReconnect: Boolean
        get() = prefs.getBoolean("autoReconnect", true)
        set(v) = prefs.edit().putBoolean("autoReconnect", v).apply()

    var keepAwake: Boolean
        get() = prefs.getBoolean("keepAwake", true)
        set(v) = prefs.edit().putBoolean("keepAwake", v).apply()

    var confirmDisconnect: Boolean
        get() = prefs.getBoolean("confirmDisconnect", true)
        set(v) = prefs.edit().putBoolean("confirmDisconnect", v).apply()

    var newsNotify: Boolean
        get() = prefs.getBoolean("newsNotify", true)
        set(v) = prefs.edit().putBoolean("newsNotify", v).apply()

    /** 0 Koneksi, 1 Perangkat, 2 Berita. */
    var startTab: Int
        get() = prefs.getInt("startTab", 0)
        set(v) = prefs.edit().putInt("startTab", v).apply()

    var newsCache: String
        get() = prefs.getString("newsCache", "").orEmpty()
        set(v) = prefs.edit().putString("newsCache", v).apply()

    val newsFp: String
        get() = prefs.getString("newsFp", null) ?: News.newFingerprint().also { prefs.edit().putString("newsFp", it).apply() }

    var newsNotified: String
        get() = prefs.getString("newsNotified", "").orEmpty()
        set(v) = prefs.edit().putString("newsNotified", v).apply()

    var newsSeen: String
        get() = prefs.getString("newsSeen", "").orEmpty()
        set(v) = prefs.edit().putString("newsSeen", v).apply()

    fun alias(host: String): String = prefs.getString("alias." + host.filter(Char::isDigit), "").orEmpty()

    fun setAlias(host: String, name: String) {
        val k = "alias." + host.filter(Char::isDigit)
        if (name.isBlank()) prefs.edit().remove(k).apply() else prefs.edit().putString(k, name.trim()).apply()
        bump()
    }

    fun favorite(host: String): Boolean = prefs.getBoolean("fav." + host.filter(Char::isDigit), false)

    fun setFavorite(host: String, on: Boolean) {
        prefs.edit().putBoolean("fav." + host.filter(Char::isDigit), on).apply()
        bump()
    }

    fun forgetHost(host: String) {
        val d = host.filter(Char::isDigit)
        prefs.edit().remove("pin.$d").remove("alias.$d").remove("fav.$d").apply()
        history = history.filter { it.host.filter(Char::isDigit) != d }
        bump()
    }

    fun hostPin(host: String): String? = prefs.getString("pin." + host.filter(Char::isDigit), null)

    fun setHostPin(host: String, pin: String?) {
        val k = "pin." + host.filter(Char::isDigit)
        prefs.edit().apply { if (pin.isNullOrEmpty()) remove(k) else putString(k, pin) }.apply()
    }

    fun forgetAllPins() {
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("pin.") }.forEach { e.remove(it) }
        e.apply()
    }

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) = prefs.edit().putBoolean("onboarded", v).apply()

    /** Riwayat sesi (terbaru dulu, maksimal 50). */
    var history: List<SessionRecord>
        get() = runCatching {
            val arr = JSONArray(prefs.getString("history", "[]"))
            (0 until arr.length()).map { SessionRecord.from(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        set(v) {
            prefs.edit().putString("history", JSONArray(v.take(50).map { it.json() }).toString()).apply()
            historyFlow.value = v.take(50)
        }

    fun record(rec: SessionRecord) {
        val known = history.firstOrNull { it.host == rec.host && !it.specs.isEmpty }?.specs
        val merged = if (rec.specs.isEmpty && known != null) rec.copy(specs = known) else rec
        history = listOf(merged) + history.filterNot { it.startedAt == rec.startedAt }
    }

    fun clearHistory() {
        prefs.edit().remove("history").apply()
        historyFlow.value = emptyList()
    }

    /** Sumber tunggal riwayat untuk UI: berubah seketika saat sesi mencatat sesuatu. */
    fun observeHistory(): StateFlow<List<SessionRecord>> {
        if (!primed) { historyFlow.value = history; primed = true }
        return historyFlow
    }

    /** Naik tiap alias/favorit berubah supaya daftar host ikut segar. */
    fun observeHostMeta(): StateFlow<Int> = metaFlow

    private fun bump() { metaFlow.value++ }

    companion object {
        private val historyFlow = MutableStateFlow<List<SessionRecord>>(emptyList())
        private val metaFlow = MutableStateFlow(0)
        private var primed = false
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
    val specs: HostSpecs = HostSpecs(),
    val trace: String = "",
) {
    fun json(): JSONObject = JSONObject()
        .put("host", host).put("name", name).put("startedAt", startedAt)
        .put("durationSec", durationSec).put("outcome", outcome).put("specs", specs.json()).put("trace", trace)

    companion object {
        fun from(o: JSONObject) = SessionRecord(
            o.getString("host"), o.optString("name"), o.getLong("startedAt"),
            o.optLong("durationSec"), o.optString("outcome", "ok"),
            o.optJSONObject("specs")?.let(HostSpecs::from) ?: HostSpecs(), o.optString("trace"),
        )
    }
}

/** Kunci preferensi tambahan; nilai int memakai indeks pilihan di halaman Pengaturan. */
object P {
    const val ORIENTATION = "orientation"        // 0 otomatis, 1 lanskap, 2 potret
    const val RAIL_AUTOHIDE = "railAutohide"      // 0 mati, 1 = 5 d, 2 = 10 d, 3 = 20 d
    const val START_MUTED = "startMuted"
    const val START_MIC = "startMic"
    const val START_CLIP = "startClip"
    const val START_RES = "startRes"              // 0 auto, 1 720p, 2 1080p
    const val MAX_MBPS = "maxMbps"                // 0 auto, lalu 8/12/20/30
    const val ACCEL = "accel"                     // 0 mati, 1 sedang, 2 kuat
    const val SCROLL_SPEED = "scrollSpeed"        // 0 lambat, 1 normal, 2 cepat
    const val TWO_FINGER_RIGHT = "twoFingerRight"
    const val SWIPE3 = "swipe3"
    const val HUD_RIGHT = "hudRight"
    const val HUD_SIZE = "hudSize"                // 0 S, 1 M, 2 L
    const val SESSION_HAPTIC = "sessionHaptic"
    const val AUTO_LAST = "autoLast"
    const val FORCE_RELAY = "forceRelay"
    const val SAVE_PREVIEW = "savePreview"             // wallpaper host kecil di kartu perangkat
    const val HISTORY_DAYS = "historyDays"        // 0 semua, 7, 30, 90
    const val NEWS_HOURS = "newsHours"            // 3, 6, 12
    const val TEXT_SIZE = "textSize"              // 0 S, 1 M, 2 L
    const val REMEMBER_DEFAULT = "rememberDefault"
    const val POINTER_CAPTURE = "pointerCapture"  // tangkap mouse fisik (gerak relatif)
    const val SHOW_ID = "showId"
    val MBPS = listOf(0, 8, 12, 20, 30)
    val HISTORY = listOf(0, 7, 30, 90)
    val HOURS = listOf(3, 6, 12)
}

val HUD_ALL = listOf("FPS", "MS", "JALUR", "JARINGAN", "LOSS", "KUALITAS", "MIC")
val HUD_DEFAULT = setOf("FPS", "MS", "JALUR", "JARINGAN", "LOSS")

/** Spesifikasi host dari blok `meta.hardware`; string kosong = tidak terbaca. */
data class HostSpecs(
    val hostname: String = "",
    val os: String = "",
    val motherboard: String = "",
    val cpu: String = "",
    val gpu: String = "",
    val ram: String = "",
    val storage: String = "",
) {
    val isEmpty get() = listOf(hostname, os, motherboard, cpu, gpu, ram, storage).all { it.isEmpty() }

    fun json(): JSONObject = JSONObject()
        .put("hostname", hostname).put("os", os).put("motherboard", motherboard)
        .put("cpu", cpu).put("gpu", gpu).put("ram", ram).put("storage", storage)

    companion object {
        fun from(o: JSONObject) = HostSpecs(
            o.str("hostname"), o.str("os"), o.str("motherboard"),
            o.str("cpu"), o.str("gpu"), o.str("ram"), o.str("storage"),
        )

        private fun JSONObject.str(k: String) = if (isNull(k)) "" else optString(k).trim()
    }
}
