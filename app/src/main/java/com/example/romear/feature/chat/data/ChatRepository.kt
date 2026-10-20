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
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

class ChatRepository @Inject constructor(
    private val api: ChatApi,
    private val store: TokenStore,
    private val dispatcher: CoroutineDispatcher,
) {
    private fun JSONObject.body() = toString().toRequestBody("application/json".toMediaType())
    private fun ResponseBody.items(): JSONArray = use { JSONArray(it.string()) }
    private fun JSONObject.optional(key: String) = if (isNull(key)) null else getString(key)

    suspend fun getConversations(cursor: String? = null, limit: Int = 20): List<ConversationItem> =
        withContext(dispatcher) {
            val items = api.getConversations(cursor, limit).items()
            List(items.length()) { index ->
                val item = items.getJSONObject(index)
                ConversationItem(
                    id = item.getString("id"),
                    partner = ChatPartner(
                        id = item.optional("partnerId").orEmpty(),
                        displayName = item.getString("title"),
                        photoUrl = item.optional("photoUrl"),
                    ),
                    lastMessageAt = item.optJSONObject("lastMessage")?.optional("createdAt"),
                    unreadCount = item.optInt("unreadCount"),
                )
            }
        }

    private fun message(item: JSONObject, currentUserId: String?) = ChatMessage(
        id = item.getString("id"),
        conversationId = item.getString("conversationId"),
        senderId = item.getString("senderId"),
        body = if (item.isNull("deletedAt")) item.getString("body") else "Message deleted",
        createdAt = item.getString("createdAt"),
        isOutgoing = currentUserId == item.getString("senderId"),
        readAt = null,
        deliveredAt = item.optional("deliveredAt"),
    )

    suspend fun getMessages(conversationId: String, cursor: String? = null, limit: Int = 50): List<ChatMessage> =
        withContext(dispatcher) {
            val userId = store.currentUserId()
            val items = api.getMessages(conversationId, cursor, limit).items()
            List(items.length()) { message(items.getJSONObject(it), userId) }
                .sortedWith(compareBy<ChatMessage> { it.createdAt }.thenBy { it.id })
        }

    suspend fun sendMessage(conversationId: String, body: String, clientMessageId: String = UUID.randomUUID().toString()): ChatMessage =
        withContext(dispatcher) {
            val request = JSONObject().put("clientMsgId", clientMessageId).put("body", body)
            val item = api.sendMessage(conversationId, request.body(), clientMessageId).use { JSONObject(it.string()) }
            message(item, store.currentUserId())
        }

    suspend fun acknowledge(conversationId: String, lastMessageId: String) = withContext(dispatcher) {
        api.acknowledge(conversationId, JSONObject().put("lastMessageId", lastMessageId).body()).close()
    }
}
