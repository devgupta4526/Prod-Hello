package com.example.romear.feature.pools.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.romear.feature.pools.data.PoolsRepository
import com.example.romear.feature.pools.domain.PoolDetail
import com.example.romear.feature.pools.domain.PoolItem
import com.example.romear.feature.pools.domain.PoolKnockItem
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
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class HappeningViewModel @Inject constructor(
    private val poolsRepository: PoolsRepository,
) : ViewModel() {

    private val _pools = MutableStateFlow<List<PoolItem>>(emptyList())
    val pools: StateFlow<List<PoolItem>> = _pools.asStateFlow()

    private var loadJob: Job? = null
    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _events = MutableSharedFlow<HappeningEvent>()
    val events: SharedFlow<HappeningEvent> = _events.asSharedFlow()

    init {
        loadPools()
    }

    fun loadPools() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _loading.value = true
            _loadFailed.value = false
            try {
                _pools.value = poolsRepository.listPools()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _loadFailed.value = true
            } finally {
                if (currentCoroutineContext().isActive) _loading.value = false
            }
        }
    }

    fun createPool(title: String, activity: String, capacity: Int) {
        viewModelScope.launch {
            try {
                val now = Instant.now()
                val start = now.plus(1, ChronoUnit.HOURS).toString()
                val end = now.plus(4, ChronoUnit.HOURS).toString()
                poolsRepository.createPool(
                    title = title,
                    activity = activity,
                    lat = 28.6315,
                    lng = 77.2167,
                    timeWindowStart = start,
                    timeWindowEnd = end,
                    capacity = capacity,
                )
                _events.emit(HappeningEvent.Success("Pool hosted!"))
                loadPools()
            } catch (e: Exception) {
                _events.emit(HappeningEvent.Error(e.message ?: "Failed to create pool"))
            }
        }
    }

    suspend fun getPoolDetail(id: String): PoolDetail? {
        return try {
            poolsRepository.getPool(id)
        } catch (e: Exception) {
            _events.emit(HappeningEvent.Error(e.message ?: "Failed to load pool details"))
            null
        }
    }

    suspend fun listKnocks(poolId: String): List<PoolKnockItem> {
        return try {
            poolsRepository.listKnocks(poolId)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun knock(poolId: String, intro: String) {
        viewModelScope.launch {
            try {
                poolsRepository.knock(poolId, intro)
                _events.emit(HappeningEvent.Success("Knock sent to host!"))
            } catch (e: Exception) {
                _events.emit(HappeningEvent.Error(e.message ?: "Failed to knock"))
            }
        }
    }

    fun acceptKnock(poolId: String, knockId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                poolsRepository.acceptKnock(poolId, knockId)
                _events.emit(HappeningEvent.Success("Knock accepted!"))
                onDone()
                loadPools()
            } catch (e: Exception) {
                _events.emit(HappeningEvent.Error(e.message ?: "Failed to accept knock"))
            }
        }
    }

    fun declineKnock(poolId: String, knockId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                poolsRepository.declineKnock(poolId, knockId)
                onDone()
            } catch (e: Exception) {
                _events.emit(HappeningEvent.Error(e.message ?: "Failed to decline knock"))
            }
        }
    }

    fun leavePool(poolId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                poolsRepository.leavePool(poolId)
                _events.emit(HappeningEvent.Success("Left pool"))
                onDone()
                loadPools()
            } catch (e: Exception) {
                _events.emit(HappeningEvent.Error(e.message ?: "Failed to leave pool"))
            }
        }
    }

    fun closePool(poolId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                poolsRepository.closePool(poolId)
                _events.emit(HappeningEvent.Success("Pool closed"))
                onDone()
                loadPools()
            } catch (e: Exception) {
                _events.emit(HappeningEvent.Error(e.message ?: "Failed to close pool"))
            }
        }
    }

    sealed class HappeningEvent {
        data class Success(val message: String) : HappeningEvent()
        data class Error(val error: String) : HappeningEvent()
    }
}
