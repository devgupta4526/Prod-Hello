package com.example.romear.feature.discovery.data

import com.example.romear.feature.discovery.domain.DiscoveryUser
import com.example.romear.feature.discovery.domain.FuzzedMarker
import com.example.romear.feature.discovery.domain.PublicProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import javax.inject.Inject

class DiscoveryRepository @Inject constructor(
    private val api: DiscoveryApi,
    private val dispatcher: CoroutineDispatcher,
) {
    private fun JSONObject.body() = toString().toRequestBody("application/json".toMediaType())
    private fun ResponseBody.json() = use { JSONObject(it.string()) }

    suspend fun updatePresence(lat: Double, lng: Double, accuracyM: Int?, availability: String): Boolean =
        withContext(dispatcher) {
            val json = JSONObject()
                .put("lat", lat)
                .put("lng", lng)
                .put("availability", availability)
            accuracyM?.let { json.put("accuracyM", it) }
            api.updatePresence(json.body())
            true
        }

    suspend fun clearPresence(): Boolean = withContext(dispatcher) {
        api.clearPresence()
        true
    }

    suspend fun getNearbyUsers(radiusKm: Int? = null, interests: String? = null): List<DiscoveryUser> =
        withContext(dispatcher) {
            val response = api.getNearbyUsers(radiusKm, interests).json()
            val items = response.getJSONArray("items")
            val users = mutableListOf<DiscoveryUser>()
            for (i in 0 until items.length()) {
                val obj = items.getJSONObject(i)
                val markerObj = obj.getJSONObject("marker")
                val interestsArr = obj.getJSONArray("sharedInterests")
                val shared = mutableListOf<String>()
                for (j in 0 until interestsArr.length()) {
                    shared.add(interestsArr.getString(j))
                }

                users.add(
                    DiscoveryUser(
                        userId = obj.getString("userId"),
                        displayName = obj.getString("displayName"),
                        age = if (obj.isNull("age")) null else obj.getInt("age"),
                        photoUrl = if (obj.isNull("photoUrl")) null else obj.getString("photoUrl"),
                        availability = obj.getString("availability"),
                        distanceBucket = obj.getString("distanceBucket"),
                        marker = FuzzedMarker(
                            lat = markerObj.getDouble("lat"),
                            lng = markerObj.getDouble("lng"),
                        ),
                        sharedInterests = shared,
                        upFor = if(obj.isNull("upFor")) null else obj.getString("upFor"),
                    ),
                )
            }
            users
        }

    suspend fun getUserProfile(userId: String): PublicProfile = withContext(dispatcher) {
        val obj = api.getUserProfile(userId).json()
        val photosArr = obj.getJSONArray("photos")
        val photos = mutableListOf<String>()
        for (i in 0 until photosArr.length()) {
            photos.add(photosArr.getJSONObject(i).getString("url"))
        }
        val interestsArr = obj.getJSONArray("interests")
        val interests = mutableListOf<String>()
        for (i in 0 until interestsArr.length()) {
            interests.add(interestsArr.getString(i))
        }

        PublicProfile(
            userId = obj.getString("userId"),
            displayName = obj.getString("displayName"),
            bio = if (obj.isNull("bio")) null else obj.getString("bio"),
            age = if (obj.isNull("age")) null else obj.getInt("age"),
            photos = photos,
            photoIds = List(photosArr.length()) { photosArr.getJSONObject(it).getString("id") },
            prompts = obj.optJSONArray("prompts")?.let { arr -> List(arr.length()) { i -> val p = arr.getJSONObject(i); com.example.romear.feature.discovery.domain.ProfilePrompt(p.getString("id"), p.getString("answer")) } } ?: emptyList(),
            idealWeekend = obj.optString("idealWeekend"),
            upFor = if(obj.isNull("upFor")) null else obj.getString("upFor"),
            upForUntil = if(obj.isNull("upForUntil")) null else obj.getString("upForUntil"),
            phoneVerified = obj.optBoolean("phoneVerified"),
            interests = interests,
            availability = if (obj.isNull("availability")) null else obj.getString("availability"),
        )
    }
}
