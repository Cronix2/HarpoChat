// SPDX-License-Identifier: MIT
package app.harpochat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HarpoColorScheme = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = AppColors.OnBubbleMine,
    primaryContainer = AppColors.SurfaceHigh,
    onPrimaryContainer = AppColors.OnBgPrimary,
    secondary = AppColors.Muted,
    tertiaryContainer = AppColors.KeyLight,
    background = AppColors.Bg,
    onBackground = AppColors.OnBgPrimary,
    surface = AppColors.Bg,
    onSurface = AppColors.OnBgPrimary,
    surfaceVariant = AppColors.Surface,
    onSurfaceVariant = AppColors.OnBubbleOther,
    surfaceContainer = AppColors.Sheet,
    surfaceContainerLow = AppColors.Sheet,
    surfaceContainerHigh = AppColors.Surface,
    surfaceContainerHighest = AppColors.SurfaceHigh,
    outline = AppColors.Divider,
    outlineVariant = AppColors.Divider,
    error = AppColors.Danger
)

/** Thème unique, appliqué une seule fois à la racine (MainActivity). */
@Composable
fun HarpoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HarpoColorScheme, typography = Typography, content = content)
}
