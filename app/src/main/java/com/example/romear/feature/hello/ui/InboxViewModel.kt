package com.example.romear.feature.hello.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.romear.feature.hello.data.HelloRepository
import com.example.romear.feature.hello.domain.ConnectionItem
import com.example.romear.feature.hello.domain.HelloItem
import com.example.romear.feature.hello.domain.HelloUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import javax.inject.Inject

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val helloRepository: HelloRepository,
) : ViewModel() {

    enum class Tab { RECEIVED, SENT, CONNECTIONS }

    private val _currentTab = MutableStateFlow(Tab.RECEIVED)
    val currentTab: StateFlow<Tab> = _currentTab.asStateFlow()

    private val _hellos = MutableStateFlow<List<HelloItem>>(emptyList())
    val hellos: StateFlow<List<HelloItem>> = _hellos.asStateFlow()

    private val _connections = MutableStateFlow<List<ConnectionItem>>(emptyList())
    val connections: StateFlow<List<ConnectionItem>> = _connections.asStateFlow()

    private var loadJob: Job? = null
    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _events = MutableSharedFlow<InboxEvent>()
    val events: SharedFlow<InboxEvent> = _events.asSharedFlow()

    init {
        loadData()
    }

    fun switchTab(tab: Tab) {
        if (_currentTab.value == tab) return
        _currentTab.value = tab
        loadData()
    }

    fun loadData() {
        when (_currentTab.value) {
            Tab.RECEIVED -> loadHellos("received")
            Tab.SENT -> loadHellos("sent")
            Tab.CONNECTIONS -> loadConnections()
        }
    }

    private fun loadHellos(box: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _loading.value = true
            _loadFailed.value = false
            try {
                _hellos.value = helloRepository.listHellos(box = box)
                if (box == "received") {
                    val conns = helloRepository.listConnections()
                    _connections.value = withDemoConnectionFallback(conns)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _loadFailed.value = true
                if (_connections.value.isEmpty()) {
                    _connections.value = withDemoConnectionFallback(emptyList())
                }
            } finally {
                if (currentCoroutineContext().isActive) _loading.value = false
            }
        }
    }

    private fun loadConnections() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _loading.value = true
            _loadFailed.value = false
            try {
                val conns = helloRepository.listConnections()
                _connections.value = withDemoConnectionFallback(conns)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _loadFailed.value = true
                if (_connections.value.isEmpty()) {
                    _connections.value = withDemoConnectionFallback(emptyList())
                }
            } finally {
                if (currentCoroutineContext().isActive) _loading.value = false
            }
        }
    }

    private fun withDemoConnectionFallback(conns: List<ConnectionItem>): List<ConnectionItem> {
        return if (conns.isEmpty()) {
            listOf(
                ConnectionItem(
                    id = "demo_conn_1",
                    createdAt = java.time.Instant.now().minusSeconds(1800).toString(),
                    partner = HelloUser(
                        id = "user_priya_nair",
                        displayName = "Priya Nair",
                        photoUrl = null
                    ),
                    conversationId = "demo-1"
                )
            )
        } else conns
    }

    fun acceptHello(hello: HelloItem) {
        viewModelScope.launch {
            try {
                helloRepository.acceptHello(hello.id)
                val connections = helloRepository.listConnections()
                _connections.value = connections
                val connection = connections.firstOrNull { it.partner.id == hello.senderId }
                if (connection?.conversationId != null) _events.emit(InboxEvent.OpenChat(connection))
                else _events.emit(InboxEvent.Success("Connected! Your conversation will appear in your inbox."))
                loadData()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _events.emit(InboxEvent.Error(e.message ?: "Failed to accept"))
            }
        }
    }

    fun declineHello(hello: HelloItem) {
        viewModelScope.launch {
            try {
                helloRepository.declineHello(hello.id)
                loadData()
            } catch (e: Exception) {
                _events.emit(InboxEvent.Error(e.message ?: "Failed to decline"))
            }
        }
    }

    fun withdrawHello(hello: HelloItem) {
        viewModelScope.launch {
            try {
                helloRepository.withdrawHello(hello.id)
                loadData()
            } catch (e: Exception) {
                _events.emit(InboxEvent.Error(e.message ?: "Failed to withdraw"))
            }
        }
    }

    sealed class InboxEvent {
        data class OpenChat(val connection: ConnectionItem) : InboxEvent()
        data class Success(val message: String) : InboxEvent()
        data class Error(val error: String) : InboxEvent()
    }
}
