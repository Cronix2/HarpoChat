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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.harpochat.ui.theme.AppColors
import kotlinx.coroutines.delay
import java.security.SecureRandom
import java.util.UUID

class CodepairActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        actionBar?.hide()

        setContent {
            CodepairScreen(onBack = { finish() })
        }
    }
}

/* =========================================================
 * Écran principal (onglets Importer / Générer)
 * ========================================================= */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodepairScreen(onBack: () -> Unit) {
    val tabs = listOf("Importer", "Générer")
    var selected by remember { mutableIntStateOf(0) }

    // ID “profil” local pour la démo du générateur
    val myUserId = remember { UUID.randomUUID().toString() }

    // 👇 Récupère le contexte une seule fois dans un scope @Composable
    val ctx = LocalContext.current

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Importer à partir d’un code") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                    }
                )
            },
            containerColor = AppColors.Bg
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .background(AppColors.Bg)
                    .padding(padding)
            ) {
                TabRow(selectedTabIndex = selected, containerColor = AppColors.Sheet) {
                    tabs.forEachIndexed { i, label ->
                        Tab(selected = selected == i, onClick = { selected = i }, text = { Text(label) })
                    }
                }

                when (selected) {
                    0 -> CodepairImporter(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        onSubmit = { id, code ->
                            // 👇 Utilise le ctx capturé, pas LocalContext.current
                            android.widget.Toast
                                .makeText(ctx, "Demande envoyée à\nID: $id\nCode: $code", android.widget.Toast.LENGTH_SHORT)
                                .show()
                        }
                    )

                    1 -> CodepairGenerator(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        myUserId = myUserId,
                        periodSec = 60,
                        passLen = 10
                    )
                }
            }
        }
    }
}

/* =========================================================
 * Onglet IMPORTER (ID + Code) — plein écran
 * ========================================================= */
@Composable
private fun CodepairImporter(
    modifier: Modifier = Modifier,
    onSubmit: (id: String, code: String) -> Unit
) {
    var requestId by remember { mutableStateOf("") }
    var requestCode by remember { mutableStateOf("") }

    Column(modifier.padding(16.dp)) {
        OutlinedTextField(
            value = requestId,
            onValueChange = { requestId = it },
            label = { Text("ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = requestCode,
            onValueChange = { requestCode = it },
            label = { Text("Mot de passe (10 caractères)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSubmit(requestId.trim(), requestCode.trim()) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Envoyer la demande") }
    }
}

/* =========================================================
 * Onglet GÉNÉRER (ID fixe + code rotatif + timer synchrone)
 * ========================================================= */
@Composable
private fun CodepairGenerator(
    modifier: Modifier = Modifier,
    myUserId: String,
    periodSec: Int = 60,
    passLen: Int = 10
) {
    // Alphabet sans ambigüités
    val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789abcdefghijkmnpqrstuvwxyz"

    var cycleStartMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var secLeft by remember { mutableIntStateOf(periodSec) }
    var passcode by remember { mutableStateOf("") }

    fun genPass(len: Int): String {
        val rnd = SecureRandom()
        val sb = StringBuilder(len)
        repeat(len) { sb.append(alphabet[rnd.nextInt(alphabet.length)]) }
        return sb.toString()
    }

    fun regenNow(now: Long = System.currentTimeMillis()) {
        passcode = genPass(passLen)
        cycleStartMs = now
        secLeft = periodSec
    }

    // Une seule boucle pour piloter timer + régénération
    LaunchedEffect(myUserId, passLen, periodSec) {
        regenNow()
        while (true) {
            val now = System.currentTimeMillis()
            val elapsedSec = ((now - cycleStartMs) / 1000).toInt().coerceAtLeast(0)
            val left = (periodSec - (elapsedSec % periodSec))
            if (left != secLeft) secLeft = left

            if (elapsedSec >= periodSec) {
                regenNow(now)
            }
            delay(16L) // ~60 fps pour la fluidité
        }
    }

    // Progression fluide horaire (1f -> 0f)
    val progress by produceState(initialValue = 1f, key1 = cycleStartMs, key2 = periodSec) {
        while (true) {
            val now = System.currentTimeMillis()
            val totalMs = periodSec * 1000f
            val remaining = (totalMs - (now - cycleStartMs)).coerceIn(0f, totalMs)
            value = remaining / totalMs
            delay(16L)
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Timer au-dessus, centré
        MinuteBadgeClockwise(
            progress = progress,
            secLeft = secLeft,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(12.dp))

        // Carte plein écran
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("ID (fixe)", color = AppColors.Muted, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = myUserId,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.OnBgPrimary
                )

                Spacer(Modifier.height(12.dp))
                Text("Mot de passe (change toutes les ${periodSec}s)", color = AppColors.Muted, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = passcode.ifEmpty { "——" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = AppColors.OnBgPrimary
                )
            }
        }

        Button(
            onClick = { regenNow() },               // régénération immédiate + resync
            modifier = Modifier.fillMaxWidth()
        ) { Text("Nouveau code") }
    }
}

/* =========================================================
 * Badge minute (horaire, fluide, centré)
 * ========================================================= */
@Composable
private fun MinuteBadgeClockwise(
    progress: Float,      // 1f -> 0f
    secLeft: Int,
    modifier: Modifier = Modifier
) {
    val arcColor = MaterialTheme.colorScheme.primary

    Box(modifier, contentAlignment = Alignment.Center) {
        // Arc sens horaire (en haut -> vers la droite)
        Canvas(Modifier.matchParentSize()) {
            val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val rect = Rect(inset, inset, size.width - inset, size.height - inset)
            // piste grise complète
            drawArc(
                color = AppColors.Muted.copy(alpha = 0.25f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = rect.topLeft,
                size = rect.size,
                style = stroke
            )

            // arc en sens horaire
            drawArc(
                color = AppColors.Timer,
                startAngle = -90f,
                sweepAngle = -360f * progress, // ← négatif = sens horaire
                useCenter = false,
                topLeft = rect.topLeft,
                size = rect.size,
                style = stroke
            )
        }
        Text(
            text = secLeft.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = AppColors.OnBgPrimary
        )
    }
}
