package com.example.romear.feature.auth.data

import com.example.romear.core.security.TokenStore
import com.example.romear.feature.auth.domain.Account
import com.example.romear.feature.auth.domain.AuthFailure
import com.example.romear.feature.auth.domain.OtpChallenge
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import retrofit2.HttpException

class AuthRepository @Inject constructor(private val api: AuthApi, private val store: TokenStore, private val dispatcher: CoroutineDispatcher) {
    private fun JSONObject.body() = toString().toRequestBody("application/json".toMediaType())
    private fun account(json: JSONObject) = Account(json.getString("status"), json.getBoolean("hasDateOfBirth"), json.getBoolean("hasProfile"), json.getBoolean("hasPhoto"), json.getBoolean("guidelinesAccepted"))
    private suspend fun <T> request(block: suspend () -> T): T = withContext(dispatcher) {
        try { block() }
        catch (failure: HttpException) {
            val code = try { JSONObject(failure.response()?.errorBody()?.string().orEmpty()).getJSONObject("error").getString("code") } catch (_: JSONException) { "service_unavailable" }
            throw AuthFailure(code, failure.response()?.headers()?.get("Retry-After")?.toIntOrNull() ?: 0)
        } catch (_: IOException) { throw AuthFailure("offline") }
        catch (_: JSONException) { throw AuthFailure("service_unavailable") }
    }
    private fun ResponseBody.json() = use { JSONObject(it.string()) }
    suspend fun restore(): Account? = request {
        val refresh = store.refreshToken() ?: return@request null
        val result = api.refresh(JSONObject().put("refreshToken", refresh).put("deviceId", store.deviceId()).body()).json()
        store.save(result.getString("accessToken"), result.getString("refreshToken"))
        account(result.getJSONObject("user"))
    }
    suspend fun requestOtp(phone: String, requestId: String, appHash: String?): OtpChallenge = request {
        val input = JSONObject().put("phone", "+91$phone").put("deviceId", store.deviceId()).put("requestId", requestId)
        if (appHash != null) input.put("appHash", appHash)
        val response = api.requestOtp(input.body()).json()
        OtpChallenge(response.getString("challengeId"), System.currentTimeMillis() + response.getInt("resendAfterSec") * 1000L)
    }
    suspend fun verify(challenge: String, code: String): Account = request {
        val response = api.verifyOtp(JSONObject().put("challengeId", challenge).put("code", code).put("deviceId", store.deviceId()).body()).json()
        store.save(response.getString("accessToken"), response.getString("refreshToken"))
        account(response.getJSONObject("user"))
    }
    suspend fun dateOfBirth(value: String): Account = request { account(api.dob(JSONObject().put("dateOfBirth", value).body()).json()) }
    suspend fun profile(name: String, bio: String, interests: List<String>): Account = request {
        account(api.profile(JSONObject().put("displayName", name).put("bio", bio).put("interests", JSONArray(interests)).body()).json())
    }
    suspend fun photo(file: File): Account = request {
        val signed = api.uploadUrl().json()
        val url = signed.getString("uploadUrl").toHttpUrl()
        if (url.scheme != "https" || url.host != "api.cloudinary.com") throw AuthFailure("storage_unavailable")
        val fields = signed.getJSONObject("fields")
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
        fields.keys().forEach { key -> multipart.addFormDataPart(key, fields.getString(key)) }
        multipart.addFormDataPart("file", "profile.jpg", file.asRequestBody("image/jpeg".toMediaType()))
        // A separate unauthenticated client prevents sending session tokens to the media provider.
        OkHttpClient.Builder().callTimeout(90, java.util.concurrent.TimeUnit.SECONDS).build().newCall(Request.Builder().url(url).post(multipart.build()).build()).execute().use {
            if (!it.isSuccessful) throw AuthFailure("photo_upload_failed")
        }
        account(api.confirmPhoto(JSONObject().put("uploadId", signed.getString("uploadId")).body()).json())
    }
    suspend fun sessions(): List<AccountSession> = request {
        val items = api.sessions().use { JSONArray(it.string()) }
        List(items.length()) { index ->
            val item = items.getJSONObject(index)
            AccountSession(item.getString("id"), item.getString("createdAt"), item.getBoolean("current"))
        }
    }
    suspend fun revokeSession(id: String) = request { api.revokeSession(id).close() }
    suspend fun acceptGuidelines(): Account = request { account(api.guidelines().json()) }
    suspend fun logout() = request { try { api.logout().close() } finally { store.clear() } }
    suspend fun clearSession() = withContext(dispatcher) { store.clear() }
}

data class AccountSession(val id: String, val createdAt: String, val current: Boolean)
