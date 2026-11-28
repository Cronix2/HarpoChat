// SPDX-License-Identifier: MIT
package app.calc.scientific.e2e

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Base64

/**
 * Ajout d'un contact, avec consentement explicite (spec §9).
 *
 * 1. **A montre une invitation** (QR en personne, ou texte envoyé par une autre messagerie) :
 *    un secret S de 32 octets et une date d'expiration. Rien d'autre : ni nom, ni adresse.
 * 2. **B l'importe et envoie une demande** dans la boîte « demande », dérivée de S :
 *    sa clé X25519 éphémère, un aléa kB et son nom, chiffrés avec une clé dérivée de S.
 * 3. **A voit la demande et le code de contrôle**, le compare avec celui de B, puis accepte
 *    (ou ignore). Sans acceptation, B ne reçoit rien : ni clé, ni boîte permanente.
 * 4. **A répond** dans la boîte « réponse », dérivée de S et de kB : sa clé X25519 éphémère
 *    et son nom. Chacun calcule alors le même secret X25519, d'où découlent les deux boîtes
 *    permanentes (une par sens), les deux clés de chiffrement et l'identifiant du fil.
 *
 * Une personne qui aurait photographié le QR peut lire la demande, mais pas calculer le secret
 * X25519. Si elle envoie sa propre demande, son code de contrôle diffère de celui de B :
 * la comparaison à voix haute la démasque.
 */
object Pairing {
    private val SALT = "calc-pair-v1".toByteArray()
    private const val VERSION: Byte = 1

    /** Invitation montrée en personne (QR) : courte, pour qu'une photo du QR ne serve pas plus tard. */
    const val QR_TTL_MS = 15 * 60 * 1000L
    /** Invitation envoyée par une autre messagerie : le temps que le contact l'ouvre. */
    const val REMOTE_TTL_MS = 48 * 60 * 60 * 1000L
    const val MAX_NAME_BYTES = 64

    class Invite(val secret: ByteArray, val expiresAt: Long) {
        val id: String get() = inviteId(secret)
        fun isExpired(now: Long = System.currentTimeMillis()) = now >= expiresAt
    }

    fun newInvite(ttlMs: Long, now: Long = System.currentTimeMillis()) =
        Invite(Crypto.random(32), (now + ttlMs) / 1000 * 1000)

