// file: com/example/harpochat/link/InMemoryMailbox.kt
package com.example.harpochat.link

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Simule un serveur "boîte aux lettres" :
 * - A enregistre une invite (optionnel)
 * - B poste une JoinRequest sur inviteId
 * - A consulte/valide/refuse
 */
object InMemoryMailbox {

    /** inviteId -> liste de JoinRequest */
    private val _requests = MutableStateFlow<Map<String, List<JoinRequest>>>(emptyMap())
    val requests: StateFlow<Map<String, List<JoinRequest>>> = _requests

    /** inviteId -> dernière décision d’A (accept/refuse) */
    private val _decisions = MutableStateFlow<Map<String, InviteDecision>>(emptyMap())
    val decisions: StateFlow<Map<String, InviteDecision>> = _decisions

    /** (facultatif) A "enregistre" son invite (pas obligatoire ici) */
    fun registerInvite(invite: InvitePayload) {
        _requests.update { m -> if (invite.expiresAt < System.currentTimeMillis()) m else m }
    }

    /** B poste une requête join pour un inviteId. */
    fun postJoinRequest(req: JoinRequest) {
        _requests.update { current ->
            val list = current[req.inviteId].orEmpty() + req
            current + (req.inviteId to list)
        }
    }

    /** A consulte les requêtes pendantes pour son inviteId. */
    fun pendingFor(inviteId: String): List<JoinRequest> = _requests.value[inviteId].orEmpty()

    /** A décide (accept/refuse) pour son inviteId => purge les requêtes. */
    fun decide(decision: InviteDecision) {
        _decisions.update { it + (decision.inviteId to decision) }
        _requests.update { it - decision.inviteId }
    }

    /** B peut lire la décision d’A si elle existe (polling simple). */
    fun decisionFor(inviteId: String): InviteDecision? = _decisions.value[inviteId]
}
