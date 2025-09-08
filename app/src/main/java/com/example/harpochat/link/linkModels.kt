/*
——————What are you doing here ?——————
⠀⣞⢽⢪⢣⢣⢣⢫⡺⡵⣝⡮⣗⢷⢽⢽⢽⣮⡷⡽⣜⣜⢮⢺⣜⢷⢽⢝⡽⣝
⠸⡸⠜⠕⠕⠁⢁⢇⢏⢽⢺⣪⡳⡝⣎⣏⢯⢞⡿⣟⣷⣳⢯⡷⣽⢽⢯⣳⣫⠇
⠀⠀⢀⢀⢄⢬⢪⡪⡎⣆⡈⠚⠜⠕⠇⠗⠝⢕⢯⢫⣞⣯⣿⣻⡽⣏⢗⣗⠏⠀
⠀⠪⡪⡪⣪⢪⢺⢸⢢⢓⢆⢤⢀⠀⠀⠀⠀⠈⢊⢞⡾⣿⡯⣏⢮⠷⠁⠀⠀
⠀⠀⠀⠈⠊⠆⡃⠕⢕⢇⢇⢇⢇⢇⢏⢎⢎⢆⢄⠀⢑⣽⣿⢝⠲⠉⠀⠀⠀⠀
⠀⠀⠀⠀⠀⡿⠂⠠⠀⡇⢇⠕⢈⣀⠀⠁⠡⠣⡣⡫⣂⣿⠯⢪⠰⠂⠀⠀⠀⠀
⠀⠀⠀⠀⡦⡙⡂⢀⢤⢣⠣⡈⣾⡃⠠⠄⠀⡄⢱⣌⣶⢏⢊⠂⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⢝⡲⣜⡮⡏⢎⢌⢂⠙⠢⠐⢀⢘⢵⣽⣿⡿⠁⠁⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠨⣺⡺⡕⡕⡱⡑⡆⡕⡅⡕⡜⡼⢽⡻⠏⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⣼⣳⣫⣾⣵⣗⡵⡱⡡⢣⢑⢕⢜⢕⡝⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⣴⣿⣾⣿⣿⣿⡿⡽⡑⢌⠪⡢⡣⣣⡟⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⡟⡾⣿⢿⢿⢵⣽⣾⣼⣘⢸⢸⣞⡟⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠁⠇⠡⠩⡫⢿⣝⡻⡮⣒⢽⠋⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
————————————————————————————————————
*/

// file: com/example/harpochat/link/LinkModels.kt
package com.example.harpochat.link

import java.util.Base64
import java.util.UUID

/** Payload affiché par A en QR. */
data class InvitePayload(
    val inviteId: String,         // identifiant public (UUID)
    val aIdPub: String,           // clé publique (placeholder pour l’instant)
    val oneTimeCode: String,      // code court qui change périodiquement (placeholder)
    val expiresAt: Long           // epoch millis
) {
    fun toWire(): String {
        // encodage très simple (dev only). Plus tard: JSON + Base64URL.
        val raw = listOf(inviteId, aIdPub, oneTimeCode, expiresAt.toString()).joinToString("|")
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
    }

    companion object {
        fun fromWire(s: String): InvitePayload? = try {
            val raw = String(Base64.getUrlDecoder().decode(s))
            val parts = raw.split("|")
            InvitePayload(
                inviteId = parts[0],
                aIdPub = parts[1],
                oneTimeCode = parts[2],
                expiresAt = parts[3].toLong()
            )
        } catch (_: Throwable) { null }
    }
}

/** Requête de B vers A après scan du QR. */
data class JoinRequest(
    val inviteId: String,
    val bIdPub: String,           // clé publique B (placeholder)
    val deviceInfo: String,       // ex: "Pixel 7"
    val ts: Long = System.currentTimeMillis()
)

/** Réponse d’A (accept/refuse). */
data class InviteDecision(
    val inviteId: String,
    val accept: Boolean
)

/** Helpers “faux crypto” pour la démo. */
object DemoCrypto {
    fun genPublicKey(): String = "pub_" + UUID.randomUUID().toString()
    fun genCode(): String = UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
}
