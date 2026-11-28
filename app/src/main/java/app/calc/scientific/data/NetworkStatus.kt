// SPDX-License-Identifier: MIT
package app.calc.scientific.data

/**
 * État réel du lien au mesh, affiché par l'indicateur des conversations et le panneau
 * « Chiffrement et réseau ». Produit par [app.calc.scientific.net.MeshClient].
 */
data class NetworkStatus(
    val phase: Phase,
    /** Nœuds de l'annuaire vérifié jusqu'aux racines embarquées. */
    val totalRelays: Int = 0,
    /** Nœuds de l'annuaire qui ne sont pas jugés défaillants par la mesure des loops. */
    val activeRelays: Int = 0,
    /** Médiane des allers-retours des loops, ou null tant qu'aucun n'est revenu. */
    val loopRttMs: Long? = null,
    val loopsSent: Int = 0,
    val loopsReturned: Int = 0,
    val loopsLost: Int = 0,
    /** Trafic leurre montant (drops et loops) et descendant (débit constant demandé à la gateway). */
    val coverTraffic: Boolean = false,
    /** Étapes du trajet d'un message, de la gateway à la boîte aux lettres. */
    val hops: List<String> = emptyList(),
    /** Au moins 3 nœuds : les paquets traversent entrée, relais et boîte, trois nœuds distincts. */
    val multiHop: Boolean = false,
    /**
     * Plusieurs sauts ET au moins deux opérateurs distincts. Sinon (moins de 3 nœuds, ou tous
     * parrainés par la même racine), un seul opérateur pourrait relier l'entrée et la boîte :
     * le contenu reste chiffré, mais l'écran signale un « réseau de test ».
     */
    val anonymous: Boolean = false,
    /** Empreinte courte du nœud d'entrée (8 caractères hexadécimaux). */
    val gateway: String? = null,
    /** Dernière erreur de connexion, lisible. */
    val error: String? = null,
    /** Prochaine tentative de connexion (horloge murale, ms), en phase [Phase.OFFLINE]. */
    val retryAt: Long? = null
) {
    enum class Phase { STOPPED, CONNECTING, ONLINE, OFFLINE }

    companion object {
        val Stopped = NetworkStatus(Phase.STOPPED)
    }
}
