/*
——————What are you doing here ?——————
⠀⣞⢽⢪⢣⢣⢣⢫⡺⡵⣝⡮⣗⢷⢽⢽⢽⣮⡷⡽⣜⣜⢮⢺⣜⢷⢽⢝⡽⣝
⠸⡸⠜⠕⠕⠁⢁⢇⢏⢽⢺⣪⡳⡝⣎⣏⢯⢞⡿⣟⣷⣳⢯⡷⣽⢽⢯⣳⣫⠇
⠀⠀⢀⢀⢄⢬⢪⡪⡎⣆⡈⠚⠜⠕⠇⠗⠝⢕⢯⢫⣞⣯⣿⣻⡽⣏⢗⣗⠏⠀
⠀⠪⡪⡪⣪⢪⢺⢸⢢⢓⢆⢤⢀⠀⠀⠀⠀⠈⢊⢞⡾⣿⡯⣏⢮⠷⠁⠀⠀
⠀⠀⠀⠈⠊⠆⡃⠕⢕⢇⢇⢇⢇⢇⢏⢎⢎⢆⢄⠀⢑⣽⣿⢝⠲⠉⠀⠀⠀⠀
⠀⠀⠀⠀⠀⡿⠂⠠⠀⡇⢇⠕⢈⣀⠀⠁⠡⠣⡣⡫⣂⣿⠯⢪⠰⠂⠀⠀⠀⠀
⠀⠀⠀⠀⡦⡙⡂⢀⢤⢣⠣⡈⣾⡃⠠⠄⠀⡄⢱⣌⣶⢏⢊⠂⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⢝⡲⣜⡮⡏⢎⢌⢂⠙⠢⠐⢀⢘⢵⣽⣿⡿⠁⠁⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠨⣺⡺⡕⡕⡱⡑⡆⡕⡅⡕⡜⡼⢽⡻⠏⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⣼⣳⣫⣾⣵⣗⡵⡱⡡⢣⢑⢕⢜⢕⡝⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⣴⣿⣾⣿⣿⣿⡿⡽⡑⢌⠪⡢⡣⣣⡟⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⡟⡾⣿⢿⢿⢵⣽⣾⣼⣘⢸⢸⣞⡟⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠁⠇⠡⠩⡫⢿⣝⡻⡮⣒⢽⠋⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
————————————————————————————————————
*/

package com.example.harpochat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.harpochat.data.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel branché sur Room (via ChatRepository).
 * - Expose messages en Flow<StateFlow<List<ChatMsg>>> pour le fil courant
 * - openThread(id, title) pour changer de conversation (et la créer si besoin)
 * - send(text) pour envoyer (insère en SENDING puis simule ACK → SENT + réponse entrante)
 * - addIncoming / markRead / updateStatus si besoin
 */
class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ChatRepository.create(app)

    /** Fil de discussion courant (simple état mémoire). */
    private val currentThreadId = MutableStateFlow("thread-alice")

    /** Flux des messages du fil courant, prêt à être collecté par l’UI. */
    val messages: StateFlow<List<ChatMsg>> =
        currentThreadId
            .flatMapLatest { threadId -> repo.messages(threadId) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )

    /**
     * Ouvre (ou crée si besoin) un fil de discussion identifié par [id].
     * [title] sert au stockage du fil (affichage liste, etc.).
     */
    fun openThread(id: String, title: String) {
        viewModelScope.launch {
            repo.ensureThread(id, title)
            currentThreadId.value = id
        }
    }

    /** Envoie un message dans le fil courant. */
    fun send(text: String) {
        viewModelScope.launch {
            repo.sendLocal(currentThreadId.value, text)
        }
    }

    /** Injecte un message entrant (utile pour tests réseau ou notifications). */
    fun addIncoming(text: String) {
        viewModelScope.launch {
            repo.addIncoming(currentThreadId.value, text)
        }
    }

    /** Marque un message comme 'lu'. */
    fun markRead(messageId: String) {
        viewModelScope.launch {
            repo.markRead(messageId)
        }
    }

    /** Met à jour un statut arbitraire (SENDING/SENT/DELIVERED/READ). */
    fun updateStatus(messageId: String, status: MessageStatus) {
        viewModelScope.launch {
            repo.updateStatus(messageId, status)
        }
    }
}
