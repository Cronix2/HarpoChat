// SPDX-License-Identifier: MIT
package app.harpochat.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.harpochat.BuildConfig
import app.harpochat.data.ChatRepository
import app.harpochat.data.DemoData
import app.harpochat.security.AppLock
import app.harpochat.security.PinPolicy
import app.harpochat.security.ProtectionLevel
import app.harpochat.security.Session
import app.harpochat.setup.PinField
import app.harpochat.ui.NoKeyboardLearning
import app.harpochat.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(session: Session, appLock: AppLock, onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        NoKeyboardLearning {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ProfileSection(session.repo, snackbar)
                PinSection(appLock, snackbar)
                SecuritySection(appLock)
                if (BuildConfig.DEBUG) DemoSection(session)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun ProfileSection(repo: ChatRepository, snackbar: SnackbarHostState) {
    var name by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        name = withContext(Dispatchers.IO) { repo.getSetting(ChatRepository.KEY_DISPLAY_NAME) } ?: ""
    }
    Section("Profil") {
        Text("Nom présenté à vos nouveaux contacts dans votre code d'invitation.", color = AppColors.Muted)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(32) },
            label = { Text("Nom affiché") },
            placeholder = { Text("Contact") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(onClick = {
            scope.launch {
                withContext(Dispatchers.IO) {
                    repo.putSetting(ChatRepository.KEY_DISPLAY_NAME, name.trim().ifEmpty { "Contact" })
                }
                snackbar.showSnackbar("Nom enregistré")
            }
        }) { Text("Enregistrer") }
    }
}

@Composable
private fun PinSection(appLock: AppLock, snackbar: SnackbarHostState) {
    var current by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var secretConfirm by remember { mutableStateOf("") }
    var duress by remember { mutableStateOf("") }
    var duressConfirm by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val check = PinPolicy.check(secret, secretConfirm, duress, duressConfirm)
    fun err(e: String?) = if (submitted) e else null

    Section("Changer les codes") {
        PinField("Code secret actuel", current, if (submitted && current.isEmpty()) "Requis" else null) { current = it }
        PinField("Nouveau code secret", secret, err(check.secret)) { secret = it }
        PinField("Confirmer", secretConfirm, err(check.secretConfirm)) { secretConfirm = it }
        PinField("Nouveau code d'effacement", duress, err(check.duress)) { duress = it }
        PinField("Confirmer", duressConfirm, err(check.duressConfirm), ImeAction.Done) { duressConfirm = it }
        Button(
            enabled = !busy,
            onClick = {
                submitted = true
                if (!check.ok || current.isEmpty()) return@Button
                busy = true
                scope.launch {
                    val ok = appLock.changePins(current, secret, duress)
                    busy = false
                    if (ok) {
                        current = ""; secret = ""; secretConfirm = ""; duress = ""; duressConfirm = ""
                        submitted = false
                        snackbar.showSnackbar("Codes mis à jour")
                    } else {
                        snackbar.showSnackbar("Code secret actuel incorrect")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text(if (busy) "Chiffrement…" else "Mettre à jour") }
    }
}

@Composable
private fun SecuritySection(appLock: AppLock) {
    val level = remember { appLock.protectionLevel() }
    Section("Sécurité") {
        Fact("Clé de chiffrement protégée par : ${level.label}", ok = level == ProtectionLevel.STRONGBOX || level == ProtectionLevel.TEE)
        Fact("Base de données chiffrée (SQLCipher, AES-256)", ok = true)
        Fact("Verrouillage automatique dès que l'app quitte l'écran", ok = true)
        Fact("Captures et enregistrement d'écran bloqués", ok = true)
        Fact("Clavier sans apprentissage dans les champs sensibles", ok = true)
        OutlinedButton(onClick = appLock::lock, modifier = Modifier.fillMaxWidth()) { Text("Verrouiller maintenant") }
    }
}

@Composable
private fun Fact(text: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            Icons.Filled.CheckCircle, contentDescription = null,
            tint = if (ok) AppColors.Timer else AppColors.Warning
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Builds debug uniquement : remplit l'espace privé pour les démonstrations (sans second téléphone). */
@Composable
private fun DemoSection(session: Session) {
    val scope = rememberCoroutineScope()
    Section("Démonstration (debug)") {
        Text("Crée quelques conversations fictives. Absent des builds de release.", color = AppColors.Muted)
        OutlinedButton(onClick = { scope.launch(Dispatchers.IO) { DemoData.seed(session.repo) } }) {
            Text("Créer des conversations de démo")
        }
    }
}
