package com.example.harpochat.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Source de vérité locale pour la liste des messages.
 * Pas (encore) de réseau : on simule l’ACK et une réponse bot.
 */
class ChatRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    /** Envoi d’un message « moi ». */
    fun send(text: String) {
        if (text.isBlank()) return
        val outgoing = ChatMessage(
            text = text.trim(),
            sender = Sender.Me,
            status = MessageStatus.Sending
        )
        // Ajoute le message
        _messages.value = _messages.value + outgoing

        // Simule l’ACK réseau → Sent
        scope.launch {
            delay(250)
            updateStatus(outgoing.id, MessageStatus.Sent)

            // Simule une réponse « bot » après un petit délai
            delay(600)
            addIncoming("Réçu: ${outgoing.text}")
        }
    }

    /** Ajoute un message entrant (expéditeur « Other »). */
    fun addIncoming(text: String) {
        val incoming = ChatMessage(
            text = text,
            sender = Sender.Other,
            status = MessageStatus.Delivered
        )
        _messages.value = _messages.value + incoming
    }

    /** Met à jour le statut d’un message par id. */
    fun updateStatus(id: String, status: MessageStatus) {
        _messages.value = _messages.value.map { msg ->
            if (msg.id == id) msg.copy(status = status) else msg
        }
    }

    /** Marque un message comme « lu ». */
    fun markRead(id: String) = updateStatus(id, MessageStatus.Read)
}
