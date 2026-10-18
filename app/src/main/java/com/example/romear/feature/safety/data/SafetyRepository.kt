package com.example.romear.feature.safety.data

import com.example.romear.feature.safety.domain.BlockedUserItem
import com.example.romear.feature.safety.domain.SafetyStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import javax.inject.Inject

class SafetyRepository @Inject constructor(
    private val api: SafetyApi,
    private val dispatcher: CoroutineDispatcher,
) {
    private fun JSONObject.body() = toString().toRequestBody("application/json".toMediaType())
    private fun ResponseBody.json() = use { JSONObject(it.string()) }

    suspend fun blockUser(blockedUserId: String): Boolean = withContext(dispatcher) {
        val json = JSONObject().put("blockedUserId", blockedUserId)
        api.blockUser(json.body())
        true
    }

    suspend fun listBlocks(): List<BlockedUserItem> = withContext(dispatcher) {
        val response = api.listBlocks().json()
        val items = response.getJSONArray("items")
        val list = mutableListOf<BlockedUserItem>()
        for (i in 0 until items.length()) {
            val obj = items.getJSONObject(i)
            val u = obj.getJSONObject("blockedUser")
            list.add(
                BlockedUserItem(
                    id = obj.getString("id"),
                    blockedUserId = obj.getString("blockedUserId"),
                    displayName = u.getString("displayName"),
                    photoUrl = if (u.isNull("photoUrl")) null else u.getString("photoUrl"),
                    createdAt = obj.getString("createdAt"),
                ),
            )
        }
        list
    }

    suspend fun unblockUser(userId: String): Boolean = withContext(dispatcher) {
        api.unblockUser(userId)
        true
    }

    suspend fun report(
        targetType: String,
        targetId: String,
        reason: String,
        details: String? = null,
    ): Boolean = withContext(dispatcher) {
        val json = JSONObject()
            .put("targetType", targetType)
            .put("targetId", targetId)
            .put("reason", reason)
        details?.let { json.put("details", it) }
        api.report(json.body())
        true
    }

    suspend fun getSafetyStatus(): SafetyStatus = withContext(dispatcher) {
        val obj = api.getSafetyStatus().json()
        SafetyStatus(
            suspended = obj.getBoolean("suspended"),
            pendingDeletion = obj.getBoolean("pendingDeletion"),
            deletionRequestedAt = if (obj.isNull("deletionRequestedAt")) null else obj.getString("deletionRequestedAt"),
        )
    }

    suspend fun submitAppeal(reason: String): Boolean = withContext(dispatcher) {
        val json = JSONObject().put("reason", reason)
        api.submitAppeal(json.body())
        true
    }
}
