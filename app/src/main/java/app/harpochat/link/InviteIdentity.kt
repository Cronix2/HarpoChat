// SPDX-License-Identifier: MIT
package app.harpochat.link

import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import java.security.SecureRandom

/**
 * Caller-side Ed25519 identity material used when creating an Invite.
 *
 * [privateSeed] is the 32-byte raw seed (never leaves the device once stored
 * in `SecureStore`). [publicKey] is derived from the seed and can be shared
 * publicly; it is used as the stable device identity by the relay.
 */
data class InviteIdentity(
    val publicKey: ByteArray,
    val privateSeed: ByteArray
) {
    companion object {
        /** Generate a fresh Ed25519 key pair. */
        fun generate(rng: SecureRandom = SecureRandom()): InviteIdentity {
            val seed = ByteArray(32).also { rng.nextBytes(it) }
            val spec = EdDSANamedCurveTable.getByName("Ed25519")
            val privSpec = EdDSAPrivateKeySpec(seed, spec)
            return InviteIdentity(
                publicKey = privSpec.a.toByteArray(), // derived public key
                privateSeed = seed
            )
        }

        /** Rebuild from a stored seed. */
        fun fromSeed(seed: ByteArray): InviteIdentity {
            require(seed.size == 32) { "seed must be 32 bytes" }
            val spec = EdDSANamedCurveTable.getByName("Ed25519")
            val privSpec = EdDSAPrivateKeySpec(seed, spec)
            return InviteIdentity(
                publicKey = privSpec.a.toByteArray(),
                privateSeed = seed
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InviteIdentity) return false
        return publicKey.contentEquals(other.publicKey)
            && privateSeed.contentEquals(other.privateSeed)
    }

    override fun hashCode(): Int {
        var r = publicKey.contentHashCode()
        r = 31 * r + privateSeed.contentHashCode()
        return r
    }
}
