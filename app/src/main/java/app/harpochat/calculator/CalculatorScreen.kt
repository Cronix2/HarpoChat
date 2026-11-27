// SPDX-License-Identifier: MIT
package app.harpochat.calculator

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.harpochat.ui.theme.HarpoIcons
import app.harpochat.ui.theme.Inter
import kotlin.math.abs

/**
 * Palette de la façade : volontairement banale, celle de n'importe quelle calculatrice
 * (et de l'icône du lanceur). L'identité « Silence » n'apparaît qu'une fois l'espace privé ouvert.
 */
internal object CalcColors {
    val Bg = Color(0xFF121417)
    val Num = Color(0xFF24282D)
    val Fn = Color(0xFF3A3F46)
    val Op = Color(0xFFF2A23A)
    val OnOp = Color(0xFF1A1206)
    val Text = Color(0xFFF2F3F5)
    val Dim = Color(0xFF9AA0A8)
    val Hint = Color(0xFF8B9098)
}

private enum class KeyKind { NUM, FN, OP, MEM }

@Composable
internal fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    /** Appui long sur « = » : reçoit les chiffres saisis (candidat code). */
    onLongEquals: (String) -> Unit,
    /** Position de la touche « = » dans la fenêtre : point de départ de l'animation d'ouverture. */
    onEqualsPositioned: (Offset) -> Unit = {}
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val expression by viewModel.expression.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val radMode by viewModel.radMode.collectAsState()
    val invMode by viewModel.invMode.collectAsState()
    val memory by viewModel.memory.collectAsState()
    val digits = { expression.filter(Char::isDigit) }

    Column(
        Modifier
            .fillMaxSize()
            .background(CalcColors.Bg)
            .systemBarsPadding()
            .padding(horizontal = if (landscape) 24.dp else 18.dp, vertical = if (landscape) 8.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (landscape) 8.dp else 16.dp)
    ) {
        Display(
            expression = expression,
            preview = preview,
            angle = if (radMode) "RAD" else "DEG",
            hasMemory = memory != null,
            landscape = landscape,
            onSwipeErase = viewModel::backspace,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
        if (landscape) {
            ScientificPad(viewModel, invMode, radMode, digits, onLongEquals, onEqualsPositioned, Modifier.weight(2.6f))
        } else {
            BasicPad(viewModel, expression.isEmpty(), digits, onLongEquals, onEqualsPositioned)
        }
    }
}

// ---------------------------------------------------------------------------
// Affichage
// ---------------------------------------------------------------------------

@Composable
private fun Display(
    expression: String,
    preview: String,
    angle: String,
    hasMemory: Boolean,
    landscape: Boolean,
    onSwipeErase: () -> Unit,
    modifier: Modifier
) {
    val measurer = rememberTextMeasurer()
    var widthPx by remember { mutableFloatStateOf(0f) }
    val pretty = prettifyExpression(expression)
    val base = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Light, textAlign = TextAlign.End)
    val maxSp = if (landscape) 44f else 76f
    val minSp = if (landscape) 26f else 34f
    // Plus grande taille qui tient sur une ligne (recherche dichotomique).
    val fontSp = remember(pretty, widthPx, landscape) {
        if (widthPx <= 0f) maxSp else {
            var lo = minSp; var hi = maxSp; var best = minSp
            repeat(10) {
                val mid = (lo + hi) / 2
                val w = measurer.measure(AnnotatedString(pretty), base.copy(fontSize = mid.sp), maxLines = 1).size.width
                if (w <= widthPx) { best = mid; lo = mid } else hi = mid
            }
            best
        }
    }
    val scroll = rememberScrollState()
    LaunchedEffect(pretty) { scroll.scrollTo(scroll.maxValue) }

    Column(
        modifier
            .onSizeChanged { widthPx = it.width.toFloat() - 16f }
            // Balayer l'affichage horizontalement efface le dernier caractère.
            .pointerInput(Unit) {
                var acc = 0f
                detectHorizontalDragGestures(
                    onDragStart = { acc = 0f },
                    onHorizontalDrag = { _, dx ->
                        acc += dx
                        if (abs(acc) > 70f) { onSwipeErase(); acc = 0f }
                    }
                )
            }
            .semantics { contentDescription = "Affichage. Balayer pour effacer le dernier chiffre." }
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(angle, color = CalcColors.Hint, fontFamily = Inter, fontSize = 13.sp)
            Text(if (hasMemory) "M" else "", color = CalcColors.Hint, fontFamily = Inter, fontSize = 13.sp)
        }
        Box(Modifier.weight(1f))
        Text(
            pretty,
            style = base.copy(fontSize = fontSp.sp),
            color = if (preview.isNotEmpty()) CalcColors.Dim else CalcColors.Text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.fillMaxWidth().horizontalScroll(scroll)
        )
        Text(
            if (preview.isNotEmpty()) prettifyResult(preview) else "",
            style = base.copy(fontSize = if (landscape) 30.sp else 44.sp),
            color = CalcColors.Text,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().height(if (landscape) 40.dp else 58.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Claviers
// ---------------------------------------------------------------------------

@Composable
private fun BasicPad(
    vm: CalculatorViewModel,
    empty: Boolean,
    digits: () -> String,
    onLongEquals: (String) -> Unit,
    onEqualsPositioned: (Offset) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 12.dp
        val keyH = (maxWidth - gap * 3) / 4 * 0.94f
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            @Composable
            fun row(content: @Composable RowScope.() -> Unit) =
                Row(Modifier.fillMaxWidth().height(keyH), horizontalArrangement = Arrangement.spacedBy(gap), content = content)

            row {
                Key(if (empty) "AC" else "C", KeyKind.FN, "Effacer", onClick = vm::clearAll)
                Key("±", KeyKind.FN, "Changer le signe", onClick = vm::toggleSignOfLastNumber)
                Key("%", KeyKind.FN, "Pourcentage", onClick = vm::addPercent)
                Key("÷", KeyKind.OP, "Diviser") { vm.addBinaryOp("÷") }
            }
            row { digitKeys(vm, '7', '8', '9'); Key("×", KeyKind.OP, "Multiplier") { vm.addBinaryOp("×") } }
            row { digitKeys(vm, '4', '5', '6'); Key("−", KeyKind.OP, "Soustraire") { vm.addBinaryOp("−") } }
            row { digitKeys(vm, '1', '2', '3'); Key("+", KeyKind.OP, "Additionner") { vm.addBinaryOp("+") } }
            row {
                Key("0", KeyKind.NUM, weight = 2.1f) { vm.addDigit('0') }
                Key(",", KeyKind.NUM, "Virgule", onClick = vm::addDot)
                EqualsKey(vm, digits, onLongEquals, onEqualsPositioned)
            }
        }
    }
}

