package com.example.romear.feature.events.domain

data class TicketTierItem(
    val id: String,
    val name: String,
    val description: String?,
    val priceInPaise: Int,
    val capacity: Int,
    val remaining: Int,
)

data class EventItem(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val startsAt: String,
    val endsAt: String,
    val venueName: String,
    val address: String?,
    val status: String,
    val distanceBucket: String,
    val tiers: List<TicketTierItem>,
)

data class CreateOrderResult(
    val orderId: String,
    val status: String,
    val totalPaise: Int,
    val expiresAt: String,
)

data class UserTicketItem(
    val id: String,
    val status: String,
    val qrPayload: String,
    val checkedInAt: String?,
    val tierName: String,
    val priceInPaise: Int,
    val eventTitle: String,
    val startsAt: String,
    val endsAt: String,
    val venueName: String,
    val address: String?,
)
