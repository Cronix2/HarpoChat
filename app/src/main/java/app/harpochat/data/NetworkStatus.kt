// SPDX-License-Identifier: MIT
package app.harpochat.data

/**
 * État du réseau chiffré affiché dans le panneau « Chiffrement et réseau ».
 *
 * Tant que le transport réel (harpochat-core) n'est pas branché, ces valeurs sont
 * SIMULÉES et l'interface le dit explicitement ([simulated]).
 */
data class NetworkStatus(
    val activeRelays: Int,
    val totalRelays: Int,
    val meanDelayMs: Int,
    val coverTraffic: Boolean,
    val hops: List<String>,
    val simulated: Boolean
) {
    companion object {
        /** Ce que montrera un mesh de 5 nœuds avec un chemin entrée → relais → boîte (spec v0.3, §6.1). */
        val Simulated = NetworkStatus(
            activeRelays = 5,
            totalRelays = 5,
            meanDelayMs = 1200,
            coverTraffic = true,
            hops = listOf("Entrée", "Relais", "Boîte"),
            simulated = true
        )
    }
}
