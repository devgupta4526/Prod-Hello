package com.example.romear.feature.chat.data

import com.example.romear.core.security.TokenStore
import com.example.romear.feature.chat.domain.ChatMessage
import com.example.romear.feature.chat.domain.ChatPartner
import com.example.romear.feature.chat.domain.ConversationItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

class ChatRepository @Inject constructor(
    private val api: ChatApi,
    private val store: TokenStore,
    private val dispatcher: CoroutineDispatcher,
) {
    private fun JSONObject.body() = toString().toRequestBody("application/json".toMediaType())
    private fun ResponseBody.json() = use { JSONObject(it.string()) }

    suspend fun getConversations(cursor: String? = null, limit: Int = 20): List<ConversationItem> =
        withContext(dispatcher) {
            val response = api.getConversations(cursor, limit).json()
            val items = response.getJSONArray("items")
            val list = mutableListOf<ConversationItem>()
            for (i in 0 until items.length()) {
                val obj = items.getJSONObject(i)
                val partnerObj = obj.getJSONObject("partner")
                list.add(
                    ConversationItem(
                        id = obj.getString("id"),
                        partner = ChatPartner(
                            id = partnerObj.getString("id"),
                            displayName = partnerObj.getString("displayName"),
                            photoUrl = if (partnerObj.isNull("photoUrl")) null else partnerObj.getString("photoUrl"),
                        ),
                        lastMessageAt = if (obj.isNull("lastMessageAt")) null else obj.getString("lastMessageAt"),
                        unreadCount = obj.optInt("unreadCount", 0),
                    ),
                )
            }
            list
        }

    suspend fun getMessages(conversationId: String, cursor: String? = null, limit: Int = 50): List<ChatMessage> =
        withContext(dispatcher) {
            val currentUserId = store.currentUserId()
            val response = api.getMessages(conversationId, cursor, limit).json()
            val items = response.getJSONArray("items")
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until items.length()) {
                val obj = items.getJSONObject(i)
                val senderId = obj.getString("senderId")
                list.add(
                    ChatMessage(
                        id = obj.getString("id"),
                        conversationId = obj.getString("conversationId"),
                        senderId = senderId,
                        body = obj.getString("body"),
                        createdAt = obj.getString("createdAt"),
                        isOutgoing = currentUserId != null && currentUserId == senderId,
                        readAt = if (obj.isNull("readAt")) null else obj.getString("readAt"),
                    ),
                )
            }
            list
        }

    suspend fun sendMessage(conversationId: String, body: String): ChatMessage =
        withContext(dispatcher) {
            val currentUserId = store.currentUserId() ?: ""
            val key = UUID.randomUUID().toString()
            val json = JSONObject().put("idempotencyKey", key).put("body", body)
            val obj = api.sendMessage(conversationId, json.body(), key).json()
            ChatMessage(
                id = obj.getString("id"),
                conversationId = obj.getString("conversationId"),
                senderId = obj.getString("senderId"),
                body = obj.getString("body"),
                createdAt = obj.getString("createdAt"),
                isOutgoing = true,
                readAt = null,
            )
        }

    suspend fun markAsRead(messageId: String): Boolean = withContext(dispatcher) {
        api.markAsRead(messageId)
        true
    }
}
