package com.example.romear.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.romear.feature.auth.data.AccountSession
import com.example.romear.feature.auth.data.AuthRepository
import com.example.romear.feature.discovery.data.DiscoveryRepository
import com.example.romear.feature.profile.data.ProfileRepository
import com.example.romear.feature.profile.domain.MyProfile
import com.example.romear.feature.safety.data.SafetyRepository
import com.example.romear.feature.safety.domain.BlockedUserItem
import com.example.romear.feature.safety.domain.SafetyStatus
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
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val safetyRepository: SafetyRepository,
    private val discoveryRepository: DiscoveryRepository,
) : ViewModel() {

    private val _profile = MutableStateFlow<MyProfile?>(null)
    val profile: StateFlow<MyProfile?> = _profile.asStateFlow()

    private val _sessions = MutableStateFlow<List<AccountSession>>(emptyList())
    val sessions: StateFlow<List<AccountSession>> = _sessions.asStateFlow()

    private val _safetyStatus = MutableStateFlow<SafetyStatus?>(null)
    val safetyStatus: StateFlow<SafetyStatus?> = _safetyStatus.asStateFlow()

    private val _blockedUsers = MutableStateFlow<List<BlockedUserItem>>(emptyList())
    val blockedUsers: StateFlow<List<BlockedUserItem>> = _blockedUsers.asStateFlow()

    private val _events = MutableSharedFlow<ProfileEvent>()
    val events: SharedFlow<ProfileEvent> = _events.asSharedFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            try {
                _profile.value = profileRepository.getMyProfile()
            } catch (_: Exception) {}
            try {
                _sessions.value = authRepository.sessions()
            } catch (_: Exception) {}
            try {
                _safetyStatus.value = safetyRepository.getSafetyStatus()
            } catch (_: Exception) {}
        }
    }

    fun updateAvailability(availability: String, location: android.location.Location? = null) {
        viewModelScope.launch {
            try {
                if (availability == "OFF") {
                    discoveryRepository.clearPresence()
                } else {
                    requireNotNull(location) { "Location is required" }
                    discoveryRepository.updatePresence(location.latitude, location.longitude, location.accuracy.toInt().coerceIn(1,10000), availability)
                }
                _profile.value = _profile.value?.copy(availability = availability)
                _events.emit(ProfileEvent.Success("Availability updated"))
            } catch (e: Exception) {
                _events.emit(ProfileEvent.Error(e.message ?: "Failed to update availability"))
            }
        }
    }

    fun revokeSession(session: AccountSession) {
        viewModelScope.launch {
            try {
                authRepository.revokeSession(session.id)
                if (session.current) {
                    authRepository.clearSession()
                    _events.emit(ProfileEvent.SignedOut)
                } else {
                    _sessions.value = authRepository.sessions()
                }
            } catch (e: Exception) {
                _events.emit(ProfileEvent.Error(e.message ?: "Failed to revoke session"))
            }
        }
    }

    fun loadBlockedUsers() {
        viewModelScope.launch {
            try {
                _blockedUsers.value = safetyRepository.listBlocks()
            } catch (e: Exception) {
                _events.emit(ProfileEvent.Error(e.message ?: "Failed to load blocked users"))
            }
        }
    }

    fun unblockUser(userId: String) {
        viewModelScope.launch {
            try {
                safetyRepository.unblockUser(userId)
                _blockedUsers.value = _blockedUsers.value.filter { it.blockedUserId != userId }
                _events.emit(ProfileEvent.Success("User unblocked"))
            } catch (e: Exception) {
                _events.emit(ProfileEvent.Error(e.message ?: "Failed to unblock"))
            }
        }
    }

    fun requestDataExport() {
        viewModelScope.launch {
            try {
                profileRepository.requestDataExport()
                _events.emit(ProfileEvent.Success("Data export requested. You will receive an update shortly."))
            } catch (e: Exception) {
                _events.emit(ProfileEvent.Error(e.message ?: "Export request failed"))
            }
        }
    }

    fun requestAccountDeletion() {
        viewModelScope.launch {
            try {
                val scheduled = profileRepository.requestAccountDeletion()
                _safetyStatus.value = _safetyStatus.value?.copy(pendingDeletion = true, deletionRequestedAt = scheduled)
                _events.emit(ProfileEvent.Success("Account deletion scheduled for $scheduled. You can cancel within 30 days."))
            } catch (e: Exception) {
                _events.emit(ProfileEvent.Error(e.message ?: "Account deletion request failed"))
            }
        }
    }

    fun cancelAccountDeletion() {
        viewModelScope.launch {
            try {
                profileRepository.cancelAccountDeletion()
                _safetyStatus.value = _safetyStatus.value?.copy(pendingDeletion = false, deletionRequestedAt = null)
                _events.emit(ProfileEvent.Success("Account deletion cancelled"))
            } catch (e: Exception) {
                _events.emit(ProfileEvent.Error(e.message ?: "Failed to cancel deletion"))
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                authRepository.logout()
            } catch (_: Exception) {}
            finally {
                _events.emit(ProfileEvent.SignedOut)
            }
        }
    }

    sealed class ProfileEvent {
        data class Success(val message: String) : ProfileEvent()
        data class Error(val error: String) : ProfileEvent()
        data object SignedOut : ProfileEvent()
    }
}
