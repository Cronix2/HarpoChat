package com.example.harpochat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.harpochat.ui.ChatScreen
import com.example.harpochat.ui.ChatViewModel

class ChatActivity : ComponentActivity() {
    companion object {
        const val EXTRA_THREAD_ID = "thread_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        actionBar?.hide()

        // You can use this later with your ViewModel (e.g. vm.openThread(threadId))
        val threadId: String = intent.getStringExtra(EXTRA_THREAD_ID) ?: "default"

        setContent {
            MaterialTheme {
                Surface {
                    val vm: ChatViewModel = viewModel()
                    val messages by vm.messages.collectAsState(initial = emptyList())

                    ChatScreen(
                        messages = messages,
                        onSend = vm::send,
                        onRetry = { /* plus tard */ },
                        title = "Alice",           // ou le nom du groupe
                        avatarInitial = "A"        // facultatif, dérivé de title si omis
                    )
                }
            }
        }
    }
}
