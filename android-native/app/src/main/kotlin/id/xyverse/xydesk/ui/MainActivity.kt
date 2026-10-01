package id.xyverse.xydesk.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import id.xyverse.xydesk.core.tr
import id.xyverse.xydesk.core.Images
import id.xyverse.xydesk.core.NewsWatch
import id.xyverse.xydesk.ui.kit.Tab
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import id.xyverse.xydesk.core.LocalLang
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import id.xyverse.xydesk.BuildConfig
import id.xyverse.xydesk.core.Previews
import id.xyverse.xydesk.core.Store
import android.os.Build
import id.xyverse.xydesk.ui.kit.XyTheme

private enum class Stage { SPLASH, ONBOARDING, AUTH }

class MainActivity : ComponentActivity() {
    private val store by lazy { Store(applicationContext) }
    private var lastBack = 0L
    private var openNews = false

    private fun shareText(text: String) =
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Images.attach(applicationContext)
        NewsWatch.schedule(applicationContext)
        if (intent.getStringExtra("tab") == "news") openNews = true
        onBackPressedDispatcher.addCallback(this) {
            val now = System.currentTimeMillis()
            if (now - lastBack < 2000) finish()
            else { lastBack = now; Toast.makeText(this@MainActivity, "Tekan sekali lagi untuk keluar".tr(store.lang), Toast.LENGTH_SHORT).show() }
        }
        setContent {
            var jwt by remember { mutableStateOf(store.jwt) }
            var stage by remember { mutableStateOf(Stage.SPLASH) }
            var lang by remember { mutableStateOf(store.lang) }
            var haptics by remember { mutableStateOf(store.haptics) }
            var replay by remember { mutableStateOf(false) }
            val hapticOwner = LocalHapticFeedback.current
            val quiet = remember { object : HapticFeedback { override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit } }
            CompositionLocalProvider(LocalLang provides lang, LocalHapticFeedback provides if (haptics) hapticOwner else quiet) {
            XyTheme {
                AnimatedContent(
                    Triple(stage, jwt != null, 0),
                    transitionSpec = { (fadeIn(tween(500)) + scaleIn(tween(500), 0.98f)) togetherWith fadeOut(tween(250)) },
                    label = "root",
                ) { (st, loggedIn) ->
                    if (st == Stage.SPLASH) {
                        SplashScreen(playVoice = !store.onboarded || replay, short = store.onboarded && !replay) {
                            replay = false
                            stage = if (store.onboarded) Stage.AUTH else Stage.ONBOARDING
                        }
                    } else if (st == Stage.ONBOARDING) {
                        OnboardingScreen { store.onboarded = true; stage = Stage.AUTH }
                    } else if (!loggedIn) {
                        LoginScreen(onGoogle = ::googleIdToken, onOpenUrl = { BrowserActivity.open(this@MainActivity, it) }) { token, email ->
                            store.jwt = token; store.email = email; jwt = token
                        }
                    } else {
                        var refresh by remember { mutableStateOf(0) }
                        LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose {} }
                        val history by store.observeHistory().collectAsState()
                        HomeShell(
                            email = store.email.orEmpty(),
                            lastHost = store.lastHost,
                            startTab = if (openNews) Tab.NEWS else listOf(Tab.HOME, Tab.DEVICES, Tab.NEWS).getOrElse(store.startTab) { Tab.HOME },
                            history = history,
                            onConnect = { host, pin -> openSession(host, pin) },
                            onOpenUrl = { BrowserActivity.open(this@MainActivity, it) },
                            onShare = { shareText(it) },
                            googleToken = { runCatching { googleIdToken() }.getOrNull() },
                            settings = Settings(
                                lang = lang,
                                onLang = { lang = it; store.lang = it },
                                haptics = haptics,
                                onHaptics = { haptics = it; store.haptics = it },
                                onReplayIntro = { replay = true; stage = Stage.SPLASH },
                                deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}",
                                appVersion = BuildConfig.VERSION_NAME,
                                historyCount = history.size,
                                onClearHistory = { store.clearHistory(); Previews.clear(applicationContext); Images.clearDisk(); refresh++ },
                                store = store,
                                onOpenAppSettings = {
                                    startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                                },
                            ),
                            onLogout = { store.clear(); jwt = null },
                        )
                    }
                }
                LegalOverlay()
            }
            }
        }
    }

    private fun openSession(host: String, pin: String) {
        store.lastHost = host
        startActivity(
            Intent(this, SessionActivity::class.java)
                .putExtra("jwt", store.jwt)
                .putExtra("host", host)
                .putExtra("pin", pin),
        )
    }

    private suspend fun googleIdToken(): String? {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (clientId.isEmpty()) throw IllegalStateException("Google Sign-In belum dikonfigurasi di build ini.")
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(clientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val result = CredentialManager.create(this).getCredential(this, request)
            GoogleIdTokenCredential.createFrom(result.credential.data).idToken
        } catch (e: Exception) {
            Log.w("XyDeskAuth", "google: $e")
            throw IllegalStateException("Google Sign-In dibatalkan atau gagal.")
        }
    }
}
