// SPDX-License-Identifier: MIT
package app.harpochat.net

import android.annotation.SuppressLint
import app.harpochat.core.FfiFrame
import app.harpochat.core.FfiFrameKind
import app.harpochat.core.FfiHandshake
import app.harpochat.core.FfiSizeClass
import app.harpochat.core.FfiTransport
import app.harpochat.core.decodeFrame
import app.harpochat.core.encodeFrame
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** Échec de connexion à un nœud, avec un message lisible pour le panneau réseau. */
class LinkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Lien chiffré vers un nœud du mesh (guide d'intégration §6).
 *
 * Pile : TCP → TLS → WebSocket binaire → Noise XX (`Noise_XX_25519_ChaChaPoly_BLAKE2s`).
 * - TLS ne sert qu'à ressembler à du HTTPS ordinaire : le certificat (auto-signé, ou celui d'un
 *   reverse proxy) n'est pas vérifié. L'URL réelle part dans la requête et le SNI.
 * - L'authentification du nœud, c'est Noise : il doit prouver l'identité [expectedNodeId]
 *   (clé d'une racine embarquée ou d'un descripteur vérifié), sinon le lien est refusé.
 * - Le téléphone n'a pas d'identité : clé statique neuve à chaque connexion.
 * - Après la poignée de main, chaque message WebSocket est un message Noise de taille fixe
 *   (2 048 ou 32 768 o). Toute autre taille, tout texte ou tout déchiffrement raté ferme le lien.
 */
class NodeLink private constructor(
    private val ws: WebSocket,
    private val transport: FfiTransport,
    private val inbound: Channel<ByteArray>,
    val endpoint: String,
    val nodeId: ByteArray
) {
    @Volatile var closed = false; private set

    /** Instant (ms) du dernier message reçu, trames de bourrage comprises : sert à la vivacité. */
    @Volatile var lastRxAt = System.currentTimeMillis(); private set

    /** Envoie une trame de lien de petite classe. Thread-safe (OkHttp met en file). */
    fun send(kind: FfiFrameKind, body: ByteArray): Boolean {
        if (closed) return false
        val frame = encodeFrame(kind, body, FfiSizeClass.SMALL)
        val ct = synchronized(transport) { transport.encrypt(frame) }
        return ws.send(ct.toByteString())
    }

    /**
     * Prochaine trame utile (les trames de bourrage sont ignorées), ou null quand le lien est fermé.
     * Lève si le nœud envoie un message invalide : l'appelant ferme alors le lien.
     */
    suspend fun receive(): FfiFrame? {
        while (true) {
            val message = inbound.receiveCatching().getOrNull() ?: return null
            lastRxAt = System.currentTimeMillis()
            if (message.size != SMALL_CT && message.size != LARGE_CT) throw LinkException("taille de message inattendue")
            val plain = synchronized(transport) { transport.decrypt(message) }
            val frame = decodeFrame(plain)
            if (frame.kind != FfiFrameKind.PADDING) return frame
        }
    }

    fun close() {
        if (closed) return
        closed = true
        ws.close(1000, null)
        inbound.close()
        synchronized(transport) { runCatching { transport.close() } }
    }

    companion object {
        private const val SMALL_CT = 2048
        private const val LARGE_CT = 32768
        private const val HANDSHAKE_TIMEOUT_MS = 15_000L

        /** Client partagé : TLS sans vérification de certificat (voir la doc de la classe). */
        val http: OkHttpClient by lazy {
            val trustAll = TrustAll()
            val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
            OkHttpClient.Builder()
                .sslSocketFactory(tls.socketFactory, trustAll)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // la vivacité est surveillée par MeshClient
                .writeTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build()
        }

        /**
         * Ouvre un lien et authentifie le nœud. Lève [LinkException] (ou une FfiException) en cas d'échec.
         */
        suspend fun connect(endpoint: String, expectedNodeId: ByteArray): NodeLink {
            val inbound = Channel<ByteArray>(Channel.UNLIMITED)
            val failure = Channel<Throwable>(1)
            val request = Request.Builder().url(endpoint).header("User-Agent", "Mozilla/5.0").build()
            val ws = http.newWebSocket(request, object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    inbound.trySend(bytes.toByteArray())
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    webSocket.cancel() // jamais de texte sur ce lien
                    failure.trySend(LinkException("message texte reçu"))
                    inbound.close()
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    inbound.close()
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    inbound.close()
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    failure.trySend(LinkException(describe(t, response), t))
                    inbound.close(t)
                }
            })

            val hs = FfiHandshake.initiator()
            try {
                val session = withTimeout(HANDSHAKE_TIMEOUT_MS) {
                    ws.send(hs.writeMessage().toByteString())               // message 1
                    val m2 = inbound.receiveCatching().getOrNull()
                        ?: throw failure.tryReceive().getOrNull() ?: LinkException("lien fermé par le nœud")
                    hs.readMessage(m2)                                     // message 2 : preuve NodeAuth
                    ws.send(hs.writeMessage().toByteString())              // message 3
                    hs.finish()
                }
                val proven = session.peerNodeId
                if (proven == null || !proven.contentEquals(expectedNodeId)) {
                    ws.cancel()
                    runCatching { session.transport.close() }
                    throw LinkException("identité du nœud incorrecte")
                }
                return NodeLink(ws, session.transport, inbound, endpoint, expectedNodeId)
            } catch (t: Throwable) {
                ws.cancel()
                throw t
            } finally {
                runCatching { hs.close() }
            }
        }

        private fun describe(t: Throwable, response: Response?): String = when {
            response != null -> "le serveur a répondu HTTP ${response.code} au lieu d'ouvrir le lien"
            t is java.net.UnknownHostException -> "adresse introuvable"
            t is java.net.ConnectException -> "connexion refusée"
            t is java.net.SocketTimeoutException -> "délai dépassé"
            t is javax.net.ssl.SSLException -> "échec TLS"
            else -> t.javaClass.simpleName
        }
    }
}

/**
 * Accepte tout certificat. Voulu : l'authentification réelle du nœud est faite par Noise
 * (vérification de son identité contre les racines), TLS ne sert que de camouflage.
 */
@SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
private class TrustAll : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
