package com.example.harpochat.messaging

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.harpochat.ChatActivity
import com.example.harpochat.data.ChatRepository
import com.example.harpochat.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

class CodePairActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        actionBar?.hide()
        setContent { CodePairScreen(onBack = { finish() }) }
    }
}

/* ───────────────── Écran principal ───────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodePairScreen(onBack: () -> Unit) {
    val tabs = listOf("Importer", "Générer")
    var selected by remember { mutableIntStateOf(0) }

    // profil local simplifié
    val myUserId = remember { "USER-" + UUID.randomUUID().toString().take(8) }
    val myUserName = remember { android.os.Build.MODEL ?: "Moi" }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Importer par code") },
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
                    0 -> ImportTab()
                    1 -> GenerateTab(myUserId = myUserId, myUserName = myUserName)
                }
            }
        }
    }
}

/* ───────────────── Onglet GÉNÉRER (côté A) ───────────────── */

@Composable
private fun GenerateTab(myUserId: String, myUserName: String) {
    val ctx = LocalLifecycleOwner.current as ComponentActivity
    val repo = remember { ChatRepository.create(ctx.applicationContext) }
    val scope = rememberCoroutineScope()

    var threadId by remember { mutableStateOf(UUID.randomUUID().toString()) }

    val periodSec = 60
    var cycleStartMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // code tournant fluide (10 chars, change chaque minute)
    val rollingCode by rememberRollingCode(
        idSeed = myUserId,
        periodSec = periodSec,
        cycleStartMs = cycleStartMs
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MinuteBadgeSmooth(periodSec = periodSec, cycleStartMs = cycleStartMs, size = 64.dp)
        Spacer(Modifier.height(12.dp))

        Surface(color = AppColors.Sheet, tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Votre ID", color = AppColors.Muted, style = MaterialTheme.typography.labelMedium)
                Text(myUserId, color = AppColors.OnBgPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Mot de passe (60s)", color = AppColors.Muted, style = MaterialTheme.typography.labelMedium)
                Text(rollingCode, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
            }
        }

        Spacer(Modifier.height(24.dp))

        Text("Demandes entrantes", color = AppColors.OnBgPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        val pending = CodeMailbox.requests   // SnapshotStateList → lecture réactive
        if (pending.none { it.targetUserId == myUserId }) {
            Text("Aucune demande…", color = AppColors.Muted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pending.filter { it.targetUserId == myUserId }, key = { it.requestId }) { req ->
                    Surface(color = AppColors.Sheet, tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("De: ${req.requesterName}", color = AppColors.OnBgPrimary)
                            Text("Appareil: ${req.deviceInfo}", color = AppColors.Muted)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { CodeMailbox.decide(req.requestId, accept = false) }) {
                                    Text("Refuser")
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = {
                                    scope.launch {
                                        repo.ensureThread(req.threadId, req.requesterName)
                                    }
                                    CodeMailbox.decide(req.requestId, accept = true)
                                    ctx.startActivity(
                                        Intent(ctx, ChatActivity::class.java)
                                            .putExtra(ChatActivity.EXTRA_THREAD_ID, req.threadId)
                                            .putExtra("extra_title", req.requesterName)
                                    )
                                }) { Text("Accepter") }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(onClick = {
            threadId = UUID.randomUUID().toString()
            cycleStartMs = System.currentTimeMillis()     // resynchronise badge + code
        }) { Text("Nouveau code / nouveau fil") }
    }
}

/* ───────────────── Onglet IMPORTER (côté B) ───────────────── */

@Composable
private fun ImportTab() {
    var id by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = id,
            onValueChange = { id = it.trim() },
            label = { Text("ID de la personne") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.trim() },
            label = { Text("Mot de passe (10 caractères)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val ok = verifyRollingCode(id, code, 60)
                if (!ok) {
                    info = "Code invalide ou expiré."
                } else {
                    CodeMailbox.post(
                        CodeJoinRequest(
                            requestId = UUID.randomUUID().toString(),
                            targetUserId = id,
                            requesterName = android.os.Build.MODEL ?: "Contact",
                            deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                            threadId = UUID.randomUUID().toString()
                        )
                    )
                    info = "Demande envoyée. En attente d’acceptation."
                    code = ""
                }
            },
            enabled = id.isNotBlank() && code.length == 10,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Envoyer la demande") }

        Spacer(Modifier.height(24.dp))
        info?.let {
            Surface(color = AppColors.Sheet, tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
                Text(it, modifier = Modifier.padding(12.dp), color = AppColors.OnBgPrimary)
            }
        }
    }
}

/* ───────────────── Badge minute fluide (horaire) ───────────────── */

@Composable
private fun MinuteBadgeSmooth(periodSec: Int, cycleStartMs: Long, size: Dp) {
    val periodMs = periodSec * 1000L
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(periodSec, cycleStartMs) {
        while (true) {
            withFrameNanos { /* cadence ~60fps */ }
            nowMs = System.currentTimeMillis()
        }
    }

    val remaining = (periodMs - (nowMs - cycleStartMs)).coerceIn(0L, periodMs)
    val progress = remaining.toFloat() / periodMs.toFloat()  // 1 → 0 (horaire)
    val secLeft = ((remaining + 999) / 1000).toInt()

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            strokeWidth = 6.dp,
            trackColor = AppColors.Muted.copy(alpha = 0.35f),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.matchParentSize()
        )
        Text(secLeft.toString(), style = MaterialTheme.typography.labelLarge, color = AppColors.OnBgPrimary)
    }
}

/* ───────────────── Code tournant (génération / vérif) ───────────────── */

private val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"

@Composable
private fun rememberRollingCode(idSeed: String, periodSec: Int, cycleStartMs: Long): State<String> {
    val state = remember { mutableStateOf(generateCode(idSeed, System.currentTimeMillis(), 10)) }
    LaunchedEffect(idSeed, periodSec, cycleStartMs) {
        while (true) {
            state.value = generateCode(idSeed, System.currentTimeMillis(), 10)
            delay(200L) // fluide, pas coûteux
        }
    }
    return state
}

private fun verifyRollingCode(idSeed: String, code: String, periodSec: Int): Boolean {
    val now = System.currentTimeMillis()
    val windows = listOf(0L, -periodSec * 1000L, periodSec * 1000L)  // ±1 fenêtre
    return windows.any { w -> generateCode(idSeed, now + w, 10) == code }
}

private fun generateCode(seed: String, timeMs: Long, length: Int): String {
    val minuteBucket = timeMs / 60000L
    val input = (seed + ":" + minuteBucket).toByteArray()
    val digest = MessageDigest.getInstance("SHA-256").digest(input)
    val sb = StringBuilder(length)
    var i = 0
    while (sb.length < length) {
        val b = digest[i % digest.size].toInt() and 0xFF
        sb.append(ALPHABET[b % ALPHABET.length])
        i++
    }
    return sb.toString()
}

/* ───────────────── Mailbox locale (simulation) ───────────────── */

data class CodeJoinRequest(
    val requestId: String,
    val targetUserId: String,   // ID de A
    val requesterName: String,
    val deviceInfo: String,
    val threadId: String
)

object CodeMailbox {
    // état réactif simple
    val requests = mutableStateListOf<CodeJoinRequest>()

    fun post(req: CodeJoinRequest) {
        requests.add(req)
    }

    fun decide(requestId: String, accept: Boolean) {
        requests.removeAll { it.requestId == requestId }
        // plus tard : notifier le requérant via réseau
    }
}
