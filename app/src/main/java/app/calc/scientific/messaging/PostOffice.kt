// SPDX-License-Identifier: MIT
package app.calc.scientific.messaging

import android.util.Log
import app.calc.scientific.data.AppDatabase
import app.calc.scientific.data.ChatRepository
import app.calc.scientific.data.InRequestEntity
import app.calc.scientific.data.InviteEntity
import app.calc.scientific.data.MessageEntity
import app.calc.scientific.data.OutRequestEntity
import app.calc.scientific.data.OutboxEntity
import app.calc.scientific.data.PeerEntity
import app.calc.scientific.data.SENDER_ME
import app.calc.scientific.data.ThreadEntity
import app.calc.scientific.e2e.AppMessage
import app.calc.scientific.e2e.Crypto
import app.calc.scientific.e2e.PairCipher
import app.calc.scientific.e2e.Pairing
import app.calc.scientific.e2e.hex
import app.calc.scientific.net.MeshClient
import app.calc.scientific.net.Watch
import app.calc.scientific.ui.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Courrier de la session : relie la base chiffrée au mesh.
 *
 * - Surveille les boîtes utiles : demandes reçues sur mes invitations, réponses à mes demandes,
 *   et la boîte entrante de chaque contact.
 * - Traite ce qui arrive : demande d'ajout, acceptation, message, accusé de réception.
 * - Vide la file d'envoi ([OutboxEntity]) au rythme du réseau. Elle survit au verrouillage :
 *   un message écrit hors ligne part à la prochaine ouverture.
 */
