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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.example.harpochat.messaging.ConversationsActivity
import com.example.harpochat.security.SecureStore
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.*

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

/* ====== État ====== */
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

    // ====== Moteur "expression" ======
    var expr by remember { mutableStateOf("") }            // ligne du haut
    var angleInRadians by remember { mutableStateOf(false) } // toggle Rad
    fun setExpr(s: String) { expr = s }
    fun digitsOnly(): String = expr.filter { it.isDigit() } // pour PIN long-press "="

    // Helpers d’édition
    fun insertRaw(s: String) { expr += s }
    fun digit(d: String) {
        if (expr == "0") expr = d else expr += d
    }
    fun dot() {
        // On autorise "." si le segment numérique courant n’en a pas déjà
        val lastNum = Regex("""\d+(?:[.,]\d+)?$""").find(expr)?.value ?: ""

        if (!lastNum.contains('.') && !lastNum.contains(',')) {
            expr += "."
        }
    }

    fun binOp(op: String) {
        if (expr.isBlank()) return
        val t = expr.trimEnd()

        // Est-ce que l'expression se termine déjà par un opérateur ?
        val endsWithOp = t.isNotEmpty() && t.last() in charArrayOf(
            '+', '-', '×', '÷', '*', '/', '^'
        )

        expr = if (endsWithOp) {
            // remplace l'opérateur de fin
            t.dropLast(1) + " $op "
        } else {
            // ajoute l'opérateur
            "$t $op "
        }
    }

    fun funcPrefix(name: String) { expr += "$name(" }
    fun openParen() { expr += "(" }
    fun closeParen() { expr += ")" }
    fun clearAll() { expr = "" }
    fun backspace() { if (expr.isNotEmpty()) expr = expr.dropLast(1) }

    fun equalsNormal() {
        val r = tryEvaluate(expr, angleInRadians)
        if (r.isNotEmpty()) {
            setExpr(r)         // le résultat devient l’expression courante
            justDidEquals = true
        }
    }

    // ====== UI ======
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.End
    ) {
        // 5% haut, 25% display, 65% clavier, 3% bas, 2% espace
        val weightDisplay = 0.25f
        val weightClavier = 0.65f
        val weightBas = 0.05f
        val weightHaut = 0.05f
        val weightEspace = 0.02f

        Spacer(Modifier.weight(weightHaut))

        val exprFontSize  = if (isLandscape) 52.sp else 56.sp
        val resultFontSize = if (isLandscape) 48.sp else 52.sp

        // ====== Display ======
        Box(
            modifier = Modifier
                .weight(weightDisplay)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.End
            ) {
                AnimatedContent(
                    targetState = expr.ifBlank { "0" },
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
                        text = text.replace('.', ','),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = exprFontSize),
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(6.dp))

                val preview = tryEvaluate(expr, angleInRadians)
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

        Spacer(Modifier.weight(weightEspace))

        // ====== Clavier ======
        Column(
            modifier = Modifier
                .weight(weightClavier)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(KeyPadding)
        ) {
            if (!isLandscape) {
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KeyPadding)
                ) {
                    MemKey("MC"); MemKey("M+"); MemKey("M-"); MemKey("MR")
                }
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KeyPadding)
                ) {
                    FuncKey("C") { clearAll() }
                    OpKey("÷") { binOp("÷") }
                    OpKey("×") { binOp("×") }
                    FuncKey("⌫") { backspace() }
                }
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KeyPadding)
                ) {
                    DigitKey("7") { digit("7") }; DigitKey("8") { digit("8") }; DigitKey("9") { digit("9") }; OpKey("−") { binOp("-") }
                }
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KeyPadding)
                ) {
                    DigitKey("4") { digit("4") }; DigitKey("5") { digit("5") }; DigitKey("6") { digit("6") }; OpKey("+") { binOp("+") }
                }
                Row(
                    Modifier.weight(2f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KeyPadding)
                ) {
                    Column(
                        modifier = Modifier.weight(3f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(KeyPadding)
                    ) {
                        Row(
                            Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(KeyPadding)
                        ) {
                            DigitKey("1") { digit("1") }
                            DigitKey("2") { digit("2") }
                            DigitKey("3") { digit("3") }
                        }
                        Row(
                            Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(KeyPadding)
                        ) {
                            BigDigitKey("0") { digit("0") }
                            DigitKey(".") { dot() }
                        }
                    }
                    EqualKeyTall(
                        modifier = Modifier.weight(1f).padding(start = 8.dp).fillMaxHeight(),
                        onTap = { equalsNormal() },
                        onLong = {
                            when (validatePins(digitsOnly().trim())) {
                                PinResult.SECRET -> onUnlock()
                                PinResult.DURESS -> onDuress()
                                PinResult.NO_MATCH -> {}
                            }
                        }
                    )
                }
            } else {
                // ======== Scientifique paysage ========
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyPadding)) {
                    FuncKey("(") { openParen() }
                    FuncKey(")") { closeParen() }
                    FuncKey("1/x") { insertRaw("1/(") }           // puis saisir x et ")"
                    MemKey("MC"); MemKey("M+"); MemKey("M-"); MemKey("MR")
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyPadding)) {
                    FuncKey("x²") { insertRaw("^2") }             // à poser après un nombre (ex: 5^2)
                    FuncKey("x³") { insertRaw("^3") }
                    FuncKey("xʸ") { binOp("^") }
                    FuncKey("C")  { clearAll() }
                    OpKey("÷")    { binOp("÷") }
                    OpKey("×")    { binOp("×") }
                    FuncKey("⌫")  { backspace() }
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyPadding)) {
                    FuncKey("x!") { funcPrefix("fact") }          // fact(
                    FuncKey("√")  { funcPrefix("sqrt") }          // sqrt(
                    FuncKey("ʸ√X"){ insertRaw("^(1/(") }         // saisie de y puis ")" puis X
                    DigitKey("7"){ digit("7") }; DigitKey("8"){ digit("8") }; DigitKey("9"){ digit("9") }; OpKey("−"){ binOp("-") }
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyPadding)) {
                    FuncKey("e")  { insertRaw("e") }              // constante e
                    FuncKey("ln") { funcPrefix("ln") }
                    FuncKey("lg") { funcPrefix("lg") }
                    DigitKey("4"){ digit("4") }; DigitKey("5"){ digit("5") }; DigitKey("6"){ digit("6") }; OpKey("+"){ binOp("+") }
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyPadding)) {
                    FuncKey("sin") { funcPrefix("sin") }
                    FuncKey("cos") { funcPrefix("cos") }
                    FuncKey("tan") { funcPrefix("tan") }
                    DigitKey("1"){ digit("1") }; DigitKey("2"){ digit("2") }; DigitKey("3"){ digit("3") }
                    EqualKey(onTap = { equalsNormal() }, onLong = {
                        when (validatePins(digitsOnly().trim())) {
                            PinResult.SECRET -> onUnlock()
                            PinResult.DURESS -> onDuress()
                            PinResult.NO_MATCH -> {}
                        }
                    })
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeyPadding)) {
                    // Inv -> autre raccourci 1/x, Rad -> toggle radians/degrés, π, %, 0, .
                    FuncKey(if (angleInRadians) "Rad" else "Deg") { angleInRadians = !angleInRadians }
                    FuncKey("Inv") { insertRaw("1/(") }
                    FuncKey("π")   { insertRaw("π") }
                    DigitKey("%")  { insertRaw("/100") }           // pourcentage comme /100
                    DigitKey("0")  { digit("0") }
                    DigitKey(".")  { dot() }
                    EqualKey(onTap = { equalsNormal() }, onLong = {
                        when (validatePins(digitsOnly().trim())) {
                            PinResult.SECRET -> onUnlock()
                            PinResult.DURESS -> onDuress()
                            PinResult.NO_MATCH -> {}
                        }
                    })
                }
            }
        }

        Spacer(Modifier.weight(weightBas))
    }

    LaunchedEffect(justDidEquals) {
        if (justDidEquals) {
            delay(200)
            justDidEquals = false
        }
    }
}

