// SPDX-License-Identifier: MIT
package app.calc.scientific.net

import kotlin.math.ln
import kotlin.math.roundToLong

/**
 * Rythme du trafic montant (spec §6.3, guide d'intégration §8.2).
 *
 * Les paquets partent aux instants d'un processus de Poisson : l'intervalle suit une loi
 * exponentielle `-ln(1-u)/λ`. À chaque instant, l'app envoie un vrai paquet s'il y en a un
 * en attente, sinon un leurre. Un observateur voit donc le même flux, qu'on écrive ou non.
 */
object CoverSchedule {
    /** Débit montant app ouverte : environ un paquet toutes les deux secondes (spec §13). */
    const val UP_RATE_FOREGROUND_HZ = 0.5

    /** Débit descendant demandé à la gateway (trame CoverRate) app au premier plan. */
    const val DOWN_RATE_FOREGROUND_HZ = 1.0

    /** Prochain intervalle, en millisecondes, pour un tirage uniforme [u] dans [0, 1). */
    fun nextDelayMs(rateHz: Double, u: Double): Long {
        require(rateHz > 0) { "débit nul" }
        val v = u.coerceIn(0.0, 1.0 - 1e-12)
        return (-ln(1.0 - v) / rateHz * 1000.0).roundToLong()
    }
}
