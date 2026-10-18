package com.example.romear.feature.chat.domain

data class ChatPartner(
    val id: String,
    val displayName: String,
    val photoUrl: String?,
)

data class ConversationItem(
    val id: String,
    val partner: ChatPartner,
    val lastMessageAt: String?,
    val unreadCount: Int,
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val body: String,
    val createdAt: String,
    val isOutgoing: Boolean,
    val readAt: String?,
)
