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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrScreen(onBack: () -> Unit) {
    val tabs = listOf("Scanner", "Générer")
    var selected by remember { mutableIntStateOf(0) }

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
                    0 -> QrScannerView(Modifier.weight(1f))
                    1 -> QrGeneratorView(Modifier.weight(1f))
                }
            }
        }
    }
}

/* ---------------- Scanner ---------------- */

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun QrScannerView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastDecoded by remember { mutableStateOf<String?>(null) }

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

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        val provider = cameraProviderFuture.get()

        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(previewView.surfaceProvider)
        }

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().apply {
                setAnalyzer(executor, QrAnalyzer { qrText ->
                    lastDecoded = qrText
                })
            }

        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
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
        )
        Surface(modifier = Modifier.fillMaxWidth(), color = AppColors.Sheet) {
            Text(
                text = lastDecoded ?: "Visez un QR code…",
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