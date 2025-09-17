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

import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.compose.material3.AlertDialog as M3AlertDialog // alias M3
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.harpochat.ChatActivity
import com.example.harpochat.calculator.CalculatorActivity
import com.example.harpochat.data.ThreadEntity
import com.example.harpochat.security.SecureStore
import com.example.harpochat.settings.PinSettingsActivity
import java.util.UUID
import android.app.Dialog
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.harpochat.R
import androidx.core.graphics.drawable.toDrawable


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

        // Alerte "PIN par défaut" si nécessaire (revient tant que pas changé)
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
                    if (res.resultCode == RESULT_OK) {
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

/**
 * Affiche une alerte *à chaque ouverture* tant que l’utilisateur
 * n’a pas changé au moins un PIN dans l’écran Paramètres.
 * On s’appuie sur le flag CalculatorActivity.KEY_PINS_CHANGED,
 * qui est mis à `true` dans PinSettingsActivity quand l’utilisateur
 * enregistre de nouveaux codes.
 */
@SuppressLint("InflateParams", "SetTextI18n")
fun maybeWarnDefaultPin(activity: Activity) {
    val prefs = SecureStore.prefs(activity)

    // Si l’utilisateur a déjà changé au moins un PIN, on ne montre plus l’alerte
    val pinsChanged = prefs.getBoolean(CalculatorActivity.KEY_PINS_CHANGED, false)
    if (pinsChanged) return

    if (activity.isFinishing || activity.isDestroyed) return

    activity.runOnUiThread {
        if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread

        // 1) Dialog "pleinement custom"
        val dialog = Dialog(activity)
        dialog.setCancelable(false)

        // 2) On gonfle TON layout
        val view = LayoutInflater.from(activity)
            .inflate(R.layout.dialog_default_pin, null, false)
        dialog.setContentView(view)

        // 3) On laisse le fond de la fenêtre transparent pour voir ton bg arrondi
        dialog.window?.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())

        // 4) Optionnel : largeur confortable
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        // 🔥 Ajout de marges aux bords de l’écran
        WindowManager.LayoutParams().apply {
            copyFrom(dialog.window?.attributes)
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            // marge horizontale (ici 48px de chaque côté → tu peux ajuster)
            val margin = (activity.resources.displayMetrics.density * 20).toInt()
            horizontalMargin = 0f
            dialog.window?.decorView?.setPadding(margin, 0, margin, 0)
        }

        // 5) Brancher les vues du layout
        val tvTitle   = view.findViewById<TextView>(R.id.tvTitle)
        val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
        val btnPrimary   = view.findViewById<Button>(R.id.btnPrimary)
        val btnSecondary = view.findViewById<Button>(R.id.btnSecondary)

        // 6) Texte (ou garde ceux définis dans ton XML)
        tvTitle.text = "Renforcez votre sécurité"
        tvMessage.text = "Votre code de déverrouillage ou d’effacement est encore celui par défaut. Par sécurité, changez-les maintenant."

        // 7) Appliquer tes drawables & couleurs de /res/drawable et /res/color
        btnPrimary.background = ContextCompat.getDrawable(activity, R.drawable.dialog_btn_primary)
        btnPrimary.setTextColor(ContextCompat.getColor(activity, R.color.dialog_btn_primary_text))

        btnSecondary.background = ContextCompat.getDrawable(activity, R.drawable.dialog_btn_secondary)
        btnSecondary.setTextColor(ContextCompat.getColor(activity, R.color.dialog_btn_secondary_text))

        // 8) Actions
        btnPrimary.setOnClickListener {
            activity.startActivity(Intent(activity, PinSettingsActivity::class.java))
            dialog.dismiss()
        }
        btnSecondary.setOnClickListener {
            // Pas de flag "vu" : on re-affichera tant que les PIN sont par défaut
            dialog.dismiss()
        }

        dialog.show()
    }
}