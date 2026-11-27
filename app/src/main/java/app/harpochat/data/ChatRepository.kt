// SPDX-License-Identifier: MIT
package app.harpochat.data

import app.harpochat.link.InviteIdentity
import app.harpochat.ui.ChatMsg
import app.harpochat.ui.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.Base64
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

    /** Nouveau fil : la durée éphémère par défaut des réglages s'applique. */
    suspend fun createThread(id: String, title: String, verified: Boolean = false) =
        threads.insertIfAbsent(ThreadEntity(id = id, title = title, verified = verified, ephemeralSeconds = defaultEphemeralSeconds()))

    suspend fun renameThread(id: String, title: String) = threads.rename(id, title)

    suspend fun setEphemeral(threadId: String, seconds: Long) = threads.setEphemeral(threadId, seconds)

    suspend fun markThreadRead(threadId: String) = messages.markThreadRead(threadId)

    suspend fun sendLocal(threadId: String, text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        messages.insert(MessageEntity(id, threadId, SENDER_ME, body, now, MessageStatus.SENDING.code, expiry(threadId, now)))
        // --- Simulateur de démonstration (remplacé par le transport réel) ---
        scope.launch {
            delay(250)
            messages.setStatus(id, MessageStatus.SENT.code)
            delay(600)
            messages.setStatus(id, MessageStatus.DELIVERED.code)
            val t = System.currentTimeMillis()
            messages.insert(
                MessageEntity(UUID.randomUUID().toString(), threadId, "peer", "Reçu : $body", t, MessageStatus.DELIVERED.code, expiry(threadId, t))
            )
        }
    }

    /** Données de démonstration (builds debug) : message déjà « remis ». */
    suspend fun insertDemoMessage(threadId: String, mine: Boolean, body: String, time: Long) =
        messages.insert(
            MessageEntity(
                UUID.randomUUID().toString(), threadId, if (mine) SENDER_ME else "peer", body, time,
                if (mine) MessageStatus.READ.code else MessageStatus.DELIVERED.code,
                expiry(threadId, time)
            )
        )

    /**
     * Destruction des messages éphémères : à l'ouverture de la session puis chaque minute.
     * La boucle vit dans la portée de session : elle s'arrête au verrouillage.
     */
    fun startExpiryJob() {
        scope.launch {
            while (isActive) {
                messages.purgeExpired(System.currentTimeMillis())
                delay(60_000)
            }
        }
    }

    // ---------- Réglages privés (dans la base chiffrée) ----------

    suspend fun getSetting(key: String): String? = kv.get(key)
    fun settingFlow(key: String): Flow<String?> = kv.flow(key)
    suspend fun putSetting(key: String, value: String) = kv.put(KvEntity(key, value))

    /** Identité Ed25519 de l'appareil, créée au premier besoin (transitoire jusqu'à harpochat-core). */
    suspend fun identity(): InviteIdentity =
        getSetting(KEY_IDENTITY_SEED)?.let { InviteIdentity.fromSeed(Base64.getDecoder().decode(it)) }
            ?: InviteIdentity.generate().also { putSetting(KEY_IDENTITY_SEED, Base64.getEncoder().encodeToString(it.privateSeed)) }

    /** Empreinte lisible de la clé publique (16 caractères hexadécimaux, par groupes de 4). */
    suspend fun fingerprint(): String =
        MessageDigest.getInstance("SHA-256").digest(identity().publicKey).take(8)
            .joinToString("") { "%02X".format(it) }.chunked(4).joinToString(" ")

    suspend fun defaultEphemeralSeconds(): Long = getSetting(KEY_DEFAULT_EPHEMERAL)?.toLongOrNull() ?: 0L

    private suspend fun expiry(threadId: String, from: Long): Long? {
        val ttl = threads.ephemeralSeconds(threadId) ?: 0L
        return if (ttl > 0) from + ttl * 1000 else null
    }

    private fun MessageEntity.toUi() = ChatMsg(
        id = id,
        text = body,
        time = time,
        isMine = sender == SENDER_ME,
        status = MessageStatus.fromCode(status),
        expiresAt = expiresAt
    )

    companion object {
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_IDENTITY_SEED = "identity_seed"
        const val KEY_DEFAULT_EPHEMERAL = "default_ephemeral_seconds"
    }
}

/** Durées éphémères proposées à l'utilisateur. */
enum class EphemeralChoice(val seconds: Long, val label: String, val short: String) {
    OFF(0, "Désactivés", "Off"),
    ONE_HOUR(3_600, "1 heure", "1 h"),
    ONE_DAY(86_400, "24 heures", "24 h"),
    ONE_WEEK(604_800, "7 jours", "7 j");

    companion object {
        fun of(seconds: Long) = entries.firstOrNull { it.seconds == seconds } ?: OFF
    }
}
