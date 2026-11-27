// SPDX-License-Identifier: MIT
package app.calc.scientific.net

import android.util.Log
import org.corekit.FfiFrameKind
import org.corekit.FfiHop
import org.corekit.FfiNode
import org.corekit.FfiRoot
import org.corekit.FfiRoute
import org.corekit.FfiSizeClass
import org.corekit.buildDrop
import org.corekit.buildForward
import org.corekit.buildLoopPacket
import org.corekit.coverRateBody
import org.corekit.forwardRoute
import org.corekit.localDelivery
import org.corekit.phoneLoopRoute
import org.corekit.randomBytes
import org.corekit.verifyDirectory
import app.calc.scientific.data.NetworkStatus
import app.calc.scientific.data.NetworkStatus.Phase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.SecureRandom
import kotlin.math.min

/**
 * Connexion de l'app au mesh, tant que la session est déverrouillée.
 *
 * 1. Se relie à une racine embarquée (WSS + Noise, identité vérifiée : [NodeLink]).
 * 2. Déclare sa boîte de réception (jeton neuf à chaque connexion), le débit descendant voulu
 *    (CoverRate), puis demande l'annuaire, vérifié jusqu'aux racines.
 * 3. Émet son trafic montant au rythme d'un processus de Poisson : un vrai paquet s'il y en a,
 *    sinon un leurre, moitié drop, moitié loop. Les loops mesurent le délai et les pertes.
 * 4. Surveille le lien et se reconnecte avec un délai croissant.
 *
 * L'envoi et la réception de messages (PUT, SUBSCRIBE, PUSH) s'ajoutent à ce socle à l'étape N2.
 */
