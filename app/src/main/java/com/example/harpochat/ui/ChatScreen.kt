package com.example.harpochat.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterEnd
import androidx.compose.ui.Alignment.Companion.CenterStart
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ---------- Palette sombre ---------- */
private val ChatBg = Color(0xFF0F1115)
private val BubbleMine = Color(0xFF4C6FFF)
private val BubbleOther = Color(0xFF1F2632)
private val OnBubbleMine = Color.White
private val OnBubbleOther = Color(0xFFD8DDE6)
private val DividerDark = Color(0xFF1E2430)
private val ChipDark = Color(0xFF171B22)

/* ---------- Modèle minimal ---------- */
enum class MessageStatus { SENDING, SENT, DELIVERED, READ }

data class ChatMsg(
    val id: String,
    val text: String,
    /** Peut être un String "HH:mm" ou un Long (epoch millis) */
    val time: Any,
    val isMine: Boolean,
    val status: MessageStatus = MessageStatus.SENT
)

/* ---------- Écran de chat ---------- */
@Composable
fun ChatScreen(
    messages: List<ChatMsg>,
    onSend: (String) -> Unit,
    onRetry: () -> Unit,            // conservé pour compat avec ton Activity
    title: String = "Conversation",
    avatarInitial: String = title.firstOrNull()?.uppercase() ?: "?"
) {
    var draft by remember { mutableStateOf("") }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = ChatBg,
            surface = ChatBg,
            onBackground = Color(0xFFE7EAF1),
            primary = BubbleMine
        )
    ) {
        Scaffold(
            containerColor = ChatBg,
            topBar = {
                // Cartouche en haut (avatar + nom)
                Surface(
                    color = ChipDark,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .height(100.dp) // ← un peu plus grand
                ) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 30.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2A2F3A),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    avatarInitial,
                                    color = Color(0xFFD8DDE6),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .background(ChatBg)
                    .padding(padding)
            ) {
                /* Liste des messages (collée en bas) */
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.Bottom,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Espace “début de conversation” jusqu’à ~la moitié de l’écran
                    item(key = "half-spacer") {
                        Box(
                            Modifier
                                .fillParentMaxHeight(0.5f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (messages.size < 6) {
                                Text(
                                    "Voici le début de la conversation",
                                    color = Color(0xFFFFE082),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Messages
                    items(messages, key = { it.id }) { m ->
                        MessageBubble(msg = m)
                        Spacer(Modifier.height(6.dp))
                    }

                    item(key = "bottom-pad") { Spacer(Modifier.height(4.dp)) }
                }

                /* Zone de saisie */
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message…") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = ChipDark,
                            focusedContainerColor = ChipDark,
                            unfocusedBorderColor = DividerDark,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val txt = draft.trim()
                            if (txt.isNotEmpty()) {
                                onSend(txt)
                                draft = ""
                            }
                        },
                        shape = RoundedCornerShape(24.dp)
                    ) { Text("Envoyer") }
                }
            }
        }
    }
}

/* ---------- Icône d’état du message ---------- */
@Composable
private fun MessageStatusIcon(status: MessageStatus, isMine: Boolean) {
    val neutral = if (isMine) Color(0xFFE8ECFF) else Color(0xFFB7C0D0)
    when (status) {
        MessageStatus.SENDING -> {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = neutral
            )
        }
        MessageStatus.SENT -> androidx.compose.material3.Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = "Envoyé",
            tint = neutral,
            modifier = Modifier.size(14.dp)
        )
        MessageStatus.DELIVERED -> androidx.compose.material3.Icon(
            imageVector = Icons.Outlined.DoneAll,
            contentDescription = "Reçu",
            tint = neutral,
            modifier = Modifier.size(14.dp)
        )
        MessageStatus.READ -> androidx.compose.material3.Icon(
            imageVector = Icons.Outlined.DoneAll,
            contentDescription = "Lu",
            tint = MaterialTheme.colorScheme.primary, // bleu pour “lu”
            modifier = Modifier.size(14.dp)
        )
    }
}

/* ---------- Bulle de message ---------- */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun MessageBubble(msg: ChatMsg) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxW = maxWidth * 0.72f     // ← largeur max ≈ 72% écran
        val minW = maxWidth * 0.05f     // ← largeur min ≈ 5% écran
        val bubbleColor = if (msg.isMine) BubbleMine else BubbleOther
        val onBubble = if (msg.isMine) OnBubbleMine else OnBubbleOther

        Surface(
            modifier = Modifier
                .widthIn(min = minW, max = maxW)                // ← borne la largeur
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .align(if (msg.isMine) CenterEnd else CenterStart),
            shape = RoundedCornerShape(18.dp),
            color = bubbleColor
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = msg.text,
                    color = onBubble,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatTimeLabel(msg.time),
                        style = MaterialTheme.typography.labelSmall,
                        color = onBubble.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.width(6.dp))
                    MessageStatusIcon(status = msg.status, isMine = msg.isMine)
                }
            }
        }
    }
}

/* ---------- Helpers ---------- */
private fun formatTimeLabel(time: Any): String =
    when (time) {
        is String -> time
        is Long -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
        else -> ""
    }
