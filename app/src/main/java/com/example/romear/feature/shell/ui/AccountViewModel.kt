package com.example.romear.feature.shell.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.romear.feature.auth.data.AccountSession
import com.example.romear.feature.auth.data.AuthRepository
import com.example.romear.feature.auth.domain.AuthFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class AccountUiState(
    val loading: Boolean = false,
    val sessions: List<AccountSession> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class AccountViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountUiState())
    val state = mutableState.asStateFlow()
    private val signedOut = Channel<Unit>(Channel.BUFFERED)
    val events = signedOut.receiveAsFlow()

    fun loadSessions() = perform { repository.sessions() }

    fun revoke(session: AccountSession) {
        if (session.current) signOut()
        else perform {
            repository.revokeSession(session.id)
            repository.sessions()
        }
    }

    private fun perform(action: suspend () -> List<AccountSession>) {
        if (mutableState.value.loading) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            try {
                mutableState.value = AccountUiState(sessions = action())
            } catch (failure: AuthFailure) {
                mutableState.value = mutableState.value.copy(loading = false, error = failure.code)
            }
        }
    }

    fun signOut() {
        if (mutableState.value.loading) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            try {
                repository.logout()
            } catch (_: AuthFailure) {
                // Local credentials are cleared even when the server cannot be reached.
            }
            mutableState.value = AccountUiState()
            signedOut.send(Unit)
        }
    }
}
