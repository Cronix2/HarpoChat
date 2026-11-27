// SPDX-License-Identifier: MIT
package app.calc.scientific.net

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.corekit.FfiFrameKind
import org.corekit.FfiMailboxKeys
import org.corekit.FfiSizeClass
import org.corekit.coreVersion
import org.corekit.decodeBootstrap
import org.corekit.encodeFrame
import org.corekit.sizes
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Rejoue sur l'appareil les vecteurs de test du noyau (test-vectors/v1.json) à travers les
 * bindings : la bibliothèque native, JNA et les formats sont ceux du serveur.
 */
@RunWith(AndroidJUnit4::class)
class CoreOnDeviceTest {

    private val vectors: JSONObject by lazy {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        JSONObject(ctx.assets.open("core-vectors/v1.json").bufferedReader().readText())
    }

    private fun v(name: String) = vectors.getJSONObject("vectors").getJSONObject(name)
    private fun String.unhex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun le_noyau_se_charge() {
        assertTrue(coreVersion().isNotBlank())
    }

    @Test fun tailles_figees() {
        val small = vectors.getJSONObject("sizes").getJSONObject("small")
        val s = sizes(FfiSizeClass.SMALL)
        assertEquals(small.getInt("noise_ciphertext"), s.noiseCiphertext.toInt())
        assertEquals(small.getInt("link_plaintext"), s.linkPlaintext.toInt())
        assertEquals(small.getInt("sphinx_packet"), s.sphinxPacket.toInt())
        assertEquals(small.getInt("max_message"), s.maxMessage.toInt())
    }

    @Test fun vecteur_bootstrap() {
        val vec = v("bootstrap")
        val roots = decodeBootstrap(vec.getString("output").unhex())
        val ins = vec.getJSONObject("inputs")
        assertEquals(2, roots.size)
        assertEquals(ins.getString("root1"), roots[0].idPub.hex())
        assertEquals(listOf(ins.getString("root1_endpoint")), roots[0].endpoints)
        assertEquals(ins.getString("root2"), roots[1].idPub.hex())
        assertEquals(listOf(ins.getString("root2_endpoint")), roots[1].endpoints)
    }

    @Test fun vecteur_cles_de_boite() {
        val vec = v("mailbox_keys")
        val ins = vec.getJSONObject("inputs")
        val keys = FfiMailboxKeys.derive(ins.getString("pair_secret").unhex(), ins.getInt("epoch").toUInt())
        val out = vec.getString("output")
        assertTrue(out.contains(keys.mailboxId().hex()))
        assertTrue(out.contains(keys.readPub().hex()))
        assertTrue(out.contains(keys.writePub().hex()))
    }

    @Test fun vecteur_trame_de_lien() {
        val vec = v("link_frame_small")
        val frame = encodeFrame(FfiFrameKind.SPHINX, vec.getJSONObject("inputs").getString("body").unhex(), FfiSizeClass.SMALL)
        assertEquals(vec.getString("output"), frame.hex())
    }

    @Test fun le_bloc_embarque_se_decode() {
        val roots = Roots.load()
        assertTrue(roots.isNotEmpty())
        roots.forEach { assertEquals(32, it.idPub.size); assertTrue(it.endpoints.all { e -> e.startsWith("wss://") }) }
    }

    /**
     * Lien réel vers la première racine de cette build (poignée de main Noise et identité vérifiée).
     * Lancer avec `-e mesh 1` quand un nœud est joignable.
     */
    @Test fun lien_reel_vers_une_racine() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("mesh") == "1")
        val root = Roots.load().first()
        val link = withTimeout(20_000) { NodeLink.connect(root.endpoints.first(), root.idPub) }
        try {
            link.send(FfiFrameKind.DIRECTORY_REQ, ByteArray(0))
            val frame = withTimeout(20_000) {
                var f = link.receive()
                while (f != null && f.kind != FfiFrameKind.DIRECTORY) f = link.receive()
                f
            }
            assertEquals(FfiFrameKind.DIRECTORY, frame?.kind)
            val nodes = org.corekit.verifyDirectory(frame!!.body, listOf(root.idPub), (System.currentTimeMillis() / 1000).toULong())
            assertTrue(nodes.any { it.idPub.contentEquals(root.idPub) })
        } finally {
            link.close()
        }
    }
}
