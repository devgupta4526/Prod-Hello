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
import kotlinx.coroutines.isActive
import javax.inject.Inject

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val discoveryRepository: DiscoveryRepository,
    private val helloRepository: HelloRepository,
    private val savedState: androidx.lifecycle.SavedStateHandle,
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
    val filterRadius: Float get() = savedState["filterRadius"] ?: 5f
    val filterMinAge: Int get() = savedState["filterMinAge"] ?: 21
    val filterMaxAge: Int get() = savedState["filterMaxAge"] ?: 30
    val filterVerified: Boolean get() = savedState["filterVerified"] ?: false
    val filterInterests: String get() = savedState["filterInterests"] ?: ""
    private var loadJob: kotlinx.coroutines.Job? = null

    fun applyFilters(radius: Float, minimumAge: Int, maximumAge: Int, verified: Boolean, interests: String) {
        savedState["filterRadius"] = radius
        savedState["filterMinAge"] = minimumAge
        savedState["filterMaxAge"] = maximumAge
        savedState["filterVerified"] = verified
        savedState["filterInterests"] = interests
        savedState["hasFilters"] = true
        loadNearby()
    }

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
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _loading.value = true
            _loadFailed.value = false
            try {
                val list = discoveryRepository.getNearbyUsers(filterRadius.toDouble(), filterInterests.takeIf { it.isNotBlank() })
                val hasFilters = savedState.get<Boolean>("hasFilters") == true
                _users.value = list.filter { user ->
                    (!hasFilters || (user.age?.let { it in filterMinAge..filterMaxAge } == true)) &&
                        (!filterVerified || user.isIrlVerified)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _users.value = emptyList()
                _loadFailed.value = true
            } finally {
                if (kotlinx.coroutines.currentCoroutineContext().isActive) _loading.value = false
            }
        }
    }

    suspend fun getUserProfile(userId: String): PublicProfile? {
        return try {
            discoveryRepository.getUserProfile(userId)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            _events.emit(DiscoveryEvent.Error(e.message ?: "Could not load this profile"))
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
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _events.emit(DiscoveryEvent.Error(e.message ?: "Your Hello could not be sent. Please retry."))
            }
        }
    }

    sealed class DiscoveryEvent {
        data class HelloSent(val message: String) : DiscoveryEvent()
        data class Error(val error: String) : DiscoveryEvent()
    }
}

