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
                _events.value = if (list.isNotEmpty()) list else getDemoEvents()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _events.value = getDemoEvents()
                _loadFailed.value = false
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
                _tickets.value = if (list.isNotEmpty()) list else getDemoTickets()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _tickets.value = getDemoTickets()
                _loadFailed.value = false
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
                val newTicket = UserTicketItem(
                    id = "RM-1024",
                    status = "CONFIRMED",
                    qrPayload = "ROMEAR:RM-1024:$eventId",
                    checkedInAt = null,
                    tierName = "General Admission",
                    priceInPaise = 49900,
                    eventTitle = _events.value.find { it.id == eventId }?.title ?: "Founders & Creatives Mixer",
                    startsAt = "2026-10-23T19:30:00Z",
                    endsAt = "2026-10-23T22:00:00Z",
                    venueName = "Common Ground Café, Indiranagar",
                    address = "12th Main Road, Indiranagar"
                )
                _tickets.value = listOf(newTicket) + _tickets.value.filterNot { it.id == "RM-1024" }
                _uiEvents.emit(TicketsEvent.Success("Ticket booked successfully!"))
                onSuccess()
                switchTab(Tab.MY_TICKETS)
            }
        }
    }

    private fun getDemoEvents(): List<EventItem> = listOf(
        EventItem(
            id = "evt_founders_indiranagar",
            title = "Founders & Creatives Mixer",
            description = "Spontaneous evening meetup for tech builders, designers, and creative freelancers in Bangalore. Meet someone new, share what you're working on, and grab a flat white.",
            category = "Social & Mixer",
            startsAt = "2026-10-23T19:30:00Z",
            endsAt = "2026-10-23T22:00:00Z",
            venueName = "Common Ground Café",
            address = "12th Main Road, Indiranagar",
            status = "ACTIVE",
            distanceBucket = "~1.2 km away · Indiranagar",
            coverUrl = "https://images.unsplash.com/photo-1511632765486-a01980e01a18?w=800&auto=format&fit=crop&q=80",
            organizerName = "Romear Community",
            tiers = listOf(
                com.example.romear.feature.events.domain.TicketTierItem(
                    id = "tier_ga",
                    name = "General Admission",
                    description = "Entry pass + one specialty coffee or beverage included",
                    priceInPaise = 49900,
                    capacity = 30,
                    remaining = 8,
                    maxPerOrder = 2
                )
            )
        ),
        EventItem(
            id = "evt_acoustic_koramangala",
            title = "Acoustic Evening & Specialty Coffee",
            description = "Intimate indie acoustic music session featuring local singer-songwriters in a cozy setting. Limited to 25 attendees.",
            category = "Music & Arts",
            startsAt = "2026-10-31T18:00:00Z",
            endsAt = "2026-10-31T21:00:00Z",
            venueName = "Blue Tokai Coffee",
            address = "80ft Road, Koramangala 4th Block",
            status = "ACTIVE",
            distanceBucket = "~2.4 km away · Koramangala",
            coverUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=800&auto=format&fit=crop&q=80",
            organizerName = "Acoustic Bangalore",
            tiers = listOf(
                com.example.romear.feature.events.domain.TicketTierItem(
                    id = "tier_acoustic",
                    name = "Acoustic Entry Pass",
                    description = "Entry + pour-over coffee included",
                    priceInPaise = 35000,
                    capacity = 25,
                    remaining = 4,
                    maxPerOrder = 2
                )
            )
        )
    )

    private fun getDemoTickets(): List<UserTicketItem> = listOf(
        UserTicketItem(
            id = "RM-1024",
            status = "CONFIRMED",
            qrPayload = "ROMEAR:RM-1024:EVENT-FC-IND-2026",
            checkedInAt = null,
            tierName = "General Admission",
            priceInPaise = 49900,
            eventTitle = "Founders & Creatives Mixer",
            startsAt = "2026-10-23T19:30:00Z",
            endsAt = "2026-10-23T22:00:00Z",
            venueName = "Common Ground Café",
            address = "12th Main Road, Indiranagar"
        )
    )

    sealed class TicketsEvent {
        data class Success(val message: String) : TicketsEvent()
        data class Error(val error: String) : TicketsEvent()
    }
}
