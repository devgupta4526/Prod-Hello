package com.example.romear.feature.profile.domain

data class MyProfile(
    val userId: String,
    val displayName: String,
    val bio: String?,
    val age: Int?,
    val photos: List<String>,
    val interests: List<String>,
    val availability: String?,
)
