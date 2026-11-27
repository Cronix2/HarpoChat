// SPDX-License-Identifier: MIT
package app.harpochat.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.harpochat.ui.PrimaryButton
import app.harpochat.ui.theme.HarpoIcons
import app.harpochat.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Premier lancement, en trois étapes : présentation, code secret, code d'effacement.
 * Il n'existe AUCUN code par défaut.
 */
@Composable
fun SetupScreen(onConfigure: suspend (secret: String, duress: String) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var secret by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    when (step) {
        0 -> Intro(onStart = { step = 1 })
        1 -> CodeStepScreen(
            step = CodeStep(
                eyebrow = "Étape 2 sur 3",
                title = "Choisissez votre code secret",
                body = "Tapé sur la calculatrice puis validé par un appui long sur « = », il ouvre votre espace privé."
            ),
            stepIndex = 1, stepCount = 3, busy = false, externalError = null,
            footer = "Ensuite : le code d'effacement",
            onDone = { secret = it; step = 2 }
        )
        else -> CodeStepScreen(
            step = CodeStep(
                eyebrow = "Étape 3 sur 3",
                title = "Choisissez un code d'effacement",
                body = "Saisi de la même façon, il détruit instantanément toutes les données, sans rien afficher. Une double vibration le confirme.",
                extraCheck = { if (it == secret) "Doit être différent du code secret" else null }
            ),
            stepIndex = 2, stepCount = 3, busy = busy, externalError = null,
            footer = "Gardez-le en tête : c'est votre issue de secours.",
            onDone = { duress ->
                busy = true
                scope.launch {
                    try { onConfigure(secret, duress) } finally { busy = false }
                }
            }
        )
    }
}

@Composable
private fun Intro(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        StepBar(0, 3)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ÉTAPE 1 SUR 3", style = MaterialTheme.typography.labelSmall, color = Palette.Mint)
            Text("Une calculatrice, et un espace que vous seul ouvrez", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Cette configuration n'apparaît qu'une fois. Ensuite, l'application ressemblera à une calculatrice ordinaire.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.Muted
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Point(HarpoIcons.Key, "Un code secret", "Tapé sur la calculatrice, suivi d'un appui long sur « = », il ouvre vos conversations.")
            Point(HarpoIcons.Trash, "Un code d'effacement", "Saisi de la même façon, il détruit tout, en silence.")
            Point(HarpoIcons.ShieldCheck, "Rien ne quitte ce téléphone", "Vos codes ne sont jamais stockés : ils dérivent la clé de chiffrement, liée à la puce sécurisée.")
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Commencer", onClick = onStart, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Point(icon: ImageVector, title: String, body: String) {
    Row(
        Modifier.fillMaxWidth().background(Palette.Surface, RoundedCornerShape(18.dp)).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.size(40.dp).background(Palette.MintAvatar, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Palette.Mint, modifier = Modifier.size(20.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
    }
}
