package id.xyverse.xydesk.net

import id.xyverse.xydesk.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(val status: Int, message: String) : Exception(message)

/** Klien HTTP ke backend XyDesk: OTP email, signal-token, TURN. */
object Api {
    private val json = "application/json".toMediaType()
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private suspend fun post(path: String, body: JSONObject, jwt: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(BuildConfig.API_URL + path)
                .post(body.toString().toRequestBody(json))
                .apply { if (jwt != null) header("Authorization", "Bearer $jwt") }
                .build()
            http.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw ApiException(res.code, errorMessage(text, res.code))
                if (text.isBlank()) JSONObject() else JSONObject(text)
            }
        }

    private fun errorMessage(text: String, code: Int): String = runCatching {
        val o = JSONObject(text)
        o.optString("hint").ifBlank { o.optString("reason") }.ifBlank { o.optString("error") }
    }.getOrNull()?.ifBlank { null } ?: "HTTP $code"

    suspend fun requestOtp(email: String) {
        post("/auth/request-otp", JSONObject().put("email", email))
    }

    /** Mengembalikan JWT XyDesk. */
    suspend fun verifyOtp(email: String, otp: String): String =
        post("/auth/verify-otp", JSONObject().put("email", email).put("otp", otp)).getString("token")

    suspend fun googleLogin(idToken: String): JSONObject =
        post("/auth/google", JSONObject().put("id_token", idToken))

    suspend fun signalToken(jwt: String, deviceId: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${BuildConfig.API_URL}/signal-token?id=$deviceId")
            .header("Authorization", "Bearer $jwt")
            .build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw ApiException(res.code, "signal-token HTTP ${res.code}")
            res.body?.string().orEmpty().trim()
        }
    }

    /** Daftar ICE server dari /turn-ice; kosong bila relay belum dikonfigurasi. */
    suspend fun turnIce(deviceId: String, signalToken: String): List<JSONObject> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${BuildConfig.API_URL}/turn-ice?id=$deviceId&token=$signalToken")
            .build()
        runCatching {
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use emptyList()
                val arr = JSONObject(res.body?.string().orEmpty()).optJSONArray("iceServers")
                    ?: return@use emptyList()
                List(arr.length()) { arr.getJSONObject(it) }
            }
        }.getOrDefault(emptyList())
    }
}
