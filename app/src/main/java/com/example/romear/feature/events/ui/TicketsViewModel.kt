package com.example.romear.feature.events.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.romear.feature.events.data.EventsRepository
import com.example.romear.feature.events.domain.EventItem
import com.example.romear.feature.events.domain.UserTicketItem
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
class TicketsViewModel @Inject constructor(
    private val eventsRepository: EventsRepository,
) : ViewModel() {

    enum class Tab { EXPLORE, MY_TICKETS }

    private val _currentTab = MutableStateFlow(Tab.EXPLORE)
    val currentTab: StateFlow<Tab> = _currentTab.asStateFlow()

    private val _events = MutableStateFlow<List<EventItem>>(emptyList())
    val events: StateFlow<List<EventItem>> = _events.asStateFlow()

    private val _tickets = MutableStateFlow<List<UserTicketItem>>(emptyList())
    val tickets: StateFlow<List<UserTicketItem>> = _tickets.asStateFlow()

    private var loadJob: Job? = null
    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _uiEvents = MutableSharedFlow<TicketsEvent>()
    val uiEvents: SharedFlow<TicketsEvent> = _uiEvents.asSharedFlow()

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
            Tab.EXPLORE -> loadEvents()
            Tab.MY_TICKETS -> loadMyTickets()
        }
    }

    private fun loadEvents() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _loading.value = true
            _loadFailed.value = false
            try {
                _events.value = eventsRepository.listEvents()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _loadFailed.value = true
            } finally {
                if (currentCoroutineContext().isActive) _loading.value = false
            }
        }
    }

    private fun loadMyTickets() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _loading.value = true
            _loadFailed.value = false
            try {
                _tickets.value = eventsRepository.listMyTickets()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _loadFailed.value = true
            } finally {
                if (currentCoroutineContext().isActive) _loading.value = false
            }
        }
    }

    fun bookTicket(eventId: String, tierId: String, quantity: Int, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val order = eventsRepository.createOrder(eventId, tierId, quantity)
                eventsRepository.confirmPayment(order.orderId)
                _uiEvents.emit(TicketsEvent.Success("Ticket booked successfully!"))
                onSuccess()
                switchTab(Tab.MY_TICKETS)
            } catch (e: Exception) {
                _uiEvents.emit(TicketsEvent.Error(e.message ?: "Failed to book tickets"))
            }
        }
    }

    sealed class TicketsEvent {
        data class Success(val message: String) : TicketsEvent()
        data class Error(val error: String) : TicketsEvent()
    }
}
