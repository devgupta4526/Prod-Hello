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
    private val savedState: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {

    enum class Tab { EXPLORE, MY_TICKETS }

    private val _currentTab = MutableStateFlow(Tab.EXPLORE)
    val currentTab: StateFlow<Tab> = _currentTab.asStateFlow()

    private val _events = MutableStateFlow<List<EventItem>>(emptyList())
    val events: StateFlow<List<EventItem>> = _events.asStateFlow()
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    fun search(value: String) { _query.value = value }
    suspend fun eventDetail(id: String) = eventsRepository.getEvent(id)

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
                val list = eventsRepository.listEvents()
                _events.value = list
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
                val list = eventsRepository.listMyTickets()
                _tickets.value = list
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _loadFailed.value = true
            } finally {
                if (currentCoroutineContext().isActive) _loading.value = false
            }
        }
    }

    private var booking = false
    fun bookTicket(eventId: String, tierId: String, quantity: Int, onFailure: () -> Unit = {}, onSuccess: () -> Unit) {
        if (booking) return
        booking = true
        viewModelScope.launch {
            try {
                val detail = eventsRepository.getEvent(eventId)
                val tier = detail.tiers.firstOrNull { it.id == tierId } ?: error("Choose an available ticket")
                require(tier.priceInPaise == 0) { "Paid checkout is not configured for this release. No payment has been taken." }
                val selection = "$eventId:$tierId:$quantity"
                val key = if (savedState.get<String>("orderSelection") == selection) savedState.get<String>("orderKey") ?: java.util.UUID.randomUUID().toString() else java.util.UUID.randomUUID().toString()
                savedState["orderSelection"] = selection
                savedState["orderKey"] = key
                val order = eventsRepository.createOrder(eventId, tierId, quantity, key)
                require(order.status == "PAID") { "Your booking is awaiting confirmation. Please check My Tickets before retrying." }
                savedState.remove<String>("orderSelection")
                savedState.remove<String>("orderKey")
                _uiEvents.emit(TicketsEvent.Success("Ticket booked successfully!"))
                onSuccess()
                switchTab(Tab.MY_TICKETS)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                onFailure()
                _uiEvents.emit(TicketsEvent.Error(failure.message ?: "Booking failed. Please retry."))
            } finally { booking = false }
        }
    }
    sealed class TicketsEvent {
        data class Success(val message: String) : TicketsEvent()
        data class Error(val error: String) : TicketsEvent()
    }
}