/* =============== Parseur / Évaluateur =============== */

private sealed interface Tok
private data class Num(val v: Double): Tok
private data class OpTok(val op: String): Tok   // + - * / ^ (× ÷ mappés)
private data class FuncTok(val name: String): Tok // sin cos tan ln lg sqrt fact
private data class Paren(val open: Boolean): Tok
private object ConstPi : Tok
private object ConstE  : Tok

private val OP_PRECEDENCE = mapOf(
    "+" to 1, "-" to 1,
    "*" to 2, "/" to 2,
    "^" to 3
)
private fun rightAssoc(op: String) = op == "^"

private fun isCompleteExpression(s: String): Boolean {
    if (s.isBlank()) return false
    var bal = 0
    for (c in s) {
        if (c == '(') bal++
        if (c == ')') bal--
        if (bal < 0) return false
    }
    if (bal != 0) return false
    val end = s.trim().lastOrNull() ?: return false
    return end.isDigit() || end == ')' || end == 'π' || end == 'e'
}

private fun tokenize(raw: String): List<Tok> {
    val src = raw
        .replace('×','*')
        .replace('÷','/')
        .replace(',','.')
        .replace("＋","+").replace("−","-")

    val out = mutableListOf<Tok>()
    var i = 0
    while (i < src.length) {
        val c = src[i]
        when {
            c.isWhitespace() -> i++
            c.isDigit() || c=='.' -> {
                val start = i
                i++
                while (i < src.length && (src[i].isDigit() || src[i]=='.')) i++
                out += Num(src.substring(start, i).toDouble())
            }
            c == '(' -> { out += Paren(true); i++ }
            c == ')' -> { out += Paren(false); i++ }
            c == 'π' -> { out += ConstPi; i++ }
            c == 'e' -> { out += ConstE; i++ }
            "+-*/^".contains(c) -> { out += OpTok(c.toString()); i++ }
            else -> {
                // Fonctions: sin cos tan ln lg sqrt fact
                val names = listOf("sin","cos","tan","ln","lg","sqrt","fact")
                val match = names.firstOrNull { src.regionMatches(i, it, 0, it.length, ignoreCase = true) }
                if (match != null) {
                    out += FuncTok(match.lowercase())
                    i += match.length
                } else {
                    // caractère inconnu -> on ignore
                    i++
                }
            }
        }
    }
    return out
}

