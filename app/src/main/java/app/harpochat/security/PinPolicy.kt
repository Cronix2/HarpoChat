// SPDX-License-Identifier: MIT
package app.harpochat.security

/** Règles de choix des PIN, partagées par le premier lancement et les réglages. */
object PinPolicy {
    const val MIN_LENGTH = 6
    const val MAX_LENGTH = 12

    /** Message d'erreur pour un PIN isolé, ou null s'il est acceptable. */
    fun problemWith(pin: String): String? = when {
        pin.isEmpty() -> "Requis"
        !pin.all(Char::isDigit) -> "Chiffres uniquement"
        pin.length < MIN_LENGTH -> "Au moins $MIN_LENGTH chiffres"
        pin.length > MAX_LENGTH -> "Au plus $MAX_LENGTH chiffres"
        pin.toSet().size == 1 -> "Trop simple : un seul chiffre répété"
        isSequence(pin) -> "Trop simple : suite de chiffres"
        else -> null
    }

    /** Erreurs par champ pour la paire secret/détresse (avec confirmations). */
    data class Check(
        val secret: String? = null,
        val secretConfirm: String? = null,
        val duress: String? = null,
        val duressConfirm: String? = null
    ) {
        val ok get() = listOf(secret, secretConfirm, duress, duressConfirm).all { it == null }
    }

    fun check(secret: String, secretConfirm: String, duress: String, duressConfirm: String): Check {
        val s = problemWith(secret)
        val d = problemWith(duress) ?: if (duress == secret) "Doit être différent du code secret" else null
        return Check(
            secret = s,
            secretConfirm = if (s == null && secretConfirm != secret) "Ne correspond pas" else null,
            duress = d,
            duressConfirm = if (d == null && duressConfirm != duress) "Ne correspond pas" else null
        )
    }

    private fun isSequence(pin: String): Boolean {
        if (pin.length < 3) return false
        val diffs = pin.zipWithNext { a, b -> b - a }.toSet()
        return diffs == setOf(1) || diffs == setOf(-1)
    }
}
