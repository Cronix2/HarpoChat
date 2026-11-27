// SPDX-License-Identifier: MIT
package app.calc.scientific.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.calc.scientific.R

/*
 * Polices embarquées dans l'APK (variables, licence OFL : docs/licenses).
 * Jamais de Google Fonts téléchargeables : une requête vers Google trahirait l'app.
 */
/** Fixe explicitement l'axe « wght » de la police variable (sinon elle reste sur son instance par défaut). */
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: FontWeight) =
    Font(res, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val Manrope = FontFamily(
    variable(R.font.manrope, FontWeight.Medium),
    variable(R.font.manrope, FontWeight.SemiBold),
    variable(R.font.manrope, FontWeight.Bold),
    variable(R.font.manrope, FontWeight.ExtraBold)
)

val Inter = FontFamily(
    variable(R.font.inter, FontWeight.Light),
    variable(R.font.inter, FontWeight.Normal),
    variable(R.font.inter, FontWeight.Medium),
    variable(R.font.inter, FontWeight.SemiBold),
    variable(R.font.inter, FontWeight.Bold)
)

private fun title(size: Int, weight: FontWeight = FontWeight.ExtraBold, spacing: Double = -0.02) =
    TextStyle(fontFamily = Manrope, fontWeight = weight, fontSize = size.sp, letterSpacing = (size * spacing).sp)

private fun body(size: Int, weight: FontWeight = FontWeight.Normal, line: Int = (size * 1.45).toInt()) =
    TextStyle(fontFamily = Inter, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

val Typography = Typography(
    displayLarge = title(56), displayMedium = title(44), displaySmall = title(36),
    headlineLarge = title(32), headlineMedium = title(30), headlineSmall = title(24),
    titleLarge = title(22), titleMedium = title(17, FontWeight.Bold, 0.0), titleSmall = title(15, FontWeight.Bold, 0.0),
    bodyLarge = body(16), bodyMedium = body(15), bodySmall = body(13),
    labelLarge = body(15, FontWeight.SemiBold, 20), labelMedium = body(13, FontWeight.Medium, 18),
    labelSmall = body(12, FontWeight.Medium, 16)
)
