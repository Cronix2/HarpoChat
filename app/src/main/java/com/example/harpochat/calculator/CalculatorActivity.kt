package com.example.harpochat.calculator

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.example.harpochat.messaging.ConversationsActivity
import com.example.harpochat.security.SecureStore
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class CalculatorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Anti-screenshot
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

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
        private const val KEY_SECRET_PIN = "calculator_secret_pin"
        private const val KEY_DURESS_PIN = "calculator_duress_pin"
    }
}

private enum class Op { ADD, SUB, MUL, DIV }
private enum class PinResult { SECRET, DURESS, NO_MATCH }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalculatorScreen(
    onUnlock: () -> Unit,
    onDuress: () -> Unit,
    validatePins: (String) -> PinResult
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
            configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val ctx = LocalContext.current

    // --- état calculatrice
    var display by remember { mutableStateOf(TextFieldValue("0")) }
    var accumulator by remember { mutableStateOf<BigDecimal?>(null) }
    var pendingOp by remember { mutableStateOf<Op?>(null) }
    var resetOnNextDigit by remember { mutableStateOf(false) }
    val mc = remember { MathContext(16, RoundingMode.HALF_UP) }

    fun setDisplayText(s: String) { display = TextFieldValue(s) }
    fun getDisplayText(): String = display.text

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
            val current = getDisplayText().toBigDecimalOrNull() ?: BigDecimal.ZERO
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
                setDisplayText(result.toPlainString().trimEndZeros())
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
            val current = getDisplayText().toBigDecimalOrNull() ?: BigDecimal.ZERO
            val acc = accumulator
            val op = pendingOp
            if (acc != null && op != null) {
                val result = when (op) {
                    Op.ADD -> acc.add(current, mc)
                    Op.SUB -> acc.subtract(current, mc)
                    Op.MUL -> acc.multiply(current, mc)
                    Op.DIV -> if (current == BigDecimal.ZERO) BigDecimal.ZERO else acc.divide(current, mc)
                }
                setDisplayText(result.toPlainString().trimEndZeros())
                accumulator = null
                pendingOp = null
                resetOnNextDigit = true
            }
        } catch (_: Throwable) {
            setDisplayText("Error")
            accumulator = null
            pendingOp = null
            resetOnNextDigit = true
        }
    }

    // --- UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        // bandeau résultat
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text(
                text = getDisplayText(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.End,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        Spacer(Modifier.height(12.dp))

        // Clavier – portrait simple / paysage scientifique
        if (!isLandscape) {
            // PORTRAIT (simple)
            CalcRow {
                MemKey("MC"); MemKey("M+"); MemKey("M-"); MemKey("MR")
            }
            CalcRow {
                FuncKey("C") { clearAll() }
                OpKey("÷") { applyOp(Op.DIV) }
                FuncKey("×") { applyOp(Op.MUL) }
                FuncKey("⌫") { setDisplayText(getDisplayText().dropLast(1).ifEmpty { "0" }) }
            }
            CalcRow { DigitKey("7") { inputDigit("7") }; DigitKey("8"){inputDigit("8")}; DigitKey("9"){inputDigit("9")}; OpKey("−"){applyOp(Op.SUB)} }
            CalcRow { DigitKey("4") { inputDigit("4") }; DigitKey("5"){inputDigit("5")}; DigitKey("6"){inputDigit("6")}; OpKey("+"){applyOp(Op.ADD)} }
            CalcRow { DigitKey("1") { inputDigit("1") }; DigitKey("2"){inputDigit("2")}; DigitKey("3"){inputDigit("3")};
                // "=" surface : tap = calcul, long-press = secret/duress
                EqualKey(
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
            CalcRow {
                // 0 large, point, virgule
                BigDigitKey("0") { inputDigit("0") }
                DigitKey(".") { inputDot() }
                DigitKey(",") { /* séparateur visuel (placeholder) */ }
            }
        } else {
            // LANDSCAPE (scientifique – placeholder des fonctions)
            Text("Deg", color = MaterialTheme.colorScheme.onBackground, modifier = Modifier
                .padding(bottom = 8.dp)
                .align(Alignment.Start))
            val left = listOf("(", ")", "1/x", "MC", "M+", "M-", "MR")
            val second = listOf("x²", "x³", "C", "÷", "×", "⌫")
            val third = listOf("7", "8", "9", "−")
            val fourth = listOf("4", "5", "6", "+")
            val fifth = listOf("1", "2", "3", "=")
            val sixth = listOf("%", "0", ",")

            // 1ère ligne
            CalcRow {
                left.forEach { label ->
                    when (label) {
                        "C" -> FuncKey("C") { clearAll() }
                        "÷" -> OpKey("÷") { applyOp(Op.DIV) }
                        "×" -> OpKey("×") { applyOp(Op.MUL) }
                        "⌫" -> FuncKey("⌫") { setDisplayText(getDisplayText().dropLast(1).ifEmpty { "0" }) }
                        else -> MemKey(label) // placeholders
                    }
                }
            }
            CalcRow {
                second.forEach { l ->
                    when (l) {
                        "C" -> FuncKey("C") { clearAll() }
                        "÷" -> OpKey("÷") { applyOp(Op.DIV) }
                        "×" -> OpKey("×") { applyOp(Op.MUL) }
                        "⌫" -> FuncKey("⌫") { setDisplayText(getDisplayText().dropLast(1).ifEmpty { "0" }) }
                        else -> FuncKey(l) { /* TODO scientific */ }
                    }
                }
            }
            CalcRow { DigitKey("7"){inputDigit("7")}; DigitKey("8"){inputDigit("8")}; DigitKey("9"){inputDigit("9")}; OpKey("−"){applyOp(Op.SUB)} }
            CalcRow { DigitKey("4"){inputDigit("4")}; DigitKey("5"){inputDigit("5")}; DigitKey("6"){inputDigit("6")}; OpKey("+"){applyOp(Op.ADD)} }
            CalcRow {
                DigitKey("1"){inputDigit("1")}; DigitKey("2"){inputDigit("2")}; DigitKey("3"){inputDigit("3")}
                EqualKey(
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
            CalcRow {
                FuncKey("%") { // x% = x/100
                    val v = getDisplayText().toBigDecimalOrNull() ?: BigDecimal.ZERO
                    setDisplayText(v.divide(BigDecimal(100), mc).toPlainString().trimEndZeros())
                    resetOnNextDigit = true
                }
                BigDigitKey("0") { inputDigit("0") }
                DigitKey(",") { /* placeholder */ }
            }
        }
    }
}

@Composable private fun CalcRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
    Spacer(Modifier.height(8.dp))
}

@Composable private fun KeyBase(
    label: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
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
            Text(label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RowScope.DigitKey(label: String, onClick: () -> Unit) =
    KeyBase(label, modifier = Modifier.weight(1f), onClick = onClick)

@Composable
private fun RowScope.BigDigitKey(label: String, onClick: () -> Unit) =
    KeyBase(label, modifier = Modifier.weight(2f), onClick = onClick)

@Composable
private fun RowScope.OpKey(label: String, onClick: () -> Unit) =
    KeyBase(
        label,
        modifier = Modifier.weight(1f),
        container = MaterialTheme.colorScheme.tertiaryContainer,
        onClick = onClick
    )

@Composable
private fun RowScope.FuncKey(label: String, onClick: () -> Unit) =
    KeyBase(label, modifier = Modifier.weight(1f), onClick = onClick)

@Composable
private fun RowScope.MemKey(label: String) =
    KeyBase(label, modifier = Modifier.weight(1f), onClick = { /* TODO */ })

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.EqualKey(onTap: () -> Unit, onLong: () -> Unit) {
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


private fun String.trimEndZeros(): String =
    try {
        val s = this.toBigDecimal().stripTrailingZeros().toPlainString()
        if (s == "-0") "0" else s
    } catch (_: Throwable) { this }


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
