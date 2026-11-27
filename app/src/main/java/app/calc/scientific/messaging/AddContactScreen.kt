// SPDX-License-Identifier: MIT
package app.calc.scientific.messaging

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calc.scientific.data.ChatRepository
import app.calc.scientific.link.Invite
import app.calc.scientific.link.InviteCodec
import app.calc.scientific.security.AppLock
import app.calc.scientific.security.Session
import app.calc.scientific.ui.CountdownRing
import app.calc.scientific.ui.MintPanel
import app.calc.scientific.ui.PrimaryButton
import app.calc.scientific.ui.ScreenHeader
import app.calc.scientific.ui.SecondaryButton
import app.calc.scientific.ui.theme.LineIcons
import app.calc.scientific.ui.theme.Palette
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
import java.util.UUID

/** Relais transitoire : remplacé par l'amorçage du mesh (`harpochat-core`). */
private const val DEFAULT_RELAY_URL = "wss://relay.invalid/v1/ws"
private const val QR_PERIOD_SEC = 60

/** Code de contrôle lisible à voix haute : 10 caractères en deux groupes. */
private fun spoken(code: String) = code.uppercase().chunked(5).joinToString(" ")

@Composable
fun AddContactScreen(session: Session, appLock: AppLock, onBack: () -> Unit, onOpenThread: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<Invite?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current

    fun handleScan(raw: String) {
        InviteCodec.decode(raw)
            .onSuccess { inv ->
                info = when {
                    inv.isExpired() -> "Ce code a expiré. Demandez à votre contact d'afficher un nouveau code."
                    !InviteCodec.verify(inv) -> "Signature invalide : ce code a été modifié ou falsifié."
                    else -> { pending = inv; null }
                }
            }
            .onFailure { info = "Ce QR code n'est pas une invitation." }
    }

    fun startScan() {
        info = null
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        // Le scanner du système s'ouvre dans un autre écran : ne pas verrouiller pendant ce temps.
        appLock.expectExternalActivity(true)
        GmsBarcodeScanning.getClient(ctx, options).startScan()
            .addOnSuccessListener { code -> code.rawValue?.let(::handleScan) }
            .addOnFailureListener { e ->
                // Fermer le scanner remonte CANCELLED ou INTERNAL selon la version : pas une erreur.
                val code = (e as? MlKitException)?.errorCode
                if (code == MlKitException.UNAVAILABLE || code == MlKitException.CODE_SCANNER_UNAVAILABLE ||
                    code == MlKitException.CODE_SCANNER_GOOGLE_PLAY_SERVICES_VERSION_TOO_OLD
                ) info = "Scanner indisponible sur cet appareil (services Google requis)."
            }
            .addOnCompleteListener { appLock.expectExternalActivity(false) }
    }

    Column(Modifier.fillMaxSize().background(Palette.Ink)) {
        ScreenHeader("Ajouter un contact", onBack)
        Tabs(tab) { tab = it }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            when (tab) {
                0 -> MyCode(session.repo)
                1 -> ScanTab(info, ::startScan)
                else -> FileTab()
            }
        }
    }

    pending?.let { inv ->
        ConfirmSheet(
            invite = inv,
            onCancel = { pending = null },
            onAccept = {
                pending = null
                session.scope.launch {
                    session.repo.createThread(inv.threadId, inv.displayName, verified = true)
                    withContext(Dispatchers.Main) { onOpenThread(inv.threadId) }
                }
            }
        )
    }
}

@Composable
private fun Tabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(Palette.Surface, RoundedCornerShape(14.dp)).padding(4.dp)
    ) {
        listOf("Mon code", "Scanner", "Fichier").forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(40.dp)
                    .background(if (on) Color(0xFF233029) else Color.Transparent, RoundedCornerShape(10.dp))
                    .selectable(selected = on, role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (on) Palette.MintText else Palette.Muted
                )
            }
        }
    }
}