private fun toRpn(tokens: List<Tok>): List<Tok> {
    val out = mutableListOf<Tok>()
    val stack = ArrayDeque<Tok>()
    for (t in tokens) {
        when (t) {
            is Num, is ConstPi, is ConstE -> out += t
            is FuncTok -> stack.addFirst(t)
            is OpTok -> {
                while (stack.isNotEmpty()) {
                    val top = stack.first()
                    if (top is OpTok) {
                        val pTop = OP_PRECEDENCE[top.op] ?: -1
                        val pCur = OP_PRECEDENCE[t.op] ?: -1
                        val cond = if (rightAssoc(t.op)) (pCur < pTop) else (pCur <= pTop)
                        if (cond) out += stack.removeFirst() else break
                    } else if (top is FuncTok) {
                        out += stack.removeFirst()
                    } else break
                }
                stack.addFirst(t)
            }
            is Paren -> {
                if (t.open) stack.addFirst(t) else {
                    while (stack.isNotEmpty() && stack.first() !is Paren) {
                        out += stack.removeFirst()
                    }
                    if (stack.isNotEmpty() && stack.first() is Paren) stack.removeFirst()
                    if (stack.isNotEmpty() && stack.first() is FuncTok) out += stack.removeFirst()
                }
            }
        }
    }
    while (stack.isNotEmpty()) out += stack.removeFirst()
    return out
}

