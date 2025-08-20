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
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
    var justDidEquals by remember { mutableStateOf(false) }


    // Affichage (texte saisi)
    var display by remember { mutableStateOf(TextFieldValue("0")) }

    // Moteur
    var accumulator by remember { mutableStateOf<BigDecimal?>(null) }
    var pendingOp by remember { mutableStateOf<Op?>(null) }
    var resetOnNextDigit by remember { mutableStateOf(false) }
    val mc = remember { MathContext(16, RoundingMode.HALF_UP) }

    // Pour afficher temporairement l'expression avant "="
    var lastExpr by remember { mutableStateOf<String?>(null) }
    var showEquals by remember { mutableStateOf(false) }

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


    /** Construit la ligne d’expression (ligne du haut) */
    // Construit la ligne d’expression
    fun expressionLine(): String {
        val cur = prettyText(getDisplayText())
        return if (pendingOp != null && accumulator != null) {
            val left = accumulator!!.stripTrailingZeros().toPlainString().replace('.', ',')
            if (resetOnNextDigit) {
                "$left ${opSymbol(pendingOp!!)}"   // on attend le 2e opérande → pas de 'cur'
            } else {
                "$left ${opSymbol(pendingOp!!)} $cur"
            }
        } else {
            cur
        }
    }




    /** Aperçu du résultat (ligne du bas) tant que l’expression est complète */
    // Renvoie l’aperçu du résultat uniquement si acc + op + second opérande sont présents
    fun previewResultOrEmpty(): String {
        val acc = accumulator ?: return ""
        val op  = pendingOp   ?: return ""
        // second opérande doit exister (et ne pas être simplement "0" parce qu’on n’a rien saisi)
        val rightTxt = getDisplayText()
        val right = rightTxt.replace(',', '.').toBigDecimalOrNull() ?: return ""
        // si resetOnNextDigit est encore vrai, on n’a pas commencé le deuxième opérande
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
        lastExpr = null
        showEquals = false
    }

    fun applyOp(op: Op) {
        try {
            val current = getDisplayText().replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO
            val acc = accumulator

            if (acc == null) {
                // premier opérande
                accumulator = current
            } else if (pendingOp != null) {
                // évalue acc (op) current pour chaînage
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

            // Était-on dans un état "calcul possible" ? (acc + op + second opérande)
            val canCompute = acc != null && op != null && !resetOnNextDigit

            if (acc != null && op != null) {
                val result = when (op) {
                    Op.ADD -> acc.add(current, mc)
                    Op.SUB -> acc.subtract(current, mc)
                    Op.MUL -> acc.multiply(current, mc)
                    Op.DIV -> if (current == BigDecimal.ZERO) BigDecimal.ZERO else acc.divide(current, mc)
                }
                // le résultat devient la nouvelle "entrée"
                setDisplayText(formatForDisplay(result))
                accumulator = null
                pendingOp = null
                resetOnNextDigit = false

                // N’arme l’animation que si un résultat était réellement affichable
                if (canCompute) {
                    justDidEquals = true
                }
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
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        // ===== Afficheur compact, résultat plus grand et centrage vertical =====
        val exprFontSize  = if (isLandscape) 52.sp else 56.sp
        val resultFontSize = if (isLandscape) 48.sp else 52.sp
        val cardPaddingH = 16.dp
        val cardPaddingV = 12.dp

        val displayHeight = if (isLandscape) 92.dp else 300.dp // ajuste librement (ex: 80–120.dp)
        val displayPaddingTop = if (isLandscape) 8.dp else 20.dp

        Box(
            modifier = Modifier
                .padding(top = displayPaddingTop)
                .fillMaxWidth()
                .height(displayHeight) // <- hauteur fixe
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = cardPaddingH, vertical = cardPaddingV),
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.End
            ) {
                // --- Ligne 1 : expression (ou texte courant) ---
                AnimatedContent(
                    targetState = expressionLine(),
                    transitionSpec = {
                        if (justDidEquals) {
                            slideInVertically(animationSpec = tween(180)) { h -> +h } togetherWith
                                    slideOutVertically(animationSpec = tween(180)) { h -> -h }
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

                // --- Ligne 2 : prévisualisation résultat (plus grande) ---
                val preview = previewResultOrEmpty()
                AnimatedContent(
                    targetState = preview,
                    transitionSpec = {
                        if (justDidEquals) {
                            slideInVertically(animationSpec = tween(160)) { h -> +h } togetherWith
                                    slideOutVertically(animationSpec = tween(160)) { h -> -h }
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

        Spacer(Modifier.height(12.dp))

// Le pavé de touches prend tout l’espace restant
        Column(
            modifier = Modifier.weight(1f)
        ) {
            if (!isLandscape) {
                // ======== PORTRAIT ========
                CalcRow { MemKey("MC"); MemKey("M+"); MemKey("M-"); MemKey("MR") }
                CalcRow {
                    FuncKey("C") { clearAll() }
                    OpKey("÷") { applyOp(Op.DIV) }
                    OpKey("×") { applyOp(Op.MUL) }
                    FuncKey("⌫") { backspace() }
                }
                CalcRow { DigitKey("7"){inputDigit("7")}; DigitKey("8"){inputDigit("8")}; DigitKey("9"){inputDigit("9")}; OpKey("−"){applyOp(Op.SUB)} }
                CalcRow { DigitKey("4"){inputDigit("4")}; DigitKey("5"){inputDigit("5")}; DigitKey("6"){inputDigit("6")}; OpKey("+"){applyOp(Op.ADD)} }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = spacedBy(KeyPadding)
                ) {
                    Column(
                        modifier = Modifier.weight(3f),
                        verticalArrangement = spacedBy(KeyPadding)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = spacedBy(KeyPadding)) {
                            DigitKey("1") { inputDigit("1") }
                            DigitKey("2") { inputDigit("2") }
                            DigitKey("3") { inputDigit("3") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = spacedBy(KeyPadding)) {
                            BigDigitKey("0") { inputDigit("0") }
                            DigitKey(".") { inputDot() }
                        }
                    }
                    EqualKeyTall(
                        modifier = Modifier.weight(0.94f),
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
                Spacer(Modifier.height(KeyPadding))
            } else {
                // ======== PAYSAGE (scientifique light) ========
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
    LaunchedEffect(justDidEquals) {
        if (justDidEquals) {
            delay(200)   // un chouïa > aux tweens (180 ms)
            justDidEquals = false
        }
    }

}

/* =============== Composants de touches =============== */

private val MemTextBlue = Color(0xFF4C6FFF)
private val KeyDark = Color(0xFF1A1F27)   // fond gris foncé
private val KeyLight = Color(0xFF3A404D)  // fond gris clair (opérateurs)

val KeyHeight = 80.dp
val KeyPadding = 8.dp

@Composable
private fun CalcRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = spacedBy(KeyPadding),
        content = content
    )
    Spacer(Modifier.height(KeyPadding))
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
        modifier = modifier.height(KeyHeight),
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
    val tallHeight = KeyHeight * 2 + KeyPadding
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
