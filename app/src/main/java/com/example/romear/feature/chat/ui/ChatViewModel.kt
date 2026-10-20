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
                val merged = (_messages.value.filter { existing -> list.none { it.id == existing.id } } + list)
                    .distinctBy { it.id }.sortedWith(compareBy<ChatMessage> { it.createdAt }.thenBy { it.id })
                _messages.value = merged
                if (merged.isEmpty() && convId.startsWith("demo")) {
                    _messages.value = getDemoSeedMessages(convId)
                }
                list.lastOrNull { !it.isOutgoing }?.let { latest ->
                    if (acknowledgedId != latest.id) {
                        repository.acknowledge(convId, latest.id)
                        acknowledgedId = latest.id
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (_messages.value.isEmpty() && convId.startsWith("demo")) {
                    _messages.value = getDemoSeedMessages(convId)
                } else if (!silent) {
                    _error.emit(e.message ?: "Failed to load messages")
                }
            } finally {
                _loading.value = false
                refreshing = false
            }
        }
    }

    private fun getDemoSeedMessages(convId: String): List<ChatMessage> {
        val now = java.time.Instant.now()
        return listOf(
            ChatMessage(
                id = "msg_seed_1",
                conversationId = convId,
                senderId = "partner",
                body = "Hey! Saw you're also nearby. Are you at the third wave coffee? ☕",
                createdAt = now.minusSeconds(180).toString(),
                isOutgoing = false,
                readAt = null,
                deliveredAt = now.minusSeconds(170).toString()
            ),
            ChatMessage(
                id = "msg_seed_2",
                conversationId = convId,
                senderId = "me",
                body = "Hey Priya! Yes, just grabbed a table by the window. Great spot!",
                createdAt = now.minusSeconds(110).toString(),
                isOutgoing = true,
                readAt = now.minusSeconds(100).toString(),
                deliveredAt = now.minusSeconds(105).toString()
            ),
            ChatMessage(
                id = "msg_seed_3",
                conversationId = convId,
                senderId = "partner",
                body = "Awesome! Up for a quick filter coffee hangout?",
                createdAt = now.minusSeconds(45).toString(),
                isOutgoing = false,
                readAt = null,
                deliveredAt = now.minusSeconds(40).toString()
            )
        )
    }

    fun sendMessage(body: String) {
        val convId = activeConversationId ?: return
        if (body.isBlank() || _sending.value) return
        _sending.value = true
        val normalized = body.trim()
        val tempId = "msg_local_" + System.currentTimeMillis()
        val localMsg = ChatMessage(
            id = tempId,
            conversationId = convId,
            senderId = "me",
            body = normalized,
            createdAt = java.time.Instant.now().toString(),
            isOutgoing = true,
            readAt = null,
            deliveredAt = java.time.Instant.now().toString()
        )
        // Immediate optimistic UI update
        _messages.value = (_messages.value + localMsg).distinctBy { it.id }
            .sortedWith(compareBy<ChatMessage> { it.createdAt }.thenBy { it.id })
        if (draft.value.trim() == body.trim()) updateDraft("")
        viewModelScope.launch { _sent.emit(Unit) }

        if (convId.startsWith("demo")) {
            _sending.value = false
            // Simulate realistic reply from Priya Nair after 1.5s
            viewModelScope.launch {
                kotlinx.coroutines.delay(1400)
                val replyText = when {
                    normalized.contains("coffee", ignoreCase = true) -> "Sounds fantastic! I'll walk over in 5 mins ☕✨"
                    normalized.contains("meet", ignoreCase = true) || normalized.contains("plan", ignoreCase = true) -> "Let's definitely do it! I'm right around the corner."
                    normalized.contains("hey", ignoreCase = true) || normalized.contains("hello", ignoreCase = true) -> "Hey there! Great to connect with you too! 👋"
                    else -> "Sounds great! Let's connect right by the cafe entrance."
                }
                val reply = ChatMessage(
                    id = "msg_reply_" + System.currentTimeMillis(),
                    conversationId = convId,
                    senderId = "partner",
                    body = replyText,
                    createdAt = java.time.Instant.now().toString(),
                    isOutgoing = false,
                    readAt = null,
                    deliveredAt = java.time.Instant.now().toString()
                )
                _messages.value = (_messages.value + reply).distinctBy { it.id }
                    .sortedWith(compareBy<ChatMessage> { it.createdAt }.thenBy { it.id })
            }
            return
        }

        val key = if (savedState.get<String>("pendingBody") == normalized) {
            savedState.get<String>("pendingKey") ?: java.util.UUID.randomUUID().toString()
        } else java.util.UUID.randomUUID().toString()
        savedState["pendingBody"] = normalized
        savedState["pendingKey"] = key
        viewModelScope.launch {
            try {
                val sent = repository.sendMessage(convId, normalized, key)
                _messages.value = (_messages.value.filter { it.id != tempId } + sent).distinctBy { it.id }
                    .sortedWith(compareBy<ChatMessage> { it.createdAt }.thenBy { it.id })
                savedState.remove<String>("pendingBody")
                savedState.remove<String>("pendingKey")
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
