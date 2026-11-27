// SPDX-License-Identifier: MIT
package app.calc.scientific.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.calc.scientific.security.PinPolicy
import app.calc.scientific.ui.NoKeyboardLearning
import app.calc.scientific.ui.PrimaryButton
import app.calc.scientific.ui.theme.LineIcons
import app.calc.scientific.ui.theme.Palette

/** Une étape de saisie de code, avec confirmation. */
data class CodeStep(
    val eyebrow: String,
    val title: String,
    val body: String,
    /** Règle propre à l'étape (ex. « différent du code secret »), en plus de la politique générale. */
    val extraCheck: (String) -> String? = { null },
    /** Faux pour une simple vérification (code actuel) : pas de confirmation ni de politique. */
    val isNewCode: Boolean = true
)

/**
 * Saisie d'un code en cases : barre de progression, titre, cases, règle, bouton Continuer.
 * Chaque nouveau code est saisi deux fois.
 */
@Composable
fun CodeStepScreen(
    step: CodeStep,
    stepIndex: Int,
    stepCount: Int,
    busy: Boolean,
    externalError: String?,
    onDone: (String) -> Unit,
    footer: String? = null,
    top: @Composable () -> Unit = {}
) {
    var value by remember(step) { mutableStateOf("") }
    var confirm by remember(step) { mutableStateOf("") }
    var confirming by remember(step) { mutableStateOf(false) }
    var error by remember(step) { mutableStateOf<String?>(null) }
    val shownError = error ?: externalError

    fun submit() {
        if (busy) return
        if (!confirming) {
            val problem = if (step.isNewCode) PinPolicy.problemWith(value) ?: step.extraCheck(value)
            else if (value.length < PinPolicy.MIN_LENGTH) "Au moins ${PinPolicy.MIN_LENGTH} chiffres" else null
            when {
                problem != null -> error = problem
                step.isNewCode -> { confirming = true; error = null }
                else -> onDone(value)
            }
        } else if (confirm == value) {
            onDone(value)
        } else {
            error = "Les deux saisies ne correspondent pas. Recommencez la confirmation."
            confirm = ""
        }
    }

    NoKeyboardLearning {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            top()
            StepBar(stepIndex, stepCount)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(step.eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Mint)
                AnimatedContent(
                    targetState = confirming,
                    transitionSpec = { (slideInHorizontally { it / 6 } + fadeIn()) togetherWith fadeOut() },
                    label = "confirm"
                ) { c ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (c) "Confirmez ce code" else step.title,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            if (c) "Saisissez-le une seconde fois." else step.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.Muted
                        )
                    }
                }
            }
            if (confirming) {
                PinBoxes(confirm, { confirm = it; error = null }, onSubmit = ::submit, isError = shownError != null)
            } else {
                PinBoxes(value, { value = it; error = null }, onSubmit = ::submit, isError = shownError != null)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (shownError != null) LineIcons.Warning else LineIcons.CheckCircle, null,
                    tint = if (shownError != null) Palette.Amber else Palette.Mint,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    shownError ?: if (step.isNewCode) "6 à 12 chiffres, ni suite ni chiffre répété" else "Le code que vous utilisez aujourd'hui",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (shownError != null) Palette.Amber else Palette.Mint
                )
            }
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val current = if (confirming) confirm else value
                PrimaryButton(
                    if (busy) "Chiffrement en cours…" else "Continuer",
                    onClick = ::submit,
                    enabled = !busy && current.length >= PinPolicy.MIN_LENGTH,
                    modifier = Modifier.fillMaxWidth()
                )
                if (busy) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Palette.Mint)
                        Spacer(Modifier.size(8.dp))
                        Text("Génération des clés liées à la puce sécurisée", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                } else if (footer != null) {
                    Text(footer, style = MaterialTheme.typography.bodySmall, color = Palette.Faint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Barre de progression segmentée. */
@Composable
fun StepBar(index: Int, count: Int) {
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = "Étape ${index + 1} sur $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(count) { i ->
            Box(
                Modifier.weight(1f).height(4.dp)
                    .background(if (i <= index) Palette.Mint else Palette.SurfaceHigh, RoundedCornerShape(2.dp))
            )
        }
    }
}

/**
 * Cases de saisie : un champ texte invisible (clavier numérique) sous des cases qui affichent
 * un point par chiffre. Le nombre de cases s'adapte de 6 à 12.
 */
@Composable
fun PinBoxes(value: String, onChange: (String) -> Unit, onSubmit: () -> Unit, isError: Boolean) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val boxes = (maxOf(PinPolicy.MIN_LENGTH, value.length + 1)).coerceAtMost(PinPolicy.MAX_LENGTH)
    BasicTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter(Char::isDigit).take(PinPolicy.MAX_LENGTH)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Transparent),
        modifier = Modifier.fillMaxWidth().focusRequester(focus)
            .semantics { contentDescription = "Code, ${value.length} chiffres saisis" },
        decorationBox = { inner ->
            Box {
                Box(Modifier.size(1.dp)) { inner() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (boxes > 8) 6.dp else 8.dp)) {
                    repeat(boxes) { i ->
                        val active = i == value.length
                        Box(
                            Modifier.weight(1f).height(56.dp)
                                .background(Palette.Surface, RoundedCornerShape(14.dp))
                                .border(
                                    1.dp,
                                    when { isError -> Palette.Amber; active -> Palette.Mint; else -> Palette.SurfaceHigh },
                                    RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (i < value.length) Box(Modifier.size(12.dp).background(Palette.Text, CircleShape))
                        }
                    }
                }
            }
        }
    )
}
