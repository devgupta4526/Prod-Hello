package com.example.romear.feature.social

import javax.inject.Inject
import javax.inject.Named
import retrofit2.Retrofit
import retrofit2.http.*
import okhttp3.ResponseBody
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

interface SocialApi {
    @GET suspend fun get(@Url path: String): ResponseBody
    @PUT suspend fun put(@Url path: String, @Body body: RequestBody): ResponseBody
    @POST suspend fun post(@Url path: String, @Body body: RequestBody): ResponseBody
    @DELETE suspend fun delete(@Url path: String): ResponseBody
}
class SocialRepository @Inject constructor(@Named("authenticated") retrofit: Retrofit, private val auth: com.example.romear.feature.auth.data.AuthRepository) {
    suspend fun updateProfile(name: String, bio: String, interests: List<String>) { auth.profile(name, bio, interests) }
    suspend fun uploadProfilePhoto(file: java.io.File) { auth.photo(file) }
    private val api = retrofit.create(SocialApi::class.java)
    suspend fun request(path: String, method: String = "GET", body: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        val data = body.toString().toRequestBody("application/json".toMediaType())
        try {
            val response = when(method) { "PUT" -> api.put(path, data); "POST" -> api.post(path, data); "DELETE" -> api.delete(path); else -> api.get(path) }
            response.use { JSONObject(it.string()) }
        } catch(failure: retrofit2.HttpException) {
            val code = try { JSONObject(failure.response()?.errorBody()?.string().orEmpty()).getJSONObject("error").getString("code") } catch(_: Exception) { "unavailable" }
            val message = when(code) {
                "agreement_required" -> "Everyone participating must choose the same time before you confirm."
                "plan_closed" -> "This plan has changed or its time has passed. Refresh and try again."
                "plan_not_finished" -> "You can choose to meet again after the plan's scheduled end."
                "plan_not_started" -> "Memories open when your confirmed plan starts."
                "circle_full" -> "This circle is full. Try another circle."
                "user_unavailable" -> "This person is not accepting Hellos right now."
                "context_unavailable" -> "This photo or prompt has changed. Open the profile again."
                "memory_limit" -> "You can share up to 20 photos per plan. Remove one before adding another."
                "invalid_photo" -> "Choose a supported photo under the size limit."
                "daily_cap_reached" -> "You have reached today's Hello limit. Try again tomorrow."
                "hello_already_pending" -> "You already have a pending Hello with this person."
                "validation_failed" -> "Check your answers, activity and venue before saving."
                "unauthorized" -> "Please sign in again."
                "forbidden", "not_found" -> "This item is no longer available to you."
                else -> "Could not complete this request. Please try again."
            }
            throw IllegalStateException(message, failure)
        }
    }
    suspend fun image(id: String): ByteArray = withContext(Dispatchers.IO) { api.get("v1/memories/$id/image").use { it.bytes() } }
}
