package com.example.romear.feature.hello.domain

data class HelloUser(
    val id: String,
    val displayName: String,
    val photoUrl: String?,
)

data class HelloItem(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val message: String,
    val status: String,
    val expiresAt: String,
    val createdAt: String,
    val sender: HelloUser?,
    val receiver: HelloUser?,
    val contextText: String? = null,
)

data class ConnectionItem(
    val id: String,
    val createdAt: String,
    val partner: HelloUser,
    val conversationId: String?,
)

data class SendHelloResult(
    val helloId: String,
    val status: String,
    val connected: Boolean,
    val connectionId: String?,
)
