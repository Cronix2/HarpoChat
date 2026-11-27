// SPDX-License-Identifier: MIT
package app.harpochat.net

import app.harpochat.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class NetLogicTest {

    @Test fun `le bloc d'amorçage embarqué se révèle en CBOR v1`() {
        val cbor = Roots.reveal(BuildConfig.LAYOUT_TABLE)
        // { "v": 1, "roots": [...] } : map(2), texte "v", entier 1, texte "roots"
        assertEquals("a2617601", cbor.copyOfRange(0, 4).hex())
        assertEquals("roots", String(cbor, 5, 5))
    }

    @Test fun `aucune adresse ne se lit en clair dans le bloc obscurci`() {
        val raw = java.util.Base64.getDecoder().decode(BuildConfig.LAYOUT_TABLE)
        assertFalse(String(raw, Charsets.ISO_8859_1).contains("wss://"))
    }

    @Test fun `les intervalles de Poisson ont la bonne moyenne`() {
        val r = Random(42)
        val n = 20_000
        val mean = (1..n).sumOf { CoverSchedule.nextDelayMs(0.5, r.nextDouble()) } / n.toDouble()
        assertEquals(2000.0, mean, 60.0) // λ = 0,5/s → 2 s en moyenne
    }

    @Test fun `un tirage limite ne donne pas un intervalle infini`() {
        assertTrue(CoverSchedule.nextDelayMs(0.5, 1.0) < Long.MAX_VALUE / 2)
        assertEquals(0L, CoverSchedule.nextDelayMs(0.5, 0.0))
    }

    @Test fun `un loop revenu donne un aller-retour, un loop inconnu est ignoré`() {
        val t = LoopTracker()
        t.onSent("a", now = 1_000, routeDelaysMs = 200, nodesExceptGateway = listOf("n1"))
        assertFalse(t.onReturned("zz", 1_100))
        assertTrue(t.onReturned("a", 1_450))
        assertEquals(450L, t.medianRttMs())
        assertFalse(t.onReturned("a", 1_500)) // pas deux fois
    }

    @Test fun `un loop non revenu après délais plus 10 s est perdu`() {
        val t = LoopTracker()
        t.onSent("a", now = 0, routeDelaysMs = 300, nodesExceptGateway = listOf("n1"))
        t.expire(10_299)
        assertEquals(0, t.lost)
        t.expire(10_300)
        assertEquals(1, t.lost)
        assertNull(t.medianRttMs())
    }

    @Test fun `un nœud qui perd plus de 75 pour cent sur 10 essais devient suspect`() {
        val t = LoopTracker()
        repeat(8) { t.onSent("l$it", 0, 0, listOf("bad", "good")) }
        repeat(2) { t.onSent("r$it", 0, 0, listOf("bad")) }
        repeat(2) { t.onReturned("r$it", 10) }
        t.expire(20_000)
        // bad : 10 essais, 8 pertes (80 %) ; good : 8 essais seulement
        assertEquals(setOf("bad"), t.suspects())
    }

    @Test fun `sous 10 essais aucun nœud n'est suspect`() {
        val t = LoopTracker()
        repeat(9) { t.onSent("l$it", 0, 0, listOf("n")) }
        t.expire(20_000)
        assertTrue(t.suspects().isEmpty())
    }
}
