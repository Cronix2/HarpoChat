// SPDX-License-Identifier: MIT
package app.calc.scientific.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.calc.scientific.security.AppLock
import app.calc.scientific.setup.CodeStep
import app.calc.scientific.setup.CodeStepScreen
import app.calc.scientific.ui.IconAction
import app.calc.scientific.ui.PrimaryButton
import app.calc.scientific.ui.theme.LineIcons
import app.calc.scientific.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Changement des codes en trois étapes : code actuel, nouveau code secret, nouveau code d'effacement.
 * La clé de la base ne change pas : elle est simplement re-chiffrée avec le nouveau code.
 */
@Composable
fun ChangeCodesScreen(appLock: AppLock, onBack: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (done) {
        Done(onBack)
        return
    }
    val back: @Composable () -> Unit = { Box(Modifier.padding(start = 0.dp)) { IconAction(LineIcons.Back, "Retour", onBack) } }
    when (step) {
        0 -> CodeStepScreen(
            step = CodeStep("Étape 1 sur 3", "Votre code secret actuel", "Pour confirmer que c'est bien vous.", isNewCode = false),
            stepIndex = 0, stepCount = 3, busy = false, externalError = error,
            top = back,
            onDone = { current = it; error = null; step = 1 }
        )
        1 -> CodeStepScreen(
            step = CodeStep("Étape 2 sur 3", "Nouveau code secret", "Il remplacera l'ancien dès la fin de cette étape."),
            stepIndex = 1, stepCount = 3, busy = false, externalError = null,
            top = back,
            onDone = { secret = it; step = 2 }
        )
        else -> CodeStepScreen(
            step = CodeStep(
                "Étape 3 sur 3", "Nouveau code d'effacement", "Il détruit toutes les données, en silence.",
                extraCheck = { if (it == secret) "Doit être différent du code secret" else null }
            ),
            stepIndex = 2, stepCount = 3, busy = busy, externalError = null,
            top = back,
            onDone = { duress ->
                busy = true
                scope.launch {
                    val ok = appLock.changePins(current, secret, duress)
                    busy = false
                    if (ok) done = true else {
                        error = "Code secret actuel incorrect. Rien n'a été modifié."
                        step = 0
                    }
                }
            }
        )
    }
}

@Composable
private fun Done(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Palette.Ink).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Box(Modifier.size(72.dp).background(Palette.MintAvatar, CircleShape), contentAlignment = Alignment.Center) {
            Icon(LineIcons.Check, null, tint = Palette.Mint, modifier = Modifier.size(32.dp))
        }
        Text("Codes mis à jour", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Vos données ont été re-chiffrées avec le nouveau code. L'ancien ne fonctionne plus.",
            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(8.dp))
        PrimaryButton("Terminé", onBack, Modifier.fillMaxWidth())
    }
}
