package com.example.romear.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.romear.feature.chat.data.ChatRepository
import com.example.romear.feature.chat.domain.ChatMessage
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
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error.asSharedFlow()

    val draft: StateFlow<String> = savedState.getStateFlow("draft", "")
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()
    private val _sent = MutableSharedFlow<Unit>()
    val sent: SharedFlow<Unit> = _sent.asSharedFlow()

    fun updateDraft(text: String) { savedState["draft"] = text }

    private var activeConversationId: String? = null
    private var refreshing = false
    private var acknowledgedId: String? = null

    fun initConversation(conversationId: String) {
        if (activeConversationId == conversationId) return
        activeConversationId = conversationId
        loadMessages()
    }

    fun loadMessages(silent: Boolean = false) {
        val convId = activeConversationId ?: return
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            if (!silent) _loading.value = true
            try {
                val list = repository.getMessages(convId)
                if (activeConversationId != convId) return@launch
                _messages.value = (_messages.value.filter { existing -> list.none { it.id == existing.id } } + list)
                    .distinctBy { it.id }.sortedWith(compareBy<ChatMessage> { it.createdAt }.thenBy { it.id })
                list.lastOrNull { !it.isOutgoing }?.let { latest ->
                    if (acknowledgedId != latest.id) {
                        repository.acknowledge(convId, latest.id)
                        acknowledgedId = latest.id
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (!silent) _error.emit(e.message ?: "Failed to load messages")
            } finally {
                _loading.value = false
                refreshing = false
            }
        }
    }

    fun sendMessage(body: String) {
        val convId = activeConversationId ?: return
        if (body.isBlank() || _sending.value) return
        _sending.value = true
        val normalized = body.trim()
        val key = if (savedState.get<String>("pendingBody") == normalized) {
            savedState.get<String>("pendingKey") ?: java.util.UUID.randomUUID().toString()
        } else java.util.UUID.randomUUID().toString()
        savedState["pendingBody"] = normalized
        savedState["pendingKey"] = key
        viewModelScope.launch {
            try {
                val sent = repository.sendMessage(convId, normalized, key)
                _messages.value = (_messages.value + sent).distinctBy { it.id }
                    .sortedWith(compareBy<ChatMessage> { it.createdAt }.thenBy { it.id })
                savedState.remove<String>("pendingBody")
                savedState.remove<String>("pendingKey")
                if (draft.value.trim() == body.trim()) updateDraft("")
                _sent.emit(Unit)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _error.emit(e.message ?: "Failed to send message")
            } finally {
                _sending.value = false
            }
        }
    }
}
