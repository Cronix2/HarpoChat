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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface {
                    val vm: ChatViewModel = viewModel()
                    val messages by vm.messages.collectAsState()   // <-- observe le flow
                    ChatScreen(
                        messages = messages,
                        onSend = vm::send
                    )
                }
            }
        }
    }
}
