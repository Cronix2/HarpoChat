/*
——————What are you doing here ?——————
⠀⣞⢽⢪⢣⢣⢣⢫⡺⡵⣝⡮⣗⢷⢽⢽⢽⣮⡷⡽⣜⣜⢮⢺⣜⢷⢽⢝⡽⣝
⠸⡸⠜⠕⠕⠁⢁⢇⢏⢽⢺⣪⡳⡝⣎⣏⢯⢞⡿⣟⣷⣳⢯⡷⣽⢽⢯⣳⣫⠇
⠀⠀⢀⢀⢄⢬⢪⡪⡎⣆⡈⠚⠜⠕⠇⠗⠝⢕⢯⢫⣞⣯⣿⣻⡽⣏⢗⣗⠏⠀
⠀⠪⡪⡪⣪⢪⢺⢸⢢⢓⢆⢤⢀⠀⠀⠀⠀⠈⢊⢞⡾⣿⡯⣏⢮⠷⠁⠀⠀
⠀⠀⠀⠈⠊⠆⡃⠕⢕⢇⢇⢇⢇⢇⢏⢎⢎⢆⢄⠀⢑⣽⣿⢝⠲⠉⠀⠀⠀⠀
⠀⠀⠀⠀⠀⡿⠂⠠⠀⡇⢇⠕⢈⣀⠀⠁⠡⠣⡣⡫⣂⣿⠯⢪⠰⠂⠀⠀⠀⠀
⠀⠀⠀⠀⡦⡙⡂⢀⢤⢣⠣⡈⣾⡃⠠⠄⠀⡄⢱⣌⣶⢏⢊⠂⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⢝⡲⣜⡮⡏⢎⢌⢂⠙⠢⠐⢀⢘⢵⣽⣿⡿⠁⠁⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠨⣺⡺⡕⡕⡱⡑⡆⡕⡅⡕⡜⡼⢽⡻⠏⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⣼⣳⣫⣾⣵⣗⡵⡱⡡⢣⢑⢕⢜⢕⡝⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⣴⣿⣾⣿⣿⣿⡿⡽⡑⢌⠪⡢⡣⣣⡟⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⡟⡾⣿⢿⢿⢵⣽⣾⣼⣘⢸⢸⣞⡟⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
⠀⠀⠀⠀⠁⠇⠡⠩⡫⢿⣝⡻⡮⣒⢽⠋⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
————————————————————————————————————
*/

package com.example.harpochat.messaging

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.AlertDialog as M3AlertDialog // alias pour éviter le conflit avec android.app.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.harpochat.ChatActivity
import com.example.harpochat.calculator.CalculatorActivity
import com.example.harpochat.data.ThreadEntity
import com.example.harpochat.security.SecureStore
import com.example.harpochat.settings.PinSettingsActivity
import java.util.UUID

/* =========================
 * Constantes
 * ========================= */
private const val DEFAULT_SECRET = "527418"
private const val DEFAULT_DURESS = "1234"
private const val KEY_PIN_WARNING_SHOWN = "pin_warning_shown"

/* =========================
 * Palette locale
 * ========================= */
private object ConvColors {
    // Surfaces / fonds
    val bg          = Color(0xFF0F1115)
    val sheetBg     = bg
    val rowAvatarBg = Color(0xFF2A2F3A)
    val divider     = Color(0xFF1E2430)

    // Texte
    val textPrimary   = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFF8C96A7)

    // Icônes
    val iconDefault = Color(0xFFB7C0D0)
    val iconAccent  = Color(0xFFFFFFFF)
}

/* =========================
 * Activity
 * ========================= */