class PostOffice(
    private val db: AppDatabase,
    private val scope: CoroutineScope,
    private val mesh: MeshClient,
    private val repo: ChatRepository
) {
    private val pairing = db.pairingDao()
    private val outbox = db.outboxDao()
    private val messages = db.messageDao()
    private val threads = db.threadDao()

    val incomingRequests: Flow<List<InRequestEntity>> = pairing.inRequestsFlow()
    val outgoingRequests: Flow<List<OutRequestEntity>> = pairing.outRequestsFlow()

    private val _accepted = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Fil créé quand un contact accepte ma demande. */
    val accepted: SharedFlow<String> = _accepted

    private sealed class Route {
        class Request(val invite: InviteEntity) : Route()
        class Response(val request: OutRequestEntity) : Route()
        class Peer(val peer: PeerEntity) : Route()
    }

    @Volatile private var routes: Map<String, Route> = emptyMap()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    fun start() {
        mesh.inbound = ::onBlob
        scope.launch {
            combine(pairing.invitesFlow(), pairing.outRequestsFlow(), pairing.peersFlow()) { inv, out, peers ->
                buildMap {
                    inv.forEach { put(Pairing.requestBox(it.secret).hex(), Route.Request(it)) }
                    out.forEach { put(Pairing.responseBox(it.secret, it.kB).hex(), Route.Response(it)) }
                    peers.forEach { put(it.boxIn.hex(), Route.Peer(it)) }
                } to buildList {
                    inv.forEach { add(Watch(Pairing.requestBox(it.secret), it.createdAt)) }
                    out.forEach { add(Watch(Pairing.responseBox(it.secret, it.kB), it.createdAt)) }
                    peers.forEach { add(Watch(it.boxIn, it.createdAt)) }
                }
            }.collect { (map, watches) ->
                routes = map
                mesh.setWatches(watches)
            }
        }
        scope.launch {
            outbox.flow().collect { rows ->
                rows.filter { inFlight.add(it.id) }.forEach { row ->
                    mesh.put(row.box, row.blob) { scope.launch { onSent(row) } }
                }
            }
        }
        scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                pairing.purgeInvites(now)
                pairing.purgeOutRequests(now)
                pairing.purgeOrphanInRequests()
                delay(60_000)
            }
        }
    }

    // ---------------------------------------------------------------- invitations (côté A)

    /** Invitation QR à montrer : réutilise la dernière si elle a encore plus de 3 minutes. */
    suspend fun qrInvite(forceNew: Boolean = false): Pairing.Invite {
        val now = System.currentTimeMillis()
        if (!forceNew) {
            lastQr?.takeIf { it.expiresAt - now > 3 * 60_000 && pairing.invite(it.id) != null }?.let { return it }
        }
        return newInvite(Pairing.QR_TTL_MS, inPerson = true).also { lastQr = it }
    }
    @Volatile private var lastQr: Pairing.Invite? = null

    /** Invitation à envoyer par une autre messagerie. */
    suspend fun remoteInvite(): Pairing.Invite = newInvite(Pairing.REMOTE_TTL_MS, inPerson = false)

    private suspend fun newInvite(ttl: Long, inPerson: Boolean): Pairing.Invite {
        val inv = Pairing.newInvite(ttl)
        pairing.insertInvite(InviteEntity(inv.id, inv.secret, inv.expiresAt, inPerson, System.currentTimeMillis()))
        return inv
    }

    /** J'accepte la demande [requestId] : le contact est créé des deux côtés. Renvoie le fil, ou null. */
    suspend fun accept(requestId: String): String? {
        val r = pairing.inRequest(requestId) ?: return null
        val inv = pairing.invite(r.inviteId)?.takeUnless { it.expiresAt <= System.currentTimeMillis() } ?: return null
        val e = Crypto.x25519KeyPair()
        val keys = Pairing.derive(inv.secret, e.private, e.public, r.ePublic, inviter = true)
        val reply = Pairing.sealAccept(inv.secret, r.kB, Pairing.Accept(e.public, myName()))
        e.private.fill(0)
        queue(Pairing.responseBox(inv.secret, r.kB), reply, null)
        createPeer(keys, r.name, verified = inv.inPerson)
        // Invitation à usage unique : les autres demandes reçues pour elle disparaissent.
        pairing.deleteInvite(inv.id)
        pairing.deleteInRequestsFor(inv.id)
        return keys.threadId
    }

    suspend fun refuse(requestId: String) = pairing.deleteInRequest(requestId)

    // ---------------------------------------------------------------- invitations (côté B)

    sealed class ImportResult {
        class Sent(val sas: String) : ImportResult()
        class Error(val message: String) : ImportResult()
    }

    /** J'importe une invitation (scannée ou collée) : ma demande part, avec mon code de contrôle. */
    suspend fun importInvite(text: String, inPerson: Boolean): ImportResult {
        val inv = Pairing.decode(text) ?: return ImportResult.Error("Ce n'est pas une invitation.")
        if (inv.isExpired()) return ImportResult.Error("Cette invitation a expiré. Demandez-en une nouvelle à votre contact.")
        if (pairing.invite(inv.id) != null) return ImportResult.Error("C'est votre propre invitation.")
        val e = Crypto.x25519KeyPair()
        val kB = Crypto.random(32)
        val sas = Pairing.sas(inv.secret, e.public, kB)
        val now = System.currentTimeMillis()
        pairing.insertOutRequest(
            OutRequestEntity(inv.id, inv.secret, e.private, e.public, kB, sas, inPerson, now, inv.expiresAt + MAILBOX_TTL_MS)
        )
        queue(Pairing.requestBox(inv.secret), Pairing.sealRequest(inv.secret, Pairing.Request(e.public, kB, myName())), null)
        return ImportResult.Sent(sas)
    }

    suspend fun cancelRequest(id: String) = pairing.deleteOutRequest(id)

    // ---------------------------------------------------------------- messages

    /**
     * Envoie [text] dans le fil [threadId]. Un texte trop long pour un paquet part en plusieurs messages.
     * Les fils de démonstration (sans contact réel) gardent le simulateur local.
     */
    suspend fun send(threadId: String, text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        val peer = pairing.peer(threadId) ?: return repo.sendLocal(threadId, body)
        val ttl = threads.ephemeralSeconds(threadId) ?: 0L
        val cipher = PairCipher(peer.keyOut, peer.keyIn)
        for (part in split(body, AppMessage.MAX_TEXT_BYTES)) {
            val id = AppMessage.newId()
            val now = System.currentTimeMillis()
            messages.insert(
                MessageEntity(id.hex(), threadId, SENDER_ME, part, now, MessageStatus.SENDING.code, if (ttl > 0) now + ttl * 1000 else null)
            )
            queue(peer.boxOut, cipher.seal(AppMessage.Text(id, now, ttl, part)), id.hex())
        }
    }

    // ---------------------------------------------------------------- réception

    private suspend fun onBlob(box: ByteArray, blob: ByteArray) {
        when (val route = routes[box.hex()]) {
            is Route.Request -> onRequest(route.invite, blob)
            is Route.Response -> onAccept(route.request, blob)
            is Route.Peer -> onPeerMessage(route.peer, blob)
            null -> Unit // boîte qui n'est plus surveillée : le blob est acquitté et oublié
        }
    }

    private suspend fun onRequest(inv: InviteEntity, blob: ByteArray) {
        if (inv.expiresAt <= System.currentTimeMillis()) return
        val r = Pairing.openRequest(inv.secret, blob) ?: return
        pairing.insertInRequest(
            InRequestEntity(
                Pairing.requestId(inv.secret, r), inv.id, r.ePub, r.kB,
                r.name.ifBlank { "Contact" }, Pairing.sas(inv.secret, r.ePub, r.kB), System.currentTimeMillis()
            )
        )
    }

    private suspend fun onAccept(req: OutRequestEntity, blob: ByteArray) {
        val a = Pairing.openAccept(req.secret, req.kB, blob) ?: return
        val keys = Pairing.derive(req.secret, req.ePrivate, req.ePublic, a.ePub, inviter = false)
        createPeer(keys, a.name.ifBlank { "Contact" }, verified = req.inPerson)
        pairing.deleteOutRequest(req.id)
        _accepted.tryEmit(keys.threadId)
    }

    private suspend fun onPeerMessage(peer: PeerEntity, blob: ByteArray) {
        val cipher = PairCipher(peer.keyOut, peer.keyIn)
        when (val m = cipher.open(blob) ?: return) {
            is AppMessage.Text -> {
                val now = System.currentTimeMillis()
                val time = minOf(m.sentAt, now)
                val row = MessageEntity(
                    m.id.hex(), peer.threadId, "peer", m.body, time, MessageStatus.DELIVERED.code,
                    if (m.ttlSeconds > 0) now + m.ttlSeconds * 1000 else null
                )
                if (messages.insertIfAbsent(row) != -1L) {
                    queue(peer.boxOut, cipher.seal(AppMessage.Receipt(listOf(m.id))), null)
                }
            }
            is AppMessage.Receipt -> m.ids.forEach { messages.raiseStatus(it.hex(), MessageStatus.DELIVERED.code) }
        }
    }

    // ---------------------------------------------------------------- interne

    private suspend fun createPeer(keys: Pairing.PairKeys, name: String, verified: Boolean) {
        pairing.insertPeer(PeerEntity(keys.threadId, keys.boxOut, keys.boxIn, keys.keyOut, keys.keyIn, System.currentTimeMillis()))
        threads.insertIfAbsent(
            ThreadEntity(id = keys.threadId, title = name, verified = verified, ephemeralSeconds = repo.defaultEphemeralSeconds())
        )
    }

    private suspend fun queue(box: ByteArray, blob: ByteArray, messageId: String?) =
        outbox.insert(OutboxEntity(Crypto.random(16).hex(), box, blob, messageId, System.currentTimeMillis()))

    private suspend fun onSent(row: OutboxEntity) {
        try {
            outbox.delete(row.id)
            row.messageId?.let { messages.raiseStatus(it, MessageStatus.SENT.code) }
        } catch (t: Throwable) {
            Log.d(TAG, "file d'envoi : ${t.message}")
        } finally {
            inFlight.remove(row.id)
        }
    }

    private suspend fun myName(): String = repo.getSetting(ChatRepository.KEY_DISPLAY_NAME)?.takeIf { it.isNotBlank() } ?: "Contact"

    private companion object {
        const val TAG = "calc.post"
        /** Durée de conservation d'un blob par les nœuds (spec §7.3). */
        const val MAX_DAYS = 7
        const val MAILBOX_TTL_MS = MAX_DAYS * 24 * 3600 * 1000L

        /** Découpe [text] en morceaux d'au plus [maxBytes] octets UTF-8, sans couper un caractère. */
        fun split(text: String, maxBytes: Int): List<String> {
            val parts = ArrayList<String>()
            val sb = StringBuilder()
            var bytes = 0
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                val s = String(Character.toChars(cp))
                val n = s.toByteArray(Charsets.UTF_8).size
                if (bytes + n > maxBytes) { parts += sb.toString(); sb.clear(); bytes = 0 }
                sb.append(s); bytes += n
                i += Character.charCount(cp)
            }
            if (sb.isNotEmpty()) parts += sb.toString()
            return parts
        }
    }
}
