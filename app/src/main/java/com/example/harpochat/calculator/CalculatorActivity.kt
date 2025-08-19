package com.example.harpochat.calculator

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.Window
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.RowScope
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
import androidx.core.content.edit
import com.example.harpochat.messaging.ConversationsActivity
import com.example.harpochat.security.SecureStore
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/* =========================
 *  Activité Calculatrice
 * ========================= */
class CalculatorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Masque la barre de titre si présente
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        enableEdgeToEdge() // pas de barre "Calculator"

        // Pins par défaut — NE PAS MODIFIER sans accord
        val prefs = SecureStore.prefs(this)
        if (!prefs.contains(KEY_SECRET_PIN)) prefs.edit { putString(KEY_SECRET_PIN, "527418") }
        if (!prefs.contains(KEY_DURESS_PIN)) prefs.edit { putString(KEY_DURESS_PIN, "1234") }

        setContent {
            DarkCalcTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    CalculatorScreen(
                        onUnlock = {
                            startActivity(Intent(this, ConversationsActivity::class.java))
                        },
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

    // Affichage (texte saisi dans l’écran)
    var display by remember { mutableStateOf(TextFieldValue("0")) }

    // Moteur
    var accumulator by remember { mutableStateOf<BigDecimal?>(null) }
    var pendingOp by remember { mutableStateOf<Op?>(null) }
    var resetOnNextDigit by remember { mutableStateOf(false) }
    val mc = remember { MathContext(16, RoundingMode.HALF_UP) }

    // Nouveaux états pour l’affichage "vidéo 2"
    var lastExpr by remember { mutableStateOf<String?>(null) }   // ex : "7 + 9"
    var showEquals by remember { mutableStateOf(false) }         // si true => "7 + 9 =" sur la 1re ligne

    fun setDisplayText(s: String) { display = TextFieldValue(s) }
    fun getDisplayText(): String = display.text
    fun prettyText(s: String): String = s.replace('.', ',')

    fun opSymbol(op: Op) = when (op) {
        Op.ADD -> "+"
        Op.SUB -> "−"
        Op.MUL -> "×"
        Op.DIV -> "÷"
    }

    /** Construit la ligne d’expression (ligne du haut) */
    fun expressionLine(): String {
        val cur = prettyText(getDisplayText())
        return when {
            showEquals && lastExpr != null -> "${lastExpr} ="
            pendingOp != null && accumulator != null -> {
                val left = accumulator!!.stripTrailingZeros().toPlainString().replace('.', ',')
                "$left ${opSymbol(pendingOp!!)} $cur"
            }
            else -> cur
        }
    }

    /** Aperçu du résultat (ligne du bas) tant que l’expression est complète */
    fun previewResultOrEmpty(): String {
        if (showEquals) return ""
        val acc = accumulator ?: return ""
        val op = pendingOp ?: return ""
        val right = getDisplayText().replace(',', '.').toBigDecimalOrNull() ?: return ""
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
        lastExpr = null; showEquals = false
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
        lastExpr = null; showEquals = false
    }

    fun inputDot() {
        val cur = getDisplayText()
        if (!cur.contains(".")) setDisplayText(cur + ".")
        lastExpr = null; showEquals = false
    }

    fun clearAll() {
        setDisplayText("0")
        accumulator = null
        pendingOp = null
        resetOnNextDigit = false
        lastExpr = null
        showEquals = false
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
            lastExpr = null; showEquals = false
        } catch (_: Throwable) {
            setDisplayText("Error")
            accumulator = null
            pendingOp = null
            resetOnNextDigit = true
            lastExpr = null; showEquals = false
        }
    }

    fun equalsNormal() {
        try {
            val current = getDisplayText().replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO
            val acc = accumulator
            val op = pendingOp
            if (acc != null && op != null) {
                // fige l’expression pour l’affichage
                val left = acc.stripTrailingZeros().toPlainString().replace('.', ',')
                val right = prettyText(getDisplayText())
                lastExpr = "$left ${opSymbol(op)} $right"

                // calcule le résultat
                val result = when (op) {
                    Op.ADD -> acc.add(current, mc)
                    Op.SUB -> acc.subtract(current, mc)
                    Op.MUL -> acc.multiply(current, mc)
                    Op.DIV -> if (current == BigDecimal.ZERO) BigDecimal.ZERO else acc.divide(current, mc)
                }
                setDisplayText(formatForDisplay(result))

                // reset moteur, conserve l’expression
                accumulator = null
                pendingOp = null
                resetOnNextDigit = true
                showEquals = true
            }
        } catch (_: Throwable) {
            setDisplayText("Error")
            accumulator = null
            pendingOp = null
            resetOnNextDigit = true
            lastExpr = null; showEquals = false
        }
    }

    /* ---------- UI ---------- */
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        // Afficheur : expression (ligne 1) + résultat (ligne 2)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = expressionLine(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.90f),
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                val preview = previewResultOrEmpty()
                Text(
                    text = if (preview.isNotEmpty()) preview else getDisplayText(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (!isLandscape) {
            /* ======== PORTRAIT ======== */
            CalcRow { MemKey("MC"); MemKey("M+"); MemKey("M-"); MemKey("MR") }
            CalcRow {
                FuncKey("C") { clearAll() }
                OpKey("÷") { applyOp(Op.DIV) }
                OpKey("×") { applyOp(Op.MUL) }
                FuncKey("⌫") { backspace() }
            }
            CalcRow { DigitKey("7"){inputDigit("7")}; DigitKey("8"){inputDigit("8")}; DigitKey("9"){inputDigit("9")}; OpKey("−"){applyOp(Op.SUB)} }
            CalcRow { DigitKey("4"){inputDigit("4")}; DigitKey("5"){inputDigit("5")}; DigitKey("6"){inputDigit("6")}; OpKey("+"){applyOp(Op.ADD)} }

            // Deux dernières rangées fusionnées : "=" sur 2 lignes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(3f),
                    verticalArrangement = spacedBy(8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = spacedBy(8.dp)) {
                        DigitKey("1") { inputDigit("1") }
                        DigitKey("2") { inputDigit("2") }
                        DigitKey("3") { inputDigit("3") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = spacedBy(8.dp)) {
                        BigDigitKey("0") { inputDigit("0") }
                        DigitKey(".") { inputDot() }
                    }
                }
                EqualKeyTall(
                    modifier = Modifier.weight(1f),
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
            Spacer(Modifier.height(8.dp))
        } else {
            /* ======== PAYSAGE (scientifique light) ======== */
            CalcRow {
                FuncKey("(") { /* TODO */ }
                FuncKey(")") { /* TODO */ }
                FuncKey("1/x") { /* TODO */ }
                MemKey("MC"); MemKey("M+"); MemKey("M-"); MemKey("MR")
            }
            CalcRow {
                FuncKey("x²") { /* TODO */ }
                FuncKey("x³") { /* TODO */ }
                FuncKey("C") { clearAll() }
                OpKey("÷") { applyOp(Op.DIV) }
                OpKey("×") { applyOp(Op.MUL) }
                FuncKey("⌫") { backspace() }
            }
            CalcRow { DigitKey("7"){inputDigit("7")}; DigitKey("8"){inputDigit("8")}; DigitKey("9"){inputDigit("9")}; OpKey("−"){applyOp(Op.SUB)} }
            CalcRow { DigitKey("4"){inputDigit("4")}; DigitKey("5"){inputDigit("5")}; DigitKey("6"){inputDigit("6")}; OpKey("+"){applyOp(Op.ADD)} }
            CalcRow {
                DigitKey("1"){inputDigit("1")}; DigitKey("2"){inputDigit("2")}; DigitKey("3"){inputDigit("3")}
                EqualKey(onTap = { equalsNormal() }, onLong = {
                    when (validatePins(getDisplayText().trim())) {
                        PinResult.SECRET -> onUnlock()
                        PinResult.DURESS -> onDuress()
                        PinResult.NO_MATCH -> {}
                    }
                })
            }
            CalcRow {
                FuncKey("%") {
                    val v = getDisplayText().replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO
                    setDisplayText(formatForDisplay(v.divide(BigDecimal(100), mc)))
                    resetOnNextDigit = true
                    lastExpr = null; showEquals = false
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
}

/* =============== Composants de touches =============== */

private val MemTextBlue = Color(0xFF4C6FFF)
private val KeyDark = Color(0xFF1A1F27)   // fond gris foncé
private val KeyLight = Color(0xFF3A404D)  // fond gris clair (opérateurs)

@Composable
private fun CalcRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = spacedBy(8.dp),
        content = content
    )
    Spacer(Modifier.height(8.dp))
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
        modifier = modifier.height(56.dp),
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

@Composable private fun RowScope.DigitKey(label: String, onClick: () -> Unit) =
    KeyBase(label, modifier = Modifier.weight(1f), onClick = onClick)

@Composable private fun RowScope.BigDigitKey(label: String, onClick: () -> Unit) =
    KeyBase(label, modifier = Modifier.weight(2f), onClick = onClick)

@Composable private fun RowScope.OpKey(label: String, onClick: () -> Unit) =
    KeyBase(label, modifier = Modifier.weight(1f), container = KeyLight, onClick = onClick)

@Composable private fun RowScope.FuncKey(label: String, onClick: () -> Unit) =
    KeyBase(label, modifier = Modifier.weight(1f), container = KeyLight, onClick = onClick)

@Composable private fun RowScope.MemKey(label: String) =
    KeyBase(label, modifier = Modifier.weight(1f), container = KeyDark, contentColor = MemTextBlue, onClick = { })

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun RowScope.EqualKey(onTap: () -> Unit, onLong: () -> Unit) {
    Surface(
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .combinedClickable(onClick = onTap, onLongClick = onLong),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("=", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun RowScope.EqualKeyWide(onTap: () -> Unit, onLong: () -> Unit) {
    Surface(
        modifier = Modifier
            .weight(2f) // large
            .height(56.dp)
            .combinedClickable(onClick = onTap, onLongClick = onLong),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("=", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** "=" sur 2 lignes (hauteur = 2 touches + l’espacement) */
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun EqualKeyTall(
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onLong: () -> Unit
) {
    val tallHeight = 56.dp * 2 + 8.dp
    Surface(
        modifier = modifier
            .height(tallHeight)
            .combinedClickable(onClick = onTap, onLongClick = onLong),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("=", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
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
        val symbols = DecimalFormatSymbols.getInstance(Locale.FRANCE) // virgule décimale
        val fmt = DecimalFormat("0.######E0", symbols)
        fmt.format(bd)
    } else {
        bd.stripTrailingZeros().toPlainString().replace('.', ',')
    }
}

/* Petit thème sombre cohérent */
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
