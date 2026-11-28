// SPDX-License-Identifier: MIT
package app.calc.scientific.e2e

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingTest {

    /** Déroule l'appairage complet, comme le feraient A et B. */
    private fun pair(): Triple<Pairing.PairKeys, Pairing.PairKeys, String> {
        // A crée et montre l'invitation.
        val invA = Pairing.newInvite(Pairing.QR_TTL_MS)
        // B l'importe et envoie sa demande.
        val invB = Pairing.decode(Pairing.encode(invA))!!
        val eB = Crypto.x25519KeyPair()
        val kB = Crypto.random(32)
        val reqBlob = Pairing.sealRequest(invB.secret, Pairing.Request(eB.public, kB, "Bob"))
        val sasB = Pairing.sas(invB.secret, eB.public, kB)
        // A ouvre la demande et voit le même code de contrôle.
        val req = Pairing.openRequest(invA.secret, reqBlob)!!
        assertEquals("Bob", req.name)
        assertEquals(sasB, Pairing.sas(invA.secret, req.ePub, req.kB))
        // A accepte et répond.
        val eA = Crypto.x25519KeyPair()
        val keysA = Pairing.derive(invA.secret, eA.private, eA.public, req.ePub, inviter = true)
        val accBlob = Pairing.sealAccept(invA.secret, req.kB, Pairing.Accept(eA.public, "Alice"))
        // B ouvre la réponse.
        val acc = Pairing.openAccept(invB.secret, kB, accBlob)!!
        assertEquals("Alice", acc.name)
        val keysB = Pairing.derive(invB.secret, eB.private, eB.public, acc.ePub, inviter = false)
        return Triple(keysA, keysB, sasB)
    }

    @Test fun `les deux côtés obtiennent les mêmes secrets, croisés`() {
        val (a, b, _) = pair()
        assertEquals(a.threadId, b.threadId)
        assertArrayEquals(a.boxOut, b.boxIn)
        assertArrayEquals(a.boxIn, b.boxOut)
        assertArrayEquals(a.keyOut, b.keyIn)
        assertArrayEquals(a.keyIn, b.keyOut)
        assertFalse(a.boxOut.contentEquals(a.boxIn))
        assertFalse(a.keyOut.contentEquals(a.keyIn))
    }

    @Test fun `un message chiffré par A se lit chez B, pas l'inverse avec la même clé`() {
        val (a, b, _) = pair()
        val ca = PairCipher(a.keyOut, a.keyIn)
        val cb = PairCipher(b.keyOut, b.keyIn)
        val id = AppMessage.newId()
        val blob = ca.seal(AppMessage.Text(id, 1_700_000_000_000, 3600, "Salut, ça va ?"))
        val got = cb.open(blob) as AppMessage.Text
        assertEquals("Salut, ça va ?", got.body)
        assertArrayEquals(id, got.id)
        assertEquals(3600L, got.ttlSeconds)
        assertNull(ca.open(blob)) // A ne peut pas relire son propre sens avec sa clé entrante
    }

    @Test fun `un blob modifié est rejeté`() {
        val (a, b, _) = pair()
        val blob = PairCipher(a.keyOut, a.keyIn).seal(AppMessage.Text(AppMessage.newId(), 0, 0, "x"))
        blob[blob.size / 2] = (blob[blob.size / 2].toInt() xor 1).toByte()
        assertNull(PairCipher(b.keyOut, b.keyIn).open(blob))
    }

    @Test fun `un usurpateur qui a photographié le QR n'obtient pas le même code de contrôle`() {
        val inv = Pairing.newInvite(Pairing.QR_TTL_MS)
        val bob = Pairing.sas(inv.secret, Crypto.x25519KeyPair().public, Crypto.random(32))
        val eve = Pairing.sas(inv.secret, Crypto.x25519KeyPair().public, Crypto.random(32))
        assertNotEquals(bob, eve)
        assertTrue(Regex("""\d{3} \d{3}""").matches(bob))
    }

    @Test fun `une demande ne s'ouvre pas avec une autre invitation`() {
        val inv = Pairing.newInvite(Pairing.QR_TTL_MS)
        val other = Pairing.newInvite(Pairing.QR_TTL_MS)
        val blob = Pairing.sealRequest(inv.secret, Pairing.Request(Crypto.x25519KeyPair().public, Crypto.random(32), "Bob"))
        assertNull(Pairing.openRequest(other.secret, blob))
        assertNotNull(Pairing.openRequest(inv.secret, blob))
    }

    @Test fun `l'invitation est courte, neutre et garde son expiration`() {
        val inv = Pairing.newInvite(Pairing.REMOTE_TTL_MS, now = 1_800_000_000_000)
        val text = Pairing.encode(inv)
        assertEquals(50, text.length)
        assertTrue(text.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        val back = Pairing.decode("  $text\n")!!
        assertArrayEquals(inv.secret, back.secret)
        assertEquals(inv.expiresAt, back.expiresAt)
        assertNull(Pairing.decode("bonjour"))
        assertNull(Pairing.decode(text.dropLast(2)))
    }

    @Test fun `un nom trop long est tronqué sans couper un caractère`() {
        val inv = Pairing.newInvite(Pairing.QR_TTL_MS)
        val long = "é".repeat(100)
        val r = Pairing.openRequest(inv.secret, Pairing.sealRequest(inv.secret, Pairing.Request(ByteArray(32) { 9 }, ByteArray(32), long)))!!
        assertEquals("é".repeat(Pairing.MAX_NAME_BYTES / 2), r.name)
    }

    @Test fun `les blobs n'ont que quatre tailles possibles`() {
        val key = Crypto.random(32)
        val sizes = listOf(0, 1, 100, 226, 227, 500, 1000, Crypto.MAX_PLAIN).map { n ->
            Crypto.seal(key, Crypto.pad(ByteArray(n))).size
        }.toSet()
        assertTrue(Crypto.BLOB_SIZES.toSet().containsAll(sizes))
        assertEquals(1400, Crypto.seal(key, Crypto.pad(ByteArray(Crypto.MAX_PLAIN))).size)
        assertArrayEquals(ByteArray(300) { 7 }, Crypto.unpad(Crypto.pad(ByteArray(300) { 7 })))
    }

    @Test fun `accusé de réception et texte maximal se codent et se décodent`() {
        val ids = List(3) { AppMessage.newId() }
        val r = AppMessage.decode(AppMessage.encode(AppMessage.Receipt(ids))) as AppMessage.Receipt
        assertEquals(3, r.ids.size)
        assertArrayEquals(ids[2], r.ids[2])
        val body = "a".repeat(AppMessage.MAX_TEXT_BYTES)
        val t = AppMessage.decode(AppMessage.encode(AppMessage.Text(AppMessage.newId(), 1, 0, body))) as AppMessage.Text
        assertEquals(body, t.body)
        assertNull(AppMessage.decode(byteArrayOf(9, 9, 9)))
    }

    @Test fun `X25519 refuse une clé publique nulle`() {
        val e = Crypto.x25519KeyPair()
        assertTrue(runCatching { Crypto.x25519(e.private, ByteArray(32)) }.isFailure)
    }
}
