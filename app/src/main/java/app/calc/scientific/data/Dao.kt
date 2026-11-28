// SPDX-License-Identifier: MIT
package app.calc.scientific.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadDao {
    /** Conversations triées par dernière activité, avec aperçu et non-lus. */
    @Query(
        """
        SELECT t.id, t.title, t.createdAt, t.verified, t.ephemeralSeconds,
            (SELECT m.body   FROM messages m WHERE m.threadId = t.id ORDER BY m.time DESC LIMIT 1) AS lastBody,
            (SELECT m.time   FROM messages m WHERE m.threadId = t.id ORDER BY m.time DESC LIMIT 1) AS lastTime,
            (SELECT m.sender FROM messages m WHERE m.threadId = t.id ORDER BY m.time DESC LIMIT 1) AS lastSender,
            (SELECT COUNT(*) FROM messages m
                WHERE m.threadId = t.id AND m.sender != 'me' AND m.status < 3) AS unread
        FROM threads t
        ORDER BY COALESCE(lastTime, t.createdAt) DESC
        """
    )
    fun summariesFlow(): Flow<List<ThreadSummary>>

    @Query("SELECT * FROM threads WHERE id = :id")
    fun threadFlow(id: String): Flow<ThreadEntity?>

    /** N'écrase JAMAIS un fil existant (corrige le renommage en « Alice »). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(thread: ThreadEntity)

    @Query("UPDATE threads SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)

    @Query("UPDATE threads SET ephemeralSeconds = :seconds WHERE id = :id")
    suspend fun setEphemeral(id: String, seconds: Long)

    @Query("SELECT ephemeralSeconds FROM threads WHERE id = :id")
    suspend fun ephemeralSeconds(id: String): Long?
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY time ASC")
    fun messagesFlow(threadId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(m: MessageEntity)

    /** Message reçu : un doublon (même identifiant) est ignoré. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(m: MessageEntity): Long

    /** Accusé de réception : ne fait jamais reculer un statut (lu reste lu). */
    @Query("UPDATE messages SET status = :status WHERE id = :id AND sender = 'me' AND status < :status")
    suspend fun raiseStatus(id: String, status: Int)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: Int)

    @Query("UPDATE messages SET status = 3 WHERE threadId = :threadId AND sender != 'me' AND status < 3")
    suspend fun markThreadRead(threadId: String)

    /** Messages éphémères arrivés à échéance (effacés pour de bon grâce à secure_delete). */
    @Query("DELETE FROM messages WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    suspend fun purgeExpired(now: Long): Int
}

@Dao
interface KvDao {
    @Query("SELECT value FROM kv WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM kv WHERE `key` = :key")
    fun flow(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: KvEntity)
}

@Dao
interface PairingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertInvite(i: InviteEntity)
    @Query("SELECT * FROM invites") fun invitesFlow(): Flow<List<InviteEntity>>
    @Query("SELECT * FROM invites WHERE id = :id") suspend fun invite(id: String): InviteEntity?
    @Query("DELETE FROM invites WHERE id = :id") suspend fun deleteInvite(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertOutRequest(r: OutRequestEntity)
    @Query("SELECT * FROM requests_out ORDER BY createdAt DESC") fun outRequestsFlow(): Flow<List<OutRequestEntity>>
    @Query("DELETE FROM requests_out WHERE id = :id") suspend fun deleteOutRequest(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertInRequest(r: InRequestEntity)
    @Query("SELECT * FROM requests_in ORDER BY receivedAt DESC") fun inRequestsFlow(): Flow<List<InRequestEntity>>
    @Query("SELECT * FROM requests_in WHERE id = :id") suspend fun inRequest(id: String): InRequestEntity?
    @Query("DELETE FROM requests_in WHERE id = :id") suspend fun deleteInRequest(id: String)
    @Query("DELETE FROM requests_in WHERE inviteId = :inviteId") suspend fun deleteInRequestsFor(inviteId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPeer(p: PeerEntity)
    @Query("SELECT * FROM peers") fun peersFlow(): Flow<List<PeerEntity>>
    @Query("SELECT * FROM peers WHERE threadId = :threadId") suspend fun peer(threadId: String): PeerEntity?

    /** Invitations et demandes expirées. Une demande reçue disparaît avec son invitation. */
    @Query("DELETE FROM invites WHERE expiresAt <= :now") suspend fun purgeInvites(now: Long)
    @Query("DELETE FROM requests_out WHERE expiresAt <= :now") suspend fun purgeOutRequests(now: Long)
    @Query("DELETE FROM requests_in WHERE inviteId NOT IN (SELECT id FROM invites)") suspend fun purgeOrphanInRequests()
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(o: OutboxEntity)
    @Query("SELECT * FROM outbox ORDER BY createdAt") fun flow(): Flow<List<OutboxEntity>>
    @Query("DELETE FROM outbox WHERE id = :id") suspend fun delete(id: String)
}
