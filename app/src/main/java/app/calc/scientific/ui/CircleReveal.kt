// SPDX-License-Identifier: MIT
package app.calc.scientific.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.hypot
import kotlin.math.max

/**
 * Découpe circulaire qui grandit depuis [origin] (la touche « = ») jusqu'à couvrir tout l'écran.
 * [progress] va de 0 (invisible) à 1 (plein écran).
 */
class CircleRevealShape(private val progress: Float, private val origin: Offset) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val center = if (origin.isSpecified) origin else Offset(size.width * 0.8f, size.height * 0.9f)
        // Rayon jusqu'au coin le plus éloigné : le cercle finit par tout couvrir.
        val far = max(
            max(hypot(center.x, center.y), hypot(size.width - center.x, center.y)),
            max(hypot(center.x, size.height - center.y), hypot(size.width - center.x, size.height - center.y))
        )
        val r = far * progress
        return Outline.Generic(Path().apply { addOval(Rect(center, r)) })
    }
}
