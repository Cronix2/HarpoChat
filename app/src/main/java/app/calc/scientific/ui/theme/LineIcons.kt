// SPDX-License-Identifier: MIT
package app.calc.scientific.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Icônes de l'app : tracés SVG au trait (viewBox 24×24), identiques aux maquettes.
 * Règle de la direction Silence : jamais d'emoji, uniquement ces vecteurs.
 * La couleur vient du `tint` d'Icon(), le tracé est dessiné en noir.
 */
object LineIcons {

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0"

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r},${y}h${w - 2 * r}a$r,$r 0 0 1 $r,$r v${h - 2 * r}a$r,$r 0 0 1 -$r,$r h-${w - 2 * r}a$r,$r 0 0 1 -$r,-$r v-${h - 2 * r}a$r,$r 0 0 1 $r,-$r z"

    private fun stroke(name: String, vararg paths: String, width: Float = 1.8f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round
                )
            }
        }.build()

    private fun filled(name: String, d: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
        }.build()

    val Lock by lazy { stroke("lock", rect(5f, 11f, 14f, 9f, 2f), "M8 11V8a4 4 0 0 1 8 0v3") }
    val Settings by lazy {
        stroke(
            "settings",
            circle(12f, 12f, 3f),
            "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"
        )
    }
    val Search by lazy { stroke("search", circle(11f, 11f, 7f), "M20 20l-3.5-3.5") }
    val Shield by lazy { stroke("shield", "M12 3 5 6v6c0 4.2 3 7.6 7 9 4-1.4 7-4.8 7-9V6z") }
    val ShieldCheck by lazy { stroke("shield-check", "M12 3 5 6v6c0 4.2 3 7.6 7 9 4-1.4 7-4.8 7-9V6z", "M9 12l2 2 4-4", width = 2f) }
    val Back by lazy { stroke("back", "M15 5 8 12l7 7", width = 1.9f) }
    val Close by lazy { stroke("close", "M6 6l12 12M18 6 6 18", width = 1.9f) }
    val Plus by lazy { stroke("plus", "M12 5v14M5 12h14", width = 2f) }
    val Mic by lazy { stroke("mic", rect(9f, 3f, 6f, 11f, 3f), "M5 11a7 7 0 0 0 14 0M12 18v3", width = 2f) }
    val Timer by lazy { stroke("timer", circle(12f, 13f, 8f), "M12 9v4l2.5 2M10 2h4", width = 2f) }
    val Send by lazy { stroke("send", "M4 12 20 4l-6 16-3-7z", width = 2f) }
    val Warning by lazy { stroke("warning", "M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0zM12 9v4M12 17h.01", width = 2f) }
    val Check by lazy { stroke("check", "M5 12l4.5 4.5L19 7", width = 2.2f) }
    val CheckCircle by lazy { stroke("check-circle", circle(12f, 12f, 9f), "M8.5 12l2.5 2.5 4.5-5", width = 2f) }
    val ChevronRight by lazy { stroke("chevron-right", "M9 5l7 7-7 7", width = 2f) }
    val Trash by lazy { stroke("trash", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3", width = 1.9f) }
    val Info by lazy { stroke("info", circle(12f, 12f, 9f), "M12 8v5M12 16h.01", width = 2f) }
    val Eye by lazy { stroke("eye", "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z", circle(12f, 12f, 3f)) }
    val EyeOff by lazy { stroke("eye-off", "M3 3l18 18M10.6 5.1A10.8 10.8 0 0 1 12 5c6.4 0 10 7 10 7a17.6 17.6 0 0 1-3.2 4.1M6.6 6.6C3.8 8.4 2 12 2 12s3.6 7 10 7a10.4 10.4 0 0 0 5.4-1.6M9.9 9.9a3 3 0 0 0 4.2 4.2") }
    val QrCode by lazy { stroke("qr", "M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h3v3h-3zM18 18h3v3h-3z") }
    val Scan by lazy { stroke("scan", "M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3M4 12h16") }
    val File by lazy { stroke("file", "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z", "M14 3v5h5") }
    val Image by lazy { stroke("image", rect(3f, 5f, 18f, 14f, 2f), circle(8.5f, 10f, 1.5f), "M21 16l-5-5-8 8") }
    val Key by lazy { stroke("key", circle(8f, 15f, 4f), "M10.8 12.2 20 3M17 6l3 3M15 8l2 2") }
    val Calculator by lazy {
        stroke("calculator", rect(5f, 3f, 14f, 18f, 2.5f), "M8 7h8M8 12h1M12 12h0M16 12h0M8 16h1M12 16h0M16 16h0")
    }
    val Backspace by lazy { stroke("backspace", "M21 5H9l-6 7 6 7h12a1 1 0 0 0 1-1V6a1 1 0 0 0-1-1z", "M12 9l6 6M18 9l-6 6") }
    val Play by lazy { filled("play", "M7 4v16l13-8z") }
}
