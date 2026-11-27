// SPDX-License-Identifier: MIT
package app.harpochat.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette « Silence » de l'espace privé : encre, un seul accent vert d'eau,
 * ambre réservé aux alertes, rouge doux réservé aux actions destructrices.
 * (La façade calculatrice a sa propre palette, volontairement banale : CalcColors.)
 */
object Palette {
    // Fonds et surfaces
    val Ink = Color(0xFF0B0D10)
    val Sheet = Color(0xFF12151A)
    val Surface = Color(0xFF15181D)
    val SurfaceHigh = Color(0xFF1E242B)
    val Line = Color(0xFF1E2228)
    val Scrim = Color(0x99050608)

    // Texte
    val Text = Color(0xFFE9EDF0)
    val TextSoft = Color(0xFFC9D0D6)
    val Muted = Color(0xFF8A949E)
    val Faint = Color(0xFF6F7983)

    // Accent
    val Mint = Color(0xFF8FD6B8)
    val OnMint = Color(0xFF0B0D10)
    val MintText = Color(0xFFBFEBD8)
    val MintAvatar = Color(0xFF1C2A25)
    val MintBubble = Color(0xFF1C3A30)
    val MintBubbleMeta = Color(0xFF9DCDB8)
    val MintPanel = Color(0xFF111A17)
    val MintPanelLine = Color(0xFF1E2E28)
    val MintTrack = Color(0xFF2B4A3F)

    // Neutres d'avatar
    val Avatar = Color(0xFF1F2229)
    val AvatarText = Color(0xFFD5DAE0)

    // Sémantique
    val Amber = Color(0xFFF0C27A)
    val AmberBg = Color(0xFF221B12)
    val Danger = Color(0xFFFF9B8A)
    val DangerBg = Color(0xFF1A1210)
    val DangerLine = Color(0xFF4A2A26)
}
