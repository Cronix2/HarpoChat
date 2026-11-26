// SPDX-License-Identifier: MIT
package app.harpochat.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "threads")
data class ThreadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["threadId", "time"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val sender: String,   // "me" ou identifiant du contact
    val body: String,     // en clair DANS la base, elle-même chiffrée (SQLCipher)
    val time: Long,
    val status: Int       // 0=SENDING, 1=SENT, 2=DELIVERED, 3=READ
)

/** Réglages privés (nom affiché, graine d'identité…) : stockés DANS la base chiffrée. */
@Entity(tableName = "kv")
data class KvEntity(
    @PrimaryKey val key: String,
    val value: String
)

/** Ligne de la liste des conversations. */
data class ThreadSummary(
    val id: String,
    val title: String,
    val createdAt: Long,
    val lastBody: String?,
    val lastTime: Long?,
    val lastSender: String?,
    val unread: Int
)

const val SENDER_ME = "me"
