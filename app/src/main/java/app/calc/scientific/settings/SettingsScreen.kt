// SPDX-License-Identifier: MIT
package app.calc.scientific.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.calc.scientific.BuildConfig
import app.calc.scientific.data.ChatRepository
import app.calc.scientific.data.DemoData
import app.calc.scientific.bg.BackgroundPrefs
import app.calc.scientific.bg.ResultService
import app.calc.scientific.data.EphemeralChoice
import app.calc.scientific.security.AppLock
import app.calc.scientific.security.Session
import app.calc.scientific.ui.GroupLabel
import app.calc.scientific.ui.IconAction
import app.calc.scientific.ui.MintPanel
import app.calc.scientific.ui.NoKeyboardLearning
import app.calc.scientific.ui.PrimaryButton
import app.calc.scientific.ui.ScreenHeader
import app.calc.scientific.ui.SecondaryButton
import app.calc.scientific.ui.theme.LineIcons
import app.calc.scientific.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(session: Session, appLock: AppLock, onBack: () -> Unit, onChangeCodes: () -> Unit) {
    val repo = session.repo
    val scope = rememberCoroutineScope()
    val level = remember { appLock.protectionLevel() }
    val displayName by repo.settingFlow(ChatRepository.KEY_DISPLAY_NAME).collectAsStateWithLifecycle(initialValue = null)
    val defaultTtl by repo.settingFlow(ChatRepository.KEY_DEFAULT_EPHEMERAL).collectAsStateWithLifecycle(initialValue = null)
    var editName by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var ttlMenu by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    var background by remember { mutableStateOf(BackgroundPrefs.isEnabled(ctx)) }
    // La demande de permission s'affiche par-dessus l'app : pas de verrouillage pendant ce temps.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        appLock.expectExternalActivity(false)
    }
    fun toggleBackground() {
        background = !background
        BackgroundPrefs.setEnabled(ctx, background)
        if (background) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                appLock.expectExternalActivity(true)
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            ResultService.start(ctx)
        } else {
            ResultService.stop(ctx)
        }
    }

    Column(Modifier.fillMaxSize().background(Palette.Ink)) {
        ScreenHeader("Réglages", onBack) {
            IconAction(LineIcons.Lock, "Verrouiller maintenant", appLock::lock)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // --- Bilan de protection, calculé à partir de l'état réel ---
            MintPanel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).background(Palette.MintAvatar, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(LineIcons.ShieldCheck, null, tint = Palette.Mint, modifier = Modifier.size(22.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(if (level.hardware) "Protection maximale" else "Protection renforcée", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (level.hardware) "5 protections actives sur 5" else "4 sur 5 : cet appareil n'a pas de puce sécurisée",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (level.hardware) Palette.Mint else Palette.Amber
                        )
                    }
                }
                Check("Clé protégée par : ${level.label}", ok = level.hardware)
                Check("Verrouillage dès que l'app quitte l'écran", ok = true)
                Check("Captures et enregistrement d'écran bloqués", ok = true)
                Check("Clavier sans apprentissage", ok = true)
                Check("Aucune sauvegarde hors du téléphone", ok = true)
            }

            Group("Profil") {
                SettingRow("Nom affiché", displayName ?: "Contact", onClick = { editName = true })
            }
            Group("Codes") {
                SettingRow("Code secret et code d'effacement", "Modifier", onClick = onChangeCodes)
            }
            Group("Réception") {
                SettingRow("Réception en arrière-plan", if (background) "Activée" else "Désactivée", onClick = ::toggleBackground)
            }
            Text(
                if (background)
                    "Les messages arrivent même app verrouillée. Ils restent chiffrés jusqu'à votre code ; vous voyez seulement « Nouveau résultat disponible »."
                else
                    "Désactivée, les messages vous attendent dans le réseau (7 jours au plus) et arrivent à l'ouverture de l'app.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Faint, modifier = Modifier.padding(horizontal = 4.dp)
            )
            Group("Confidentialité") {
                Box {
                    SettingRow(
                        "Messages éphémères par défaut",
                        EphemeralChoice.of(defaultTtl?.toLongOrNull() ?: 0).short,
                        onClick = { ttlMenu = true }
                    )
                    DropdownMenu(expanded = ttlMenu, onDismissRequest = { ttlMenu = false }, containerColor = Palette.Sheet) {
                        EphemeralChoice.entries.forEach { c ->
                            DropdownMenuItem(text = { Text(c.label) }, onClick = {
                                ttlMenu = false
                                scope.launch(Dispatchers.IO) { repo.putSetting(ChatRepository.KEY_DEFAULT_EPHEMERAL, c.seconds.toString()) }
                            })
                        }
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                Group("Démonstration (build debug)") {
                    SettingRow("Créer des conversations de démo", "", onClick = { scope.launch(Dispatchers.IO) { DemoData.seed(repo) } })
                }
            }

            SecondaryButton("Tout effacer maintenant", { confirmWipe = true }, Modifier.fillMaxWidth(), danger = true, icon = LineIcons.Trash)
        }
    }

    if (editName) {
        NameSheet(displayName ?: "", onDismiss = { editName = false }) { name ->
            editName = false
            scope.launch(Dispatchers.IO) { repo.putSetting(ChatRepository.KEY_DISPLAY_NAME, name.trim().ifEmpty { "Contact" }) }
        }
    }
    if (confirmWipe) {
        WipeSheet(onDismiss = { confirmWipe = false }) {
            confirmWipe = false
            scope.launch { appLock.wipeNow() }
        }
    }
}

@Composable
private fun Check(text: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(if (ok) LineIcons.Check else LineIcons.Info, null, tint = if (ok) Palette.Mint else Palette.Amber, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.TextSoft)
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GroupLabel(title)
        Column(Modifier.fillMaxWidth().background(Palette.Surface, RoundedCornerShape(16.dp))) { content() }
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        Icon(LineIcons.ChevronRight, null, tint = Palette.Faint, modifier = Modifier.size(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameSheet(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Palette.Sheet) {
        NoKeyboardLearning {
            Column(Modifier.navigationBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Nom affiché", style = MaterialTheme.typography.headlineSmall)
                Text("Présenté à vos nouveaux contacts dans votre code d'invitation.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                Box(Modifier.fillMaxWidth().background(Palette.Surface, RoundedCornerShape(14.dp)).padding(16.dp)) {
                    if (name.isEmpty()) Text("Contact", style = MaterialTheme.typography.bodyLarge, color = Palette.Faint)
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it.take(32) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
                        cursorBrush = SolidColor(Palette.Mint),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                PrimaryButton("Enregistrer", { onSave(name) }, Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WipeSheet(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Palette.Sheet) {
        Column(Modifier.navigationBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(LineIcons.Trash, null, tint = Palette.Danger, modifier = Modifier.size(28.dp))
            Text("Tout effacer ?", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Conversations, contacts et clés seront détruits définitivement. L'application redeviendra une calculatrice vierge, à reconfigurer.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.Muted
            )
            HorizontalDivider(color = Palette.Line)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Annuler", onDismiss, Modifier.weight(1f))
                SecondaryButton("Tout effacer", onConfirm, Modifier.weight(1f), danger = true)
            }
        }
    }
}
