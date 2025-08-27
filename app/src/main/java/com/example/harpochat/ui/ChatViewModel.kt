package com.example.harpochat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel minimal pour le chat, compatible avec:
 * data class ChatMsg(val id: String, val text: String, val time: Any, val mine: Boolean)
 */
class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMsg>>(emptyList())
    val messages = _messages.asStateFlow()

    /** À appeler depuis ChatActivity si tu veux précharger un fil */
    fun loadThread(threadId: String? = null) {
        // Exemple de seed: vide par défaut
        _messages.value = emptyList()
    }

    /** Envoie un message "moi" */
    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val msg = ChatMsg(
            id   = UUID.randomUUID().toString(),
            text = trimmed,
            time = System.currentTimeMillis(),   // Long epoch -> formaté en "HH:mm" par ChatScreen
            isMine = true
        )
        viewModelScope.launch {
            _messages.value = _messages.value + msg

            // (facultatif) petite réponse simulée pour tester l’UI
            delay(500)
            val echo = ChatMsg(
                id   = UUID.randomUUID().toString(),
                text = "Reçu: $trimmed",
                time = System.currentTimeMillis(),
                isMine = false
            )
            _messages.value = _messages.value + echo
        }
    }

    /** Pour injecter un message entrant (ex: push réseau) */
    fun addIncoming(text: String) {
        val incoming = ChatMsg(
            id   = UUID.randomUUID().toString(),
            text = text,
            time = System.currentTimeMillis(),
            isMine = false
        )
        _messages.value = _messages.value + incoming
    }

    fun clear() { _messages.value = emptyList() }
}
