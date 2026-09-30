package id.xyverse.xydesk.ui

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import id.xyverse.xydesk.BuildConfig
import id.xyverse.xydesk.core.Store
import id.xyverse.xydesk.ui.kit.XyTheme

private enum class Stage { SPLASH, ONBOARDING, AUTH }

class MainActivity : ComponentActivity() {
    private val store by lazy { Store(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var jwt by remember { mutableStateOf(store.jwt) }
            var stage by remember { mutableStateOf(Stage.SPLASH) }
            XyTheme {
                AnimatedContent(
                    Triple(stage, jwt != null, 0),
                    transitionSpec = { (fadeIn(tween(500)) + scaleIn(tween(500), 0.98f)) togetherWith fadeOut(tween(250)) },
                    label = "root",
                ) { (st, loggedIn) ->
                    if (st == Stage.SPLASH) {
                        SplashScreen { stage = if (store.onboarded) Stage.AUTH else Stage.ONBOARDING }
                    } else if (st == Stage.ONBOARDING) {
                        OnboardingScreen { store.onboarded = true; stage = Stage.AUTH }
                    } else if (!loggedIn) {
                        LoginScreen(onGoogle = ::googleIdToken) { token, email ->
                            store.jwt = token; store.email = email; jwt = token
                        }
                    } else {
                        var refresh by remember { mutableStateOf(0) }
                        LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose {} }
                        val history = remember(refresh) { store.history }
                        ConnectScreen(
                            email = store.email.orEmpty(),
                            initialHost = store.lastHost,
                            devices = history.distinctBy { it.host },
                            history = history,
                            onConnect = { host, pin -> openSession(host, pin) },
                            onLogout = { store.clear(); jwt = null },
                        )
                    }
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
