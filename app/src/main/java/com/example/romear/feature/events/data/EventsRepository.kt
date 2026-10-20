package com.example.romear.feature.events.data

import com.example.romear.feature.events.domain.CreateOrderResult
import com.example.romear.feature.events.domain.EventItem
import com.example.romear.feature.events.domain.TicketTierItem
import com.example.romear.feature.events.domain.UserTicketItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

class EventsRepository @Inject constructor(
    private val api: EventsApi,
    private val dispatcher: CoroutineDispatcher,
) {
    private fun JSONObject.body() = toString().toRequestBody("application/json".toMediaType())
    private fun ResponseBody.json() = use { JSONObject(it.string()) }
    private fun ResponseBody.jsonArray() = use { JSONArray(it.string()) }

    suspend fun listEvents(category: String? = null): List<EventItem> = withContext(dispatcher) {
        val items = api.listEvents(category).jsonArray()
        val list = mutableListOf<EventItem>()
        for (i in 0 until items.length()) {
            val obj = items.getJSONObject(i)
            val minPrice = obj.optInt("minPricePaise", 0)
            list.add(
                EventItem(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    description = obj.getString("description"),
                    category = obj.getString("category"),
                    startsAt = obj.getString("startsAt"),
                    endsAt = obj.getString("endsAt"),
                    venueName = obj.getString("venueName"),
                    address = obj.optString("address", null),
                    status = obj.getString("status"),
                    distanceBucket = "Nearby",
                    coverUrl = obj.optString("coverUrl").takeIf { it.startsWith("https://") },
                    organizerName = obj.optJSONObject("organizer")?.optString("displayName"),
                    organizerPhotoUrl = obj.optJSONObject("organizer")?.optString("photoUrl")?.takeIf { it.startsWith("https://") },
                    tiers = listOf(
                        TicketTierItem(
                            id = "",
                            name = "General",
                            description = null,
                            priceInPaise = minPrice,
                            capacity = 100,
                            remaining = 100,
                        ),
                    ),
                ),
            )
        }
        list
    }

    suspend fun getEvent(id: String): EventItem = withContext(dispatcher) {
        val obj = api.getEvent(id).json()
        val tiersArr = obj.getJSONArray("tiers")
        val tiers = mutableListOf<TicketTierItem>()
        for (j in 0 until tiersArr.length()) {
            val t = tiersArr.getJSONObject(j)
            tiers.add(
                TicketTierItem(
                    id = t.getString("id"),
                    name = t.getString("name"),
                    description = null,
                    priceInPaise = t.getInt("pricePaise"),
                    capacity = t.getInt("capacity"),
                    remaining = t.getInt("available"),
                    maxPerOrder = t.optInt("maxPerOrder", 6),
                ),
            )
        }
        EventItem(
            id = obj.getString("id"),
            title = obj.getString("title"),
            description = obj.getString("description"),
            category = obj.getString("category"),
            startsAt = obj.getString("startsAt"),
            endsAt = obj.getString("endsAt"),
            venueName = obj.getString("venueName"),
            address = obj.optString("address", null),
            status = obj.getString("status"),
            distanceBucket = "Nearby",
            tiers = tiers,
            coverUrl = obj.optString("coverUrl").takeIf { it.startsWith("https://") },
            organizerName = obj.optJSONObject("organizer")?.optString("displayName"),
            organizerPhotoUrl = obj.optJSONObject("organizer")?.optString("photoUrl")?.takeIf { it.startsWith("https://") },
        )
    }

    suspend fun createOrder(eventId: String, tierId: String, quantity: Int, requestId: String = UUID.randomUUID().toString()): CreateOrderResult =
        withContext(dispatcher) {
            val json = JSONObject()
                .put("tierId", tierId)
                .put("quantity", quantity)
                .put("requestId", requestId)
            val response = api.createOrder(eventId, json.body()).json()
            CreateOrderResult(
                orderId = response.getString("orderId"),
                status = response.getString("status"),
                totalPaise = response.getInt("amountPaise"),
                expiresAt = response.optString("reservedUntil", ""),
            )
        }

    suspend fun listMyTickets(): List<UserTicketItem> = withContext(dispatcher) {
        val items = api.listMyTickets().jsonArray()
        val list = mutableListOf<UserTicketItem>()
        for (i in 0 until items.length()) {
            val obj = items.getJSONObject(i)
            list.add(
                UserTicketItem(
                    id = obj.getString("id"),
                    status = obj.getString("status"),
                    qrPayload = obj.getString("qrValue"),
                    checkedInAt = if (obj.isNull("checkedInAt")) null else obj.getString("checkedInAt"),
                    tierName = obj.getString("tierName"),
                    priceInPaise = 0,
                    eventTitle = obj.getString("eventTitle"),
                    startsAt = obj.getString("startsAt"),
                    endsAt = obj.getString("startsAt"),
                    venueName = obj.getString("venueName"),
                    address = obj.optString("address", null),
                ),
            )
        }
        list
    }
}
