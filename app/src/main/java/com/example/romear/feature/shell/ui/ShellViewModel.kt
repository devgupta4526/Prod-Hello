package com.example.romear.feature.shell.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.romear.core.data.ConnectionResult
import com.example.romear.core.data.FoundationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ConnectionUiState {
    data object Idle : ConnectionUiState
    data class Loading(val waking: Boolean = false) : ConnectionUiState
    data class Finished(val result: ConnectionResult) : ConnectionUiState
}

@HiltViewModel
class ShellViewModel @Inject constructor(private val repository: FoundationRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Idle)
    val state = mutableState.asStateFlow()
    private var request: Job? = null

    fun checkConnection() {
        if (request?.isActive == true) return
        request = viewModelScope.launch {
            mutableState.value = ConnectionUiState.Loading()
            val waking = launch {
                delay(4000)
                mutableState.value = ConnectionUiState.Loading(waking = true)
            }
            try {
                mutableState.value = ConnectionUiState.Finished(repository.check())
            } finally {
                waking.cancel()
            }
        }
    }
}
