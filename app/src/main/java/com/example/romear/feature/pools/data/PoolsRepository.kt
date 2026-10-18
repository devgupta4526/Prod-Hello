package com.example.romear.feature.pools.data

import com.example.romear.core.security.TokenStore
import com.example.romear.feature.pools.domain.PoolDetail
import com.example.romear.feature.pools.domain.PoolHost
import com.example.romear.feature.pools.domain.PoolItem
import com.example.romear.feature.pools.domain.PoolKnockItem
import com.example.romear.feature.pools.domain.PoolMemberItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class PoolsRepository @Inject constructor(
    private val api: PoolsApi,
    private val store: TokenStore,
    private val dispatcher: CoroutineDispatcher,
) {
    private fun JSONObject.body() = toString().toRequestBody("application/json".toMediaType())
    private fun ResponseBody.json() = use { JSONObject(it.string()) }
    private fun ResponseBody.jsonArray() = use { JSONArray(it.string()) }

    suspend fun createPool(
        title: String,
        activity: String,
        lat: Double,
        lng: Double,
        timeWindowStart: String,
        timeWindowEnd: String,
        capacity: Int,
    ): String = withContext(dispatcher) {
        val json = JSONObject()
            .put("title", title)
            .put("description", "Casual meetup: $title")
            .put("category", activity)
            .put("startsAt", timeWindowStart)
            .put("capacity", capacity)
            .put("venueName", "Central Point")
            .put("venueLat", lat)
            .put("venueLng", lng)
            .put("areaLabel", "Nearby")
        val response = api.createPool(json.body()).json()
        response.getString("id")
    }

    suspend fun listPools(category: String? = null): List<PoolItem> = withContext(dispatcher) {
        val items = api.listPools(category).jsonArray()
        val list = mutableListOf<PoolItem>()
        for (i in 0 until items.length()) {
            val obj = items.getJSONObject(i)
            val org = obj.getJSONObject("organizer")
            list.add(
                PoolItem(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    activity = obj.getString("category"),
                    capacity = obj.getInt("capacity"),
                    memberCount = obj.getInt("memberCount"),
                    timeWindowStart = obj.getString("startsAt"),
                    timeWindowEnd = if (obj.isNull("endsAt")) obj.getString("startsAt") else obj.getString("endsAt"),
                    status = obj.getString("status"),
                    distanceBucket = obj.optString("areaLabel", "Nearby"),
                    host = PoolHost(
                        id = org.getString("id"),
                        displayName = org.getString("displayName"),
                        photoUrl = if (org.isNull("photoUrl")) null else org.getString("photoUrl"),
                    ),
                    lat = 0.0,
                    lng = 0.0,
                ),
            )
        }
        list
    }

    suspend fun getPool(id: String): PoolDetail = withContext(dispatcher) {
        val obj = api.getPool(id).json()
        val org = obj.getJSONObject("organizer")
        val isOrganizer = obj.getBoolean("isOrganizer")
        val isMember = obj.getBoolean("isMember")

        val membersList = mutableListOf<PoolMemberItem>()
        val membersArr = obj.optJSONArray("members")
        if (membersArr != null) {
            for (i in 0 until membersArr.length()) {
                val m = membersArr.getJSONObject(i)
                val u = m.getJSONObject("user")
                membersList.add(
                    PoolMemberItem(
                        id = m.getString("id"),
                        role = m.getString("role"),
                        user = PoolHost(
                            id = u.getString("id"),
                            displayName = u.getString("displayName"),
                            photoUrl = if (u.isNull("photoUrl")) null else u.getString("photoUrl"),
                        ),
                    ),
                )
            }
        } else {
            // Default host as first member
            membersList.add(
                PoolMemberItem(
                    id = org.getString("id"),
                    role = "HOST",
                    user = PoolHost(
                        id = org.getString("id"),
                        displayName = org.getString("displayName"),
                        photoUrl = if (org.isNull("photoUrl")) null else org.getString("photoUrl"),
                    ),
                ),
            )
        }

        PoolDetail(
            id = obj.getString("id"),
            title = obj.getString("title"),
            activity = obj.getString("category"),
            capacity = obj.getInt("capacity"),
            timeWindowStart = obj.getString("startsAt"),
            timeWindowEnd = if (obj.isNull("endsAt")) obj.getString("startsAt") else obj.getString("endsAt"),
            status = obj.getString("status"),
            conversationId = if (obj.isNull("conversationId")) null else obj.getString("conversationId"),
            host = PoolHost(
                id = org.getString("id"),
                displayName = org.getString("displayName"),
                photoUrl = if (org.isNull("photoUrl")) null else org.getString("photoUrl"),
            ),
            members = membersList,
            distanceBucket = obj.optString("areaLabel", "Nearby"),
            isHost = isOrganizer,
            isMember = isMember,
        )
    }

    suspend fun knock(poolId: String, introMessage: String): Boolean = withContext(dispatcher) {
        val json = JSONObject().put("message", introMessage)
        api.knock(poolId, json.body())
        true
    }

    suspend fun listKnocks(poolId: String): List<PoolKnockItem> = withContext(dispatcher) {
        val items = api.listKnocks(poolId).jsonArray()
        val list = mutableListOf<PoolKnockItem>()
        for (i in 0 until items.length()) {
            val obj = items.getJSONObject(i)
            val u = obj.getJSONObject("user")
            list.add(
                PoolKnockItem(
                    id = obj.getString("id"),
                    poolId = obj.getString("poolId"),
                    userId = obj.getString("userId"),
                    introMessage = if (obj.isNull("message")) "" else obj.getString("message"),
                    status = obj.getString("status"),
                    createdAt = obj.getString("createdAt"),
                    user = PoolHost(
                        id = u.getString("id"),
                        displayName = u.getString("displayName"),
                        photoUrl = if (u.isNull("photoUrl")) null else u.getString("photoUrl"),
                    ),
                ),
            )
        }
        list
    }

    suspend fun acceptKnock(poolId: String, knockId: String): Boolean = withContext(dispatcher) {
        api.acceptKnock(poolId, knockId)
        true
    }

    suspend fun declineKnock(poolId: String, knockId: String): Boolean = withContext(dispatcher) {
        api.declineKnock(poolId, knockId)
        true
    }

    suspend fun leavePool(poolId: String): Boolean = withContext(dispatcher) {
        api.leavePool(poolId)
        true
    }

    suspend fun closePool(poolId: String): Boolean = withContext(dispatcher) {
        api.closePool(poolId)
        true
    }
}