@OptIn(ExperimentalMaterial3Api::class)
class ConversationsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        actionBar?.hide()

        // Alerte "PIN par défaut" si nécessaire
        maybeWarnDefaultPin(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background   = ConvColors.bg,
                    surface      = ConvColors.bg,
                    onBackground = ConvColors.textPrimary,
                    primary      = ConvColors.iconAccent
                )
            ) {
                val vm: ConversationsViewModel = viewModel()
                val threads by vm.threads.collectAsState()

                var showSheet by remember { mutableStateOf(false) }
                var showCreateDialog by remember { mutableStateOf(false) }
                var newTitle by remember { mutableStateOf(TextFieldValue("")) }

                val context = this@ConversationsActivity

                val qrLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { res ->
                    if (res.resultCode == Activity.RESULT_OK) {
                        val payload = res.data?.getStringExtra("qr") ?: return@rememberLauncherForActivityResult
                        val id = payload            // à adapter si besoin
                        val title = payload.take(24)
                        vm.createThread(id, title)
                        startActivity(
                            Intent(context, ChatActivity::class.java)
                                .putExtra(ChatActivity.EXTRA_THREAD_ID, id)
                                .putExtra("extra_title", title)
                        )
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("HarpoChat") },
                            actions = {
                                IconButton(
                                    onClick = {
                                        startActivity(Intent(this@ConversationsActivity, PinSettingsActivity::class.java))
                                    }
                                ) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Paramètres")
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = { showSheet = true }) {
                            Icon(imageVector = Icons.Filled.Add, contentDescription = "Ajouter")
                        }
                    }
                ) { padding ->
                    // Liste des conversations
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ConvColors.bg)
                            .padding(padding)
                    ) {
                        items(items = threads, key = { it.id }) { t ->
                            ConversationRow(t) {
                                startActivity(
                                    Intent(this@ConversationsActivity, ChatActivity::class.java)
                                        .putExtra(ChatActivity.EXTRA_THREAD_ID, t.id)
                                        .putExtra("extra_title", t.title)
                                )
                            }
                            HorizontalDivider(color = ConvColors.divider)
                        }
                    }

                    // Bottom sheet (+)
                    if (showSheet) {
                        ModalBottomSheet(
                            onDismissRequest = { showSheet = false },
                            containerColor = ConvColors.sheetBg
                        ) {
                            SheetActionRow(
                                icon = Icons.Filled.QrCodeScanner,
                                text = "Importer depuis un QR code",
                                tint = ConvColors.iconDefault
                            ) {
                                showSheet = false
                                // Lance ton QrActivity (à adapter selon ton projet)
                                qrLauncher.launch(Intent(context, QrActivity::class.java))
                            }

                            HorizontalDivider(color = ConvColors.divider)

                            SheetActionRow(
                                icon = Icons.Filled.Key,
                                text = "Importer via un code",
                                tint = Color.White
                            ) {
                                showSheet = false
                                // Lance ton CodepairActivity (à adapter selon ton projet)
                                startActivity(Intent(context, CodepairActivity::class.java))
                            }

                            /*
                            HorizontalDivider(color = ConvColors.divider)

                            SheetActionRow(
                                icon = Icons.AutoMirrored.Filled.ArrowForward,
                                text = "Créer à partir de zéro",
                                tint = ConvColors.iconAccent
                            ) {
                                showSheet = false
                                newTitle = TextFieldValue("")
                                showCreateDialog = true
                            }
                             */

                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    // Dialog création (si tu réactives la section ci-dessus)
                    if (showCreateDialog) {
                        M3AlertDialog(
                            onDismissRequest = { showCreateDialog = false },
                            title = { Text("Nouvelle conversation") },
                            text = {
                                OutlinedTextField(
                                    value = newTitle,
                                    onValueChange = { newTitle = it },
                                    singleLine = true,
                                    placeholder = { Text("Nom du contact/groupe") }
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val title = newTitle.text.trim()
                                    if (title.isNotEmpty()) {
                                        val id = UUID.randomUUID().toString()
                                        vm.createThread(id, title)
                                        startActivity(
                                            Intent(this@ConversationsActivity, ChatActivity::class.java)
                                                .putExtra(ChatActivity.EXTRA_THREAD_ID, id)
                                                .putExtra("extra_title", title)
                                        )
                                        showCreateDialog = false
                                    }
                                }) { Text("Créer") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showCreateDialog = false }) { Text("Annuler") }
                            }
                        )
                    }
                }
            }
        }
    }
}

/* =========================
 * UI bits
 * ========================= */

@Composable
private fun ConversationRow(conv: ThreadEntity, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = MaterialTheme.shapes.large,
            color = ConvColors.rowAvatarBg
        ) {}

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                conv.title,
                color = ConvColors.textPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Touchez pour ouvrir",
                color = ConvColors.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    text: String,
    tint: Color = ConvColors.iconDefault,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(text, color = ConvColors.textPrimary) },
        leadingContent = { Icon(imageVector = icon, contentDescription = null, tint = tint) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 2.dp)
    )
}

/* =========================
 * Alerte “PIN par défaut”
 * ========================= */

fun maybeWarnDefaultPin(activity: Activity) {
    val prefs = SecureStore.prefs(activity)

    val secret = prefs.getString(CalculatorActivity.KEY_SECRET_PIN, "") ?: ""
    val duress = prefs.getString(CalculatorActivity.KEY_DURESS_PIN, "") ?: ""
    val alreadyShown = prefs.getBoolean(KEY_PIN_WARNING_SHOWN, false)
    val usingDefaults = (secret == DEFAULT_SECRET) || (duress == DEFAULT_DURESS)

    if (!usingDefaults || alreadyShown) return

    activity.runOnUiThread {
        if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread

        AlertDialog.Builder(activity) // android.app.AlertDialog, pas AppCompat
            .setTitle("PIN par défaut détecté")
            .setMessage(
                "Votre code de déverrouillage ou d’effacement est encore celui par défaut. " +
                        "Par sécurité, changez-les maintenant."
            )
            .setCancelable(false)
            .setPositiveButton("Changer maintenant") { _, _ ->
                activity.startActivity(Intent(activity, PinSettingsActivity::class.java))
                prefs.edit { putBoolean(KEY_PIN_WARNING_SHOWN, true) }
            }
            .setNegativeButton("Plus tard") { _, _ ->
                prefs.edit { putBoolean(KEY_PIN_WARNING_SHOWN, true) }
            }
            .show()
    }
}
