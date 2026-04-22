// SPDX-License-Identifier: MIT
package app.harpochat.messaging

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.harpochat.data.ChatRepository
import app.harpochat.data.ThreadEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConversationsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ChatRepository.create(app)

    /** Flux de toutes les conversations (threads) pour l’écran de liste. */
    val threads: StateFlow<List<ThreadEntity>> =
        repo.threads()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )

    /** Crée (ou met à jour) une conversation. */
    fun createThread(id: String, title: String) {
        viewModelScope.launch {
            repo.ensureThread(id, title)
        }
    }
}
