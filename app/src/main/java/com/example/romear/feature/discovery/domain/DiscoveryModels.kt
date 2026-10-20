package com.example.romear.feature.discovery.domain

data class FuzzedMarker(
    val lat: Double,
    val lng: Double,
)

data class DiscoveryUser(
    val userId: String,
    val displayName: String,
    val age: Int?,
    val photoUrl: String?,
    val availability: String,
    val distanceBucket: String,
    val marker: FuzzedMarker,
    val sharedInterests: List<String>,
    val upFor: String? = null,
    val isIrlVerified: Boolean = false,
)

data class ProfilePrompt(val id: String, val answer: String)

data class PublicProfile(
    val userId: String,
    val displayName: String,
    val bio: String?,
    val age: Int?,
    val photos: List<String>,
    val interests: List<String>,
    val availability: String?,
    val photoIds: List<String> = emptyList(),
    val prompts: List<ProfilePrompt> = emptyList(),
    val idealWeekend: String = "",
    val upFor: String? = null,
    val upForUntil: String? = null,
    val phoneVerified: Boolean = false,
    val voiceBioUrl: String? = null,
    val isIrlVerified: Boolean = false,
)

data class LiveStatusNote(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val noteText: String,
    val isCurrentUser: Boolean = false,
)

data class DailyIcebreaker(
    val id: String,
    val question: String,
    val totalAnswers: Int = 18,
    val expiresAtLabel: String = "4h left",
)
