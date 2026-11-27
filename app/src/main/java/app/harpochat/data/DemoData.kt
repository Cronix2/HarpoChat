// SPDX-License-Identifier: MIT
package app.harpochat.data

import java.util.UUID

/** Conversations fictives pour les démonstrations (utilisé uniquement par les builds debug). */
object DemoData {
    private data class Demo(val title: String, val verified: Boolean, val ttl: EphemeralChoice, val msgs: List<Triple<Boolean, String, Long>>)

    suspend fun seed(repo: ChatRepository) {
        val now = System.currentTimeMillis()
        val min = 60_000L
        listOf(
            Demo("Léa", true, EphemeralChoice.ONE_DAY, listOf(
                Triple(false, "Tu as bien reçu le document ?", 42 * min),
                Triple(true, "Oui, je le relis ce soir.", 40 * min),
                Triple(false, "Parfait. On en parle demain, même endroit.", 5 * min)
            )),
            Demo("Samir", true, EphemeralChoice.OFF, listOf(
                Triple(true, "Le rendez-vous est déplacé à 18 h.", 180 * min),
                Triple(false, "Reçu, merci.", 175 * min)
            )),
            Demo("Équipe projet", false, EphemeralChoice.ONE_WEEK, listOf(
                Triple(false, "Nouvelle version prête à tester.", 26 * 60 * min)
            )),
            Demo("Nadia", false, EphemeralChoice.ONE_HOUR, listOf(
                Triple(false, "Je t'envoie les détails ce soir.", 20 * min)
            ))
        ).forEach { d ->
            val id = UUID.randomUUID().toString()
            repo.createThread(id, d.title, verified = d.verified)
            repo.setEphemeral(id, d.ttl.seconds)
            d.msgs.forEach { (mine, body, ago) -> repo.insertDemoMessage(id, mine, body, now - ago) }
        }
    }
}
