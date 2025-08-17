package com.example.harpochat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.harpochat.data.ChatRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ChatViewModel(
    private val repo: ChatRepository = ChatRepository()
) : ViewModel() {
    val messages: StateFlow<List<ChatMessage>> =
        repo.messages.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun send(text: String) = repo.sendLocal(text)
}
