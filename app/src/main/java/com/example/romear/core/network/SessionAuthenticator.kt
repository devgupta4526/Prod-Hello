package com.example.romear.core.network

import com.example.romear.BuildConfig
import com.example.romear.core.security.TokenStore
import java.io.IOException
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject

class SessionAuthenticator(private val store: TokenStore, private val refreshClient: OkHttpClient) : Authenticator {
    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null || response.request.url.encodedPath.contains("/auth/")) return null
        val current = store.accessToken() ?: return null
        if (response.request.header("Authorization") != "Bearer $current") return response.request.newBuilder().header("Authorization", "Bearer $current").build()
        val refresh = store.refreshToken() ?: return null
        return try {
            val body = JSONObject().put("refreshToken", refresh).put("deviceId", store.deviceId()).toString().toRequestBody("application/json".toMediaType())
            refreshClient.newCall(Request.Builder().url(BuildConfig.API_BASE_URL + "v1/auth/refresh").post(body).build()).execute().use { result ->
                if (!result.isSuccessful) {
                    if (result.code == 401 || result.code == 403) store.clear()
                    return null
                }
                val tokens = JSONObject(result.body.string())
                store.save(tokens.getString("accessToken"), tokens.getString("refreshToken"))
                response.request.newBuilder().header("Authorization", "Bearer ${store.accessToken()}").build()
            }
        } catch (_: IOException) { null }
        catch (_: org.json.JSONException) { null }
    }
}