@Composable
private fun ScientificPad(
    vm: CalculatorViewModel,
    inv: Boolean,
    rad: Boolean,
    digits: () -> String,
    onLongEquals: (String) -> Unit,
    onEqualsPositioned: (Offset) -> Unit,
    modifier: Modifier
) {
    val gap = 8.dp
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        @Composable
        fun ColumnScope.row(content: @Composable RowScope.() -> Unit) =
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), content = content)

        row {
            Key("(", KeyKind.FN, small = true, onClick = vm::addLeftParen)
            Key(")", KeyKind.FN, small = true, onClick = vm::addRightParen)
            Key("±", KeyKind.FN, "Changer le signe", small = true, onClick = vm::toggleSignOfLastNumber)
            Key("MC", KeyKind.MEM, "Effacer la mémoire", small = true, onClick = vm::memoryClear)
            Key("M+", KeyKind.MEM, "Ajouter à la mémoire", small = true, onClick = vm::memoryAdd)
            Key("M−", KeyKind.MEM, "Soustraire de la mémoire", small = true, onClick = vm::memorySubtract)
            Key("MR", KeyKind.MEM, "Rappeler la mémoire", small = true, onClick = vm::memoryRecall)
        }
        row {
            Key("x²", KeyKind.FN, "Carré", small = true) { vm.addPower("2") }
            Key("x³", KeyKind.FN, "Cube", small = true) { vm.addPower("3") }
            Key("xʸ", KeyKind.FN, "Puissance", small = true) { vm.addBinaryOp("^") }
            Key("AC", KeyKind.FN, "Effacer", small = true, onClick = vm::clearAll)
            Key("÷", KeyKind.OP, "Diviser", small = true) { vm.addBinaryOp("÷") }
            Key("×", KeyKind.OP, "Multiplier", small = true) { vm.addBinaryOp("×") }
            Key("", KeyKind.FN, "Effacer le dernier chiffre", small = true, icon = HarpoIcons.Backspace, onClick = vm::backspace)
        }
        row {
            Key("x!", KeyKind.FN, "Factorielle", small = true, onClick = vm::addFactorial)
            Key("√", KeyKind.FN, "Racine carrée", small = true) { vm.addFunction("sqrt") }
            Key("ʸ√x", KeyKind.FN, "Racine n-ième", small = true, onClick = vm::addRootOfDegree)
            digitKeys(vm, '7', '8', '9', small = true)
            Key("−", KeyKind.OP, "Soustraire", small = true) { vm.addBinaryOp("−") }
        }
        row {
            Key("e", KeyKind.FN, small = true) { vm.addConstant("e") }
            Key("ln", KeyKind.FN, "Logarithme népérien", small = true) { vm.addFunction("ln") }
            Key("lg", KeyKind.FN, "Logarithme décimal", small = true) { vm.addFunction("lg") }
            digitKeys(vm, '4', '5', '6', small = true)
            Key("+", KeyKind.OP, "Additionner", small = true) { vm.addBinaryOp("+") }
        }
        Row(Modifier.weight(2f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(Modifier.weight(6f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                row {
                    Key(if (inv) "sin⁻¹" else "sin", KeyKind.FN, small = true) { vm.addFunction(if (inv) "asin" else "sin") }
                    Key(if (inv) "cos⁻¹" else "cos", KeyKind.FN, small = true) { vm.addFunction(if (inv) "acos" else "cos") }
                    Key(if (inv) "tan⁻¹" else "tan", KeyKind.FN, small = true) { vm.addFunction(if (inv) "atan" else "tan") }
                    digitKeys(vm, '1', '2', '3', small = true)
                }
                row {
                    Key(if (rad) "Rad" else "Deg", KeyKind.FN, "Unité d'angle", small = true, onClick = vm::toggleAngleMode)
                    Key("Inv", KeyKind.FN, "Fonctions inverses", small = true, onClick = vm::toggleInvMode)
                    Key("π", KeyKind.FN, "Pi", small = true) { vm.addConstant("π") }
                    Key("%", KeyKind.FN, "Pourcentage", small = true, onClick = vm::addPercent)
                    Key("0", KeyKind.NUM, small = true) { vm.addDigit('0') }
                    Key(",", KeyKind.NUM, "Virgule", small = true, onClick = vm::addDot)
                }
            }
            EqualsKey(vm, digits, onLongEquals, onEqualsPositioned, small = true)
        }
    }
}