private fun evalRpn(rpn: List<Tok>, radians: Boolean): Double {
    val st = ArrayDeque<Double>()
    for (t in rpn) {
        when (t) {
            is Num -> st.addFirst(t.v)
            is ConstPi -> st.addFirst(Math.PI)
            is ConstE  -> st.addFirst(Math.E)
            is OpTok -> {
                val b = st.removeFirst()
                val a = st.removeFirst()
                val v = when (t.op) {
                    "+" -> a + b
                    "-" -> a - b
                    "*" -> a * b
                    "/" -> a / b
                    "^" -> a.pow(b)
                    else -> Double.NaN
                }
                st.addFirst(v)
            }
            is FuncTok -> {
                val x = st.removeFirst()
                val v = when (t.name) {
                    "sin"  -> if (radians) sin(x) else sin(Math.toRadians(x))
                    "cos"  -> if (radians) cos(x) else cos(Math.toRadians(x))
                    "tan"  -> if (radians) tan(x) else tan(Math.toRadians(x))
                    "ln"   -> ln(x)
                    "lg"   -> log10(x)
                    "sqrt" -> sqrt(x)
                    "fact" -> {
                        if (x < 0 || x > 170) Double.NaN
                        else (1..x.toInt()).fold(1.0) { acc, i -> acc * i }
                    }
                    else -> Double.NaN
                }
                st.addFirst(v)
            }
            is Paren -> error("Paren in RPN")
        }
    }
    return st.first()
}

private fun tryEvaluate(s: String, radians: Boolean): String {
    return try {
        if (!isCompleteExpression(s)) "" else {
            val v = evalRpn(toRpn(tokenize(s)), radians)
            val bd = BigDecimal(v).stripTrailingZeros()
            val abs = bd.abs()
            val useSci = (abs > BigDecimal("1000000000")) ||
                    (abs != BigDecimal.ZERO && abs < BigDecimal("0.000001"))
            if (useSci) {
                val symbols = DecimalFormatSymbols.getInstance(Locale.FRANCE)
                val fmt = DecimalFormat("0.######E0", symbols)
                fmt.format(v)
            } else {
                bd.toPlainString().replace('.', ',')
            }
        }
    } catch (_: Throwable) { "" }
}

/* =============== Composants de touches =============== */

private val MemTextBlue = Color(0xFF4C6FFF)
private val KeyDark = Color(0xFF1A1F27)
private val KeyLight = Color(0xFF3A404D)

val KeyPadding = 8.dp

@Composable
private fun RowScope.DigitKey(label: String, onClick: () -> Unit) =
    KeyBase(label, modifier = Modifier.weight(1f).fillMaxHeight(), onClick = onClick)

@Composable
private fun RowScope.BigDigitKey(label: String, onClick: () -> Unit) =
    KeyBase(label, modifier = Modifier.weight(2f).fillMaxHeight(), onClick = onClick)

@Composable
private fun RowScope.OpKey(label: String, onClick: () -> Unit) =
    KeyBase(label, modifier = Modifier.weight(1f).fillMaxHeight(), container = KeyLight, onClick = onClick)

@Composable
private fun RowScope.FuncKey(label: String, onClick: () -> Unit) =
    KeyBase(label, modifier = Modifier.weight(1f).fillMaxHeight(), container = KeyLight, onClick = onClick)

@Composable
private fun RowScope.MemKey(label: String) =
    KeyBase(label, modifier = Modifier.weight(1f).fillMaxHeight(), container = KeyDark, contentColor = MemTextBlue, onClick = { })

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun RowScope.EqualKey(onTap: () -> Unit, onLong: () -> Unit) {
    Surface(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        onClick = onTap
    ) {
        Box(
            Modifier.fillMaxSize().combinedClickable(onClick = onTap, onLongClick = onLong),
            contentAlignment = Alignment.Center
        ) {
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
        Box(
            Modifier.fillMaxSize().combinedClickable(onClick = onTap, onLongClick = onLong),
            contentAlignment = Alignment.Center
        ) {
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
