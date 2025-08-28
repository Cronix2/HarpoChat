package com.example.harpochat.data

import android.content.Context
import com.example.harpochat.ui.ChatMsg
import com.example.harpochat.ui.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Repository persistant (Room + SQLCipher) avec un petit simulateur réseau.
 * - messages(threadId): Flow<List<ChatMsg>>
 * - sendLocal(threadId, text): insère en SENDING, simule ACK → SENT, puis faux incoming DELIVERED
 * - addIncoming(threadId, text): insère un message côté "other"
 * - updateStatus / markRead : maj de statut
 */
class ChatRepository private constructor(
    private val db: AppDatabase,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val msgDao = db.messageDao()
    private val threadDao = db.threadDao()

    /* ---------- Mapping Room -> UI ---------- */

    fun messages(threadId: String): Flow<List<ChatMsg>> =
        msgDao.messagesFlow(threadId).map { list ->
            list.map { it.toUi() }
        }

    private fun MessageEntity.toUi(): ChatMsg =
        ChatMsg(
            id = id,
            text = body,
            time = time,
            isMine = sender == "me",
            status = when (status) {
                0 -> MessageStatus.SENDING
                1 -> MessageStatus.SENT
                2 -> MessageStatus.DELIVERED
                3 -> MessageStatus.READ
                else -> MessageStatus.SENT
            }
        )

    private fun MessageStatus.toDb(): Int = when (this) {
        MessageStatus.SENDING   -> 0
        MessageStatus.SENT      -> 1
        MessageStatus.DELIVERED -> 2
        MessageStatus.READ      -> 3
    }

    /* ---------- Threads ---------- */

    suspend fun ensureThread(id: String, title: String) {
        threadDao.upsert(ThreadEntity(id, title))
    }

    /* ---------- Envoi local + simulateur ---------- */

    suspend fun sendLocal(threadId: String, text: String) {
        if (text.isBlank()) return

        val now = System.currentTimeMillis()
        val localId = UUID.randomUUID().toString()

        val outgoing = MessageEntity(
            id = localId,
            threadId = threadId,
            sender = "me",
            body = text.trim(),
            time = now,
            status = MessageStatus.SENDING.toDb()
        )

        // 1) on écrit en base en SENDING
        msgDao.insert(outgoing)

        // 2) simulateur: ACK réseau → SENT
        scope.launch {
            delay(250)
            msgDao.setStatus(localId, MessageStatus.SENT.toDb())

            // 3) simulateur: réponse du "correspondant"
            delay(600)
            addIncoming(threadId, "Reçu: ${outgoing.body}")
        }
    }

    /* ---------- Incoming (simulateur ou vrai réseau plus tard) ---------- */

    suspend fun addIncoming(threadId: String, text: String) {
        val incoming = MessageEntity(
            id = UUID.randomUUID().toString(),
            threadId = threadId,
            sender = "other",
            body = text,
            time = System.currentTimeMillis(),
            status = MessageStatus.DELIVERED.toDb()
        )
        msgDao.insert(incoming)
    }

    /* ---------- MAJ de statut ---------- */

    suspend fun updateStatus(id: String, status: MessageStatus) {
        msgDao.setStatus(id, status.toDb())
    }

    suspend fun markRead(id: String) = updateStatus(id, MessageStatus.READ)

    /* ---------- Factory ---------- */

    companion object {
        fun create(ctx: Context): ChatRepository {
            val factory = DbCrypto.supportFactory(ctx) // SQLCipher passphrase via Keystore
            val db = AppDatabase.build(ctx, factory)
            return ChatRepository(db)
        }
    }
}
