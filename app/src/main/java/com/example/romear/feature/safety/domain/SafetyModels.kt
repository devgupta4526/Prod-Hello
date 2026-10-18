package com.example.romear.feature.safety.domain

data class BlockedUserItem(
    val id: String,
    val blockedUserId: String,
    val displayName: String,
    val photoUrl: String?,
    val createdAt: String,
)

data class SafetyStatus(
    val suspended: Boolean,
    val pendingDeletion: Boolean,
    val deletionRequestedAt: String?,
)
