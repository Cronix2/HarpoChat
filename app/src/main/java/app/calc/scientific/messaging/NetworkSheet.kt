// SPDX-License-Identifier: MIT
package app.calc.scientific.messaging

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.calc.scientific.data.NetworkStatus
import app.calc.scientific.security.ProtectionLevel
import app.calc.scientific.security.Session
import app.calc.scientific.ui.SecondaryButton
import app.calc.scientific.ui.theme.LineIcons
import app.calc.scientific.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Panneau « Chiffrement et réseau » : ce que l'indicateur d'état résume, en détail.
 * Il s'ouvre à la demande (l'écran principal reste calme).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSheet(session: Session, protection: ProtectionLevel, onDismiss: () -> Unit) {
    val fingerprint by produceState("…") { value = withContext(Dispatchers.IO) { session.repo.fingerprint() } }
    val status by session.mesh.status.collectAsStateWithLifecycle()
    val online = status.phase == NetworkStatus.Phase.ONLINE
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Sheet,
        dragHandle = { Box(Modifier.padding(top = 12.dp).width(40.dp).height(4.dp).background(Color(0xFF2E343C), RoundedCornerShape(2.dp))) }
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Chiffrement et réseau", style = MaterialTheme.typography.headlineSmall)
                Text("Le trajet suivi par chacun de vos messages.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            }

            Route(listOf("Vous") + status.hops.ifEmpty { listOf("Réseau") } + listOf("Contact"))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat(
                        "Relais actifs",
                        if (online && status.totalRelays > 0) "${status.activeRelays} sur ${status.totalRelays}" else "—",
                        Modifier.weight(1f), warn = online && status.totalRelays > 0 && !status.anonymous
                    )
                    Stat(
                        "Aller-retour mesuré",
                        status.loopRttMs?.let { "%.1f s".format(it / 1000f).replace('.', ',') } ?: if (online) "Mesure…" else "—",
                        Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Trafic leurre", if (online && status.coverTraffic) "Actif" else "Inactif", Modifier.weight(1f), accent = online && status.coverTraffic)
                    Stat("Clé protégée par", protection.short, Modifier.weight(1f), accent = protection.hardware, warn = !protection.hardware)
                }
                if (online) {
                    Text(
                        buildString {
                            append("Entrée ").append(status.gateway ?: "—")
                            append(" · leurres revenus ").append(status.loopsReturned).append(" sur ").append(status.loopsSent)
                            if (status.loopsLost > 0) append(" · ").append(status.loopsLost).append(" perdu").append(if (status.loopsLost > 1) "s" else "")
                        },
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Palette.Faint
                    )
                }
            }

            Column(
                Modifier.fillMaxWidth().background(Palette.MintPanel, RoundedCornerShape(14.dp))
                    .border(1.dp, Palette.MintPanelLine, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Empreinte de votre clé, à comparer avec vos contacts", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                Text(fingerprint, fontFamily = FontFamily.Monospace, fontSize = 18.sp, letterSpacing = 1.sp, color = Palette.MintText)
            }

            Text(
                "Les serveurs ne voient que des paquets chiffrés de taille identique, mêlés à du trafic leurre : ni le contenu, ni l'expéditeur, ni le destinataire.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted
            )
            when {
                status.phase == NetworkStatus.Phase.OFFLINE ->
                    Notice("Hors ligne : ${status.error ?: "nœud injoignable"}. Nouvel essai automatique.", Palette.Amber)
                status.phase == NetworkStatus.Phase.CONNECTING ->
                    Notice("Connexion au réseau…" + (status.error?.let { " Dernier échec : $it." } ?: ""), Palette.Muted)
                online && status.totalRelays > 0 && !status.anonymous ->
                    Notice("Réseau de test : moins de 3 nœuds. Sans relais intermédiaire, le nœud d'entrée sait à quelle boîte vous écrivez.", Palette.Amber)
            }
            Notice("Les messages passeront par ce réseau à la prochaine étape. Pour l'instant, le lien mesure le trajet et émet le trafic leurre.", Palette.Muted)
            SecondaryButton("Fermer", onDismiss, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Notice(text: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(LineIcons.Info, null, tint = color, modifier = Modifier.padding(top = 1.dp).size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun Route(labels: List<String>) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        labels.forEachIndexed { i, label ->
            val end = i == 0 || i == labels.lastIndex
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.width(56.dp)) {
                Box(
                    Modifier.size(34.dp)
                        .background(if (end) Palette.Mint else Palette.Surface, CircleShape)
                        .border(1.dp, if (end) Palette.Mint else Palette.MintTrack, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (end) label.first().toString() else "$i",
                        fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        color = if (end) Palette.OnMint else Palette.Mint
                    )
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, textAlign = TextAlign.Center)
            }
            if (i < labels.lastIndex) {
                Box(Modifier.weight(1f).padding(top = 16.dp).height(2.dp).background(Palette.MintTrack))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, accent: Boolean = false, warn: Boolean = false) {
    Column(
        modifier.background(Color(0xFF181C22), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = when { warn -> Palette.Amber; accent -> Palette.Mint; else -> Palette.Text })
    }
}
