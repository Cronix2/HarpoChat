// SPDX-License-Identifier: MIT
package app.calc.scientific.messaging

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.PersistableBundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.calc.scientific.data.InRequestEntity
import app.calc.scientific.data.OutRequestEntity
import app.calc.scientific.e2e.Pairing
import app.calc.scientific.security.AppLock
import app.calc.scientific.security.Session
import app.calc.scientific.ui.GroupLabel
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Ajouter un contact, avec son consentement explicite (voir [Pairing]) :
 * - « Mon code » : j'affiche une invitation ; les demandes reçues s'affichent avec leur code de contrôle.
 * - « Scanner » : j'importe l'invitation d'un contact en personne ; ma demande part.
 * - « À distance » : même chose, l'invitation passant par une autre messagerie.
 */
@Composable
fun AddContactScreen(session: Session, appLock: AppLock, onBack: () -> Unit, onOpenThread: (String) -> Unit) {
    val post = session.post
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var info by remember { mutableStateOf<String?>(null) }
    var reviewing by remember { mutableStateOf<InRequestEntity?>(null) }
    val incoming by post.incomingRequests.collectAsStateWithLifecycle(initialValue = emptyList())
    val outgoing by post.outgoingRequests.collectAsStateWithLifecycle(initialValue = emptyList())
    val ctx = LocalContext.current
    val ui = rememberCoroutineScope()

    // Mon contact a accepté ma demande pendant que j'étais sur cet écran : on ouvre la conversation.
    LaunchedEffect(Unit) { post.accepted.collect { onOpenThread(it) } }

    fun import(text: String, inPerson: Boolean) {
        info = null
        ui.launch {
            when (val r = post.importInvite(text, inPerson)) {
                is PostOffice.ImportResult.Error -> info = r.message
                is PostOffice.ImportResult.Sent -> Unit // la demande s'affiche dans la liste « envoyées »
            }
        }
    }

    fun startScan() {
        info = null
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        // Le scanner du système s'ouvre dans un autre écran : ne pas verrouiller pendant ce temps.
        appLock.expectExternalActivity(true)
        GmsBarcodeScanning.getClient(ctx, options).startScan()
            .addOnSuccessListener { code -> code.rawValue?.let { import(it, inPerson = true) } }
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
        Tabs(tab, incoming.size) { tab = it; info = null }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            when (tab) {
                0 -> MyCode(post)
                1 -> ScanTab(::startScan)
                else -> RemoteTab(post, appLock, onPaste = { text -> import(text, inPerson = false) })
            }
            info?.let { Notice(it) }
            if (incoming.isNotEmpty()) {
                GroupLabel("Demandes reçues")
                incoming.forEach { r -> RequestRow(r) { reviewing = r } }
            }
            if (tab != 0 && outgoing.isNotEmpty()) {
                GroupLabel("Demandes envoyées")
                outgoing.forEach { r -> SentRow(r) { ui.launch { post.cancelRequest(r.id) } } }
            }
        }
    }

    reviewing?.let { r ->
        ReviewSheet(
            request = r,
            onDismiss = { reviewing = null },
            onRefuse = { reviewing = null; ui.launch { post.refuse(r.id) } },
            onAccept = {
                reviewing = null
                ui.launch {
                    val thread = post.accept(r.id)
                    if (thread != null) onOpenThread(thread) else info = "Cette invitation a expiré : créez-en une nouvelle."
                }
            }
        )
    }
}

