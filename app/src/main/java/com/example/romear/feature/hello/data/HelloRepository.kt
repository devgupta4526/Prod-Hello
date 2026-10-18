package com.example.romear.feature.hello.data

import com.example.romear.feature.hello.domain.ConnectionItem
import com.example.romear.feature.hello.domain.HelloItem
import com.example.romear.feature.hello.domain.HelloUser
import com.example.romear.feature.hello.domain.SendHelloResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import javax.inject.Inject

class HelloRepository @Inject constructor(
    private val api: HelloApi,
    private val dispatcher: CoroutineDispatcher,
) {
    private fun JSONObject.body() = toString().toRequestBody("application/json".toMediaType())
    private fun ResponseBody.json() = use { JSONObject(it.string()) }

    private fun parseHelloUser(obj: JSONObject?): HelloUser? {
        if (obj == null) return null
        return HelloUser(
            id = obj.getString("id"),
            displayName = obj.getString("displayName"),
            photoUrl = if (obj.isNull("photoUrl")) null else obj.getString("photoUrl"),
        )
    }

    suspend fun sendHello(receiverId: String, message: String, context: JSONObject? = null): SendHelloResult = withContext(dispatcher) {
        val payload = JSONObject().put("receiverId", receiverId).put("message", message)
        context?.let { payload.put("context", it) }
        val res = api.sendHello(payload.body()).json()
        val helloObj = res.getJSONObject("hello")
        SendHelloResult(
            helloId = helloObj.getString("id"),
            status = helloObj.getString("status"),
            connected = res.getBoolean("connected"),
            connectionId = if (res.has("connectionId")) res.getString("connectionId") else null,
        )
    }

    suspend fun listHellos(box: String, status: String? = null): List<HelloItem> = withContext(dispatcher) {
        val res = api.listHellos(box = box, status = status).json()
        val itemsArr = res.getJSONArray("items")
        val items = mutableListOf<HelloItem>()
        for (i in 0 until itemsArr.length()) {
            val obj = itemsArr.getJSONObject(i)
            items.add(
                HelloItem(
                    id = obj.getString("id"),
                    senderId = obj.getString("senderId"),
                    receiverId = obj.getString("receiverId"),
                    message = obj.getString("message"),
                    contextText = obj.optJSONObject("context")?.optString("text"),
                    status = obj.getString("status"),
                    expiresAt = obj.getString("expiresAt"),
                    createdAt = obj.getString("createdAt"),
                    sender = if (obj.has("sender")) parseHelloUser(obj.optJSONObject("sender")) else null,
                    receiver = if (obj.has("receiver")) parseHelloUser(obj.optJSONObject("receiver")) else null,
                ),
            )
        }
        items
    }

    suspend fun acceptHello(helloId: String): Boolean = withContext(dispatcher) {
        api.acceptHello(helloId)
        true
    }

    suspend fun declineHello(helloId: String): Boolean = withContext(dispatcher) {
        api.declineHello(helloId)
        true
    }

    suspend fun withdrawHello(helloId: String): Boolean = withContext(dispatcher) {
        api.withdrawHello(helloId)
        true
    }

    suspend fun listConnections(): List<ConnectionItem> = withContext(dispatcher) {
        val raw = api.listConnections().use { it.string() }
        val arr = org.json.JSONArray(raw)
        val items = mutableListOf<ConnectionItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val partnerObj = obj.getJSONObject("partner")
            items.add(
                ConnectionItem(
                    id = obj.getString("id"),
                    createdAt = obj.getString("createdAt"),
                    partner = HelloUser(
                        id = partnerObj.getString("id"),
                        displayName = partnerObj.getString("displayName"),
                        photoUrl = if (partnerObj.isNull("photoUrl")) null else partnerObj.getString("photoUrl"),
                    ),
                    conversationId = if (obj.isNull("conversationId")) null else obj.getString("conversationId"),
                ),
            )
        }
        items
    }

    suspend fun disconnect(connectionId: String): Boolean = withContext(dispatcher) {
        api.disconnect(connectionId)
        true
    }
}
