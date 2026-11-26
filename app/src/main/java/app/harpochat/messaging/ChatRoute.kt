// SPDX-License-Identifier: MIT
package app.harpochat.messaging

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.harpochat.security.Session
import app.harpochat.ui.ChatScreen
import kotlinx.coroutines.launch

/** Conversation [threadId] : le titre vient de la base (plus jamais « Alice » en dur). */
@Composable
fun ChatRoute(session: Session, threadId: String, onBack: () -> Unit) {
    val repo = session.repo
    val thread by repo.thread(threadId).collectAsStateWithLifecycle(initialValue = null)
    val messages by repo.messages(threadId).collectAsStateWithLifecycle(initialValue = emptyList())

    // Tout message affiché est lu.
    LaunchedEffect(threadId, messages.size) { repo.markThreadRead(threadId) }

    val title = thread?.title ?: ""
    ChatScreen(
        messages = messages,
        // Envoi dans la portée de session : il survit à la navigation, pas au verrouillage.
        onSend = { text -> session.scope.launch { repo.sendLocal(threadId, text) } },
        title = title,
        onBack = onBack
    )
}
