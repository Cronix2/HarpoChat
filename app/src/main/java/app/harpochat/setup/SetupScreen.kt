// SPDX-License-Identifier: MIT
package app.harpochat.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.harpochat.security.PinPolicy
import app.harpochat.ui.NoKeyboardLearning
import app.harpochat.ui.theme.AppColors
import kotlinx.coroutines.launch

/**
 * Premier lancement : l'utilisateur choisit ses deux codes. Il n'existe AUCUN code par défaut.
 */
@Composable
fun SetupScreen(onConfigure: suspend (secret: String, duress: String) -> Unit) {
    var secret by rememberSaveable { mutableStateOf("") }
    var secretConfirm by rememberSaveable { mutableStateOf("") }
    var duress by rememberSaveable { mutableStateOf("") }
    var duressConfirm by rememberSaveable { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val check = PinPolicy.check(secret, secretConfirm, duress, duressConfirm)
    fun err(e: String?) = if (submitted) e else null

    NoKeyboardLearning {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Configuration", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Cette étape n'apparaît qu'une fois. Ensuite, l'application ressemblera à une simple calculatrice.",
                color = AppColors.Muted
            )

            InfoCard(
                title = "Comment ouvrir l'espace privé",
                body = "Tapez votre code secret sur la calculatrice, puis faites un appui long sur la touche « = »."
            )

            PinField("Code secret", secret, err(check.secret)) { secret = it }
            PinField("Confirmer le code secret", secretConfirm, err(check.secretConfirm)) { secretConfirm = it }

            InfoCard(
                title = "Code d'effacement",
                body = "Saisi de la même façon, il efface instantanément et définitivement toutes les données. " +
                    "Une double vibration confirme l'effacement, sans rien afficher."
            )

            PinField("Code d'effacement", duress, err(check.duress)) { duress = it }
            PinField("Confirmer le code d'effacement", duressConfirm, err(check.duressConfirm), ImeAction.Done) { duressConfirm = it }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    submitted = true
                    if (check.ok && !busy) {
                        busy = true
                        scope.launch {
                            try { onConfigure(secret, duress) } finally { busy = false }
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.padding(4.dp))
                        Text("Génération des clés…")
                    }
                } else Text("Terminer")
            }
            Text(
                "Vos codes ne sont jamais stockés. Ils servent à dériver la clé qui chiffre vos données, " +
                    "liée à la puce sécurisée de ce téléphone.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Muted
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, color = AppColors.OnBubbleOther, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun PinField(
    label: String,
    value: String,
    error: String?,
    imeAction: ImeAction = ImeAction.Next,
    onChange: (String) -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.length <= PinPolicy.MAX_LENGTH && v.all(Char::isDigit)) onChange(v) },
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = { Text(error ?: "${PinPolicy.MIN_LENGTH} à ${PinPolicy.MAX_LENGTH} chiffres") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = imeAction),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Masquer" else "Afficher"
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}
