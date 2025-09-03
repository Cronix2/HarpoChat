package com.example.harpochat.messaging

import android.content.Intent
import android.os.Bundle
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.harpochat.ChatActivity
import com.example.harpochat.data.ThreadEntity
import java.util.UUID

/* =========================
 * One-stop palette for this screen
 * ========================= */
private object ConvColors {
    // surfaces / backgrounds
    val bg            = Color(0xFF0F1115)
    val sheetBg       = bg
    val rowAvatarBg   = Color(0xFF2A2F3A)
    val divider       = Color(0xFF1E2430)

    // text
    val textPrimary   = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFF8C96A7)

    // icons
    val iconDefault   = Color(0xFFB7C0D0)
    val iconAccent    = Color(0xFFFFFFFF)
}

@OptIn(ExperimentalMaterial3Api::class)
class ConversationsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        actionBar?.hide()

        setContent {
            // keep Material3 dark scheme; we mostly use ConvColors directly below
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = ConvColors.bg,
                    surface    = ConvColors.bg,
                    onBackground = ConvColors.textPrimary,
                    primary    = ConvColors.iconAccent
                )
            ) {

                // ==== VM + state ====
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
                        // TODO: décoder le payload (id+nom par ex.) et créer/ouvrir le thread
                        // ex rapide :
                        val id = payload // à adapter
                        val title = payload.take(24)
                        vm.createThread(id, title)
                        context.startActivity(
                            Intent(context, ChatActivity::class.java)
                                .putExtra(ChatActivity.EXTRA_THREAD_ID, id)
                                .putExtra("extra_title", title)
                        )
                    }
                }


                Scaffold(
                    topBar = { TopAppBar(title = { Text("HarpoChat") }) },
                    floatingActionButton = {
                        FloatingActionButton(onClick = { showSheet = true }) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Ajouter"
                            )
                        }
                    }
                ) { padding ->

                    // ==== list of conversations ====
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ConvColors.bg)
                            .padding(padding)
                    ) {
                        items(items = threads, key = { it.id }) { t: ThreadEntity ->
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

                    // ==== bottom sheet (+) ====
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
                                qrLauncher.launch(Intent(context, QrActivity::class.java))
                            }

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
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    // ==== create-thread dialog ====
                    if (showCreateDialog) {
                        AlertDialog(
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
                                TextButton(onClick = { showCreateDialog = false }) {
                                    Text("Annuler")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/* ---------- UI bits ---------- */

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
        leadingContent = {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 2.dp)
    )
}

// test2