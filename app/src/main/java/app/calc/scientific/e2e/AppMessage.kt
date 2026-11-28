// SPDX-License-Identifier: MIT
package app.calc.scientific.e2e

import java.nio.ByteBuffer

/**
 * Messages échangés entre deux contacts, avant chiffrement de bout en bout.
 * Format binaire compact : version ‖ type ‖ champs. Tout tient dans un blob de 1 400 o.
 */
sealed class AppMessage {
    /** Texte. [ttlSeconds] : durée de vie demandée par l'expéditeur (0 = conservé). */
    class Text(val id: ByteArray, val sentAt: Long, val ttlSeconds: Long, val body: String) : AppMessage()

    /** Accusé de réception des messages [ids] : l'expéditeur les marque « remis ». */
    class Receipt(val ids: List<ByteArray>) : AppMessage()

    companion object {
        private const val VERSION: Byte = 1
        private const val TEXT: Byte = 1
        private const val RECEIPT: Byte = 2
        const val ID_BYTES = 16
        private const val TEXT_HEADER = 2 + ID_BYTES + 8 + 4

        /** Plus long texte (en octets UTF-8) qu'un message peut porter. */
        val MAX_TEXT_BYTES = Crypto.MAX_PLAIN - TEXT_HEADER

        fun newId(): ByteArray = Crypto.random(ID_BYTES)

        fun encode(m: AppMessage): ByteArray = when (m) {
            is Text -> {
                val body = m.body.toByteArray(Charsets.UTF_8)
                require(body.size <= MAX_TEXT_BYTES) { "message trop long" }
                ByteBuffer.allocate(TEXT_HEADER + body.size)
                    .put(VERSION).put(TEXT).put(m.id).putLong(m.sentAt).putInt(m.ttlSeconds.toInt()).put(body).array()
            }
            is Receipt -> {
                val ids = m.ids.take(50)
                ByteBuffer.allocate(3 + ids.size * ID_BYTES).put(VERSION).put(RECEIPT).put(ids.size.toByte())
                    .apply { ids.forEach { put(it) } }.array()
            }
        }

        fun decode(b: ByteArray): AppMessage? = runCatching {
            val buf = ByteBuffer.wrap(b)
            if (buf.get() != VERSION) return null
            when (buf.get()) {
                TEXT -> {
                    val id = ByteArray(ID_BYTES).also { buf.get(it) }
                    val sentAt = buf.long
                    val ttl = buf.int.toLong() and 0xffffffffL
                    val body = ByteArray(buf.remaining()).also { buf.get(it) }
                    Text(id, sentAt, ttl, String(body, Charsets.UTF_8))
                }
                RECEIPT -> {
                    val n = buf.get().toInt() and 0xff
                    Receipt(List(n) { ByteArray(ID_BYTES).also { buf.get(it) } })
                }
                else -> null
            }
        }.getOrNull()
    }
}

/**
 * Chiffrement de bout en bout d'une conversation, dans les deux sens.
 *
 * Implémentation actuelle : AES-256-GCM avec une clé par sens, issue de l'échange X25519
 * authentifié de l'appairage ([Pairing.derive]). Les messages sont bourrés à une taille fixe.
 * Il manque la confidentialité persistante : elle viendra du double cliquet (Olm, vodozemac)
 * intégré au noyau, derrière cette même interface.
 */
class PairCipher(private val keyOut: ByteArray, private val keyIn: ByteArray) {
    fun seal(m: AppMessage): ByteArray = Crypto.seal(keyOut, Crypto.pad(AppMessage.encode(m)), AAD)
    fun open(blob: ByteArray): AppMessage? =
        Crypto.open(keyIn, blob, AAD)?.let(Crypto::unpad)?.let(AppMessage::decode)

    private companion object {
        val AAD = "calc-msg-v1".toByteArray()
    }
}
