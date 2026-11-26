// SPDX-License-Identifier: MIT
package app.harpochat.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import app.harpochat.ui.theme.AppColors

/**
 * Anneau de compte à rebours (sens horaire) pour les codes temporaires.
 * Appelle [onCycle] à chaque fin de période, pour régénérer le code.
 */
@Composable
fun CountdownRing(
    periodSec: Int,
    cycleStartMs: Long,
    onCycle: () -> Unit,
    modifier: Modifier = Modifier
) {
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
    val progress = remainingMs.toFloat() / periodMs
    val secLeft = ((remainingMs + 999) / 1000).toInt()

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(AppColors.Muted.copy(alpha = 0.25f), -90f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
            drawArc(AppColors.Timer, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = stroke)
        }
        Text("$secLeft", style = MaterialTheme.typography.labelLarge)
    }
}
