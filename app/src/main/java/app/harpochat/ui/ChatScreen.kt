// SPDX-License-Identifier: MIT
package app.harpochat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.harpochat.data.EphemeralChoice
import app.harpochat.ui.theme.HarpoIcons
import app.harpochat.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ---------- Modèle ---------- */

enum class MessageStatus(val code: Int, val label: String) {
    SENDING(0, "envoi…"), SENT(1, "envoyé"), DELIVERED(2, "remis"), READ(3, "lu");

    companion object {
        fun fromCode(code: Int) = entries.firstOrNull { it.code == code } ?: SENT
    }
}

data class ChatMsg(
    val id: String,
    val text: String,
    /** Epoch millis. */
    val time: Long,
    val isMine: Boolean,
    val status: MessageStatus = MessageStatus.SENT,
    /** Date de destruction si le fil est éphémère. */
    val expiresAt: Long? = null
)

/* ---------- Écran ---------- */

@Composable
fun ChatScreen(
    title: String,
    verified: Boolean,
    ephemeral: EphemeralChoice,
    messages: List<ChatMsg>,
    onSend: (String) -> Unit,
    onEphemeralChange: (EphemeralChoice) -> Unit,
    onBack: () -> Unit
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var showAttach by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }

    NoKeyboardLearning {
        Column(Modifier.fillMaxSize().background(Palette.Ink).imePadding()) {
            ChatHeader(title, verified, ephemeral, onEphemeralChange, onBack)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
            ) {
                item(key = "trust") { TrustLine(verified) }
                items(messages, key = { it.id }) { m -> Bubble(m) }
            }
            Composer(
                draft = draft,
                onDraft = { draft = it },
                onSend = { onSend(draft.trim()); draft = "" },
                onAttach = { showAttach = true }
            )
        }
    }
    if (showAttach) AttachSheet(onDismiss = { showAttach = false })
}

@Composable
private fun ChatHeader(
    title: String,
    verified: Boolean,
    ephemeral: EphemeralChoice,
    onEphemeralChange: (EphemeralChoice) -> Unit,
    onBack: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier.statusBarsPadding().fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconAction(HarpoIcons.Back, "Retour", onBack)
            Avatar(title, size = 40.dp, highlighted = verified)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    if (verified) Icon(HarpoIcons.ShieldCheck, "Contact vérifié", tint = Palette.Mint, modifier = Modifier.size(15.dp))
                }
                Text(
                    if (ephemeral == EphemeralChoice.OFF) "Messages conservés" else "Messages éphémères · ${ephemeral.short}",
                    style = MaterialTheme.typography.labelSmall, color = Palette.Muted
                )
            }
            Box {
                Row(
                    Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(role = Role.Button, onClickLabel = "Changer la durée des messages") { menu = true }
                        .padding(vertical = 4.dp)
                        .background(Palette.MintPanel, RoundedCornerShape(18.dp))
                        .border(1.dp, Palette.MintPanelLine, RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(HarpoIcons.Timer, null, tint = Palette.Mint, modifier = Modifier.size(15.dp))
                    Text(ephemeral.short, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = Palette.Mint)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Palette.Sheet) {
                    Text(
                        "Durée de vie des messages",
                        style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    EphemeralChoice.entries.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c.label) },
                            trailingIcon = { if (c == ephemeral) Icon(HarpoIcons.Check, null, tint = Palette.Mint, modifier = Modifier.size(18.dp)) },
                            onClick = { menu = false; onEphemeralChange(c) }
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = Palette.Line)
    }
}

@Composable
private fun TrustLine(verified: Boolean) {
    Box(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.Center) {
        if (verified) {
            Text("Contact vérifié en personne · chiffré de bout en bout", style = MaterialTheme.typography.labelSmall, color = Palette.Faint)
        } else {
            Row(
                Modifier.background(Palette.AmberBg, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(HarpoIcons.Warning, null, tint = Palette.Amber, modifier = Modifier.size(16.dp))
                Text(
                    "Contact non vérifié : comparez le code de contrôle en personne.",
                    style = MaterialTheme.typography.labelSmall, color = Palette.Amber
                )
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMsg) {
    val shape = if (m.isMine) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.isMine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = 290.dp)
                .background(if (m.isMine) Palette.MintBubble else Palette.Surface, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(m.text, style = MaterialTheme.typography.bodyMedium, color = if (m.isMine) Palette.MintText.copy(alpha = 0.95f) else Palette.Text)
            Text(
                meta(m),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.92f),
                color = if (m.isMine) Palette.MintBubbleMeta else Palette.Faint,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

private fun meta(m: ChatMsg): String {
    val parts = mutableListOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(m.time)))
    if (m.isMine) parts += m.status.label
    m.expiresAt?.let { parts += "disparaît dans ${remaining(it - System.currentTimeMillis())}" }
    return parts.joinToString(" · ")
}

private fun remaining(ms: Long): String {
    val min = (ms / 60_000).coerceAtLeast(1)
    return when {
        min < 60 -> "$min min"
        min < 48 * 60 -> "${min / 60} h"
        else -> "${min / (24 * 60)} j"
    }
}

@Composable
private fun Composer(draft: String, onDraft: (String) -> Unit, onSend: () -> Unit, onAttach: () -> Unit) {
    Column(Modifier.navigationBarsPadding()) {
        HorizontalDivider(color = Palette.Line)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RoundAction(HarpoIcons.Plus, "Joindre une photo, un message vocal ou un fichier", onAttach)
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).background(Palette.Surface, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (draft.isEmpty()) Text("Message", style = MaterialTheme.typography.bodyMedium, color = Palette.Faint)
                BasicTextField(
                    value = draft,
                    onValueChange = onDraft,
                    maxLines = 5,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.Text),
                    cursorBrush = SolidColor(Palette.Mint),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (draft.isBlank()) {
                RoundAction(HarpoIcons.Mic, "Message vocal", onAttach, filled = true)
            } else {
                RoundAction(HarpoIcons.Send, "Envoyer", onSend, filled = true)
            }
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, description: String, onClick: () -> Unit, filled: Boolean = false) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).background(if (filled) Palette.Mint else Palette.Surface, CircleShape)
    ) {
        Icon(icon, description, tint = if (filled) Palette.OnMint else Palette.Text, modifier = Modifier.size(20.dp))
    }
}

/** Pièces jointes : annoncées, actives avec le lot « médias » (masquage, voix transformée). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Sheet) {
        Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Joindre", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            AttachRow(HarpoIcons.Image, "Photo ou vidéo", "Visages masqués et position retirée avant l'envoi")
            AttachRow(HarpoIcons.Mic, "Message vocal", "Voix transformée, impossible à reconnaître")
            AttachRow(HarpoIcons.File, "Fichier", "Métadonnées retirées")
            Text(
                "Ces envois arrivent avec la prochaine version.",
                style = MaterialTheme.typography.bodySmall, color = Palette.Faint, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun AttachRow(icon: ImageVector, title: String, hint: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).background(Palette.Surface, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Palette.Mint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        Chip("Bientôt", fg = Palette.Muted, bg = Palette.Surface)
    }
}
