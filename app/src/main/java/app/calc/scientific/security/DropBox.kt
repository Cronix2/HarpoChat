// SPDX-License-Identifier: MIT
package app.calc.scientific.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.calc.scientific.net.Watch
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Boîte de dépôt scellée : ce qui doit rester utilisable quand l'app est verrouillée.
 *
 * - **La liste des boîtes à relever** (secrets de lecture des boîtes aux lettres). Elle permet de
 *   relever et d'acquitter le courrier, mais pas de le lire : les clés de chiffrement des
 *   conversations restent dans la base, qui ne s'ouvre qu'avec le code secret.
 * - **Les blobs reçus pendant le verrouillage**, gardés tels quels, donc toujours chiffrés de bout
 *   en bout. Ils ne deviennent lisibles qu'au déverrouillage, quand [app.calc.scientific.messaging.PostOffice]
 *   les traite.
 *
 * Les deux fichiers sont chiffrés (AES-256-GCM) avec une clé Android Keystore propre, utilisable
 * sans le code. Le code d'effacement détruit les fichiers et la clé ([wipe]).
 */
class DropBox(context: Context) {

    class Entry(val file: File, val box: ByteArray, val blob: ByteArray)

    private val dir = File(context.noBackupFilesDir, "cache_parts")
    private val index = File(context.noBackupFilesDir, "cache_index.bin")
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val rng = SecureRandom()

    /** Écrit la liste des boîtes à relever (appelé par la session déverrouillée à chaque changement). */
    @Synchronized
    fun saveWatches(watches: List<Watch>) {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeInt(watches.size)
            watches.forEach { d.write(it.box); d.writeLong(it.sinceMs) }
        }
        writeSealed(index, out.toByteArray())
    }

    @Synchronized
    fun loadWatches(): List<Watch> {
        val plain = readSealed(index) ?: return emptyList()
        return runCatching {
            DataInputStream(plain.inputStream()).use { d ->
                List(d.readInt()) { Watch(ByteArray(32).also { b -> d.readFully(b) }, d.readLong()) }
            }
        }.getOrDefault(emptyList())
    }

    /** Range un blob reçu pendant le verrouillage. */
    @Synchronized
    fun append(box: ByteArray, blob: ByteArray) {
        dir.mkdirs()
        val name = ByteArray(12).also { rng.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        writeSealed(File(dir, name), box + blob)
    }

    /** Blobs en attente, du plus ancien au plus récent. À supprimer avec [delete] une fois traités. */
    @Synchronized
    fun pending(): List<Entry> =
        dir.listFiles().orEmpty().sortedBy { it.lastModified() }.mapNotNull { f ->
            val plain = readSealed(f)
            if (plain == null || plain.size < 32) { f.delete(); null }
            else Entry(f, plain.copyOfRange(0, 32), plain.copyOfRange(32, plain.size))
        }

    fun delete(e: Entry) { e.file.delete() }

    /** Destruction complète : fichiers et clé. */
    @Synchronized
    fun wipe() {
        dir.deleteRecursively()
        index.delete()
        runCatching { if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS) }
    }

    // ---------------------------------------------------------------- interne

    private fun key(): SecretKey {
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run { init(spec); generateKey() }
    }

    private fun writeSealed(file: File, plain: ByteArray) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(c.iv + c.doFinal(plain))
        tmp.renameTo(file)
    }

    private fun readSealed(file: File): ByteArray? {
        if (!file.exists()) return null
        return runCatching {
            val raw = file.readBytes()
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
            c.doFinal(raw, 12, raw.size - 12)
        }.getOrNull()
    }

    companion object {
        private const val ALIAS = "calc_cfg_k2"
    }
}
