package com.example.harpochat.messaging

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.harpochat.ChatActivity
import com.example.harpochat.data.ChatRepository
import com.example.harpochat.qr.QrInvite
import com.example.harpochat.qr.decodeInvite
import com.example.harpochat.qr.encodeInvite
import com.example.harpochat.qr.isInviteExpired
import com.example.harpochat.qr.makeInviteNow
import com.example.harpochat.ui.theme.AppColors
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.Executors

/* =========================================================
 * Activity
 * ========================================================= */
class QrActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        actionBar?.hide()
        setContent { QrScreen(onBack = { finish() }) }
    }
}

/* =========================================================
 * Écran principal avec onglets
 * ========================================================= */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrScreen(onBack: () -> Unit) {
    val tabs = listOf("Scanner", "Générer")
    var selected by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // Repo Room (création thread locale quand on “accepte” une invite scannée)
    val ctx = LocalContext.current
    val repo = remember { ChatRepository.create(ctx.applicationContext) }

    // Profil local minimal (remplaçable plus tard)
    val myUserId = remember { UUID.randomUUID().toString() }
    val myUserName = remember { android.os.Build.MODEL ?: "Moi" }

    // État scanneur / popup / info
    var paused by remember { mutableStateOf(false) }
    var pendingInvite by remember { mutableStateOf<QrInvite?>(null) }
    var infoMsg by remember { mutableStateOf<String?>(null) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Code QR") },
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
                        Tab(
                            selected = selected == i,
                            onClick = { selected = i },
                            text = { Text(label) }
                        )
                    }
                }

                when (selected) {
                    // ---------- SCANNER ----------
                    0 -> QrScannerView(
                        modifier = Modifier.weight(1f),
                        paused = paused,
                        onDecoded = { raw ->
                            val result = decodeInvite(raw)
                            result.onSuccess { inv ->
                                if (isInviteExpired(inv)) {
                                    infoMsg = "Ce QR code a expiré."
                                } else {
                                    paused = true
                                    pendingInvite = inv
                                }
                            }.onFailure {
                                infoMsg = "QR invalide pour HarpoChat."
                            }
                        }
                    )

                    // ---------- GÉNÉRER ----------
                    1 -> QrGeneratorView(
                        modifier = Modifier.weight(1f),
                        meId = myUserId,
                        meName = myUserName
                    )
                }

                // Dialog info légère
                if (infoMsg != null) {
                    AlertDialog(
                        onDismissRequest = { infoMsg = null },
                        confirmButton = { TextButton(onClick = { infoMsg = null }) { Text("OK") } },
                        title = { Text("Information") },
                        text = { Text(infoMsg!!) }
                    )
                }

                // Dialog “Envoyer une demande ?”
                if (pendingInvite != null) {
                    val inv = pendingInvite!!
                    AlertDialog(
                        onDismissRequest = {
                            pendingInvite = null
                            paused = false
                        },
                        title = { Text("Demande de chat") },
                        text = {
                            Text(
                                "Vous avez scanné un QR d’invitation de ${inv.fromUserName}.\n" +
                                        "Voulez-vous envoyer une demande de chat ?"
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                scope.launch {
                                    // MVP : on crée localement le fil et on ouvre la conversation
                                    repo.ensureThread(inv.threadId, inv.fromUserName)
                                }
                                ctx.startActivity(
                                    Intent(ctx, ChatActivity::class.java)
                                        .putExtra(ChatActivity.EXTRA_THREAD_ID, inv.threadId)
                                        .putExtra("extra_title", inv.fromUserName)
                                )
                                pendingInvite = null
                                paused = false
                            }) { Text("Oui") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                pendingInvite = null
                                paused = false
                            }) { Text("Non") }
                        }
                    )
                }
            }
        }
    }
}

