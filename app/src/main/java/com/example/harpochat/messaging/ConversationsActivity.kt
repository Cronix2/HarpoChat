package com.example.harpochat.messaging

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.harpochat.ChatActivity   // ⟵ use this if you created ChatActivity with EXTRA_THREAD_ID
// import com.example.harpochat.MainActivity // ⟵ fallback: uncomment and use MainActivity instead

class ConversationsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        actionBar?.hide()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ConversationListScreen(
                    onOpen = { id -> openChat(id) }   // ⟵ wire row clicks to openChat()
                )
            }
        }
    }

    private fun openChat(conversationId: String) {
        // Target ChatActivity (recommended). If you don’t have it yet, swap for MainActivity.
        val intent = Intent(this, ChatActivity::class.java)
            .putExtra(ChatActivity.EXTRA_THREAD_ID, conversationId)
        // val intent = Intent(this, MainActivity::class.java).putExtra("thread_id", conversationId)
        startActivity(intent)
    }
}

/* ---------------- models ---------------- */

data class ConversationPreview(
    val id: String,
    val title: String,
    val lastMessage: String,
    val time: String,
    val unread: Int = 0
)

/* ---------------- UI ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationListScreen(
    onOpen: (String) -> Unit
) {
    val chats = remember {
        mutableStateListOf(
            ConversationPreview("1", "Alice", "On se voit ce soir ?", "18:42", 2),
            ConversationPreview("2", "Dev group", "Pushed a PR", "17:10", 0),
            ConversationPreview("3", "Bob", "ok 👍", "Hier", 0),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HarpoChat") },
                actions = {
                    IconButton(onClick = { /* TODO : Settings */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* TODO : création conversation */ }) {
                Icon(Icons.Default.Add, contentDescription = "Nouveau")
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0F1115))
                .padding(padding)
        ) {
            items(chats) { conv ->
                ConversationRow(
                    c = conv,
                    onClick = { onOpen(conv.id) }   // ⟵ pass the ID up
                )
                HorizontalDivider(color = Color(0xFF1E2430))
            }
        }
    }
}

@Composable
private fun ConversationRow(
    c: ConversationPreview,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)           // ⟵ make the row clickable
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // avatar placeholder
        Surface(
            modifier = Modifier.size(44.dp),
            shape = MaterialTheme.shapes.large,
            color = Color(0xFF2A2F3A)
        ) {}

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Spacer(Modifier.weight(1f))
                Text(
                    c.time,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF8C96A7)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                c.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFD8DDE6),
                maxLines = 1
            )
        }

        if (c.unread > 0) {
            Spacer(Modifier.width(12.dp))
            Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                Text(
                    text = c.unread.toString(),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
