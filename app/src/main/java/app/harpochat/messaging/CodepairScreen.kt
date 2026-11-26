// SPDX-License-Identifier: MIT
package app.harpochat.messaging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.harpochat.ui.CountdownRing
import app.harpochat.ui.NoKeyboardLearning
import app.harpochat.ui.theme.AppColors
import java.security.SecureRandom

private const val PERIOD_SEC = 60
private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789" // sans 0/O/1/I/L
private val RNG = SecureRandom()

private fun randomCode(len: Int) = buildString(len) { repeat(len) { append(ALPHABET[RNG.nextInt(ALPHABET.length)]) } }

/**
 * Appairage à distance par identifiant + mot de passe temporaire.
 * Démonstration d'interface : l'échange réel passera par le mesh (`harpochat-core`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodepairScreen(onBack: () -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Code d'appairage") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SecondaryTabRow(selectedTabIndex = selected, containerColor = AppColors.Sheet) {
                listOf("Saisir un code", "Mon code").forEachIndexed { i, label ->
                    Tab(selected = selected == i, onClick = { selected = i }, text = { Text(label) })
                }
            }
            if (selected == 0) ImportTab() else GenerateTab()
        }
    }
}

@Composable
private fun ImportTab() {
    var id by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    NoKeyboardLearning {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Saisissez l'identifiant et le mot de passe que votre contact vous a communiqués.", color = AppColors.Muted)
            OutlinedTextField(
                value = id, onValueChange = { id = it.uppercase().take(12) },
                label = { Text("Identifiant") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = code, onValueChange = { code = it.uppercase().take(10) },
                label = { Text("Mot de passe (10 caractères)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { sent = true },
                enabled = id.length >= 6 && code.length == 10,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Envoyer la demande") }
        }
    }
    if (sent) {
        AlertDialog(
            onDismissRequest = { sent = false },
            confirmButton = { TextButton(onClick = { sent = false }) { Text("OK") } },
            title = { Text("Demande prête") },
            text = { Text("Votre contact devra l'accepter explicitement. L'envoi réel sera actif avec le réseau HarpoChat.") }
        )
    }
}

@Composable
private fun GenerateTab() {
    val myId = remember { randomCode(8) }
    var code by remember { mutableStateOf(randomCode(10)) }
    var cycleStart by remember { mutableLongStateOf(System.currentTimeMillis()) }
    fun regen() { code = randomCode(10); cycleStart = System.currentTimeMillis() }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CountdownRing(PERIOD_SEC, cycleStart, onCycle = ::regen, modifier = Modifier.size(56.dp))
        Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Identifiant", color = AppColors.Muted, style = MaterialTheme.typography.labelMedium)
                Text(myId.chunked(4).joinToString(" "), style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
                Text("Mot de passe", color = AppColors.Muted, style = MaterialTheme.typography.labelMedium)
                Text(code.chunked(5).joinToString(" "), style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
            }
        }
        Text(
            "Transmettez-les par un canal de confiance. Le mot de passe change toutes les minutes.",
            textAlign = TextAlign.Center, color = AppColors.Muted
        )
        OutlinedButton(onClick = ::regen) { Text("Nouveau mot de passe") }
    }
}
