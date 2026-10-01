package id.xyverse.xydesk.core

import id.xyverse.xydesk.net.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

data class NewsPost(
    val slug: String, val title: String, val excerpt: String, val content: String, val cover: String,
    val category: String, val author: String, val createdAt: String, val likeCount: Int, val commentCount: Int,
)

data class NewsComment(val id: Long, val author: String, val content: String, val parentId: Long?, val createdAt: String, val official: Boolean)

data class NewsDetail(val post: NewsPost, val comments: List<NewsComment>, val liked: Boolean)

/** Klien berita yang sama dengan web: news.xydesk.my.id (D1 Worker). Tanpa akun; identitas = sidik jari perangkat. */
object News {
    const val BASE = "https://news.xydesk.my.id"
    val categories = listOf("semua", "rilis", "teknik", "umum")
    private val json = "application/json".toMediaType()

    const val ADMIN_EMAIL = "xycdigital@gmail.com"
    const val ADMIN_NAME = "Haekal Saputra"

    fun shareUrl(slug: String) = "$BASE/n/$slug"

    /** Avatar DiceBear dari nama (sama dengan web), format PNG supaya bisa didekode Android. */
    fun avatarUrl(author: String) =
        "https://api.dicebear.com/9.x/adventurer/png?size=96&seed=" + java.net.URLEncoder.encode(author, "UTF-8") + "&backgroundColor=ede9fe,fde68a,bbf7d0,bae6fd"

    suspend fun list(category: String = "semua", limit: Int = 30): List<NewsPost> {
        val q = if (category == "semua") "" else "&category=$category"
        return parseList(get("$BASE/api/news?limit=$limit$q"))
    }

    fun parseList(text: String): List<NewsPost> {
        val arr = JSONObject(text).optJSONArray("posts") ?: JSONArray()
        return (0 until arr.length()).map { post(arr.getJSONObject(it)) }
    }

    suspend fun detail(slug: String, fp: String): NewsDetail {
        val o = JSONObject(get("$BASE/api/news/$slug?fp=$fp"))
        val arr = o.optJSONArray("comments") ?: JSONArray()
        return NewsDetail(post(o.getJSONObject("post")), (0 until arr.length()).map { comment(arr.getJSONObject(it)) }, o.optBoolean("liked"))
    }

    suspend fun like(slug: String, fp: String): Pair<Boolean, Int> {
        val o = JSONObject(post("$BASE/api/news/$slug/like", JSONObject().put("fp", fp)))
        return o.optBoolean("liked") to o.optInt("likeCount")
    }

    /** `adminToken` = Google ID token founder; badge resmi tetap diputuskan server. */
    suspend fun comment(slug: String, fp: String, author: String, content: String, parentId: Long?, adminToken: String? = null): NewsComment {
        val body = JSONObject().put("fp", fp).put("author", author).put("content", content)
        if (parentId != null) body.put("parentId", parentId)
        val headers = if (adminToken != null) mapOf("x-admin-google-token" to adminToken) else emptyMap()
        return comment(JSONObject(post("$BASE/api/news/$slug/comments", body, headers)).getJSONObject("comment"))
    }

    /** Nama tampilan deterministik dari sidik jari — algoritma sama dengan web agar identitas konsisten. */
    fun displayName(fp: String): String {
        var h = 0L
        for (c in fp) h = (h * 31 + c.code) and 0xFFFFFFFFL
        return first[(h % first.size).toInt()] + " " + last[((h / first.size) % last.size).toInt()]
    }

    fun newFingerprint(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        Api.http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) error(JSONObject(text.ifBlank { "{}" }).optString("error").ifBlank { "HTTP ${res.code}" })
            text
        }
    }

    private suspend fun post(url: String, body: JSONObject, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).post(body.toString().toRequestBody(json)).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        Api.http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) error(JSONObject(text.ifBlank { "{}" }).optString("error").ifBlank { "HTTP ${res.code}" })
            text
        }
    }

    private fun post(o: JSONObject) = NewsPost(
        o.optString("slug"), o.optString("title"), o.optString("excerpt"), o.optString("content"), o.optString("cover"),
        o.optString("category"), o.optString("author"), o.optString("createdAt"), o.optInt("likeCount"), o.optInt("commentCount"),
    )

    private fun comment(o: JSONObject) = NewsComment(
        o.optLong("id"), o.optString("author"), o.optString("content"),
        if (o.isNull("parentId")) null else o.optLong("parentId"), o.optString("createdAt"), o.optBoolean("official"),
    )

    private val first = listOf(
        "Raka", "Sinta", "Bima", "Dewi", "Aldi", "Nadia", "Fajar", "Laras", "Galih", "Ayu", "Reza", "Putri", "Dimas", "Ratna", "Yoga", "Salsa",
        "Ilham", "Maya", "Rio", "Tania", "Bagus", "Intan", "Eka", "Wulan", "Arif", "Citra", "Damar", "Nirmala", "Panji", "Kirana", "Satria", "Anggi",
    )
    private val last = listOf(
        "Saputra", "Pratama", "Lestari", "Wijaya", "Ramadhan", "Maharani", "Nugroho", "Anggraini", "Santoso", "Utami", "Firmansyah", "Puspita",
        "Hidayat", "Safitri", "Kurniawan", "Melati", "Gunawan", "Andini", "Prasetyo", "Rahayu", "Mahendra", "Paramita", "Wibowo", "Larasati",
    )
}
