package com.example.harpochat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

data class ChatMessage(val id: Long, val text: String, val isMe: Boolean)

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    onSend: (String) -> Unit
) {
    var draft by remember { mutableStateOf(TextFieldValue("")) }

    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Écrire un message…") }
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val t = draft.text.trim()
                    if (t.isNotEmpty()) { onSend(t); draft = TextFieldValue("") }
                }) { Text("Envoyer") }
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner).padding(12.dp),
            reverseLayout = true
        ) {
            items(messages, key = { it.id }) { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isMe) Arrangement.End else Arrangement.Start
                ) {
                    ElevatedCard(
                        modifier = Modifier.widthIn(max = 320.dp).padding(vertical = 4.dp)
                    ) { Text(msg.text, Modifier.padding(12.dp)) }
                }
            }
        }
    }
}
