// SPDX-License-Identifier: MIT
package app.harpochat.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InviteCodecTest {

    private lateinit var identity: InviteIdentity

    @Before
    fun setUp() {
        identity = InviteIdentity.generate()
    }

    @Test
    fun encodeDecodeRoundtrip() {
        val invite = InviteCodec.create(
            identityPub = identity.publicKey,
            identityPrivSeed = identity.privateSeed,
            displayName = "Alice",
            threadId = "thread-42",
            relayUrl = "wss://relay.example/v1/ws",
            ttlSec = 60
        )
        val wire = InviteCodec.encode(invite)
        val decoded = InviteCodec.decode(wire).getOrNull()
        assertNotNull(decoded)
        assertEquals(invite, decoded)
    }

    @Test
    fun signatureVerifiesForUntamperedPayload() {
        val invite = InviteCodec.create(
            identityPub = identity.publicKey,
            identityPrivSeed = identity.privateSeed,
            displayName = "Bob",
            threadId = "t",
            relayUrl = "ws://127.0.0.1:8443/v1/ws"
        )
        assertTrue(InviteCodec.verify(invite))
    }

    @Test
    fun signatureFailsWhenCodeIsTampered() {
        val invite = InviteCodec.create(
            identityPub = identity.publicKey,
            identityPrivSeed = identity.privateSeed,
            displayName = "Bob",
            threadId = "t",
            relayUrl = "ws://127.0.0.1:8443/v1/ws"
        )
        val tampered = invite.copy(code = "aaaaaaaaaa")
        assertFalse(InviteCodec.verify(tampered))
    }

    @Test
    fun signatureFailsWhenRelayIsTampered() {
        val invite = InviteCodec.create(
            identityPub = identity.publicKey,
            identityPrivSeed = identity.privateSeed,
            displayName = "Bob",
            threadId = "t",
            relayUrl = "ws://127.0.0.1:8443/v1/ws"
        )
        val tampered = invite.copy(relayUrl = "ws://evil.example/v1/ws")
        assertFalse(InviteCodec.verify(tampered))
    }

    @Test
    fun expirationFlag() {
        val invite = InviteCodec.create(
            identityPub = identity.publicKey,
            identityPrivSeed = identity.privateSeed,
            displayName = "Bob",
            threadId = "t",
            relayUrl = "ws://127.0.0.1:8443/v1/ws",
            ttlSec = 1,
            now = 1_700_000_000_000L
        )
        assertFalse(invite.isExpired(1_700_000_000_000L))
        assertTrue(invite.isExpired(1_700_000_002_000L))
    }

    @Test
    fun identityFromSeedIsDeterministic() {
        val seed = ByteArray(32) { it.toByte() }
        val a = InviteIdentity.fromSeed(seed)
        val b = InviteIdentity.fromSeed(seed)
        assertTrue(a.publicKey.contentEquals(b.publicKey))
    }

    @Test
    fun twoIdentitiesHaveDistinctPublicKeys() {
        val a = InviteIdentity.generate()
        val b = InviteIdentity.generate()
        assertNotEquals(a.publicKey.toList(), b.publicKey.toList())
    }
}
