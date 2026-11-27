// SPDX-License-Identifier: MIT
package app.calc.scientific.net

/**
 * Mesure du réseau par les loops (guide d'intégration §8.2.3).
 *
 * Un loop est un leurre qui revient à l'app après avoir traversé le mesh. Revenu : on mesure le
 * temps d'aller-retour. Non revenu après Σdélais + 10 s : il est perdu, et compté contre chaque
 * nœud de sa route (hors gateway). Un nœud qui perd plus de 75 % des loops, sur au moins 10
 * essais, devient suspect et sort des routes. Ces mesures restent dans l'app : rien n'est publié.
 *
 * Les identifiants (loops, nœuds) sont des chaînes hexadécimales : la classe reste pure et testable.
 */
class LoopTracker(private val graceMs: Long = 10_000, private val window: Int = 20) {

    private class Pending(val sentAt: Long, val deadline: Long, val nodes: List<String>)

    private val pending = HashMap<String, Pending>()
    private val attempts = HashMap<String, Int>()
    private val losses = HashMap<String, Int>()
    private val rtts = ArrayDeque<Long>()

    var sent = 0; private set
    var returned = 0; private set
    var lost = 0; private set

    @Synchronized
    fun onSent(loopId: String, now: Long, routeDelaysMs: Long, nodesExceptGateway: List<String>) {
        pending[loopId] = Pending(now, now + routeDelaysMs + graceMs, nodesExceptGateway)
        sent++
    }

    /** @return true si [loopId] était un loop en attente (sinon la trame est autre chose). */
    @Synchronized
    fun onReturned(loopId: String, now: Long): Boolean {
        val p = pending.remove(loopId) ?: return false
        returned++
        rtts.addLast(now - p.sentAt)
        while (rtts.size > window) rtts.removeFirst()
        p.nodes.forEach { attempts[it] = (attempts[it] ?: 0) + 1 }
        return true
    }

    /** Déclare perdus les loops dont l'échéance est passée. */
    @Synchronized
    fun expire(now: Long) {
        val late = pending.filterValues { it.deadline <= now }
        late.forEach { (id, p) ->
            pending.remove(id)
            lost++
            p.nodes.forEach {
                attempts[it] = (attempts[it] ?: 0) + 1
                losses[it] = (losses[it] ?: 0) + 1
            }
        }
    }

    /** Nœuds à éviter dans les routes. */
    @Synchronized
    fun suspects(): Set<String> = attempts.filter { (node, n) ->
        n >= 10 && (losses[node] ?: 0).toDouble() / n > 0.75
    }.keys

    /** Médiane des derniers allers-retours, ou null si aucun loop n'est encore revenu. */
    @Synchronized
    fun medianRttMs(): Long? {
        if (rtts.isEmpty()) return null
        val s = rtts.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    @Synchronized
    fun reset() {
        pending.clear()
    }
}
