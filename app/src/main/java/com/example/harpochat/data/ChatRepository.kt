package com.example.harpochat.data

import com.example.harpochat.crypto.SignalCryptoEngine
import com.example.harpochat.ui.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatRepository {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private var nextId = 1L
    private val scope = CoroutineScope(Dispatchers.IO)

    private val crypto = SignalCryptoEngine().also {
        scope.launch { it.generateIdentity() } // init async
    }

    fun sendLocal(text: String) {
        // Envoi: me -> peer (E2EE), puis déchiffrement côté peer (loopback) pour affichage
        scope.launch {
            val ct = crypto.encrypt(text.toByteArray(), peerId = "peer")
            val pt = crypto.decrypt(ct, peerId = "me")
            val echo = String(pt)

            // MAJ UI sur le thread principal pas nécessaire ici car StateFlow (on reste simple)
            _messages.value = listOf(
                ChatMessage(nextId++, text, isMe = true),
                ChatMessage(nextId++, "(${ct.size} bytes chiffrés) $echo", isMe = false)
            ) + _messages.value
        }
    }
}