@Composable
private fun Tabs(selected: Int, incoming: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().background(Palette.Surface, RoundedCornerShape(14.dp)).padding(4.dp)
    ) {
        listOf(if (incoming > 0) "Mon code ($incoming)" else "Mon code", "Scanner", "À distance").forEachIndexed { i, label ->
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
private fun MyCode(post: PostOffice) {
    var generation by remember { mutableIntStateOf(0) }
    var invite by remember { mutableStateOf<Pairing.Invite?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(generation) { invite = post.qrInvite(forceNew = generation > 0) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
    val bitmap = remember(invite) { invite?.let { renderQr(Pairing.encode(it), 720) } }
    val left = invite?.let { (it.expiresAt - now).coerceAtLeast(0) } ?: 0
    val expired = invite != null && left == 0L

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.background(Color(0xFFF4F6F5), RoundedCornerShape(22.dp)).padding(14.dp), contentAlignment = Alignment.Center) {
            if (bitmap != null && !expired) {
                Image(bitmap.asImageBitmap(), contentDescription = "Mon QR code d'invitation", modifier = Modifier.size(232.dp))
            } else {
                Box(Modifier.size(232.dp))
            }
        }
        if (expired) {
            PrimaryButton("Nouveau code", { generation++ }, Modifier.fillMaxWidth())
        } else {
            Text(
                "Valable encore ${left / 60_000} min ${"%02d".format(left / 1000 % 60)} s · usage unique",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted
            )
        }
    }
    Text(
        "Montrez ce code à votre contact, en personne. Sa demande s'affichera ici avec un code de contrôle : comparez-le avec le sien avant d'accepter.",
        style = MaterialTheme.typography.bodySmall, color = Palette.Faint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ScanTab(onScan: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(88.dp).background(Palette.MintAvatar, CircleShape), contentAlignment = Alignment.Center) {
            Icon(LineIcons.Scan, null, tint = Palette.Mint, modifier = Modifier.size(38.dp))
        }
    }
    Text("Scannez le code de votre contact", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Text(
        "Le scan est réalisé par le système : l'application n'a jamais accès à votre caméra. Votre contact devra accepter votre demande.",
        style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
    )
    PrimaryButton("Scanner un code", onScan, Modifier.fillMaxWidth(), icon = LineIcons.Scan)
}

@Composable
private fun RemoteTab(post: PostOffice, appLock: AppLock, onPaste: (String) -> Unit) {
    val ctx = LocalContext.current
    val ui = rememberCoroutineScope()
    var shared by remember { mutableStateOf<String?>(null) }
    // Le partage ouvre une autre application : pas de verrouillage pendant ce temps, puis retour normal.
    val share = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        appLock.expectExternalActivity(false)
    }

    MintPanel {
        Text("Inviter quelqu'un à distance", style = MaterialTheme.typography.titleSmall, color = Palette.Mint)
        Text(
            "Une invitation valable 48 heures, à envoyer par une autre messagerie. Elle ne contient ni votre nom ni le nom de l'application. Le contact restera « à vérifier » tant que vous n'aurez pas comparé vos codes, par exemple au téléphone.",
            style = MaterialTheme.typography.bodySmall, color = Palette.Muted
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton("Partager", {
            ui.launch {
                val text = shared ?: Pairing.encode(post.remoteInvite()).also { shared = it }
                appLock.expectExternalActivity(true)
                share.launch(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
            }
        }, Modifier.weight(1f))
        SecondaryButton("Copier", {
            ui.launch {
                val text = shared ?: Pairing.encode(post.remoteInvite()).also { shared = it }
                copySensitive(ctx, text)
            }
        }, Modifier.weight(1f))
    }
    shared?.let {
        Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Palette.Faint, modifier = Modifier.fillMaxWidth())
    }

    Spacer(Modifier.height(4.dp))
    MintPanel {
        Text("J'ai reçu une invitation", style = MaterialTheme.typography.titleSmall, color = Palette.Mint)
        Text(
            "Copiez-la depuis la messagerie où vous l'avez reçue, puis collez-la ici. Votre demande partira à votre contact, qui devra l'accepter.",
            style = MaterialTheme.typography.bodySmall, color = Palette.Muted
        )
    }
    SecondaryButton("Coller l'invitation", {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.let(onPaste)
    }, Modifier.fillMaxWidth())
}

@Composable
private fun RequestRow(r: InRequestEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Palette.Surface, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(r.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text("Code de contrôle ${r.sas}", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Palette.MintText)
        }
        Text("Examiner", style = MaterialTheme.typography.labelLarge, color = Palette.Mint)
    }
}

@Composable
private fun SentRow(r: OutRequestEntity, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Palette.Surface, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("En attente d'acceptation", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text("Votre code de contrôle ${r.sas}", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Palette.MintText)
        }
        Text("Annuler", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, modifier = Modifier.clickable(onClick = onCancel).padding(6.dp))
    }
}

@Composable
private fun Notice(text: String) {
    Row(
        Modifier.fillMaxWidth().background(Palette.AmberBg, RoundedCornerShape(12.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(LineIcons.Warning, null, tint = Palette.Amber, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.Amber)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewSheet(request: InRequestEntity, onDismiss: () -> Unit, onRefuse: () -> Unit, onAccept: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Sheet
    ) {
        Column(Modifier.navigationBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Demande de ${request.name}", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Vérifiez que ce code s'affiche aussi sur le téléphone de ${request.name}. S'il diffère, refusez : quelqu'un d'autre a utilisé votre invitation.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.Muted
            )
            Box(Modifier.fillMaxWidth().background(Palette.Surface, RoundedCornerShape(14.dp)).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Text(request.sas, fontFamily = FontFamily.Monospace, fontSize = 30.sp, letterSpacing = 3.sp, color = Palette.MintText)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Refuser", onRefuse, Modifier.weight(1f))
                PrimaryButton("Codes identiques", onAccept, Modifier.weight(1f))
            }
        }
    }
}

/** Copie marquée sensible : Android 13+ n'en affiche pas l'aperçu. */
private fun copySensitive(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("", text)
    clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
    cm.setPrimaryClip(clip)
}

/** `ClipDescription.EXTRA_IS_SENSITIVE` (API 33), sans effet sur les versions antérieures. */
private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

/** QR en noir sur blanc avec ZXing core (sans la bibliothèque qui exigeait la caméra). */
private fun renderQr(text: String, size: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) AndroidColor.BLACK else AndroidColor.WHITE }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}.getOrNull()
