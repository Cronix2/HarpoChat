// SPDX-License-Identifier: MIT
package app.harpochat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.harpochat.ui.theme.HarpoIcons
import app.harpochat.ui.theme.Manrope
import app.harpochat.ui.theme.Palette

/** Bouton icône de 44 dp (cible tactile minimale de la direction Silence). */
@Composable
fun IconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Palette.Text
) {
    IconButton(onClick = onClick, modifier = modifier.size(44.dp)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** En-tête d'écran secondaire : retour + titre. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.statusBarsPadding().fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconAction(HarpoIcons.Back, "Retour", onBack)
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        actions()
    }
}

/** Bouton principal plein, vert d'eau. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Mint, contentColor = Palette.OnMint,
            disabledContainerColor = Palette.SurfaceHigh, disabledContentColor = Palette.Muted
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = modifier.height(54.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Box(Modifier.size(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Bouton secondaire sur surface. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) Palette.DangerBg else Palette.SurfaceHigh,
            contentColor = if (danger) Palette.Danger else Palette.Text
        ),
        border = if (danger) BorderStroke(1.dp, Palette.DangerLine) else null,
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = modifier.height(52.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Box(Modifier.size(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Avatar à initiale. Les contacts vérifiés reçoivent la teinte d'accent. */
@Composable
fun Avatar(name: String, size: Dp = 50.dp, highlighted: Boolean = false) {
    Box(
        Modifier.size(size).background(if (highlighted) Palette.MintAvatar else Palette.Avatar, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            fontFamily = Manrope,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.38f).sp,
            color = if (highlighted) Palette.MintText else Palette.AvatarText
        )
    }
}

/** Petite pastille d'information (durée éphémère, diffusion, statut…). */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, fg: Color = Palette.Mint, bg: Color = Color(0xFF16241F)) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
        color = fg,
        modifier = modifier.background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** Carte encadrée vert d'eau (informations de sécurité). */
@Composable
fun MintPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Palette.MintPanel, RoundedCornerShape(18.dp))
            .border(1.dp, Palette.MintPanelLine, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) { content() }
}

/** Titre de groupe en petites capitales espacées. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold),
        color = Palette.Faint,
        modifier = modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}
