package com.example.harpochat.calculator

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Window
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.example.harpochat.messaging.ConversationsActivity
import com.example.harpochat.security.SecureStore
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlinx.coroutines.delay

/* =========================
 *  Activité Calculatrice
 * ========================= */
class CalculatorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        enableEdgeToEdge()

        val prefs = SecureStore.prefs(this)
        if (!prefs.contains(KEY_SECRET_PIN)) prefs.edit { putString(KEY_SECRET_PIN, "527418") }
        if (!prefs.contains(KEY_DURESS_PIN)) prefs.edit { putString(KEY_DURESS_PIN, "1234") }

        setContent {
            DarkCalcTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    CalculatorScreen(
                        onUnlock = { startActivity(Intent(this, ConversationsActivity::class.java)) },
                        onDuress = {
                            prefs.edit { clear() }
                            Toast.makeText(this, "Memory cleared", Toast.LENGTH_SHORT).show()
                        },
                        validatePins = { entered ->
                            val secret = prefs.getString(KEY_SECRET_PIN, "") ?: ""
                            val duress = prefs.getString(KEY_DURESS_PIN, "") ?: ""
                            when (entered) {
                                secret -> PinResult.SECRET
                                duress -> PinResult.DURESS
                                else -> PinResult.NO_MATCH
                            }
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val KEY_SECRET_PIN = "calculator_secret_pin"
        const val KEY_DURESS_PIN = "calculator_duress_pin"
    }
}

/* ====== Modèles / état ====== */
private enum class Op { ADD, SUB, MUL, DIV }
private enum class PinResult { SECRET, DURESS, NO_MATCH }

/* ==============================
 *     Écran Calculatrice
 * ============================== */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalculatorScreen(
    onUnlock: () -> Unit,
    onDuress: () -> Unit,
    validatePins: (String) -> PinResult
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var justDidEquals by remember { mutableStateOf(false) }

    // Affichage (texte saisi)
    var display by remember { mutableStateOf(TextFieldValue("0")) }

    // Moteur
    var accumulator by remember { mutableStateOf<BigDecimal?>(null) }
    var pendingOp by remember { mutableStateOf<Op?>(null) }
    var resetOnNextDigit by remember { mutableStateOf(false) }
    val mc = remember { MathContext(16, RoundingMode.HALF_UP) }

    // utils
    fun setDisplayText(s: String) { display = TextFieldValue(s) }
    fun getDisplayText(): String = display.text
    fun prettyText(s: String) = s.replace('.', ',')

    fun opSymbol(op: Op) = when (op) {
        Op.ADD -> "+"
        Op.SUB -> "−"
        Op.MUL -> "×"
        Op.DIV -> "÷"
    }

    // Ligne d’expression (haut)
    fun expressionLine(): String {
        val cur = prettyText(getDisplayText())
        return if (pendingOp != null && accumulator != null) {
            val left = accumulator!!.stripTrailingZeros().toPlainString().replace('.', ',')
            if (resetOnNextDigit) "$left ${opSymbol(pendingOp!!)}"
            else "$left ${opSymbol(pendingOp!!)} $cur"
        } else cur
    }

    // Aperçu du résultat (bas)
    fun previewResultOrEmpty(): String {
        val acc = accumulator ?: return ""
        val op  = pendingOp   ?: return ""
        val rightTxt = getDisplayText()
        val right = rightTxt.replace(',', '.').toBigDecimalOrNull() ?: return ""
        if (resetOnNextDigit) return ""

        val result = when (op) {
            Op.ADD -> acc.add(right, mc)
            Op.SUB -> acc.subtract(right, mc)
            Op.MUL -> acc.multiply(right, mc)
            Op.DIV -> if (right == BigDecimal.ZERO) BigDecimal.ZERO else acc.divide(right, mc)
        }
        return formatForDisplay(result)
    }

    fun backspace() {
        val cur = getDisplayText()
        val next = if (cur.length <= 1) "0" else cur.dropLast(1)
        setDisplayText(next)
    }

    fun inputDigit(d: String) {
        val cur = getDisplayText()
        val next = when {
            resetOnNextDigit -> d
            cur == "0" && d != "0" -> d
            cur == "0" && d == "0" -> "0"
            else -> cur + d
        }
        setDisplayText(next)
        resetOnNextDigit = false
    }

    fun inputDot() {
        val cur = getDisplayText()
        if (!cur.contains(".")) setDisplayText(cur + ".")
    }

    fun clearAll() {
        setDisplayText("0")
        accumulator = null
        pendingOp = null
        resetOnNextDigit = false
    }

    fun applyOp(op: Op) {
        try {
            val current = getDisplayText().replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO
            val acc = accumulator

            if (acc == null) {
                accumulator = current
            } else if (pendingOp != null) {
                val result = when (pendingOp) {
                    Op.ADD -> acc.add(current, mc)
                    Op.SUB -> acc.subtract(current, mc)
                    Op.MUL -> acc.multiply(current, mc)
                    Op.DIV -> if (current == BigDecimal.ZERO) BigDecimal.ZERO else acc.divide(current, mc)
                    null   -> acc
                }
                accumulator = result
                setDisplayText(formatForDisplay(result))
            }
            pendingOp = op
            resetOnNextDigit = true
        } catch (_: Throwable) {
            setDisplayText("Error")
            accumulator = null
            pendingOp = null
            resetOnNextDigit = true
        }
    }

    fun equalsNormal() {
        try {
            val current = getDisplayText().replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO
            val acc = accumulator
            val op = pendingOp
            val canCompute = acc != null && op != null && !resetOnNextDigit

            if (acc != null && op != null) {
                val result = when (op) {
                    Op.ADD -> acc.add(current, mc)
                    Op.SUB -> acc.subtract(current, mc)
                    Op.MUL -> acc.multiply(current, mc)
                    Op.DIV -> if (current == BigDecimal.ZERO) BigDecimal.ZERO else acc.divide(current, mc)
                }
                setDisplayText(formatForDisplay(result))
                accumulator = null
                pendingOp = null
                resetOnNextDigit = false
                if (canCompute) justDidEquals = true
            }
        } catch (_: Throwable) {
            setDisplayText("Error")
            accumulator = null
            pendingOp = null
            resetOnNextDigit = true
        }
    }

    /* ---------- UI ---------- */
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp), // padding latéral fixe
        horizontalAlignment = Alignment.End
    ) {
        // ====== Pourcentages demandés ======
        // 5% haut, 25% display, 65% clavier, 3% bas, 2% padding display et clavier
        val weightDisplay = 0.25f
        val weightClavier = 0.65f
        val weightBas = 0.05f
        val weightHaut = 0.05f
        val weightEspace = 0.02f

        Spacer(Modifier.weight(weightHaut))

        // ====== Display 25% ======
        val exprFontSize  = if (isLandscape) 52.sp else 56.sp
        val resultFontSize = if (isLandscape) 48.sp else 52.sp

        Box(
            modifier = Modifier
                .weight(weightDisplay)
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.End
            ) {
                AnimatedContent(
                    targetState = expressionLine(),
                    transitionSpec = {
                        if (justDidEquals) {
                            slideInVertically(tween(180)) { +it } togetherWith
                                    slideOutVertically(tween(180)) { -it }
                        } else {
                            EnterTransition.None togetherWith ExitTransition.None
                        }
                    },
                    label = "expr-anim"
                ) { text ->
                    Text(
                        text = text,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = exprFontSize),
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(6.dp))

                val preview = previewResultOrEmpty()
                AnimatedContent(
                    targetState = preview,
                    transitionSpec = {
                        if (justDidEquals) {
                            slideInVertically(tween(160)) { +it } togetherWith
                                    slideOutVertically(tween(160)) { -it }
                        } else {
                            EnterTransition.None togetherWith ExitTransition.None
                        }
                    },
                    label = "preview-anim"
                ) { txt ->
                    if (txt.isNotEmpty()) {
                        Text(
                            text = txt,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.headlineLarge.copy(fontSize = resultFontSize),
                            maxLines = 1,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Spacer(Modifier.height(0.dp))
                    }
                }
            }
        }

        // ====== Espace 2% ======
        Spacer(Modifier.weight(weightEspace)) // petit écart visuel, n’impacte pas la répartition %
        Column(
            // ====== Clavier 65% ======
            modifier = Modifier
                .weight(weightClavier)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(KeyPadding)
        ) {
            if (!isLandscape) {
                // chaque rangée = weight(1f) -> même hauteur
                Row(Modifier
                    .weight(1f)
                    .fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyPadding)) {
                    MemKey("MC"); MemKey("M+"); MemKey("M-"); MemKey("MR")
                }
                Row(Modifier
                    .weight(1f)
                    .fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyPadding)) {
                    FuncKey("C") { clearAll() }
                    OpKey("÷") { applyOp(Op.DIV) }
                    OpKey("×") { applyOp(Op.MUL) }
                    FuncKey("⌫") { backspace() }
                }
                Row(Modifier
                    .weight(1f)
                    .fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyPadding)) {
                    DigitKey("7"){inputDigit("7")}; DigitKey("8"){inputDigit("8")}; DigitKey("9"){inputDigit("9")}; OpKey("−"){applyOp(Op.SUB)}
                }
                Row(Modifier
                    .weight(1f)
                    .fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyPadding)) {
                    DigitKey("4"){inputDigit("4")}; DigitKey("5"){inputDigit("5")}; DigitKey("6"){inputDigit("6")}; OpKey("+"){applyOp(Op.ADD)}
                }

                // Dernier bloc = 2 rangées à gauche + "=" qui prend toute la hauteur à droite
                Row(Modifier
                    .weight(2f)
                    .fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyPadding)) {
                    Column(
                        modifier = Modifier
                            .weight(3f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(KeyPadding)
                    ) {
                        Row(Modifier
                            .weight(1f)
                            .fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyPadding)) {
                            DigitKey("1") { inputDigit("1") }
                            DigitKey("2") { inputDigit("2") }
                            DigitKey("3") { inputDigit("3") }
                        }
                        Row(Modifier
                            .weight(1f)
                            .fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyPadding)) {
                            BigDigitKey("0") { inputDigit("0") }
                            DigitKey(".") { inputDot() }
                        }
                    }
                    EqualKeyTall(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onTap = { equalsNormal() },
                        onLong = {
                            when (validatePins(getDisplayText().trim())) {
                                PinResult.SECRET -> onUnlock()
                                PinResult.DURESS -> onDuress()
                                PinResult.NO_MATCH -> {}
                            }
                        }
                    )
                }
            } else {
                // PAYSAGE (existant, rangées à poids égaux)
                Row(Modifier
                    .weight(1f)
                    .fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyPadding)) {
                    FuncKey("(") { /* TODO */ }
                    FuncKey(")") { /* TODO */ }
                    FuncKey("1/x") { /* TODO */ }
                    MemKey("MC"); MemKey("M+"); MemKey("M-"); MemKey("MR")
                }
                Row(Modifier
                    .weight(1f)
                    .fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyPadding)) {
                    FuncKey("x²") { /* TODO */ }
                    FuncKey("x³") { /* TODO */ }
                    FuncKey("C") { clearAll() }
                    OpKey("÷") { applyOp(Op.DIV) }
                    OpKey("×") { applyOp(Op.MUL) }
                    FuncKey("⌫") { backspace() }
                }
                Row(Modifier
                    .weight(1f)
                    .fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyPadding)) {
                    DigitKey("7"){inputDigit("7")}; DigitKey("8"){inputDigit("8")}; DigitKey("9"){inputDigit("9")}; OpKey("−"){applyOp(Op.SUB)}
                }
                Row(Modifier
                    .weight(1f)
                    .fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyPadding)) {
                    DigitKey("4"){inputDigit("4")}; DigitKey("5"){inputDigit("5")}; DigitKey("6"){inputDigit("6")}; OpKey("+"){applyOp(Op.ADD)}
                }
                Row(Modifier
                    .weight(1f)
                    .fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyPadding)) {
                    DigitKey("1"){inputDigit("1")}; DigitKey("2"){inputDigit("2")}; DigitKey("3"){inputDigit("3")}
                    EqualKey(onTap = { equalsNormal() }, onLong = {
                        when (validatePins(getDisplayText().trim())) {
                            PinResult.SECRET -> onUnlock()
                            PinResult.DURESS -> onDuress()
                            PinResult.NO_MATCH -> {}
                        }
                    })
                }
                Row(Modifier
                    .weight(1f)
                    .fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyPadding)) {
                    FuncKey("%") {
                        val v = getDisplayText().replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO
                        setDisplayText(formatForDisplay(v.divide(BigDecimal(100), mc)))
                        resetOnNextDigit = true
                    }
                    BigDigitKey("0") { inputDigit("0") }
                    DigitKey(".") { inputDot() }
                    EqualKeyWide(onTap = { equalsNormal() }, onLong = {
                        when (validatePins(getDisplayText().trim())) {
                            PinResult.SECRET -> onUnlock()
                            PinResult.DURESS -> onDuress()
                            PinResult.NO_MATCH -> {}
                        }
                    })
                }
            }
        }

        // ====== Padding bas 3% ======
        Spacer(Modifier.weight(weightBas))
    }

    LaunchedEffect(justDidEquals) {
        if (justDidEquals) {
            delay(200)
            justDidEquals = false
        }
    }
}

