package com.example.romear.feature.auth.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.romear.core.data.ConnectionResult
import com.example.romear.core.data.FoundationRepository
import com.example.romear.core.data.OnboardingPreferences
import com.example.romear.feature.auth.data.AuthRepository
import com.example.romear.feature.auth.domain.Account
import com.example.romear.feature.auth.domain.AuthFailure
import com.example.romear.feature.auth.domain.OnboardingStep
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val step: OnboardingStep = OnboardingStep.SPLASH,
    val loading: Boolean = false,
    val waking: Boolean = false,
    val error: String? = null,
    val resendSeconds: Int = 0,
    val hasPhoto: Boolean = false,
)
enum class AuthEvent { ENTER_APP }

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val foundation: FoundationRepository,
    private val saved: SavedStateHandle,
    private val preferences: OnboardingPreferences,
) : ViewModel() {
    private val mutable = MutableStateFlow(AuthUiState(step = saved.get<String>("step")?.let(OnboardingStep::valueOf) ?: OnboardingStep.SPLASH, hasPhoto = saved["hasPhoto"] ?: false))
    val state = mutable.asStateFlow()
    private val channel = Channel<AuthEvent>(Channel.BUFFERED)
    val events = channel.receiveAsFlow()
    private var request: Job? = null
    var phone: String
        get() = saved["phone"] ?: ""
        set(value) { if (value != phone) saved["requestId"] = null; saved["phone"] = value }
    var name: String
        get() = saved["name"] ?: ""
        set(value) { saved["name"] = value }
    var bio: String
        get() = saved["bio"] ?: ""
        set(value) { saved["bio"] = value }
    var loginMode: Boolean
        get() = saved["loginMode"] ?: false
        set(value) { saved["loginMode"] = value }
    var dob: String
        get() = saved["dob"] ?: ""
        set(value) { saved["dob"] = value }
    var interests: List<String>
        get() = saved.get<ArrayList<String>>("interests") ?: emptyList()
        set(value) { saved["interests"] = ArrayList(value) }

    init {
        if (mutable.value.step == OnboardingStep.SPLASH) restore()
        viewModelScope.launch {
            while (true) {
                val remaining = (((saved.get<Long>("resendAt") ?: 0) - System.currentTimeMillis() + 999) / 1000).coerceAtLeast(0).toInt()
                mutable.value = mutable.value.copy(resendSeconds = remaining)
                delay(1000)
            }
        }
    }
    private fun step(next: OnboardingStep) { saved["step"] = next.name; mutable.value = mutable.value.copy(step = next, error = null) }
    private fun perform(block: suspend () -> Unit) {
        if (request?.isActive == true) return
        request = viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, error = null)
            val slow = launch { delay(4000); mutable.value = mutable.value.copy(waking = true) }
            try { block() }
            catch (failure: AuthFailure) {
                if (failure.code == "sms_unavailable" || failure.code == "challenge_expired") saved["requestId"] = null
                if (failure.code == "under_age") { repository.clearSession(); step(OnboardingStep.UNDER_AGE) }
                else if (failure.code == "unauthorized") { repository.clearSession(); step(OnboardingStep.PHONE) }
                mutable.value = mutable.value.copy(error = failure.code)
                if (failure.retryAfterSeconds > 0) saved["resendAt"] = System.currentTimeMillis() + failure.retryAfterSeconds * 1000L
            } finally { slow.cancel(); mutable.value = mutable.value.copy(loading = false, waking = false) }
        }
    }
    fun restore() = perform {
        when (foundation.check()) {
            ConnectionResult.UpgradeRequired -> { step(OnboardingStep.UPDATE); return@perform }
            ConnectionResult.Maintenance -> { step(OnboardingStep.MAINTENANCE); return@perform }
            ConnectionResult.Unconfigured -> throw AuthFailure("unconfigured")
            ConnectionResult.Offline -> throw AuthFailure("offline")
            ConnectionResult.Unavailable -> throw AuthFailure("service_unavailable")
            ConnectionResult.Connected -> Unit
        }
        val account = repository.restore()
        if (account == null) step(OnboardingStep.WELCOME) else route(account)
    }
    fun begin(login: Boolean = false) { loginMode = login; step(OnboardingStep.PHONE) }
    fun editPhone() { step(OnboardingStep.PHONE) }
    fun requestCode(appHash: String? = null, resend: Boolean = false) {
        if (!phone.matches(Regex("[6-9][0-9]{9}"))) { mutable.value = mutable.value.copy(error = "invalid_phone"); return }
        if (state.value.resendSeconds > 0) return
        perform {
            val id = if (resend) UUID.randomUUID().toString() else saved.get<String>("requestId") ?: UUID.randomUUID().toString()
            saved["requestId"] = id
            val challenge = repository.requestOtp(phone, id, appHash)
            saved["challengeId"] = challenge.id
            saved["resendAt"] = challenge.resendAtMillis
            step(OnboardingStep.OTP)
        }
    }
    fun verify(code: String) {
        if (!code.matches(Regex("[0-9]{6}"))) { mutable.value = mutable.value.copy(error = "invalid_code"); return }
        perform { route(repository.verify(saved["challengeId"] ?: throw AuthFailure("challenge_expired"), code)) }
    }
    private suspend fun route(account: Account) {
        saved["hasPhoto"] = account.hasPhoto
        mutable.value = mutable.value.copy(hasPhoto = account.hasPhoto)
        when {
            account.status == "SUSPENDED" -> step(OnboardingStep.SUSPENDED)
            account.status == "DELETION_PENDING" -> step(OnboardingStep.DELETION_PENDING)
            !account.hasDateOfBirth -> step(OnboardingStep.DATE_OF_BIRTH)
            !account.hasProfile || !account.hasPhoto -> step(OnboardingStep.PROFILE)
            !account.guidelinesAccepted -> step(OnboardingStep.GUIDELINES)
            else -> { step(OnboardingStep.COMPLETE); channel.send(AuthEvent.ENTER_APP) }
        }
    }
    fun saveDob() = perform { route(repository.dateOfBirth(dob)) }
    fun saveProfile() {
        if (name.trim().length !in 2..30 || bio.length > 160 || interests.size !in 3..8 || !state.value.hasPhoto) { mutable.value = mutable.value.copy(error = "profile_incomplete"); return }
        perform { route(repository.profile(name.trim(), bio.trim(), interests)) }
    }
    fun upload(file: File) = perform {
        try { val account = repository.photo(file); saved["hasPhoto"] = account.hasPhoto; mutable.value = mutable.value.copy(hasPhoto = account.hasPhoto) }
        finally { file.delete() }
    }
    fun photoFailure() { mutable.value = mutable.value.copy(error = "photo_upload_failed") }
    fun acceptGuidelines() = perform { repository.acceptGuidelines(); step(OnboardingStep.LOCATION) }
    fun locationFinished() { step(OnboardingStep.NOTIFICATIONS) }
    fun notificationsFinished() { step(OnboardingStep.AVAILABILITY) }
    fun finish(availability: String) = perform {
        preferences.preferredAvailability(availability)
        step(OnboardingStep.COMPLETE)
        channel.send(AuthEvent.ENTER_APP)
    }
    fun signOut() = perform {
        try { repository.logout() }
        finally {
            listOf("phone", "name", "bio", "dob", "interests", "hasPhoto", "challengeId", "requestId", "resendAt").forEach { saved.remove<Any>(it) }
            mutable.value = AuthUiState(step = OnboardingStep.PHONE)
            step(OnboardingStep.PHONE)
        }
    }
}
