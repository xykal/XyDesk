package id.xyverse.xydesk.core

import android.content.Context
import id.xyverse.xydesk.R
import id.xyverse.xydesk.net.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

data class NewsItem(val id: String, val date: String, val tag: String, val title: String, val body: String, val url: String)

data class NewsFeed(val latest: String, val items: List<NewsItem>, val fromNetwork: Boolean)

/** Berita produk: diambil dari repo publik, jatuh ke salinan bawaan bila offline. */
object News {
    private const val URL = "https://raw.githubusercontent.com/xykal/XyDesk/main/docs/news.json"

    fun bundled(ctx: Context): NewsFeed =
        parse(ctx.resources.openRawResource(R.raw.news).bufferedReader().use { it.readText() }, false)

    suspend fun load(ctx: Context): NewsFeed = withContext(Dispatchers.IO) {
        runCatching {
            Api.http.newCall(Request.Builder().url(URL).header("Cache-Control", "no-cache").build()).execute().use { res ->
                if (!res.isSuccessful) error("HTTP ${res.code}")
                parse(res.body?.string().orEmpty(), true)
            }
        }.getOrElse { bundled(ctx) }
    }

    fun parse(text: String, network: Boolean): NewsFeed {
        val o = JSONObject(text)
        val arr = o.optJSONArray("items")
        val items = (0 until (arr?.length() ?: 0)).map { i ->
            val it = arr!!.getJSONObject(i)
            NewsItem(it.optString("id"), it.optString("date"), it.optString("tag"), it.optString("title"), it.optString("body"), it.optString("url"))
        }
        return NewsFeed(o.optString("latest"), items, network)
    }

    /** true bila versi terbaru di feed lebih tinggi dari yang terpasang. */
    fun newer(latest: String, installed: String): Boolean {
        fun parts(v: String) = v.substringBefore('+').split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(latest); val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
