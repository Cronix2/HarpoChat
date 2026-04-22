// SPDX-License-Identifier: MIT
package app.harpochat.transport

import app.harpochat.link.InviteIdentity
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Transport implementation that never touches the network: it just echoes
 * outgoing messages back as a fake reply (previous `ChatRepository.sendLocal`
 * behaviour) and reports "Connected" immediately.
 *
 * Kept as the default while the real relay is not yet reachable, and as a
 * useful stub for UI tests.
 */
class LoopbackTransport : MessageTransport {
    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    override val state: Flow<TransportState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<IncomingEnvelope>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val incoming: Flow<IncomingEnvelope> = _incoming.asSharedFlow()

    private var identity: InviteIdentity? = null

    override suspend fun start(identity: InviteIdentity) {
        this.identity = identity
        _state.value = TransportState.Connected
    }

    override suspend fun stop() {
        _state.value = TransportState.Disconnected
        identity = null
    }

    override suspend fun send(msg: OutgoingMessage): String {
        val id = UUID.randomUUID().toString()
        // Instantly fabricate an incoming "echo" envelope from the peer. This
        // mimics the previous local demo: callers above see a reply arrive.
        val echo = IncomingEnvelope(
            messageId = UUID.randomUUID().toString(),
            fromIdentity = msg.toIdentity,
            ciphertext = ("echo:" + String(msg.ciphertext)).toByteArray(),
            signature = ByteArray(64), // unchecked in loopback
            tsMs = System.currentTimeMillis()
        )
        _incoming.tryEmit(echo)
        return id
    }

    override suspend fun ack(messageId: String) {
        // No-op in loopback.
    }
}
