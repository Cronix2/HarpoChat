package com.example.harpochat.messaging

import android.Manifest
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
import com.example.harpochat.link.*
import com.example.harpochat.ui.theme.AppColors
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.util.concurrent.Executors

class QrActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        actionBar?.hide()
        setContent { QrScreen(onBack = { finish() }) }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrScreen(onBack: () -> Unit) {
    val tabs = listOf("Scanner", "Générer")
    var selected by remember { mutableIntStateOf(0) }

    // ---------- existing INVITE state (côté A) ----------
    val myInvite by remember {
        mutableStateOf(
            InvitePayload(
                inviteId = java.util.UUID.randomUUID().toString(),
                aIdPub = DemoCrypto.genPublicKey(),
                oneTimeCode = DemoCrypto.genCode(),
                expiresAt = System.currentTimeMillis() + 10 * 60 * 1000
            )
        )
    }
    val inviteWire = remember(myInvite) { myInvite.toWire() }

    // Observe join requests for my invite (unchanged)
    val mailboxRequests by InMemoryMailbox.requests.collectAsState()
    val pendingForMe = mailboxRequests[myInvite.inviteId].orEmpty()

    // ---------- NEW: confirmation state for scan (côté B) ----------
    var paused by remember { mutableStateOf(false) }                  // pause analyzer
    var pendingInvite by remember { mutableStateOf<InvitePayload?>(null) } // invite decoded awaiting user choice

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
                        Tab(selected = selected == i, onClick = { selected = i }, text = { Text(label) })
                    }
                }

                when (selected) {
                    // ----- SCANNER (côté B) -----
                    0 -> QrScannerView(
                        modifier = Modifier.weight(1f),
                        paused = paused,                                     // ← NEW
                        onDecoded = { wire ->
                            val invite = InvitePayload.fromWire(wire)
                            if (invite != null && invite.expiresAt > System.currentTimeMillis()) {
                                paused = true
                                pendingInvite = invite                         // ← triggers dialog
                            }
                        }
                    )

                    // ----- GENERATE (côté A) -----
                    1 -> QrGeneratorAndInbox(
                        content = inviteWire,
                        pending = pendingForMe,
                        onAccept = {
                            InMemoryMailbox.decide(InviteDecision(myInvite.inviteId, accept = true))
                        },
                        onReject = {
                            InMemoryMailbox.decide(InviteDecision(myInvite.inviteId, accept = false))
                        }
                    )
                }

                // ---------- NEW: confirmation dialog on scan ----------
                if (pendingInvite != null) {
                    val inv = pendingInvite!!
                    AlertDialog(
                        onDismissRequest = {
                            pendingInvite = null
                            paused = false
                        },
                        title = { Text("Demande de chat") },
                        text = {
                            Text("Vous avez scanné un QR code.\nVoulez-vous envoyer une demande de chat à cette personne ?")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                // Post a join request to “server” (simulated)
                                InMemoryMailbox.postJoinRequest(
                                    JoinRequest(
                                        inviteId = inv.inviteId,
                                        bIdPub = DemoCrypto.genPublicKey(),
                                        deviceInfo = android.os.Build.MODEL ?: "device"
                                    )
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


/* Génération + “inbox” des demandes reçues par A */
@Composable
private fun QrGeneratorAndInbox(
    content: String,
    pending: List<com.example.harpochat.link.JoinRequest>,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // ===== Générateur (comme tu avais) =====
        val bitmap = remember(content) {
            try {
                val enc = com.journeyapps.barcodescanner.BarcodeEncoder()
                enc.encodeBitmap(content, com.google.zxing.BarcodeFormat.QR_CODE, 720, 720)
            } catch (_: Exception) { null }
        }
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Invite QR",
                modifier = Modifier.size(240.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text("Montre ce QR à l'autre personne", color = AppColors.Muted)
        } else {
            Text("Impossible de générer le QR", color = AppColors.Muted)
        }

        Spacer(Modifier.height(24.dp))
        // ===== Inbox de demandes (JoinRequest) reçues pour CETTE invite =====
        if (pending.isEmpty()) {
            Text("Aucune demande pour le moment…", color = AppColors.OnBgPrimary)
        } else {
            Text("Demande reçue :", color = AppColors.OnBgPrimary)
            Spacer(Modifier.height(8.dp))
            pending.forEach { req ->
                Surface(tonalElevation = 2.dp, color = AppColors.Sheet, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("Appareil: ${req.deviceInfo}", color = AppColors.OnBgPrimary)
                        Text("Clé B: ${req.bIdPub.take(20)}…", color = AppColors.Muted)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = onReject) { Text("Refuser") }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = onAccept) { Text("Accepter") }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}


/* ---------------- Scanner ---------------- */

@ExperimentalGetImage
@Composable
fun QrScannerView(
    modifier: Modifier = Modifier,
    paused: Boolean = false,
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
            Text("Autorise la caméra pour scanner un QR code.", color = AppColors.OnBgPrimary)
        }
        return
    }

    // État “live” pour éviter les lambdas figées
    val pausedState by rememberUpdatedState(paused)
    val onDecodedState by rememberUpdatedState(onDecoded)

    // Ressources CameraX mémorisées
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }

    // L’analyseur lit `pausedState` à chaque frame : pas besoin de rebinder quand paused change
    val analyzer = remember {
        ImageAnalysis.Analyzer { imageProxy: ImageProxy ->
            val media = imageProxy.image
            if (media == null) {
                imageProxy.close(); return@Analyzer
            }

            // Si en pause, on ignore juste cette frame
            if (pausedState) {
                imageProxy.close(); return@Analyzer
            }

            val image = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val txt = barcodes.firstOrNull()?.rawValue
                    if (!txt.isNullOrBlank() && !pausedState) {
                        onDecodedState(txt)
                    }
                }
                .addOnFailureListener {
                    // Ignorer silencieusement
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    // Bind CameraX une seule fois (ou quand le lifecycle change)
    DisposableEffect(lifecycleOwner) {
        val provider = cameraProviderFuture.get()

        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(previewView.surfaceProvider)
        }

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().apply {
                setAnalyzer(executor, analyzer)
            }

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

/* ---------------- Générateur ---------------- */

@Composable
private fun QrGeneratorView(modifier: Modifier = Modifier) {
    var content by remember { mutableStateOf("harpochat:hello") }
    val bitmap = remember(content) {
        try {
            val enc = BarcodeEncoder()
            enc.encodeBitmap(content, BarcodeFormat.QR_CODE, 720, 720)
        } catch (_: Exception) {
            null
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("Contenu du QR") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        if (bitmap != null) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(240.dp))
        } else {
            Text("Impossible de générer le QR", color = AppColors.Muted)
        }
    }
}

@androidx.camera.core.ExperimentalGetImage
private class QrAnalyzer(
    private val onQr: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                // Récupère le premier QR trouvé (ou adapte si tu veux plusieurs)
                val first = barcodes.firstOrNull { it.valueType == Barcode.TYPE_TEXT || it.rawValue != null }
                first?.rawValue?.let(onQr)
            }
            .addOnFailureListener {
                // rien de spécial, on ignore simplement
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}