@Composable
private fun MyCode(repo: ChatRepository) {
    val identity by produceState<app.calc.scientific.link.InviteIdentity?>(null) { value = withContext(Dispatchers.IO) { repo.identity() } }
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

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.background(Color(0xFFF4F6F5), RoundedCornerShape(22.dp)).padding(14.dp), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), contentDescription = "Mon QR code d'invitation", modifier = Modifier.size(232.dp))
            } else {
                Box(Modifier.size(232.dp))
            }
        }
        CountdownRing(QR_PERIOD_SEC, cycleStart, onCycle = {
            threadId = UUID.randomUUID().toString()
            cycleStart = System.currentTimeMillis()
        })
    }
    invite?.let {
        Column(
            Modifier.fillMaxWidth().background(Palette.Surface, RoundedCornerShape(14.dp)).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Code de contrôle, à lire à voix haute", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            Text(spoken(it.code), fontFamily = FontFamily.Monospace, fontSize = 22.sp, letterSpacing = 2.sp, color = Palette.MintText)
        }
    }
    Text(
        "Montrez ce code à votre contact, en personne. Rien n'est échangé tant que vous n'avez pas tous les deux accepté.",
        style = MaterialTheme.typography.bodySmall, color = Palette.Faint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ScanTab(info: String?, onScan: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(88.dp).background(Palette.MintAvatar, CircleShape), contentAlignment = Alignment.Center) {
            Icon(LineIcons.Scan, null, tint = Palette.Mint, modifier = Modifier.size(38.dp))
        }
    }
    Text("Scannez le code de votre contact", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Text(
        "Le scan est réalisé par le système : l'application n'a jamais accès à votre caméra.",
        style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
    )
    if (info != null) {
        Row(
            Modifier.fillMaxWidth().background(Palette.AmberBg, RoundedCornerShape(12.dp)).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(LineIcons.Warning, null, tint = Palette.Amber, modifier = Modifier.size(16.dp))
            Text(info, style = MaterialTheme.typography.bodySmall, color = Palette.Amber)
        }
    }
    PrimaryButton("Scanner un code", onScan, Modifier.fillMaxWidth(), icon = LineIcons.Scan)
}

@Composable
private fun FileTab() {
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(88.dp).background(Palette.Surface, CircleShape), contentAlignment = Alignment.Center) {
            Icon(LineIcons.File, null, tint = Palette.Muted, modifier = Modifier.size(36.dp))
        }
    }
    Text("Invitation par fichier", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Text(
        "Pour un contact éloigné : une invitation chiffrée, envoyée par une autre messagerie puis importée ici. L'import se fera depuis l'application, jamais depuis le menu « Ouvrir avec » qui trahirait sa présence.",
        style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
    )
    MintPanel {
        Text("Disponible avec le réseau", style = MaterialTheme.typography.titleSmall, color = Palette.Mint)
        Text("En attendant, ajoutez vos contacts en personne avec le QR code.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmSheet(invite: Invite, onCancel: () -> Unit, onAccept: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Sheet
    ) {
        Column(Modifier.navigationBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(LineIcons.ShieldCheck, null, tint = Palette.Mint, modifier = Modifier.size(18.dp))
                Text("Signature vérifiée", style = MaterialTheme.typography.labelLarge, color = Palette.Mint)
            }
            Text("Ajouter ${invite.displayName} ?", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Vérifiez que ce code de contrôle s'affiche aussi sur le téléphone de ${invite.displayName}.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.Muted
            )
            Box(Modifier.fillMaxWidth().background(Palette.Surface, RoundedCornerShape(14.dp)).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Text(spoken(invite.code), fontFamily = FontFamily.Monospace, fontSize = 26.sp, letterSpacing = 2.sp, color = Palette.MintText)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Non, annuler", onCancel, Modifier.weight(1f))
                PrimaryButton("Oui, ajouter", onAccept, Modifier.weight(1f))
            }
        }
    }
}

/** QR en noir sur blanc avec ZXing core (sans la bibliothèque qui exigeait la caméra). */
private fun renderQr(text: String, size: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) AndroidColor.BLACK else AndroidColor.WHITE }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}.getOrNull()