    /** Texte de l'invitation : 51 caractères base64url, sans rien qui désigne l'application. */
    fun encode(inv: Invite): String {
        val b = ByteBuffer.allocate(1 + 32 + 4)
        b.put(VERSION).put(inv.secret).putInt((inv.expiresAt / 1000).toInt())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b.array())
    }

    fun decode(text: String): Invite? {
        val raw = runCatching { Base64.getUrlDecoder().decode(text.trim()) }.getOrNull() ?: return null
        if (raw.size != 37 || raw[0] != VERSION) return null
        val b = ByteBuffer.wrap(raw, 1, 36)
        val secret = ByteArray(32).also { b.get(it) }
        val exp = (b.int.toLong() and 0xffffffffL) * 1000
        return Invite(secret, exp)
    }

    fun inviteId(s: ByteArray) = Crypto.hkdf(s, SALT, "invite-id", 16).hex()
    fun requestBox(s: ByteArray) = Crypto.hkdf(s, SALT, "request-box")
    private fun requestKey(s: ByteArray) = Crypto.hkdf(s, SALT, "request-key")
    fun responseBox(s: ByteArray, kB: ByteArray) = Crypto.hkdf(s + kB, SALT, "response-box")
    private fun responseKey(s: ByteArray, kB: ByteArray) = Crypto.hkdf(s + kB, SALT, "response-key")

    // ------------------------------------------------------------ messages

    class Request(val ePub: ByteArray, val kB: ByteArray, val name: String)
    class Accept(val ePub: ByteArray, val name: String)

    fun sealRequest(s: ByteArray, r: Request): ByteArray =
        Crypto.seal(requestKey(s), Crypto.pad(VERSION.toByte() + r.ePub + r.kB + nameBytes(r.name)), AAD_REQ)

    fun openRequest(s: ByteArray, blob: ByteArray): Request? {
        val p = Crypto.open(requestKey(s), blob, AAD_REQ)?.let(Crypto::unpad) ?: return null
        if (p.size < 65 || p[0] != VERSION) return null
        return Request(p.copyOfRange(1, 33), p.copyOfRange(33, 65), String(p, 65, p.size - 65, Charsets.UTF_8))
    }

    fun sealAccept(s: ByteArray, kB: ByteArray, a: Accept): ByteArray =
        Crypto.seal(responseKey(s, kB), Crypto.pad(VERSION.toByte() + a.ePub + nameBytes(a.name)), AAD_ACC)

    fun openAccept(s: ByteArray, kB: ByteArray, blob: ByteArray): Accept? {
        val p = Crypto.open(responseKey(s, kB), blob, AAD_ACC)?.let(Crypto::unpad) ?: return null
        if (p.size < 33 || p[0] != VERSION) return null
        return Accept(p.copyOfRange(1, 33), String(p, 33, p.size - 33, Charsets.UTF_8))
    }

    /** Identifiant local d'une demande reçue (déduplication). */
    fun requestId(s: ByteArray, r: Request) = Crypto.sha256(s, r.ePub, r.kB).copyOfRange(0, 16).hex()

    /** Code de contrôle à comparer à voix haute : 6 chiffres, liés à S et à la demande de B. */
    fun sas(s: ByteArray, ePubB: ByteArray, kB: ByteArray): String {
        val h = Crypto.hkdf(s + ePubB + kB, SALT, "sas", 8)
        val n = ByteBuffer.wrap(h).long.let { if (it < 0) -it else it } % 1_000_000
        return "%06d".format(n).let { it.substring(0, 3) + " " + it.substring(3) }
    }

    // ------------------------------------------------------------ résultat

    /** Ce que chaque côté garde pour la conversation : boîtes et clés, une par sens. */
    class PairKeys(
        val threadId: String,
        val boxOut: ByteArray,
        val boxIn: ByteArray,
        val keyOut: ByteArray,
        val keyIn: ByteArray
    )

    /**
     * Secrets permanents de la paire. [inviter] : vrai pour A, qui a montré l'invitation.
     * Les deux côtés obtiennent les mêmes valeurs, croisées (la sortie de A est l'entrée de B).
     */
    fun derive(s: ByteArray, myPrivate: ByteArray, myPublic: ByteArray, peerPublic: ByteArray, inviter: Boolean): PairKeys {
        val shared = Crypto.x25519(myPrivate, peerPublic)
        val (pubA, pubB) = if (inviter) myPublic to peerPublic else peerPublic to myPublic
        val root = Crypto.hkdf(shared + pubA + pubB, s, "root")
        val a2bBox = Crypto.hkdf(root, SALT, "a2b-box")
        val b2aBox = Crypto.hkdf(root, SALT, "b2a-box")
        val a2bKey = Crypto.hkdf(root, SALT, "a2b-key")
        val b2aKey = Crypto.hkdf(root, SALT, "b2a-key")
        val thread = Crypto.hkdf(root, SALT, "thread", 16).hex()
        shared.fill(0); root.fill(0)
        return if (inviter) PairKeys(thread, a2bBox, b2aBox, a2bKey, b2aKey)
        else PairKeys(thread, b2aBox, a2bBox, b2aKey, a2bKey)
    }

    private val AAD_REQ = "calc-pair-request".toByteArray()
    private val AAD_ACC = "calc-pair-accept".toByteArray()

    private fun nameBytes(name: String): ByteArray {
        val out = ByteArrayOutputStream()
        for (ch in name.trim()) {
            val b = ch.toString().toByteArray(Charsets.UTF_8)
            if (out.size() + b.size > MAX_NAME_BYTES) break
            out.write(b)
        }
        return out.toByteArray()
    }
}

fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
fun String.unhex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
private operator fun Byte.plus(other: ByteArray): ByteArray = byteArrayOf(this) + other
