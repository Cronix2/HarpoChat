// SPDX-License-Identifier: MIT
package app.calc.scientific.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "threads")
data class ThreadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Code de contrôle comparé en personne lors de l'ajout. */
    val verified: Boolean = false,
    /** Durée de vie des messages en secondes (0 = messages conservés). */
    val ephemeralSeconds: Long = 0
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
    val status: Int,      // 0=SENDING, 1=SENT, 2=DELIVERED, 3=READ
    /** Date de destruction (epoch ms), null si le fil n'est pas éphémère. */
    val expiresAt: Long? = null
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
    val verified: Boolean,
    val ephemeralSeconds: Long,
    val lastBody: String?,
    val lastTime: Long?,
    val lastSender: String?,
    val unread: Int
)

const val SENDER_ME = "me"

// ---------------------------------------------------------------------------
// Ajout de contacts et acheminement (étape N2). Les secrets restent dans la base chiffrée.
// ---------------------------------------------------------------------------

/** Invitation que j'ai montrée ou envoyée (côté A), en attente de demandes. */
@Entity(tableName = "invites")
class InviteEntity(
    @PrimaryKey val id: String,
    val secret: ByteArray,
    val expiresAt: Long,
    /** Montrée en personne (QR) : le contact sera marqué vérifié après comparaison du code. */
    val inPerson: Boolean,
    val createdAt: Long
)

/** Demande que j'ai envoyée en important une invitation (côté B), en attente d'acceptation. */
@Entity(tableName = "requests_out")
class OutRequestEntity(
    @PrimaryKey val id: String,
    val secret: ByteArray,
    val ePrivate: ByteArray,
    val ePublic: ByteArray,
    val kB: ByteArray,
    val sas: String,
    val inPerson: Boolean,
    val createdAt: Long,
    val expiresAt: Long
)

/** Demande reçue pour l'une de mes invitations (côté A), en attente de ma décision. */
@Entity(tableName = "requests_in")
class InRequestEntity(
    @PrimaryKey val id: String,
    val inviteId: String,
    val ePublic: ByteArray,
    val kB: ByteArray,
    val name: String,
    val sas: String,
    val receivedAt: Long
)

/** Contact accepté : une boîte et une clé par sens. */
@Entity(tableName = "peers")
class PeerEntity(
    @PrimaryKey val threadId: String,
    val boxOut: ByteArray,
    val boxIn: ByteArray,
    val keyOut: ByteArray,
    val keyIn: ByteArray,
    val createdAt: Long
)

/** Blob chiffré à déposer dans une boîte. Conservé jusqu'à son départ, même après un verrouillage. */
@Entity(tableName = "outbox")
class OutboxEntity(
    @PrimaryKey val id: String,
    val box: ByteArray,
    val blob: ByteArray,
    /** Message dont c'est l'envoi (passe à « envoyé » au départ), ou null. */
    val messageId: String?,
    val createdAt: Long
)
