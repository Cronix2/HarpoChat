// SPDX-License-Identifier: MIT
package app.calc.scientific.net

import android.util.Log
import org.corekit.FfiBlob
import org.corekit.FfiHop
import org.corekit.FfiMailboxKeys
import org.corekit.FfiMessage
import org.corekit.FfiNode
import org.corekit.FfiRoute
import org.corekit.FfiSizeClass
import org.corekit.buildAck
import org.corekit.buildFetch
import org.corekit.buildForward
import org.corekit.buildPut
import org.corekit.buildSubscribe
import org.corekit.createSurb
import org.corekit.decodeMessage
import org.corekit.epochOf
import org.corekit.forwardRoute
import org.corekit.localDelivery
import org.corekit.mailboxNodes
import org.corekit.randomBytes
import org.corekit.recoverReply
import org.corekit.replyRoute
import org.corekit.sizes
import java.security.MessageDigest
import java.util.PriorityQueue

/** Boîte surveillée : son secret de paire (directionnel) et la date depuis laquelle elle peut contenir du courrier. */
class Watch(val box: ByteArray, val sinceMs: Long)

/**
 * Boîtes aux lettres côté téléphone (spec §7, guide d'intégration §7 et §8.3).
 *
 * - **Abonnement** : à chaque connexion, un SUBSCRIBE (3 SURB) par boîte surveillée, par époque
 *   (courante et précédente) et par réplique. Chaque PUSH reçu consomme un SURB : on réabonne.
 * - **Relève** : FETCH des époques depuis la création de la boîte (7 jours au plus), en repli du temps réel.
 * - **Dépôt** : une copie du PUT par réplique (jamais sur sa propre gateway, sauf mesh de test).
 * - **Réception** : déduplication sur le contenu (chaque réplique pousse sa copie), puis ACK à chaque réplique.
 *
 * Rien ne part directement : [nextPacket] fournit le prochain paquet au rythme de Poisson de [MeshClient],
 * par ordre de priorité. Les `recovery` des SURB restent en RAM et meurent avec la connexion.
 */
internal class Mailboxes(private val inbound: suspend (box: ByteArray, blob: ByteArray) -> Unit) {

    /** Contexte de routage de la connexion courante. */
    class Ctx(val nodes: List<FfiNode>, val gateway: ByteArray, val inbox: ByteArray) {
        val dev: Boolean get() = nodes.size < 3
    }

    private enum class Kind { SUBSCRIBE, FETCH, PUT, ACK }

    private class Job(
        val kind: Kind,
        val box: ByteArray,
        val epoch: UInt,
        val priority: Int,
        /** Réplique visée ; null = toutes (développé au moment de l'envoi). */
        val replica: ByteArray? = null,
        val blob: ByteArray? = null,
        val ids: List<ByteArray> = emptyList(),
        val put: PutTracker? = null
    ) {
        var seq = 0L
    }

    /** Un PUT est « parti » quand sa copie a quitté le téléphone pour chaque réplique. */
    private class PutTracker(val onSent: () -> Unit) {
        var remaining = -1
    }

    private class SurbCtx(val kind: Kind, val box: ByteArray, val epoch: UInt, val replica: ByteArray, val recovery: ByteArray, val expiresAt: Long)

