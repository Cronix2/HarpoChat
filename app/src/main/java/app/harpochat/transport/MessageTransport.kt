// SPDX-License-Identifier: MIT
package app.harpochat.transport

import app.harpochat.link.InviteIdentity
import kotlinx.coroutines.flow.Flow

/**
 * Outgoing message to be sent through a transport.
 *
 * The payload is expected to already be a Signal-protocol ciphertext; the
 * transport is oblivious to content and only routes bytes between identities.
 */
data class OutgoingMessage(
    val threadId: String,
    val toIdentity: ByteArray,    // 32-byte Ed25519 public key of the recipient
    val ciphertext: ByteArray,
    val signature: ByteArray,     // detached Ed25519 signature (envelope signature)
    val tsMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OutgoingMessage) return false
        return threadId == other.threadId
            && toIdentity.contentEquals(other.toIdentity)
            && ciphertext.contentEquals(other.ciphertext)
            && signature.contentEquals(other.signature)
            && tsMs == other.tsMs
    }
    override fun hashCode(): Int {
        var r = threadId.hashCode()
        r = 31 * r + toIdentity.contentHashCode()
        r = 31 * r + ciphertext.contentHashCode()
        r = 31 * r + signature.contentHashCode()
        r = 31 * r + tsMs.hashCode()
        return r
    }
}

/**
 * Envelope received from a peer through the transport.
 *
 * Ciphertext is still encrypted at this point; the caller is responsible for
 * decrypting it through the Signal engine before showing anything to the user.
 */
data class IncomingEnvelope(
    val messageId: String,
    val fromIdentity: ByteArray,
    val ciphertext: ByteArray,
    val signature: ByteArray,
    val tsMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IncomingEnvelope) return false
        return messageId == other.messageId
            && fromIdentity.contentEquals(other.fromIdentity)
            && ciphertext.contentEquals(other.ciphertext)
            && signature.contentEquals(other.signature)
            && tsMs == other.tsMs
    }
    override fun hashCode(): Int {
        var r = messageId.hashCode()
        r = 31 * r + fromIdentity.contentHashCode()
        r = 31 * r + ciphertext.contentHashCode()
        r = 31 * r + signature.contentHashCode()
        r = 31 * r + tsMs.hashCode()
        return r
    }
}

/** Connectivity state surfaced to the UI. */
sealed interface TransportState {
    data object Disconnected : TransportState
    data object Connecting : TransportState
    data object Authenticating : TransportState
    data object Connected : TransportState
    data class Failed(val reason: String) : TransportState
}

/**
 * Abstraction over the actual transport channel.
 *
 * Two implementations exist:
 *  * `LoopbackTransport` — echoes messages locally for offline testing
 *    (keeps the behaviour that used to live in `ChatRepository.sendLocal`).
 *  * `RelayWsTransport` — talks to the HarpoChat Rust relay over WebSocket,
 *    authenticating with the user's [InviteIdentity].
 *
 * This layer is deliberately the ONLY place in the Android app that speaks
 * to the network for chat. Everything above it (repository, UI) sees plain
 * domain objects and never touches sockets.
 */
interface MessageTransport {
    /** Current connection state (hot flow). */
    val state: Flow<TransportState>

    /** Inbound envelopes ready to be decrypted by the caller. */
    val incoming: Flow<IncomingEnvelope>

    /** Start (or resume) the transport using the given identity. */
    suspend fun start(identity: InviteIdentity)

    /** Close the transport; safe to call multiple times. */
    suspend fun stop()

    /**
     * Send an outgoing message. Returns the server-assigned message id on
     * success, or throws. Implementations are responsible for their own
     * retry / at-least-once semantics.
     */
    suspend fun send(msg: OutgoingMessage): String

    /** Acknowledge receipt of an incoming envelope so the relay can drop it. */
    suspend fun ack(messageId: String)
}
