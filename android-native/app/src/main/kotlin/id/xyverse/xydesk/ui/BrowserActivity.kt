package id.xyverse.xydesk.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.ui.kit.Icon
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyIcon
import id.xyverse.xydesk.ui.kit.XyText

/** Peramban dalam aplikasi: tautan legal, sosial, dan berita dibuka di sini, bukan di Chrome. */
class BrowserActivity : ComponentActivity() {
    private var web: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val start = intent.getStringExtra("url") ?: run { finish(); return }
        setContent {
            var title by remember { mutableStateOf("") }
            var url by remember { mutableStateOf(start) }
            var progress by remember { mutableStateOf(true) }
            Column(Modifier.fillMaxSize().background(Xy.bg).safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Round(Icon.CLOSE) { finish() }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        XyText(title.ifBlank { "Memuat…" }, Xy.body.copy(color = Xy.textHi), maxLines = 1)
                        XyText(url.removePrefix("https://").removePrefix("http://"), Xy.caption, maxLines = 1)
                    }
                    Spacer(Modifier.width(10.dp))
                    Round(Icon.CHEVRON, 180f) { web?.takeIf { it.canGoBack() }?.goBack() }
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (progress) Xy.accent else Xy.line))
                AndroidView(factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(v: WebView, u: String, favicon: Bitmap?) { progress = true; url = u }
                            override fun onPageFinished(v: WebView, u: String) { progress = false; title = v.title.orEmpty(); url = u }
                            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                                val s = r.url.scheme.orEmpty()
                                return if (s == "http" || s == "https") false else runCatching { startActivity(Intent(Intent.ACTION_VIEW, r.url)); true }.getOrDefault(true)
                            }
                        }
                        loadUrl(start)
                        web = this
                    }
                }, Modifier.fillMaxSize())
            }
        }
        onBackPressedDispatcher.addCallback(this) { if (web?.canGoBack() == true) web?.goBack() else finish() }
    }

    override fun onDestroy() { web?.destroy(); web = null; super.onDestroy() }

    companion object {
        fun open(ctx: Context, url: String) = ctx.startActivity(Intent(ctx, BrowserActivity::class.java).putExtra("url", url))
    }
}

@androidx.compose.runtime.Composable
private fun Round(icon: Icon, rotate: Float = 0f, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Xy.overlay).clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { XyIcon(icon, Modifier.rotate(rotate), tint = Xy.textHi, size = 18.dp) }
}
