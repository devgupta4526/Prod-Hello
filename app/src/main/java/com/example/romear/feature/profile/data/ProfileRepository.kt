package com.example.romear.feature.profile.data

import com.example.romear.feature.profile.domain.MyProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class ProfileRepository @Inject constructor(
    private val api: ProfileApi,
    private val dispatcher: CoroutineDispatcher,
) {
    private fun JSONObject.body() = toString().toRequestBody("application/json".toMediaType())
    private fun ResponseBody.json() = use { JSONObject(it.string()) }

    suspend fun getMyProfile(): MyProfile = withContext(dispatcher) {
        val obj = api.getMyProfile().json()
        val photosArr = obj.getJSONArray("photos")
        val photos = mutableListOf<String>()
        for (i in 0 until photosArr.length()) {
            photos.add(photosArr.getJSONObject(i).getString("url"))
        }
        val profile = obj.getJSONObject("profile")
        val interestsArr = profile.getJSONArray("interests")
        val interests = mutableListOf<String>()
        for (i in 0 until interestsArr.length()) {
            interests.add(interestsArr.getString(i))
        }

        MyProfile(
            userId = obj.getString("userId"),
            displayName = profile.getString("displayName"),
            bio = if (profile.isNull("bio")) null else profile.getString("bio"),
            age = if (obj.isNull("age")) null else obj.getInt("age"),
            photos = photos,
            interests = interests,
            availability = if (obj.isNull("availability")) null else obj.getString("availability"),
        )
    }

    suspend fun updateProfile(
        displayName: String? = null,
        bio: String? = null,
        interests: List<String>? = null,
    ): Boolean = withContext(dispatcher) {
        val json = JSONObject()
        displayName?.let { json.put("displayName", it) }
        bio?.let { json.put("bio", it) }
        interests?.let { json.put("interests", JSONArray(it)) }
        api.updateProfile(json.body())
        true
    }

    suspend fun requestDataExport(): String = withContext(dispatcher) {
        val obj = api.requestDataExport().json()
        obj.optString("status", "pending")
    }

    suspend fun requestAccountDeletion(): String = withContext(dispatcher) {
        val obj = api.requestAccountDeletion().json()
        obj.getString("deletionScheduledAt")
    }

    suspend fun cancelAccountDeletion(): Boolean = withContext(dispatcher) {
        api.cancelAccountDeletion()
        true
    }
}
