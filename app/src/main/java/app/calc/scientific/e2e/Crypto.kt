// SPDX-License-Identifier: MIT
package app.calc.scientific.e2e

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Primitives de chiffrement de bout en bout, sur des implémentations standard
 * (BouncyCastle pour X25519, HKDF et Ed25519 ; AES-GCM du système).
 * Aucune primitive n'est réinventée ici : seulement leur assemblage.
 */
object Crypto {
    val rng = SecureRandom()

    fun random(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }

    /** HKDF-SHA256 (RFC 5869). */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: String, length: Int = 32): ByteArray {
        val gen = HKDFBytesGenerator(SHA256Digest())
        gen.init(HKDFParameters(ikm, salt, info.toByteArray(Charsets.UTF_8)))
        return ByteArray(length).also { gen.generateBytes(it, 0, length) }
    }

    fun sha256(vararg parts: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").apply { parts.forEach { update(it) } }.digest()

    // ------------------------------------------------------------- X25519

    class KeyPair(val private: ByteArray, val public: ByteArray)

    fun x25519KeyPair(): KeyPair {
        val sk = X25519PrivateKeyParameters(rng)
        return KeyPair(sk.encoded, sk.generatePublicKey().encoded)
    }

    /** Secret partagé X25519. Refuse un résultat nul (clé publique de faible ordre). */
    fun x25519(private: ByteArray, peerPublic: ByteArray): ByteArray {
        val out = ByteArray(32)
        X25519PrivateKeyParameters(private, 0).generateSecret(X25519PublicKeyParameters(peerPublic, 0), out, 0)
        require(out.any { it != 0.toByte() }) { "clé publique invalide" }
        return out
    }

    // ------------------------------------------------------------ AES-GCM

    private const val NONCE = 12
    private const val TAG_BITS = 128

    /** AES-256-GCM : nonce aléatoire (12 o) ‖ chiffré ‖ étiquette (16 o). [aad] est authentifié, pas chiffré. */
    fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        val nonce = random(NONCE)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        c.updateAAD(aad)
        return nonce + c.doFinal(plain)
    }

    /** Déchiffre, ou null si le message a été modifié ou n'est pas pour cette clé. */
    fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray? {
        if (sealed.size < NONCE + 16) return null
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed, 0, NONCE))
            c.updateAAD(aad)
            c.doFinal(sealed, NONCE, sealed.size - NONCE)
        } catch (_: AEADBadTagException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Surcoût de [seal] : nonce + étiquette. */
    const val SEAL_OVERHEAD = NONCE + 16

    // ------------------------------------------------------------ bourrage

    /**
     * Tailles possibles d'un blob déposé dans une boîte. Le nœud qui stocke le blob ne voit
     * que l'un de ces paliers, jamais la longueur réelle du message.
     * 1 400 o : taille maximale d'un blob dans un petit paquet.
     */
    val BLOB_SIZES = intArrayOf(256, 512, 1024, 1400)

    /** Plus grand texte clair qui tient dans un blob, une fois bourré et chiffré. */
    val MAX_PLAIN = BLOB_SIZES.last() - SEAL_OVERHEAD - 2

    /** Longueur (2 o) ‖ données ‖ zéros, jusqu'au palier qui donnera un blob de taille fixe. */
    fun pad(data: ByteArray): ByteArray {
        require(data.size <= MAX_PLAIN) { "message trop long" }
        val target = BLOB_SIZES.first { it - SEAL_OVERHEAD >= data.size + 2 } - SEAL_OVERHEAD
        val out = ByteArray(target)
        out[0] = (data.size shr 8).toByte()
        out[1] = data.size.toByte()
        data.copyInto(out, 2)
        return out
    }

    fun unpad(padded: ByteArray): ByteArray? {
        if (padded.size < 2) return null
        val len = ((padded[0].toInt() and 0xff) shl 8) or (padded[1].toInt() and 0xff)
        if (len > padded.size - 2) return null
        return padded.copyOfRange(2, 2 + len)
    }

    // ------------------------------------------------------------ identité

    /** Clé publique Ed25519 dérivée d'une graine de 32 octets (identité de l'appareil). */
    fun ed25519Public(seed: ByteArray): ByteArray = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
}
