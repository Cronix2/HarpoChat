// SPDX-License-Identifier: MIT
package app.harpochat.messaging

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.harpochat.data.ChatRepository
import app.harpochat.link.Invite
import app.harpochat.link.InviteCodec
import app.harpochat.link.InviteIdentity
import app.harpochat.security.AppLock
import app.harpochat.security.Session
import app.harpochat.ui.CountdownRing
import app.harpochat.ui.theme.AppColors
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Base64
import java.util.UUID

/** Relais transitoire : remplacé par l'amorçage du mesh (`harpochat-core`). */
private const val DEFAULT_RELAY_URL = "wss://relay.invalid/v1/ws"
private const val QR_PERIOD_SEC = 60

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScreen(session: Session, appLock: AppLock, onBack: () -> Unit, onOpenThread: (String) -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<Invite?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val repo = session.repo

    fun handleScan(raw: String) {
        InviteCodec.decode(raw)
            .onSuccess { inv ->
                info = when {
                    inv.isExpired() -> "Ce code a expiré. Demandez à votre contact d'en afficher un nouveau."
                    !InviteCodec.verify(inv) -> "Signature invalide : ce code a été modifié ou falsifié."
                    else -> { pending = inv; null }
                }
            }
            .onFailure { info = "Ce QR code n'est pas une invitation valide." }
    }

    fun startScan() {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        // Le scanner Google s'ouvre dans un autre écran : ne pas verrouiller pendant ce temps.
        appLock.expectExternalActivity(true)
        GmsBarcodeScanning.getClient(ctx, options).startScan()
            .addOnSuccessListener { code -> code.rawValue?.let(::handleScan) }
            .addOnFailureListener { e ->
                // Fermer le scanner remonte CANCELLED (ou INTERNAL selon la version des services
                // Google) : ce n'est pas une erreur. On ne prévient que si le service manque vraiment.
                val code = (e as? MlKitException)?.errorCode
                if (code == MlKitException.UNAVAILABLE || code == MlKitException.CODE_SCANNER_UNAVAILABLE ||
                    code == MlKitException.CODE_SCANNER_GOOGLE_PLAY_SERVICES_VERSION_TOO_OLD
                ) {
                    info = "Scanner indisponible sur cet appareil (services Google requis)."
                }
            }
            .addOnCompleteListener { appLock.expectExternalActivity(false) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajouter par QR code") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SecondaryTabRow(selectedTabIndex = selected, containerColor = AppColors.Sheet) {
                listOf("Scanner", "Mon code").forEachIndexed { i, label ->
                    Tab(selected = selected == i, onClick = { selected = i }, text = { Text(label) })
                }
            }
            when (selected) {
                0 -> ScanTab(onScan = ::startScan)
                else -> MyCodeTab(repo)
            }
        }
    }

    info?.let { msg ->
        AlertDialog(
            onDismissRequest = { info = null },
            confirmButton = { TextButton(onClick = { info = null }) { Text("OK") } },
            text = { Text(msg) }
        )
    }

    pending?.let { inv ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Ajouter ${inv.displayName} ?") },
            text = {
                Text(
                    "Signature vérifiée. Vérifiez avec votre contact que ce code de contrôle s'affiche aussi chez lui :\n\n" +
                        inv.code.chunked(5).joinToString(" ")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    session.scope.launch {
                        repo.createThread(inv.threadId, inv.displayName)
                        withContext(Dispatchers.Main) { onOpenThread(inv.threadId) }
                    }
                }) { Text("Ajouter") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun ScanTab(onScan: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(72.dp), tint = AppColors.Muted)
        Spacer(Modifier.height(16.dp))
        Text(
            "Scannez le code affiché par votre contact, en personne.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Le scan est fait par le système : l'application n'a jamais accès à la caméra.",
            textAlign = TextAlign.Center,
            color = AppColors.Muted
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Scanner un code") }
    }
}

@Composable
private fun MyCodeTab(repo: ChatRepository) {
    // Identité Ed25519 stable, conservée dans la base chiffrée (transitoire jusqu'à harpochat-core).
    val identity by produceState<InviteIdentity?>(null) {
        value = withContext(Dispatchers.IO) {
            repo.getSetting(ChatRepository.KEY_IDENTITY_SEED)
                ?.let { InviteIdentity.fromSeed(Base64.getDecoder().decode(it)) }
                ?: InviteIdentity.generate().also {
                    repo.putSetting(ChatRepository.KEY_IDENTITY_SEED, Base64.getEncoder().encodeToString(it.privateSeed))
                }
        }
    }
    val displayName by produceState("Contact") {
        value = withContext(Dispatchers.IO) { repo.getSetting(ChatRepository.KEY_DISPLAY_NAME) } ?: "Contact"
    }
    var threadId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var cycleStart by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var invite by remember { mutableStateOf<Invite?>(null) }

    LaunchedEffect(identity, displayName, threadId, cycleStart) {
        val id = identity ?: return@LaunchedEffect
        invite = withContext(Dispatchers.Default) {
            InviteCodec.create(id.publicKey, id.privateSeed, displayName, threadId, DEFAULT_RELAY_URL, QR_PERIOD_SEC.toLong())
        }
    }
    val bitmap = remember(invite) { invite?.let { renderQr(InviteCodec.encode(it), 720) } }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CountdownRing(QR_PERIOD_SEC, cycleStart, onCycle = { cycleStart = System.currentTimeMillis() }, Modifier.size(56.dp))
        Box(
            Modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), contentDescription = "Mon code d'invitation", Modifier.size(240.dp))
            } else {
                Box(Modifier.size(240.dp))
            }
        }
        invite?.let {
            Text("Code de contrôle : ${it.code.chunked(5).joinToString(" ")}", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "Montrez ce code à votre contact. Il change toutes les minutes et ne sert qu'une fois.",
            textAlign = TextAlign.Center,
            color = AppColors.Muted
        )
        OutlinedButton(onClick = {
            threadId = UUID.randomUUID().toString()
            cycleStart = System.currentTimeMillis()
        }) { Text("Nouveau code") }
    }
}

/** QR en noir sur blanc avec ZXing core (sans la bibliothèque qui exigeait la caméra). */
private fun renderQr(text: String, size: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i ->
        if (matrix[i % size, i / size]) AndroidColor.BLACK else AndroidColor.WHITE
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}.getOrNull()