/* =============== Composants de touches =============== */

private val MemTextBlue = Color(0xFF4C6FFF)
private val KeyDark = Color(0xFF1A1F27)
private val KeyLight = Color(0xFF3A404D)

val KeyPadding = 8.dp

@Composable
private fun RowScope.DigitKey(label: String, onClick: () -> Unit) =
    KeyBase(label, modifier = Modifier
        .weight(1f)
        .fillMaxHeight(), onClick = onClick)

@Composable
private fun RowScope.BigDigitKey(label: String, onClick: () -> Unit) =
    KeyBase(label, modifier = Modifier
        .weight(2f)
        .fillMaxHeight(), onClick = onClick)

@Composable
private fun RowScope.OpKey(label: String, onClick: () -> Unit) =
    KeyBase(label, modifier = Modifier
        .weight(1f)
        .fillMaxHeight(), container = KeyLight, onClick = onClick)

@Composable
private fun RowScope.FuncKey(label: String, onClick: () -> Unit) =
    KeyBase(label, modifier = Modifier
        .weight(1f)
        .fillMaxHeight(), container = KeyLight, onClick = onClick)

@Composable
private fun RowScope.MemKey(label: String) =
    KeyBase(label, modifier = Modifier
        .weight(1f)
        .fillMaxHeight(), container = KeyDark, contentColor = MemTextBlue, onClick = { })

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun RowScope.EqualKey(onTap: () -> Unit, onLong: () -> Unit) {
    Surface(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        onClick = onTap
    ) {
        Box(Modifier
            .fillMaxSize()
            .combinedClickable(onClick = onTap, onLongClick = onLong),
            contentAlignment = Alignment.Center) {
            Text("=", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun RowScope.EqualKeyWide(onTap: () -> Unit, onLong: () -> Unit) {
    Surface(
        modifier = Modifier
            .weight(2f)
            .fillMaxHeight(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        onClick = onTap
    ) {
        Box(Modifier
            .fillMaxSize()
            .combinedClickable(onClick = onTap, onLongClick = onLong),
            contentAlignment = Alignment.Center) {
            Text("=", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** “=” vertical qui remplit toute la hauteur disponible (deux rangées) */
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun EqualKeyTall(
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onLong: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        onClick = onTap
    ) {
        Box(Modifier
            .fillMaxSize()
            .combinedClickable(onClick = onTap, onLongClick = onLong),
            contentAlignment = Alignment.Center) {
            Text("=", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun KeyBase(
    label: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = container,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        onClick = onClick
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleLarge, color = contentColor)
        }
    }
}

/* =============== Helpers format / thème =============== */

private fun String.trimEndZeros(): String =
    try {
        val s = this.toBigDecimal().stripTrailingZeros().toPlainString()
        if (s == "-0") "0" else s
    } catch (_: Throwable) { this }

private fun formatForDisplay(bd: BigDecimal): String {
    val abs = bd.abs()
    val useSci = (abs.compareTo(BigDecimal("1000000000")) > 0) ||
            (abs != BigDecimal.ZERO && abs < BigDecimal("0.000001"))
    return if (useSci) {
        val symbols = DecimalFormatSymbols.getInstance(Locale.FRANCE)
        val fmt = DecimalFormat("0.######E0", symbols)
        fmt.format(bd)
    } else {
        bd.stripTrailingZeros().toPlainString().replace('.', ',')
    }
}

/* Thème sombre */
@Composable
private fun DarkCalcTheme(content: @Composable () -> Unit) {
    val dark = darkColorScheme(
        primary = Color(0xFF4C6FFF),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF2A2F3A),
        onPrimaryContainer = Color.White,
        secondary = Color(0xFF8C96A7),
        tertiaryContainer = Color(0xFF3A404D),
        background = Color(0xFF0F1115),
        onBackground = Color(0xFFE7EAF1),
        surfaceVariant = Color(0xFF1A1F27),
        onSurfaceVariant = Color(0xFFD8DDE6)
    )
    MaterialTheme(colorScheme = dark, typography = Typography(), content = content)
}
