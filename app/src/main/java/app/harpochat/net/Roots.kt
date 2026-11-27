// SPDX-License-Identifier: MIT
package app.harpochat.net

import app.harpochat.BuildConfig
import app.harpochat.core.FfiRoot
import app.harpochat.core.decodeBootstrap
import java.security.MessageDigest
import java.util.Base64

/**
 * Racines de confiance embarquées (spec §4.4) : identité publique et adresses des nœuds racines.
 *
 * Le bloc CBOR est produit à la compilation (app/build.gradle.kts) puis obscurci, pour qu'aucune
 * adresse ne se lise en clair dans l'APK. Ce n'est pas un secret : seulement de la discrétion.
 */
object Roots {
    private const val LABEL = "calc-layout-v1"

    /** Inverse de `obscured()` du script Gradle : XOR avec un flux SHA-256(LABEL ‖ compteur). */
    fun reveal(encoded: String): ByteArray {
        val data = Base64.getDecoder().decode(encoded)
        val out = ByteArray(data.size)
        var counter = 0
        var off = 0
        while (off < data.size) {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(LABEL.toByteArray())
            md.update(byteArrayOf((counter shr 24).toByte(), (counter shr 16).toByte(), (counter shr 8).toByte(), counter.toByte()))
            val block = md.digest()
            for (i in block.indices) {
                if (off + i >= data.size) break
                out[off + i] = (data[off + i].toInt() xor block[i].toInt()).toByte()
            }
            off += block.size
            counter++
        }
        return out
    }

    /** Racines de cette build. Lève une [app.harpochat.core.FfiException] si le bloc est invalide. */
    fun load(): List<FfiRoot> = decodeBootstrap(reveal(BuildConfig.LAYOUT_TABLE))
}
