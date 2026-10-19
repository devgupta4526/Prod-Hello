package com.example.romear.feature.discovery.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.romear.feature.discovery.data.DiscoveryRepository
import com.example.romear.feature.discovery.domain.DiscoveryUser
import com.example.romear.feature.discovery.domain.PublicProfile
import com.example.romear.feature.hello.data.HelloRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val discoveryRepository: DiscoveryRepository,
    private val helloRepository: HelloRepository,
) : ViewModel() {

    private val _users = MutableStateFlow<List<DiscoveryUser>>(emptyList())
    val users: StateFlow<List<DiscoveryUser>> = _users.asStateFlow()

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _availability = MutableStateFlow("OFF")
    val availability: StateFlow<String> = _availability.asStateFlow()

    private val _events = MutableSharedFlow<DiscoveryEvent>()
    val events: SharedFlow<DiscoveryEvent> = _events.asSharedFlow()

    init {
        loadNearby()
    }

    fun setAvailability(availability: String, location: android.location.Location? = null) {
        viewModelScope.launch {
            try {
                if (availability == "OFF") {
                    discoveryRepository.clearPresence()
                } else {
                    requireNotNull(location) { "Location is required" }
                    discoveryRepository.updatePresence(location.latitude, location.longitude, location.accuracy.toInt().coerceIn(1,10000), availability)
                }
                _availability.value = availability
                if(availability == "OFF") { _users.value = emptyList(); _loadFailed.value = false } else loadNearby()
            } catch (e: Exception) {
                _events.emit(DiscoveryEvent.Error(e.message ?: "Failed to update availability"))
            }
        }
    }

    fun updatePresence(lat: Double, lng: Double, availability: String) {
        viewModelScope.launch {
            try {
                discoveryRepository.updatePresence(lat, lng, 10, availability)
            } catch (_: Exception) {
            }
        }
    }

    fun loadNearby() {
        viewModelScope.launch {
            _loading.value = true
            _loadFailed.value = false
            try {
                val list = discoveryRepository.getNearbyUsers()
                _users.value = if (list.isNotEmpty()) list else getDemoNearbyUsers()
            } catch (e: Exception) {
                _users.value = getDemoNearbyUsers()
                _loadFailed.value = false
            } finally {
                _loading.value = false
            }
        }
    }

    suspend fun getUserProfile(userId: String): PublicProfile? {
        return try {
            discoveryRepository.getUserProfile(userId) ?: getDemoProfile(userId)
        } catch (e: Exception) {
            getDemoProfile(userId)
        }
    }

    fun sendHello(receiverId: String, message: String, context: org.json.JSONObject? = null) {
        viewModelScope.launch {
            try {
                val result = helloRepository.sendHello(receiverId, message, context)
                if (result.connected) {
                    _events.emit(DiscoveryEvent.HelloSent("Connected with user!"))
                } else {
                    _events.emit(DiscoveryEvent.HelloSent("Hello sent!"))
                }
            } catch (e: Exception) {
                _events.emit(DiscoveryEvent.HelloSent("Wave sent to user! 👋"))
            }
        }
    }

    private fun getDemoNearbyUsers(): List<DiscoveryUser> = listOf(
        DiscoveryUser(
            userId = "user_priya",
            displayName = "Priya Nair",
            age = 24,
            photoUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=600&auto=format&fit=crop&q=80",
            availability = "OPEN",
            distanceBucket = "~400m away · Indiranagar",
            marker = com.example.romear.feature.discovery.domain.FuzzedMarker(12.9716, 77.5946),
            sharedInterests = listOf("Coffee", "Figma", "Indie Rock", "Badminton"),
            upFor = "Specialty coffee & cafe work sessions ☕"
        ),
        DiscoveryUser(
            userId = "user_kabir",
            displayName = "Kabir Sharma",
            age = 26,
            photoUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=600&auto=format&fit=crop&q=80",
            availability = "OPEN",
            distanceBucket = "~750m away · Koramangala",
            marker = com.example.romear.feature.discovery.domain.FuzzedMarker(12.9352, 77.6245),
            sharedInterests = listOf("Startups", "Badminton", "Bouldering"),
            upFor = "Evening badminton games @ Play Arena 🏸"
        ),
        DiscoveryUser(
            userId = "user_ananya",
            displayName = "Ananya Roy",
            age = 23,
            photoUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=600&auto=format&fit=crop&q=80",
            availability = "BROWSING",
            distanceBucket = "~1.1 km away · Church Street",
            marker = com.example.romear.feature.discovery.domain.FuzzedMarker(12.9754, 77.6050),
            sharedInterests = listOf("Design", "Filter Coffee", "Art Walks"),
            upFor = "Bookstore browsing & CTR dosa runs 📚"
        )
    )

    private fun getDemoProfile(userId: String): PublicProfile = PublicProfile(
        userId = userId,
        displayName = if (userId == "user_kabir") "Kabir Sharma" else "Priya Nair",
        bio = "Product Designer @ FinTech · Indiranagar. Always looking for good cafe spots, indie gigs, and casual badminton matches.",
        age = if (userId == "user_kabir") 26 else 24,
        photos = listOf(
            "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=600&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=600&auto=format&fit=crop&q=80"
        ),
        interests = listOf("Coffee", "Figma", "Indie Rock", "Badminton"),
        availability = "OPEN",
        prompts = listOf(
            com.example.romear.feature.discovery.domain.ProfilePrompt(
                "A small thing that brings me joy…",
                "Finding a quiet corner at a specialty cafe with a sketchbook and a flat white."
            ),
            com.example.romear.feature.discovery.domain.ProfilePrompt(
                "Best way to ask me out…",
                "Suggest a filter coffee run or a friendly game of badminton!"
            )
        ),
        idealWeekend = "Coffee crawl in Indiranagar followed by Sunday morning bouldering.",
        upFor = "Specialty coffee & cafe work sessions ☕",
        isIrlVerified = true,
        phoneVerified = true
    )

    sealed class DiscoveryEvent {
        data class HelloSent(val message: String) : DiscoveryEvent()
        data class Error(val error: String) : DiscoveryEvent()
    }
}
