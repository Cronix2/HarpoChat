// SPDX-License-Identifier: MIT
package app.harpochat.transport

import android.util.Base64
import app.harpochat.link.InviteIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * WebSocket transport talking to the HarpoChat Rust relay server.
 *
 * Performs the Hello → Challenge → AuthResponse handshake with the user's
 * [InviteIdentity], then exchanges JSON-framed Envelopes. The server never
 * sees plaintext: payloads are expected to be Signal-protocol ciphertexts
 * prepared higher up the stack.
 */
class RelayWsTransport(
    private val relayUrl: String,
    private val serverVersion: Int = 1,
    private val okHttp: OkHttpClient = defaultClient()
) : MessageTransport {

    companion object {
        private const val AUTH_DOMAIN = "harpochat/v1/auth-challenge"
        private const val ENVELOPE_DOMAIN = "harpochat/v1/envelope"
        private const val CONNECT_TIMEOUT_MS = 10_000L

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // keep WS open
                .build()
    }

    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    override val state: Flow<TransportState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<IncomingEnvelope>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val incoming: Flow<IncomingEnvelope> = _incoming.asSharedFlow()

    private var socket: WebSocket? = null
    private var identity: InviteIdentity? = null
    private var loopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Pending send → continuation awaiting SendAck.
    private val pendingSends = ConcurrentHashMap<String, Continuation<String>>()
    // Continuation waiting for the welcome frame during start().
    private var welcomeCont: Continuation<Unit>? = null
    // Continuation waiting for the challenge frame during start().
    private var challengeCont: Continuation<ByteArray>? = null

    override suspend fun start(identity: InviteIdentity) {
        stop()
        this.identity = identity
        _state.value = TransportState.Connecting

        val request = Request.Builder().url(relayUrl).build()
        val listener = Listener()
        val ws = okHttp.newWebSocket(request, listener)
        socket = ws

        // 1. Send Hello.
        val hello = JSONObject().apply {
            put("type", "hello")
            put("identity", b64(identity.publicKey))
            put("version", serverVersion)
        }
        ws.send(hello.toString())

        // 2. Await Challenge.
        _state.value = TransportState.Authenticating
        val nonce = withTimeout(CONNECT_TIMEOUT_MS) {
            suspendCoroutine<ByteArray> { cont -> challengeCont = cont }
        }

        // 3. Sign and reply.
        val sig = signChallenge(identity.privateSeed, nonce)
        val authResp = JSONObject().apply {
            put("type", "auth_response")
            put("signature", b64(sig))
        }
        ws.send(authResp.toString())

        // 4. Await Welcome.
        withTimeout(CONNECT_TIMEOUT_MS) {
            suspendCoroutine<Unit> { cont -> welcomeCont = cont }
        }
        _state.value = TransportState.Connected
    }

    override suspend fun stop() {
        socket?.close(1000, "client stop")
        socket = null
        loopJob?.cancel()
        loopJob = null
        identity = null
        pendingSends.forEach { (_, c) ->
            c.resumeWithException(IllegalStateException("transport stopped"))
        }
        pendingSends.clear()
        _state.value = TransportState.Disconnected
    }

    override suspend fun send(msg: OutgoingMessage): String {
        val ws = socket ?: error("transport not started")
        val envelope = JSONObject().apply {
            put("from", b64(identity!!.publicKey))
            put("to", b64(msg.toIdentity))
            put("ciphertext", b64(msg.ciphertext))
            put("signature", b64(msg.signature))
            put("ts_ms", msg.tsMs)
        }
        val frame = JSONObject().apply {
            put("type", "send")
            put("envelope", envelope)
        }

        return suspendCoroutine { cont ->
            // The relay stamps the id. We can't correlate requests 1:1 without
            // a client-side id. To stay simple, we resume the first pending
            // waiter on the next SendAck. If concurrent sends are required,
            // callers should serialize or we add a client-side correlation id
            // to the wire protocol.
            val tag = UUID.randomUUID().toString()
            pendingSends[tag] = cont
            ws.send(frame.toString())
        }
    }

    override suspend fun ack(messageId: String) {
        val ws = socket ?: return
        val frame = JSONObject().apply {
            put("type", "ack")
            put("message_id", messageId)
        }
        ws.send(frame.toString())
    }

    // ------------------------------------------------------------------------

    private inner class Listener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val obj = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (obj.optString("type")) {
                "challenge" -> {
                    val nonce = b64d(obj.getString("nonce"))
                    challengeCont?.also {
                        challengeCont = null
                        it.resume(nonce)
                    }
                }
                "welcome" -> welcomeCont?.also {
                    welcomeCont = null
                    it.resume(Unit)
                }
                "send_ack" -> {
                    val id = obj.getString("message_id")
                    // Resume first pending continuation (see send() comment).
                    val firstKey = pendingSends.keys.firstOrNull()
                    if (firstKey != null) {
                        val cont = pendingSends.remove(firstKey)
                        cont?.resume(id)
                    }
                }
                "deliver" -> {
                    val id = obj.getString("message_id")
                    val env = obj.getJSONObject("envelope")
                    scope.launch {
                        _incoming.emit(
                            IncomingEnvelope(
                                messageId = id,
                                fromIdentity = b64d(env.getString("from")),
                                ciphertext = b64d(env.getString("ciphertext")),
                                signature = b64d(env.getString("signature")),
                                tsMs = env.getLong("ts_ms")
                            )
                        )
                    }
                }
                "error" -> {
                    val msg = obj.optString("message", "server error")
                    _state.value = TransportState.Failed(msg)
                }
                "pong" -> Unit
                else -> Unit // ignore unknown frames forward-compatibly
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            // Server speaks JSON text, but don't crash on binary.
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            challengeCont?.also {
                challengeCont = null
                it.resumeWithException(t)
            }
            welcomeCont?.also {
                welcomeCont = null
                it.resumeWithException(t)
            }
            pendingSends.forEach { (_, c) -> c.resumeWithException(t) }
            pendingSends.clear()
            _state.value = TransportState.Failed(t.message ?: t.javaClass.simpleName)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _state.value = TransportState.Disconnected
        }
    }

    // ------------------------------------------------------------------------
    // Helpers

    private fun signChallenge(privSeed: ByteArray, nonce: ByteArray): ByteArray {
        val spec = EdDSANamedCurveTable.getByName("Ed25519")
        val key = EdDSAPrivateKey(EdDSAPrivateKeySpec(privSeed, spec))
        val md = MessageDigest.getInstance("SHA-256")
        md.update(AUTH_DOMAIN.toByteArray(Charsets.UTF_8))
        md.update(nonce)
        val digest = md.digest()
        val engine = EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm))
        engine.initSign(key)
        engine.update(digest)
        return engine.sign()
    }

    /**
     * Compute the SHA256 digest a caller must sign over to produce an
     * envelope signature: SHA256("harpochat/v1/envelope" || to || ts_ms_le || ct).
     * Matches `harpo_crypto::envelope_digest` on the server.
     */
    fun envelopeDigest(to: ByteArray, tsMs: Long, ciphertext: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(ENVELOPE_DOMAIN.toByteArray(Charsets.UTF_8))
        md.update(to)
        md.update(longLe(tsMs))
        md.update(ciphertext)
        return md.digest()
    }

    private fun longLe(v: Long): ByteArray {
        val out = ByteArray(8)
        var x = v
        for (i in 0..7) {
            out[i] = (x and 0xFF).toByte()
            x = x shr 8
        }
        return out
    }

    private fun b64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun b64d(s: String): ByteArray =
        Base64.decode(s, Base64.NO_WRAP)
}
