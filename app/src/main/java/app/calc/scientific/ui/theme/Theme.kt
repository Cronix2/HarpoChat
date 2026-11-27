// SPDX-License-Identifier: MIT
package app.calc.scientific.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SilenceScheme = darkColorScheme(
    primary = Palette.Mint,
    onPrimary = Palette.OnMint,
    primaryContainer = Palette.MintAvatar,
    onPrimaryContainer = Palette.MintText,
    secondary = Palette.Muted,
    background = Palette.Ink,
    onBackground = Palette.Text,
    surface = Palette.Ink,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Surface,
    onSurfaceVariant = Palette.TextSoft,
    surfaceContainerLowest = Palette.Ink,
    surfaceContainerLow = Palette.Sheet,
    surfaceContainer = Palette.Sheet,
    surfaceContainerHigh = Palette.Surface,
    surfaceContainerHighest = Palette.SurfaceHigh,
    outline = Palette.Line,
    outlineVariant = Palette.Line,
    error = Palette.Danger,
    scrim = Palette.Scrim
)

/** Thème unique, appliqué une seule fois à la racine (MainActivity). */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SilenceScheme, typography = Typography, content = content)
}
