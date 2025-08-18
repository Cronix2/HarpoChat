package com.example.harpochat.calculator

import android.content.Intent
import androidx.core.content.edit
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.harpochat.MainActivity
import com.example.harpochat.security.SecureStore
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class CalculatorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Anti-screenshot (optionnel mais recommandé)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        val prefs = SecureStore.prefs(this)
        if (!prefs.contains(KEY_SECRET_PIN)) prefs.edit { putString(KEY_SECRET_PIN, "527418") }
        if (!prefs.contains(KEY_DURESS_PIN)) prefs.edit { putString(KEY_DURESS_PIN, "0000") }

        setContent {
            MaterialTheme {
                Surface {
                    CalculatorScreen(
                        onUnlock = { startActivity(Intent(this, MainActivity::class.java)) },
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
    var display by remember { mutableStateOf(TextFieldValue("0")) }
    var accumulator by remember { mutableStateOf<BigDecimal?>(null) }
    var pendingOp by remember { mutableStateOf<Op?>(null) }
    var resetOnNextDigit by remember { mutableStateOf(false) }
    val mc = remember { MathContext(16, RoundingMode.HALF_UP) }

    fun setDisplayText(s: String) { display = TextFieldValue(s) }
    fun getDisplayText(): String = display.text

    fun inputDigit(d: String) {
        val cur = getDisplayText()
        val next = if (resetOnNextDigit || cur == "0") d else cur + d
        setDisplayText(next)
        resetOnNextDigit = false
    }

    fun inputDot() {
        val cur = getDisplayText()
        if (!cur.contains(".")) setDisplayText("$cur.")
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
                    Op.DIV -> if (current.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO
                    else acc.divide(current, mc)
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
                    Op.DIV -> if (current.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO
                    else acc.divide(current, mc)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Afficheur lisible et sûr (lecture seule)
        OutlinedTextField(
            value = display,
            onValueChange = { /* readonly */ },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            readOnly = true,
            singleLine = true,
            textStyle = MaterialTheme.typography.displayMedium.copy(
                textAlign = TextAlign.End,
                fontWeight = FontWeight.SemiBold
            ),
            label = { Text("Calculator") }
        )

        Spacer(Modifier.height(12.dp))

        // Rangée 1
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcButton("C", Modifier.weight(1f)) { clearAll() }
            CalcButton("%", Modifier.weight(1f)) {
                val v = getDisplayText().toBigDecimalOrNull() ?: BigDecimal.ZERO
                setDisplayText(v.divide(BigDecimal(100), mc).toPlainString().trimEndZeros())
                resetOnNextDigit = true
            }
            CalcButton("+", Modifier.weight(1f)) { applyOp(Op.ADD) }
        }
        Spacer(Modifier.height(8.dp))

        // Rangée 2
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcButton("7", Modifier.weight(1f)) { inputDigit("7") }
            CalcButton("8", Modifier.weight(1f)) { inputDigit("8") }
            CalcButton("9", Modifier.weight(1f)) { inputDigit("9") }
            CalcButton("×", Modifier.weight(1f)) { applyOp(Op.MUL) }
        }
        Spacer(Modifier.height(8.dp))

        // Rangée 3
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcButton("4", Modifier.weight(1f)) { inputDigit("4") }
            CalcButton("5", Modifier.weight(1f)) { inputDigit("5") }
            CalcButton("6", Modifier.weight(1f)) { inputDigit("6") }
            CalcButton("−", Modifier.weight(1f)) { applyOp(Op.SUB) }
        }
        Spacer(Modifier.height(8.dp))

        // Rangée 4
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcButton("1", Modifier.weight(1f)) { inputDigit("1") }
            CalcButton("2", Modifier.weight(1f)) { inputDigit("2") }
            CalcButton("3", Modifier.weight(1f)) { inputDigit("3") }
            CalcButton("÷", Modifier.weight(1f)) { applyOp(Op.DIV) }
        }
        Spacer(Modifier.height(8.dp))

        // Rangée finale (0, ., =)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcButton("0", Modifier.weight(2f)) { inputDigit("0") }
            CalcButton(".", Modifier.weight(1f)) { inputDot() }

            // "=" : tap = calcul ; long-press = déverrouillage/duress
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .combinedClickable(
                        onClick = { equalsNormal() },
                        onLongClick = {
                            when (validatePins(getDisplayText())) {
                                PinResult.SECRET -> onUnlock()
                                PinResult.DURESS -> onDuress()
                                PinResult.NO_MATCH -> { /* façade seulement */ }
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                ElevatedButton(onClick = { equalsNormal() }, modifier = Modifier.fillMaxSize()) {
                    Text("=")
                }
            }
        }
    }
}

@Composable
private fun CalcButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ElevatedButton(onClick = onClick, modifier = modifier.height(56.dp)) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

private fun String.trimEndZeros(): String =
    try {
        val s = this.toBigDecimal().stripTrailingZeros().toPlainString()
        if (s == "-0") "0" else s
    } catch (_: Throwable) { this }
