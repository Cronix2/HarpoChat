// SPDX-License-Identifier: MIT
package app.harpochat.messaging

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.harpochat.data.EphemeralChoice
import app.harpochat.security.Session
import app.harpochat.ui.ChatScreen
import kotlinx.coroutines.launch

/** Conversation [threadId] : titre, vérification et durée éphémère viennent de la base. */
@Composable
fun ChatRoute(session: Session, threadId: String, onBack: () -> Unit) {
    val repo = session.repo
    val thread by repo.thread(threadId).collectAsStateWithLifecycle(initialValue = null)
    val messages by repo.messages(threadId).collectAsStateWithLifecycle(initialValue = emptyList())

    // Tout message affiché est lu.
    LaunchedEffect(threadId, messages.size) { repo.markThreadRead(threadId) }

    ChatScreen(
        title = thread?.title.orEmpty(),
        verified = thread?.verified == true,
        ephemeral = EphemeralChoice.of(thread?.ephemeralSeconds ?: 0),
        messages = messages,
        // Envoi dans la portée de session : il survit à la navigation, pas au verrouillage.
        onSend = { text -> session.scope.launch { repo.sendLocal(threadId, text) } },
        onEphemeralChange = { c -> session.scope.launch { repo.setEphemeral(threadId, c.seconds) } },
        onBack = onBack
    )
}