@Composable
private fun RowScope.digitKeys(vm: CalculatorViewModel, vararg d: Char, small: Boolean = false) {
    d.forEach { c -> Key(c.toString(), KeyKind.NUM, small = small) { vm.addDigit(c) } }
}

@Composable
private fun RowScope.EqualsKey(
    vm: CalculatorViewModel,
    digits: () -> String,
    onLongEquals: (String) -> Unit,
    onEqualsPositioned: (Offset) -> Unit,
    small: Boolean = false
) {
    Key(
        "=", KeyKind.OP, "Égal",
        small = small,
        modifier = Modifier.onGloballyPositioned { onEqualsPositioned(it.boundsInRoot().center) },
        onLongClick = { onLongEquals(digits()) },
        onClick = vm::evaluate
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.Key(
    label: String,
    kind: KeyKind,
    description: String? = null,
    weight: Float = 1f,
    small: Boolean = false,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val (bg, fg) = when (kind) {
        KeyKind.NUM -> CalcColors.Num to CalcColors.Text
        KeyKind.FN -> CalcColors.Fn to CalcColors.Text
        KeyKind.OP -> CalcColors.Op to CalcColors.OnOp
        KeyKind.MEM -> CalcColors.Num to CalcColors.Op
    }
    val size = when {
        small -> if (kind == KeyKind.OP) 22.sp else 17.sp
        kind == KeyKind.OP -> 32.sp
        kind == KeyKind.FN -> 24.sp
        else -> 30.sp
    }
    Box(
        modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .background(bg)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = description ?: label,
                onLongClick = onLongClick,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .semantics { contentDescription = description ?: label },
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) androidx.compose.material3.Icon(icon, null, tint = fg, modifier = Modifier.fillMaxHeight(0.42f))
        else Text(
            label,
            color = fg,
            fontFamily = Inter,
            fontWeight = if (kind == KeyKind.NUM) FontWeight.Normal else FontWeight.Medium,
            fontSize = size
        )
    }
}

// ---------------------------------------------------------------------------
// Mise en forme des nombres
// ---------------------------------------------------------------------------

/** Espaces fines insécables entre les milliers : 1234567.89 → 1 234 567,89. */
private fun groupThousands(num: String): String {
    val parts = num.split('.', limit = 2)
    val sign = if (parts[0].startsWith("-")) "-" else ""
    val int = parts[0].removePrefix("-")
    val grouped = int.reversed().chunked(3).joinToString(" ").reversed()
    return sign + grouped + if (parts.size > 1) "," + parts[1] else ""
}

private fun prettifyExpression(expr: String): String {
    if (expr.isBlank()) return "0"
    val shown = expr.replace("*", "×").replace("/", "÷").replace("-", "−").replace("sqrt(", "√(")
    return Regex("""\d+(?:\.\d+)?""").replace(shown) { m -> groupThousands(m.value) }
}

private fun prettifyResult(res: String): String {
    val i = res.indexOf('E')
    return if (i >= 0) groupThousands(res.substring(0, i)) + res.substring(i) else groupThousands(res)
}
