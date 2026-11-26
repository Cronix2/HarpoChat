// SPDX-License-Identifier: MIT
package app.harpochat.data

import java.util.UUID

/** Conversations fictives pour les démonstrations (utilisé uniquement par les builds debug). */
object DemoData {
    suspend fun seed(repo: ChatRepository) {
        val now = System.currentTimeMillis()
        val min = 60_000L
        val threads = listOf(
            "Léa" to listOf(
                Triple(false, "Tu as bien reçu le document ?", 42 * min),
                Triple(true, "Oui, je le relis ce soir.", 40 * min),
                Triple(false, "Parfait. On en parle demain, même endroit.", 5 * min)
            ),
            "Samir" to listOf(
                Triple(true, "Le rendez-vous est déplacé à 18 h.", 180 * min),
                Triple(false, "Reçu, merci.", 175 * min)
            ),
            "Équipe projet" to listOf(
                Triple(false, "Nouvelle version prête à tester.", 26 * 60 * min)
            )
        )
        threads.forEach { (title, msgs) ->
            val id = UUID.randomUUID().toString()
            repo.createThread(id, title)
            msgs.forEach { (mine, body, ago) -> repo.insertDemoMessage(id, mine, body, now - ago) }
        }
    }
}
