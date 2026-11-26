// SPDX-License-Identifier: MIT
package app.harpochat.messaging

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.harpochat.data.ChatRepository
import app.harpochat.data.SENDER_ME
import app.harpochat.data.ThreadSummary
import app.harpochat.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    repo: ChatRepository,
    onOpenThread: (String) -> Unit,
    onOpenQr: () -> Unit,
    onOpenCodepair: () -> Unit,
    onOpenSettings: () -> Unit,
    onLock: () -> Unit
) {
    val threads by repo.summaries().collectAsStateWithLifecycle(initialValue = emptyList())
    var showSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conversations") },
                actions = {
                    IconButton(onClick = onLock) {
                        Icon(Icons.Filled.Lock, contentDescription = "Verrouiller")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Réglages")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Nouveau contact")
            }
        }
    ) { padding ->
        if (threads.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(threads, key = { it.id }) { t ->
                    ConversationRow(t) { onOpenThread(t.id) }
                    HorizontalDivider(color = AppColors.Divider)
                }
            }
        }

        if (showSheet) {
            ModalBottomSheet(onDismissRequest = { showSheet = false }, containerColor = AppColors.Sheet) {
                SheetAction(Icons.Filled.QrCode2, "Ajouter par QR code", "Scanner ou montrer un code, en personne") {
                    showSheet = false; onOpenQr()
                }
                HorizontalDivider(color = AppColors.Divider)
                SheetAction(Icons.Filled.Key, "Ajouter par code d'appairage", "Identifiant + mot de passe temporaire") {
                    showSheet = false; onOpenCodepair()
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Aucune conversation", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ajoutez un contact avec le bouton +. Personne ne peut vous ajouter sans votre accord.",
                color = AppColors.Muted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ConversationRow(t: ThreadSummary, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).background(AppColors.SurfaceHigh, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(t.title.firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            val preview = when {
                t.lastBody == null -> "Nouvelle conversation"
                t.lastSender == SENDER_ME -> "Vous : ${t.lastBody}"
                else -> t.lastBody
            }
            Text(
                preview,
                color = AppColors.Muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val time = t.lastTime ?: t.createdAt
            Text(
                DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.Muted
            )
            if (t.unread > 0) {
                Box(
                    Modifier.widthIn(min = 20.dp).height(20.dp).background(AppColors.Accent, CircleShape)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${t.unread}", style = MaterialTheme.typography.labelSmall, color = AppColors.OnBubbleMine)
                }
            }
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, color = AppColors.Muted) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = AppColors.Sheet),
        modifier = Modifier.clickable(onClick = onClick)
    )
}