class MeshClient(
    private val scope: CoroutineScope,
    private val rootsProvider: () -> List<FfiRoot> = Roots::load
) {
    private val _status = MutableStateFlow(NetworkStatus.Stopped)
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    private val rnd = SecureRandom()
    private val loops = LoopTracker()
    private var job: Job? = null
    @Volatile private var link: NodeLink? = null
    @Volatile private var view: List<FfiNode>? = null
    @Volatile private var directoryAt = 0L
    @Volatile private var directoryWanted = false

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) { runForever() }
    }

    fun stop() {
        job?.cancel()
        job = null
        link?.close()
        link = null
        _status.value = NetworkStatus.Stopped
    }

    private suspend fun runForever() {
        val roots = try {
            rootsProvider()
        } catch (t: Throwable) {
            _status.value = NetworkStatus(Phase.OFFLINE, error = "racines de confiance illisibles")
            return
        }
        var attempt = 0
        var lastError: String? = null
        while (scope.isActive) {
            _status.update { NetworkStatus(Phase.CONNECTING, error = lastError) }
            try {
                val connected = connectAny(roots)
                attempt = 0
                runLink(connected, roots.map { it.idPub })
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                lastError = t.message ?: t.javaClass.simpleName
                Log.d(TAG, "lien perdu : $lastError")
            } finally {
                link?.close()
                link = null
                view = null
                loops.reset()
            }
            attempt++
            val wait = backoffMs(attempt)
            _status.value = NetworkStatus(Phase.OFFLINE, error = lastError, retryAt = System.currentTimeMillis() + wait)
            delay(wait)
        }
    }

    /** Essaie chaque adresse de chaque racine, dans un ordre aléatoire. */
    private suspend fun connectAny(roots: List<FfiRoot>): NodeLink {
        var last: Throwable = LinkException("aucune adresse de nœud")
        for (root in roots.shuffled(rnd)) {
            for (endpoint in root.endpoints.shuffled(rnd)) {
                try {
                    return NodeLink.connect(endpoint, root.idPub).also { link = it }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    last = t
                }
            }
        }
        throw last
    }

    private suspend fun runLink(l: NodeLink, rootIds: List<ByteArray>): Nothing = coroutineScope {
        val inbox = randomBytes(32u)
        l.send(FfiFrameKind.REGISTER_INBOX, inbox)
        l.send(FfiFrameKind.COVER_RATE, coverRateBody(CoverSchedule.DOWN_RATE_FOREGROUND_HZ))
        l.send(FfiFrameKind.DIRECTORY_REQ, ByteArray(0))
        directoryAt = System.currentTimeMillis()
        publish(l)

        launch { uplink(l, inbox) }
        launch { watchdog(l) }
        receive(l, rootIds) // rend la main (ou lève) quand le lien tombe
        throw LinkException("lien fermé par le nœud")
    }

    private suspend fun receive(l: NodeLink, rootIds: List<ByteArray>) {
        while (true) {
            val frame = l.receive() ?: return
            when (frame.kind) {
                FfiFrameKind.DIRECTORY -> {
                    val nodes = verifyDirectory(frame.body, rootIds, nowSecs())
                    view = nodes
                    directoryAt = System.currentTimeMillis()
                    publish(l)
                }
                FfiFrameKind.DELIVER -> {
                    if (frame.body.size >= 16) {
                        val id = frame.body.copyOfRange(0, 16).hex()
                        if (loops.onReturned(id, System.currentTimeMillis())) publish(l)
                        // Sinon : réponse à un SURB (FETCH / SUBSCRIBE), traitée à l'étape N2.
                    }
                }
                else -> Unit
            }
        }
    }

    /** Trafic montant : un paquet à chaque instant d'un processus de Poisson. */
    private suspend fun uplink(l: NodeLink, inbox: ByteArray) {
        while (true) {
            delay(CoverSchedule.nextDelayMs(CoverSchedule.UP_RATE_FOREGROUND_HZ, rnd.nextDouble()))
            if (directoryWanted) {
                directoryWanted = false
                l.send(FfiFrameKind.DIRECTORY_REQ, ByteArray(0))
                continue
            }
            val nodes = view
            val packet = nodes?.let { runCatching { if (rnd.nextBoolean()) drop(it, l.nodeId) else loop(it, l.nodeId, inbox) }.getOrNull() }
            if (packet != null) l.send(FfiFrameKind.SPHINX, packet)
            else l.send(FfiFrameKind.PADDING, ByteArray(0))
        }
    }

    /** Vivacité du lien, pertes des loops, rafraîchissement de l'annuaire. */
    private suspend fun watchdog(l: NodeLink) {
        while (true) {
            delay(WATCHDOG_MS)
            val now = System.currentTimeMillis()
            if (now - l.lastRxAt > SILENCE_LIMIT_MS) throw LinkException("le nœud ne répond plus")
            loops.expire(now)
            if (now - directoryAt > DIRECTORY_REFRESH_MS) { directoryWanted = true; directoryAt = now }
            publish(l)
        }
    }

    // ---------------------------------------------------------------- leurres

    /** Nœuds utilisables pour les routes : sans les nœuds jugés défaillants (la gateway reste). */
    private fun usable(nodes: List<FfiNode>, gateway: ByteArray): List<FfiNode> {
        val bad = loops.suspects()
        return nodes.filter { it.idPub.contentEquals(gateway) || it.idPub.hex() !in bad }
    }

    private fun gatewayHop(nodes: List<FfiNode>, gateway: ByteArray): FfiHop? =
        nodes.firstOrNull { it.idPub.contentEquals(gateway) }?.let { FfiHop(it.idPub, it.sphinxPub) }

    /** Un mesh de moins de 3 nœuds n'a pas de chemin anonyme : trajet direct par la gateway. */
    private fun devRoute(nodes: List<FfiNode>, gateway: ByteArray): FfiRoute? =
        gatewayHop(nodes, gateway)?.let { FfiRoute(listOf(it), listOf(0uL)) }

    private fun drop(nodes: List<FfiNode>, gateway: ByteArray): ByteArray? {
        val ok = usable(nodes, gateway)
        val route = if (ok.size >= 3) {
            val replicas = ok.filter { "mailbox" in it.roles && !it.idPub.contentEquals(gateway) }
            if (replicas.isEmpty()) return null
            forwardRoute(ok, gateway, replicas[rnd.nextInt(replicas.size)].idPub, MEAN_DELAY_MS)
        } else devRoute(ok, gateway) ?: return null
        return buildForward(buildDrop(), route.hops, localDelivery(), route.delaysMs, FfiSizeClass.SMALL)
    }

    private fun loop(nodes: List<FfiNode>, gateway: ByteArray, inbox: ByteArray): ByteArray? {
        val ok = usable(nodes, gateway)
        val route = if (ok.size >= 3) phoneLoopRoute(ok, gateway, MEAN_DELAY_MS) else devRoute(ok, gateway) ?: return null
        val loopId = randomBytes(16u)
        val packet = buildLoopPacket(route.hops, inbox, loopId, route.delaysMs)
        loops.onSent(
            loopId.hex(),
            System.currentTimeMillis(),
            route.delaysMs.sumOf { it.toLong() },
            route.hops.map { it.idPub }.filterNot { it.contentEquals(gateway) }.map { it.hex() }
        )
        return packet
    }

    // ---------------------------------------------------------------- état

    private fun publish(l: NodeLink) {
        val nodes = view.orEmpty()
        val bad = loops.suspects()
        val active = nodes.count { it.idPub.hex() !in bad }
        val anonymous = active >= 3
        _status.value = NetworkStatus(
            phase = Phase.ONLINE,
            totalRelays = nodes.size,
            activeRelays = active,
            loopRttMs = loops.medianRttMs(),
            loopsSent = loops.sent,
            loopsReturned = loops.returned,
            loopsLost = loops.lost,
            coverTraffic = true,
            hops = when {
                nodes.isEmpty() -> emptyList()
                !anonymous -> listOf("Nœud")
                active >= 12 -> listOf("Entrée", "Relais ×3", "Boîte")
                else -> listOf("Entrée", "Relais", "Boîte")
            },
            anonymous = anonymous,
            gateway = l.nodeId.hex().take(8)
        )
    }

    private fun backoffMs(attempt: Int): Long {
        val base = min(MAX_BACKOFF_MS, 2_000L shl min(attempt - 1, 5))
        return (base * (0.8 + 0.4 * rnd.nextDouble())).toLong()
    }

    private fun nowSecs(): ULong = (System.currentTimeMillis() / 1000).toULong()

    private companion object {
        const val TAG = "calc.net"
        const val MEAN_DELAY_MS = 100uL
        const val WATCHDOG_MS = 5_000L
        /** La gateway émet au moins à 1 trame/s demandée : 20 s de silence, c'est un lien mort. */
        const val SILENCE_LIMIT_MS = 20_000L
        /** Descripteurs valables 24 h, clés Sphinx tournantes : on rafraîchit bien avant. */
        const val DIRECTORY_REFRESH_MS = 60 * 60 * 1000L
        const val MAX_BACKOFF_MS = 60_000L
    }
}

internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
