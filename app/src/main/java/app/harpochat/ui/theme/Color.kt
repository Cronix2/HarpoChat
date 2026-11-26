// SPDX-License-Identifier: MIT
package app.harpochat.ui.theme

import androidx.compose.ui.graphics.Color

/** Palette sombre unique de l'app (la façade calculatrice et la messagerie la partagent). */
object AppColors {
    // Fonds
    val Bg = Color(0xFF0F1115)
    val Sheet = Color(0xFF12161D)
    val Surface = Color(0xFF1A1F27)
    val SurfaceHigh = Color(0xFF232A35)
    val Divider = Color(0xFF1E2430)

    // Bulles
    val BubbleMine = Color(0xFF4C6FFF)
    val BubbleOther = Color(0xFF1F2632)

    // Textes
    val OnBgPrimary = Color(0xFFE7EAF1)
    val OnBubbleMine = Color(0xFFFFFFFF)
    val OnBubbleOther = Color(0xFFD8DDE6)
    val Muted = Color(0xFF8C96A7)

    // Accents
    val Accent = Color(0xFF4C6FFF)
    val KeyLight = Color(0xFF3A404D)
    val Timer = Color(0xFF6EE7B7)   // vert doux (remplace le #00FF00 criard)
    val Danger = Color(0xFFFF6B6B)
    val Warning = Color(0xFFFFC56B)
}
