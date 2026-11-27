// SPDX-License-Identifier: MIT
package app.harpochat.messaging

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.harpochat.data.EphemeralChoice
import app.harpochat.data.NetworkStatus
import app.harpochat.data.SENDER_ME
import app.harpochat.data.ThreadSummary
import app.harpochat.security.ProtectionLevel
import app.harpochat.security.Session
import app.harpochat.ui.Avatar
import app.harpochat.ui.Chip
import app.harpochat.ui.IconAction
import app.harpochat.ui.NoKeyboardLearning
import app.harpochat.ui.theme.HarpoIcons
import app.harpochat.ui.theme.Palette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ConversationsScreen(
    session: Session,
    protection: ProtectionLevel,
    onOpenThread: (String) -> Unit,
    onAddContact: () -> Unit,
    onOpenSettings: () -> Unit,
    onLock: () -> Unit
) {
    val threads by session.repo.summaries().collectAsStateWithLifecycle(initialValue = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var showNetwork by remember { mutableStateOf(false) }
    val net by session.mesh.status.collectAsStateWithLifecycle()
    val shown = remember(threads, query) {
        if (query.isBlank()) threads
        else threads.filter { it.title.contains(query, true) || it.lastBody.orEmpty().contains(query, true) }
    }

    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item(key = "header") {
                Header(query, { query = it }, net, onLock, onOpenSettings, onNetwork = { showNetwork = true })
            }
            if (threads.isEmpty()) {
                item(key = "empty") { EmptyState() }
            } else {
                items(shown, key = { it.id }) { t -> ThreadRow(t) { onOpenThread(t.id) } }
                item(key = "consent") {
                    Text(
                        "Personne ne peut vous écrire sans que vous l'ayez accepté.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Faint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 20.dp)
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAddContact,
            containerColor = Palette.Mint,
            contentColor = Palette.OnMint,
            shape = RoundedCornerShape(28.dp),
            icon = { Icon(HarpoIcons.Plus, null, Modifier.size(20.dp)) },
            text = { Text("Ajouter un contact", style = MaterialTheme.typography.labelLarge) },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp)
        )
    }

    if (showNetwork) {
        NetworkSheet(session, protection, onDismiss = { showNetwork = false })
    }
}

@Composable
private fun Header(
    query: String,
    onQuery: (String) -> Unit,
    net: NetworkStatus,
    onLock: () -> Unit,
    onSettings: () -> Unit,
    onNetwork: () -> Unit
) {
    Column(
        Modifier.statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Conversations", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconAction(HarpoIcons.Lock, "Verrouiller", onLock)
            IconAction(HarpoIcons.Settings, "Réglages", onSettings)
        }
        // Indicateur d'état : le détail (trajet, relais, empreinte) s'ouvre d'un geste.
        Row(
            Modifier
                .clickable(role = Role.Button, onClickLabel = "Voir le détail du chiffrement", onClick = onNetwork)
                .padding(vertical = 6.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val (label, tint) = networkSummary(net)
            Box(Modifier.size(7.dp).background(tint, CircleShape))
            Text(label, style = MaterialTheme.typography.bodySmall, color = tint)
            Icon(HarpoIcons.ChevronRight, null, tint = tint, modifier = Modifier.size(14.dp))
        }
        NoKeyboardLearning {
            Row(
                Modifier.padding(end = 8.dp).fillMaxWidth().height(44.dp).background(Palette.Surface, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(HarpoIcons.Search, null, tint = Palette.Faint, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Rechercher", style = MaterialTheme.typography.bodyMedium, color = Palette.Faint)
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.Text),
                        cursorBrush = SolidColor(Palette.Mint),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreadRow(t: ThreadSummary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Avatar(t.title, highlighted = t.verified)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (t.verified) Icon(HarpoIcons.ShieldCheck, "Contact vérifié", tint = Palette.Mint, modifier = Modifier.size(15.dp))
            }
            val preview = when {
                t.lastBody == null -> "Nouvelle conversation"
                t.lastSender == SENDER_ME -> "Vous : ${t.lastBody}"
                else -> t.lastBody
            }
            Text(
                preview,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                color = if (t.unread > 0) Palette.TextSoft else Palette.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(shortTime(t.lastTime ?: t.createdAt), style = MaterialTheme.typography.labelSmall, color = Palette.Faint)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!t.verified) Chip("À vérifier", fg = Palette.Amber, bg = Color(0xFF2A2116))
                if (t.ephemeralSeconds > 0) Chip(EphemeralChoice.of(t.ephemeralSeconds).short)
                if (t.unread > 0) {
                    Box(
                        Modifier.widthIn(min = 20.dp).height(20.dp).background(Palette.Mint, RoundedCornerShape(10.dp)).padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${t.unread}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Palette.OnMint)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(64.dp).background(Palette.MintAvatar, CircleShape), contentAlignment = Alignment.Center) {
            Icon(HarpoIcons.ShieldCheck, null, tint = Palette.Mint, modifier = Modifier.size(28.dp))
        }
        Text("Aucune conversation", style = MaterialTheme.typography.titleMedium)
        Text(
            "Ajoutez un contact en personne, avec un QR code. Personne ne peut vous écrire sans votre accord.",
            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
    }
}

private val hourFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dayFmt = DateTimeFormatter.ofPattern("EEE", Locale.FRENCH)
private val dateFmt = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)

/** 19:36 aujourd'hui, « Hier », « lun. » cette semaine, « 12 nov. » au-delà. */
internal fun shortTime(epochMs: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = LocalDate.now(zone)
    return when (val d = date.toLocalDate()) {
        today -> date.format(hourFmt)
        today.minusDays(1) -> "Hier"
        else -> if (d.isAfter(today.minusDays(7))) date.format(dayFmt).replaceFirstChar { it.uppercase() } else date.format(dateFmt)
    }
}

/** Résumé d'une ligne de l'état du réseau, et sa couleur. */
internal fun networkSummary(net: NetworkStatus): Pair<String, Color> = when (net.phase) {
    NetworkStatus.Phase.ONLINE -> when {
        net.totalRelays == 0 -> "Connecté · annuaire en cours" to Palette.Mint
        net.anonymous -> "Réseau anonyme · ${net.activeRelays} relais" to Palette.Mint
        else -> "Réseau de test · ${net.totalRelays} nœud${if (net.totalRelays > 1) "s" else ""}" to Palette.Amber
    }
    NetworkStatus.Phase.CONNECTING -> "Connexion au réseau…" to Palette.Muted
    NetworkStatus.Phase.OFFLINE -> "Hors ligne · nouvel essai automatique" to Palette.Amber
    NetworkStatus.Phase.STOPPED -> "Réseau arrêté" to Palette.Muted
}