/* =========================================================
 * SCANNEUR
 * ========================================================= */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun QrScannerView(
    modifier: Modifier = Modifier,
    paused: Boolean,
    onDecoded: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Permission caméra
    var hasPermission by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }
    LaunchedEffect(Unit) { permLauncher.launch(Manifest.permission.CAMERA) }

    if (!hasPermission) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Autorisez la caméra pour scanner un QR code.", color = AppColors.OnBgPrimary)
        }
        return
    }

    // États “live”
    val pausedState by rememberUpdatedState(paused)
    val onDecodedState by rememberUpdatedState(onDecoded)

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }

    val analyzer = remember {
        ImageAnalysis.Analyzer { imageProxy: ImageProxy ->
            val media = imageProxy.image ?: run { imageProxy.close(); return@Analyzer }
            if (pausedState) { imageProxy.close(); return@Analyzer }
            val image = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val txt = barcodes.firstOrNull { it.rawValue != null }?.rawValue
                    if (!txt.isNullOrBlank() && !pausedState) onDecodedState(txt)
                }
                .addOnCompleteListener { imageProxy.close() }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val provider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(previewView.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().apply { setAnalyzer(executor, analyzer) }

        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis
        )

        onDispose {
            provider.unbindAll()
            executor.shutdown()
        }
    }

    Column(modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(AppColors.Bg)
        )
        Surface(modifier = Modifier.fillMaxWidth(), color = AppColors.Sheet) {
            Text(
                text = if (paused) "Pause scanneur…" else "Visez un QR code…",
                modifier = Modifier.padding(16.dp),
                color = AppColors.OnBgPrimary
            )
        }
    }
}

/* =========================================================
 * GÉNÉRATEUR (QR + badge compteur + bouton nouveau fil)
 * ========================================================= */
@Composable
private fun QrGeneratorView(
    modifier: Modifier = Modifier,
    meId: String,
    meName: String
) {
    val periodSec = 60

    // ID du fil (conservé tant qu'on ne force pas un nouveau fil)
    var threadId by remember { mutableStateOf(UUID.randomUUID().toString()) }

    // Contenu courant du QR + contrôle du cycle
    // ⬇︎ types spécialisés
    var qrText by remember { mutableStateOf("") }
    var cycleStartMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var secLeft by remember { mutableIntStateOf(periodSec) }

    // Fonction de (ré)génération, utilisée à l'initialisation, à l’expiration et quand on force
    fun regenNow() {
        val invite = makeInviteNow(
            fromUserId = meId,
            fromUserName = meName,
            threadId = threadId,
            ttlSec = periodSec.toLong()
        )
        qrText = encodeInvite(invite)
    }

    // Boucle d’anim/timing : une seule source de vérité pilote l’affichage et le changement de QR
    LaunchedEffect(meId, meName, threadId, cycleStartMs) {
        regenNow()                      // régénère immédiatement au début du cycle
        secLeft = periodSec             // timer plein
        while (true) {
            val now = System.currentTimeMillis()
            val elapsed = ((now - cycleStartMs) / 1000).toInt().coerceAtLeast(0)
            val left = (periodSec - (elapsed % periodSec))
            if (left != secLeft) secLeft = left

            if (elapsed >= periodSec) { // fin de cycle → nouveau QR + reset timer
                cycleStartMs = now
                regenNow()
                secLeft = periodSec
            }
            kotlinx.coroutines.delay(200L) // tick fluide (5 Hz), précis et léger
        }
    }

    // Bitmap du QR
    val bitmap = remember(qrText) {
        try {
            val enc = BarcodeEncoder()
            enc.encodeBitmap(qrText, BarcodeFormat.QR_CODE, 720, 720)
        } catch (_: Exception) { null }
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Badge au-dessus, aligné à droite, pas superposé au QR
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            MinuteCountdownBadge(
                secLeft = secLeft,
                periodSec = periodSec,
                modifier = Modifier.size(56.dp)
            )
        }
        Spacer(Modifier.height(8.dp))

        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Invite QR",
                modifier = Modifier.size(240.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text("Montre ce QR à l’autre personne", color = AppColors.Muted)
        } else {
            Text("Impossible de générer le QR", color = AppColors.Muted)
        }

        Spacer(Modifier.height(24.dp))

        // Force un NOUVEAU fil → nouveau QR + timer resynchronisé
        Button(
            onClick = {
                threadId = UUID.randomUUID().toString()
                cycleStartMs = System.currentTimeMillis() // reset timer au clic
                regenNow()                                // régénère immédiatement
                secLeft = periodSec
            }
        ) { Text("Nouveau code / nouveau fil") }
    }
}


/* =========================================================
 * Badge circulaire (compte à rebours d’une minute)
 * ========================================================= */
@Composable
private fun MinuteCountdownBadge(
    secLeft: Int,
    periodSec: Int,
    modifier: Modifier = Modifier
) {
    // Progression **horaire** : on affiche la part ÉCOULÉE, pas la part restante
    val progress = 1f - (secLeft / periodSec.toFloat())

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },                 // 0 → 1 (sens horaire)
            strokeWidth = 6.dp,
            trackColor = AppColors.Muted.copy(alpha = 0.35f),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.matchParentSize()
        )
        Text(
            text = secLeft.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = AppColors.OnBgPrimary
        )
    }
}
