// SPDX-License-Identifier: MIT
package app.harpochat.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.harpochat.ui.theme.Palette

/**
 * Compte à rebours d'un code temporaire : anneau + libellé.
 * Appelle [onCycle] à chaque fin de période, pour régénérer le code.
 */
@Composable
fun CountdownRing(periodSec: Int, cycleStartMs: Long, onCycle: () -> Unit, modifier: Modifier = Modifier) {
    val periodMs = periodSec * 1000L
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(cycleStartMs, periodSec) {
        while (true) {
            withFrameMillis { }
            nowMs = System.currentTimeMillis()
            if (nowMs - cycleStartMs >= periodMs) {
                onCycle()
                break // cycleStartMs va changer et relancer l'effet
            }
        }
    }
    val remainingMs = (periodMs - (nowMs - cycleStartMs)).coerceIn(0L, periodMs)
    val secLeft = ((remainingMs + 999) / 1000).toInt()

    Row(
        modifier.semantics { contentDescription = "Nouveau code dans $secLeft secondes" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Canvas(Modifier.size(28.dp)) {
            val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arc = Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(Palette.SurfaceHigh, -90f, 360f, false, Offset(inset, inset), arc, style = stroke)
            drawArc(Palette.Mint, -90f, 360f * remainingMs / periodMs, false, Offset(inset, inset), arc, style = stroke)
        }
        Text("Nouveau code dans $secLeft s · usage unique", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
    }
}
