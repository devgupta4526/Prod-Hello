package com.example.romear.feature.pools.domain

data class PoolHost(
    val id: String,
    val displayName: String,
    val photoUrl: String?,
)

data class PoolMemberItem(
    val id: String,
    val role: String,
    val user: PoolHost,
)

data class PoolItem(
    val id: String,
    val title: String,
    val activity: String,
    val capacity: Int,
    val memberCount: Int,
    val timeWindowStart: String,
    val timeWindowEnd: String,
    val status: String,
    val distanceBucket: String,
    val host: PoolHost,
    val lat: Double,
    val lng: Double,
)

data class PoolDetail(
    val id: String,
    val title: String,
    val activity: String,
    val capacity: Int,
    val timeWindowStart: String,
    val timeWindowEnd: String,
    val status: String,
    val conversationId: String?,
    val host: PoolHost,
    val members: List<PoolMemberItem>,
    val distanceBucket: String,
    val isHost: Boolean,
    val isMember: Boolean,
)

data class PoolKnockItem(
    val id: String,
    val poolId: String,
    val userId: String,
    val introMessage: String,
    val status: String,
    val createdAt: String,
    val user: PoolHost,
)
