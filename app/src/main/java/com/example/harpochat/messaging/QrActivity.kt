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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap

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
                    if (!txt.isNullOrBlank()) onDecodedState(txt)
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

    // ID du fil (conservé tant qu’on ne force pas un nouveau fil)
    var threadId by remember { mutableStateOf(UUID.randomUUID().toString()) }

    // Contenu courant du QR + contrôle du cycle
    var qrText by remember { mutableStateOf("") }
    var cycleStartMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var secLeft by remember { mutableIntStateOf(periodSec) }

    // Génère le contenu du QR pour le cycle en cours
    fun regenNow() {
        val invite = makeInviteNow(
            fromUserId = meId,
            fromUserName = meName,
            threadId = threadId,
            ttlSec = periodSec.toLong()
        )
        qrText = encodeInvite(invite)
    }

    // Une seule boucle pilote le timer + la régénération du QR
    LaunchedEffect(meId, meName, threadId) {
        cycleStartMs = System.currentTimeMillis()
        regenNow()
        secLeft = periodSec

        while (true) {
            val now = System.currentTimeMillis()
            val elapsed = ((now - cycleStartMs) / 1000).toInt().coerceAtLeast(0)
            val left = (periodSec - (elapsed % periodSec))
            if (left != secLeft) secLeft = left

            if (elapsed >= periodSec) {
                cycleStartMs = now
                regenNow()
                secLeft = periodSec
            }
            // ~60 fps pour une progression bien fluide
            kotlinx.coroutines.delay(16L)
        }
    }

    // Bitmap du QR (recalculé quand qrText change)
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
        // Badge TIMER : centré horizontalement, au-dessus du QR
        MinuteCountdownBadge(
            periodSec = periodSec,
            cycleStartMs = cycleStartMs,
            modifier = Modifier.size(64.dp)
        )


        Spacer(Modifier.height(12.dp))

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
fun MinuteCountdownBadge(
    periodSec: Int,
    cycleStartMs: Long,
    modifier: Modifier = Modifier
) {
    val periodMs = periodSec * 1000L

    // horloge fluide ~60fps
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodSec, cycleStartMs) {
        while (true) {
            nowMs = System.currentTimeMillis()
            withFrameNanos { } // cadence sur le rendu
        }
    }

    val elapsedMs = (nowMs - cycleStartMs).coerceAtLeast(0L)
    val remainingMs = (periodMs - (elapsedMs % periodMs))
    val secLeft = ((remainingMs + 999) / 1000).toInt()

    // progress en sens horaire → 0f = vide, 1f = plein
    val progress = remainingMs.toFloat() / periodMs.toFloat()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Fond
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

        // texte numérique au centre
        Text(
            text = secLeft.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = AppColors.OnBgPrimary
        )
    }
}