    @Volatile private var ctx: Ctx? = null
    private val lock = Any()
    private var seq = 0L
    private val queue = PriorityQueue<Job>(compareBy<Job>({ it.priority }, { it.seq }))
    private val watches = LinkedHashMap<String, Watch>()
    private val surbs = HashMap<String, SurbCtx>()
    private val keys = HashMap<String, FfiMailboxKeys>()
    private val seen = object : LinkedHashMap<String, Boolean>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 2048
    }
    private var subscribedEpoch = 0u
    private var lastRefreshAt = 0L

    // ---------------------------------------------------------------- API

    fun setWatches(list: List<Watch>) = synchronized(lock) {
        val next = list.associateBy { it.box.hex() }
        val added = next.keys - watches.keys
        watches.clear(); watches.putAll(next)
        if (ctx != null) added.forEach { k -> next[k]?.let { subscribeAndFetch(it, initial = true) } }
    }

    fun put(box: ByteArray, blob: ByteArray, onSent: () -> Unit) = synchronized(lock) {
        enqueue(Job(Kind.PUT, box, epochNow(), priority = 0, blob = blob, put = PutTracker(onSent)))
    }

    fun onLinkReady(c: Ctx) = synchronized(lock) {
        Log.d(TAG, "lien prêt : ${watches.size} boîte(s), ${c.nodes.size} nœud(s)")
        ctx = c
        surbs.clear()
        // Les abonnements et relèves en attente visaient l'ancienne connexion : on les refait.
        queue.removeAll { it.kind == Kind.SUBSCRIBE || it.kind == Kind.FETCH }
        subscribedEpoch = epochNow()
        lastRefreshAt = System.currentTimeMillis()
        watches.values.forEach { subscribeAndFetch(it, initial = true) }
    }

    fun onNodes(nodes: List<FfiNode>) = synchronized(lock) {
        ctx = ctx?.let { Ctx(nodes, it.gateway, it.inbox) }
    }

    fun onLinkLost() = synchronized(lock) {
        ctx = null
        surbs.clear()
    }

    /** Rotation d'époque, réabonnement périodique (repli si le nœud a redémarré), SURB expirés. */
    fun tick(now: Long) = synchronized(lock) {
        if (ctx == null) return@synchronized
        surbs.entries.removeAll { it.value.expiresAt <= now }
        val epoch = epochNow()
        if (epoch != subscribedEpoch) {
            subscribedEpoch = epoch
            watches.values.forEach { enqueue(Job(Kind.SUBSCRIBE, it.box, epoch, priority = 0)) }
        }
        if (now - lastRefreshAt > REFRESH_MS) {
            lastRefreshAt = now
            watches.values.forEach {
                enqueue(Job(Kind.SUBSCRIBE, it.box, epoch, priority = 1))
                enqueue(Job(Kind.FETCH, it.box, epoch, priority = 2))
            }
        }
    }

    /** Prochain paquet Sphinx à émettre, ou null (le rythme enverra alors un leurre). */
    fun nextPacket(): ByteArray? = synchronized(lock) {
        val c = ctx ?: return@synchronized null
        repeat(4) {
            val job = queue.poll() ?: return@synchronized null
            val packet = try {
                if (job.replica == null) { expand(job, c); null } else build(job, c)
            } catch (t: Throwable) {
                Log.d(TAG, "paquet abandonné : ${t.message}")
                null
            }
            if (packet != null) return@synchronized packet
        }
        null
    }

    /** Trame DELIVER qui n'est pas un loop : réponse à l'un de nos SURB. */
    suspend fun onDeliver(surbId: ByteArray, payload: ByteArray) {
        val sc = synchronized(lock) { surbs.remove(surbId.hex()) }
        if (sc == null) { Log.d(TAG, "DELIVER pour un SURB inconnu ${surbId.hex().take(8)}"); return }
        val msg = runCatching { decodeMessage(recoverReply(sc.recovery, payload)) }
            .onFailure { Log.d(TAG, "réponse ${sc.kind} illisible : ${it.message}") }.getOrNull() ?: return
        Log.d(TAG, "réponse ${sc.kind} : ${msg.javaClass.simpleName}")
        when (msg) {
            is FfiMessage.FetchResponse -> {
                deliver(sc, msg.blobs)
                if (msg.more) synchronized(lock) { enqueue(Job(Kind.FETCH, sc.box, sc.epoch, 1, replica = sc.replica)) }
            }
            is FfiMessage.Push -> {
                deliver(sc, msg.blobs)
                synchronized(lock) { enqueue(Job(Kind.SUBSCRIBE, sc.box, sc.epoch, 1, replica = sc.replica)) }
            }
            is FfiMessage.Notify ->
                synchronized(lock) { enqueue(Job(Kind.FETCH, sc.box, sc.epoch, 0, replica = sc.replica)) }
            else -> Unit
        }
    }

    fun close() = synchronized(lock) {
        queue.clear(); surbs.clear(); watches.clear()
        keys.values.forEach { runCatching { it.close() } }
        keys.clear()
    }

    // ---------------------------------------------------------------- interne

    private suspend fun deliver(sc: SurbCtx, blobs: List<FfiBlob>) {
        for (b in blobs) {
            val h = MessageDigest.getInstance("SHA-256").digest(b.blob).hex()
            val fresh = synchronized(lock) { seen.put(h, true) == null }
            if (fresh) {
                try {
                    inbound(sc.box, b.blob)
                } catch (t: Throwable) {
                    // Pas d'ACK : le blob reste sur la réplique et reviendra à la prochaine relève.
                    synchronized(lock) { seen.remove(h) }
                    Log.d(TAG, "blob non traité : ${t.message}")
                    continue
                }
            }
            synchronized(lock) { enqueue(Job(Kind.ACK, sc.box, sc.epoch, 1, replica = sc.replica, ids = listOf(b.id))) }
        }
    }

    private fun subscribeAndFetch(w: Watch, initial: Boolean) {
        val now = epochNow()
        val since = epochOf((w.sinceMs / 1000).toULong()).let { if (it > now) now else it }
        val oldest = maxOf(since, if (now >= 6u) now - 6u else 0u) // 7 époques : TTL de 7 jours
        enqueue(Job(Kind.SUBSCRIBE, w.box, now, priority = 0))
        if (now > 0u && now - 1u >= since) enqueue(Job(Kind.SUBSCRIBE, w.box, now - 1u, priority = 1))
        var e = now
        while (true) {
            enqueue(Job(Kind.FETCH, w.box, e, priority = if (now - e <= 1u) 1 else 2))
            if (e == oldest || e == 0u || !initial && now - e >= 1u) break
            e--
        }
    }

    private fun enqueue(j: Job) {
        j.seq = seq++
        queue.add(j)
    }

    /** Développe un travail « toutes répliques » en un travail par réplique. */
    private fun expand(job: Job, c: Ctx) {
        val reps = replicas(keysFor(job.box, job.epoch).mailboxId(), c)
        job.put?.remaining = reps.size
        reps.forEach {
            enqueue(Job(job.kind, job.box, job.epoch, job.priority, it, job.blob, job.ids, job.put).also { j -> j.seq = job.seq })
        }
    }

    private fun build(job: Job, c: Ctx): ByteArray {
        val k = keysFor(job.box, job.epoch)
        val now = nowSecs()
        val replica = job.replica!!
        val packet = when (job.kind) {
            Kind.PUT -> forward(buildPut(k, job.blob!!), replica, c)
            Kind.ACK -> forward(buildAck(k, job.ids, now), replica, c)
            Kind.FETCH -> forward(buildFetch(k, FETCH_MAX, surbsFor(Kind.FETCH, job, replica, c, FETCH_SURBS), now), replica, c)
            Kind.SUBSCRIBE -> {
                val n = sizes(FfiSizeClass.SMALL).maxSurbsPerSubscribe.toInt()
                forward(buildSubscribe(k, surbsFor(Kind.SUBSCRIBE, job, replica, c, n), now + SUBSCRIBE_SECS, now), replica, c)
            }
        }
        job.put?.let { t -> if (--t.remaining == 0) t.onSent() }
        Log.d(TAG, "envoi ${job.kind} époque ${job.epoch}")
        return packet
    }

    private fun replicas(mailboxId: ByteArray, c: Ctx): List<ByteArray> =
        if (c.dev) listOf(c.gateway)
        else mailboxNodes(mailboxId, c.nodes).filterNot { it.contentEquals(c.gateway) }

    private fun devRoute(c: Ctx): FfiRoute {
        val gw = c.nodes.first { it.idPub.contentEquals(c.gateway) }
        return FfiRoute(listOf(FfiHop(gw.idPub, gw.sphinxPub)), listOf(0uL))
    }

    private fun forward(envelope: ByteArray, replica: ByteArray, c: Ctx): ByteArray {
        val route = if (c.dev) devRoute(c) else forwardRoute(c.nodes, c.gateway, replica, MEAN_DELAY_MS)
        return buildForward(envelope, route.hops, localDelivery(), route.delaysMs, FfiSizeClass.SMALL)
    }

    private fun surbsFor(kind: Kind, job: Job, replica: ByteArray, c: Ctx, n: Int): List<ByteArray> = List(n) {
        val route = if (c.dev) devRoute(c) else replyRoute(c.nodes, c.gateway, replica, MEAN_DELAY_MS)
        val id = randomBytes(16u)
        val s = createSurb(route.hops, c.inbox, id, route.delaysMs)
        val ttl = if (kind == Kind.SUBSCRIBE) SUBSCRIBE_SECS.toLong() * 1000 else FETCH_SURB_TTL_MS
        surbs[id.hex()] = SurbCtx(kind, job.box, job.epoch, replica, s.recovery, System.currentTimeMillis() + ttl)
        s.surb
    }

    private fun keysFor(box: ByteArray, epoch: UInt): FfiMailboxKeys =
        keys.getOrPut(box.hex() + ":" + epoch) {
            if (keys.size > 256) { keys.values.forEach { runCatching { it.close() } }; keys.clear() }
            FfiMailboxKeys.derive(box, epoch)
        }

    private fun epochNow(): UInt = epochOf(nowSecs())
    private fun nowSecs(): ULong = (System.currentTimeMillis() / 1000).toULong()

    private companion object {
        const val TAG = "calc.box"
        const val MEAN_DELAY_MS = 100uL
        val FETCH_MAX: UShort = 16u
        const val FETCH_SURBS = 3
        const val FETCH_SURB_TTL_MS = 120_000L
        /** Durée d'un abonnement (le serveur la plafonne à 24 h). */
        val SUBSCRIBE_SECS: ULong = 6uL * 3600uL
        /** Réabonnement et relève périodiques, au cas où un nœud aurait redémarré (SURB perdus). */
        const val REFRESH_MS = 15 * 60 * 1000L
    }
}
