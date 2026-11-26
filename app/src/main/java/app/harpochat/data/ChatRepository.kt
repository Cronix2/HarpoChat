// SPDX-License-Identifier: MIT
package app.harpochat.data

import app.harpochat.ui.ChatMsg
import app.harpochat.ui.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Accès aux conversations, sur la base ouverte de la session courante.
 *
 * Le réseau n'existe pas encore côté Android (il viendra avec `harpochat-core`) :
 * [sendLocal] simule l'accusé de réception et une réponse, pour la démonstration.
 */
class ChatRepository(
    private val db: AppDatabase,
    private val scope: CoroutineScope
) {
    private val threads = db.threadDao()
    private val messages = db.messageDao()
    private val kv = db.kvDao()

    fun summaries(): Flow<List<ThreadSummary>> = threads.summariesFlow()

    fun thread(id: String): Flow<ThreadEntity?> = threads.threadFlow(id)

    fun messages(threadId: String): Flow<List<ChatMsg>> =
        messages.messagesFlow(threadId).map { list -> list.map { it.toUi() } }

    suspend fun createThread(id: String, title: String) =
        threads.insertIfAbsent(ThreadEntity(id = id, title = title))

    suspend fun renameThread(id: String, title: String) = threads.rename(id, title)

    suspend fun markThreadRead(threadId: String) = messages.markThreadRead(threadId)

    suspend fun sendLocal(threadId: String, text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        val id = UUID.randomUUID().toString()
        messages.insert(
            MessageEntity(id, threadId, SENDER_ME, body, System.currentTimeMillis(), MessageStatus.SENDING.code)
        )
        // --- Simulateur de démonstration (remplacé par le transport réel) ---
        scope.launch {
            delay(250)
            messages.setStatus(id, MessageStatus.SENT.code)
            delay(600)
            messages.setStatus(id, MessageStatus.DELIVERED.code)
            messages.insert(
                MessageEntity(
                    UUID.randomUUID().toString(), threadId, "peer", "Reçu : $body",
                    System.currentTimeMillis(), MessageStatus.DELIVERED.code
                )
            )
        }
    }

    /** Données de démonstration (builds debug) : message déjà « remis ». */
    suspend fun insertDemoMessage(threadId: String, mine: Boolean, body: String, time: Long) =
        messages.insert(
            MessageEntity(
                UUID.randomUUID().toString(), threadId, if (mine) SENDER_ME else "peer", body, time,
                if (mine) MessageStatus.READ.code else MessageStatus.DELIVERED.code
            )
        )

    // ---------- Réglages privés (dans la base chiffrée) ----------

    suspend fun getSetting(key: String): String? = kv.get(key)
    fun settingFlow(key: String): Flow<String?> = kv.flow(key)
    suspend fun putSetting(key: String, value: String) = kv.put(KvEntity(key, value))

    private fun MessageEntity.toUi() = ChatMsg(
        id = id,
        text = body,
        time = time,
        isMine = sender == SENDER_ME,
        status = MessageStatus.fromCode(status)
    )

    companion object {
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_IDENTITY_SEED = "identity_seed"
    }
}
