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
                _users.value = list
            } catch (e: Exception) {
                _loadFailed.value = true
            } finally {
                _loading.value = false
            }
        }
    }

    suspend fun getUserProfile(userId: String): PublicProfile? {
        return try {
            discoveryRepository.getUserProfile(userId)
        } catch (e: Exception) {
            _events.emit(DiscoveryEvent.Error(e.message ?: "Failed to load profile"))
            null
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
                _events.emit(DiscoveryEvent.Error(e.message ?: "Failed to send Hello"))
            }
        }
    }

    sealed class DiscoveryEvent {
        data class HelloSent(val message: String) : DiscoveryEvent()
        data class Error(val error: String) : DiscoveryEvent()
    }
}
