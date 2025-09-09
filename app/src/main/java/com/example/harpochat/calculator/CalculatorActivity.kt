package com.example.harpochat.calculator

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Window
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.example.harpochat.messaging.ConversationsActivity
import com.example.harpochat.security.SecureStore
import kotlinx.coroutines.delay
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/* =========================
 *  Activité Calculatrice
 * ========================= */
class CalculatorActivity : ComponentActivity() {

    // Récupération du ViewModel (fourni par hilt, factory, ou par défaut)
    private val calcViewModel: CalculatorViewModel by viewModels()

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
                        viewModel = calcViewModel,
                        onUnlock = {
                            // Par sécurité, on nettoie l’écran après unlock
                            calcViewModel.clearAll()
                            startActivity(Intent(this, ConversationsActivity::class.java))
                        },
                        onDuress = {
                            // Purge stockage + clear écran
                            prefs.edit { clear() }
                            calcViewModel.clearAll()
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

/** Ajoute des espaces fines insécables entre les milliers: 1234567.89 -> 1 234 567.89 */
private fun groupThousands(num: String): String {
    // on ne touche pas aux notations scientifiques
    if (num.contains('E') || num.contains('e')) return num
    val parts = num.split('.', limit = 2)
    val intPart = parts[0]
    val decPart = if (parts.size > 1) parts[1] else null

    val sb = StringBuilder()
    var count = 0
    for (i in intPart.length - 1 downTo 0) {
        sb.append(intPart[i])
        count++
        if (count == 3 && i > 0) {
            sb.append('\u202F') // espace fine insécable
            count = 0
        }
    }
    val groupedInt = sb.reverse().toString()
    return if (decPart != null) "$groupedInt.$decPart" else groupedInt
}

/** Remplace chaque nombre (\\d+(\\.\\d+)?) de l'expression par une version groupée */
private fun prettifyExpression(expr: String): String {
    if (expr.isBlank()) return "0"
    val numberRegex = Regex("""\d+(?:\.\d+)?""")
    return numberRegex.replace(expr) { m -> groupThousands(m.value) }
}

/** Préparation du texte de preview (résultat) pour l’affichage */
private fun prettifyResult(res: String): String {
    if (res.isBlank()) return res
    // gère 1.23E9 -> on groupe seulement la partie mantisse AVANT 'E'
    return if (res.contains('E')) {
        val i = res.indexOf('E')
        groupThousands(res.substring(0, i)) + res.substring(i)
    } else {
        groupThousands(res)
    }
}

@Composable
private fun exprFontFor(len: Int, landscape: Boolean): Float {
    return when {
        len <= 14 -> if (landscape) 52f else 56f
        len <= 22 -> if (landscape) 44f else 48f
        len <= 32 -> if (landscape) 36f else 40f
        len <= 48 -> if (landscape) 30f else 32f
        else      -> if (landscape) 26f else 28f
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    onUnlock: () -> Unit,
    onDuress: () -> Unit,
    validatePins: (String) -> PinResult
) {
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // États exposés par le ViewModel
    val expressionText by viewModel.expression.collectAsState()
    val previewText by viewModel.preview.collectAsState()
    val invMode by viewModel.invMode.collectAsState()
    val radMode by viewModel.radMode.collectAsState()

    // Animation “just did equals”
    var justDidEquals by remember { mutableStateOf(false) }

    // Pour la vérification PIN, on reprend uniquement les chiffres de l’expression
    fun digitsOnlyFromExpression(): String = expressionText.filter { it.isDigit() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.End
    ) {
        // Layout weights : ~5% top spacer, 25% display, 65% keyboard, ~5% bottom spacer
        val weightDisplay = 0.25f
        val weightClavier = 0.65f
        val weightBas = 0.05f
        val weightHaut = 0.05f
        val weightEspace = 0.02f

        Spacer(Modifier.weight(weightHaut))

        val exprSize = exprFontFor(expressionText.length, isLandscape).sp
        val resultFontSize = if (isLandscape) 36.sp else 40.sp

        // ====== Display ======
        Box(
            modifier = Modifier
                .weight(weightDisplay)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            // Mesure de la largeur disponible (en px) SANS BoxWithConstraints
            var maxWidthPx by remember { mutableFloatStateOf(0f) }
            val containerMod = Modifier
                .fillMaxSize()
                .onSizeChanged { maxWidthPx = it.width.toFloat() }

            @OptIn(ExperimentalTextApi::class)
            val textMeasurer = rememberTextMeasurer()

            Column(
                modifier = containerMod,
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.End
            ) {
                // -------- Expression (grouping + autosize synchrone + scroll si nécessaire) --------
                AnimatedContent(
                    targetState = expressionText,
                    transitionSpec = {
                        if (justDidEquals) {
                            slideInVertically(tween(180)) { +it } togetherWith
                                    slideOutVertically(tween(180)) { -it }
                        } else {
                            EnterTransition.None togetherWith ExitTransition.None
                        }
                    },
                    label = "expr-anim"
                ) { raw ->
                    val pretty = prettifyExpression(raw.ifBlank { "0" })

                    val minSp = if (isLandscape) 26f else 28f
                    val maxSp = 56f
                    val baseStyle = MaterialTheme.typography.headlineLarge

                    fun fits(fs: Float): Boolean {
                        if (maxWidthPx <= 0f) return true // pas encore mesuré -> éviter un saut
                        val layout = textMeasurer.measure(
                            text = AnnotatedString(pretty),
                            style = baseStyle.copy(fontSize = fs.sp),
                            maxLines = 1
                        )
                        return layout.size.width <= maxWidthPx
                    }

                    // Recherche binaire -> taille correcte AVANT affichage
                    var lo = minSp
                    var hi = maxSp
                    var chosen = minSp
                    repeat(12) {
                        val mid = (lo + hi) / 2f
                        if (fits(mid)) { chosen = mid; lo = mid } else { hi = mid }
                    }

                    val overflowEvenAtMin = !fits(minSp)
                    val scroll = rememberScrollState()
                    LaunchedEffect(pretty, overflowEvenAtMin) {
                        if (overflowEvenAtMin) scroll.scrollTo(scroll.maxValue)
                    }

                    Text(
                        text = pretty,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        style = baseStyle.copy(fontSize = chosen.sp),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (overflowEvenAtMin) Modifier.horizontalScroll(scroll) else Modifier)
                    )
                }

                Spacer(Modifier.height(6.dp))

                // -------- Preview (hauteur fixe = pas de "saut") --------
                val previewHeight = if (isLandscape) 44.dp else 52.dp
                Box(
                    Modifier.fillMaxWidth().height(previewHeight),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    AnimatedContent(
                        targetState = previewText,
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
                                text = prettifyResult(txt),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = (if (isLandscape) 36.sp else 40.sp)
                                ),
                                maxLines = 1,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Spacer(Modifier)
                        }
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
                // Portrait : simple + "=" vertical
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
                    FuncKey("C") { viewModel.clearAll() }
                    OpKey("÷") { viewModel.addBinaryOp("÷") }
                    OpKey("×") { viewModel.addBinaryOp("×") }
                    FuncKey("⌫") { viewModel.backspace() }
                }
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KeyPadding)
                ) {
                    DigitKey("7") { viewModel.addDigit('7') }
                    DigitKey("8") { viewModel.addDigit('8') }
                    DigitKey("9") { viewModel.addDigit('9') }
                    OpKey("−") { viewModel.addBinaryOp("−") }
                }
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KeyPadding)
                ) {
                    DigitKey("4") { viewModel.addDigit('4') }
                    DigitKey("5") { viewModel.addDigit('5') }
                    DigitKey("6") { viewModel.addDigit('6') }
                    OpKey("+") { viewModel.addBinaryOp("+") }
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
                            DigitKey("1") { viewModel.addDigit('1') }
                            DigitKey("2") { viewModel.addDigit('2') }
                            DigitKey("3") { viewModel.addDigit('3') }
                        }
                        Row(
                            Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(KeyPadding)
                        ) {
                            BigDigitKey("0") { viewModel.addDigit('0') }
                            DigitKey(".") { viewModel.addDot() }
                        }
                    }
                    EqualKeyTall(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                            .fillMaxHeight(),
                        onTap = {
                            viewModel.evaluate()
                            justDidEquals = true
                        },
                        onLong = {
                            when (validatePins(digitsOnlyFromExpression().trim())) {
                                PinResult.SECRET -> onUnlock()
                                PinResult.DURESS -> onDuress()
                                PinResult.NO_MATCH -> {}
                            }
                        }
                    )
                }
            } else {
                // ======== Scientifique paysage ========
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KeyPadding)
                ) {
                    FuncKey("(") { viewModel.addLeftParen() }
                    FuncKey(")") { viewModel.addRightParen() }
                    FuncKey("±") { viewModel.toggleSignOfLastNumber() }
                    FuncKey("1/x") { viewModel.reciprocalOfLastTerm() }
                    MemKey("MC"); MemKey("M+"); MemKey("M-"); MemKey("MR")
                }
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KeyPadding)
                ) {
                    FuncKey("x²") { viewModel.addBinaryOp("^"); viewModel.addDigit('2') }
                    FuncKey("x³") { viewModel.addBinaryOp("^"); viewModel.addDigit('3') }
                    FuncKey("xʸ") { viewModel.addBinaryOp("^") }
                    FuncKey("C") { viewModel.clearAll() }
                    OpKey("÷") { viewModel.addBinaryOp("÷") }
                    OpKey("×") { viewModel.addBinaryOp("×") }
                    FuncKey("⌫") { viewModel.backspace() }
                }
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KeyPadding)
                ) {
                    FuncKey("x!") { viewModel.addFactorial() }
                    FuncKey("√") { viewModel.addFunction("sqrt") }
                    FuncKey("ʸ√X") {
                        // y-th root of X == X^(1/y) -> on insère ^ puis (1/ … )
                        viewModel.addBinaryOp("^")
                        viewModel.addLeftParen()
                        viewModel.addDigit('1')
                        viewModel.addBinaryOp("÷")
                        // l’utilisateur saisira y, puis on lui laisse fermer “)”
                    }
                    DigitKey("7") { viewModel.addDigit('7') }
                    DigitKey("8") { viewModel.addDigit('8') }
                    DigitKey("9") { viewModel.addDigit('9') }
                    OpKey("−") { viewModel.addBinaryOp("−") }
                }
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KeyPadding)
                ) {
                    FuncKey("e") { viewModel.addConstant("e") }
                    FuncKey("ln") { viewModel.addFunction("ln") }
                    FuncKey("lg") { viewModel.addFunction("log10") }
                    DigitKey("4") { viewModel.addDigit('4') }
                    DigitKey("5") { viewModel.addDigit('5') }
                    DigitKey("6") { viewModel.addDigit('6') }
                    OpKey("+") { viewModel.addBinaryOp("+") }
                }

                // ✅ Fusion des 2 dernières rangées : 6 cases à gauche, 1 case "=" à droite
                Row(
                    Modifier.weight(2f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KeyPadding)
                ) {
                    // 6 cases (2 rangées x 6 touches)
                    Column(
                        modifier = Modifier.weight(6f).fillMaxHeight(), // <<--- 6f (au lieu de 3f)
                        verticalArrangement = Arrangement.spacedBy(KeyPadding)
                    ) {
                        Row(
                            Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(KeyPadding)
                        ) {
                            FuncKey(if (invMode) "sin⁻¹" else "sin") {
                                viewModel.addFunction(if (invMode) "asin" else "sin")
                            }
                            FuncKey(if (invMode) "cos⁻¹" else "cos") {
                                viewModel.addFunction(if (invMode) "acos" else "cos")
                            }
                            FuncKey(if (invMode) "tan⁻¹" else "tan") {
                                viewModel.addFunction(if (invMode) "atan" else "tan")
                            }
                            DigitKey("1") { viewModel.addDigit('1') }
                            DigitKey("2") { viewModel.addDigit('2') }
                            DigitKey("3") { viewModel.addDigit('3') }
                        }
                        Row(
                            Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(KeyPadding)
                        ) {
                            FuncKey(if (radMode) "Rad" else "Deg") { viewModel.toggleAngleMode() }
                            FuncKey("Inv") { viewModel.toggleInvMode() }
                            FuncKey("π") { viewModel.addConstant("π") }
                            DigitKey("%") {
                                viewModel.addBinaryOp("÷")
                                viewModel.addDigit('1'); viewModel.addDigit('0'); viewModel.addDigit('0')
                            }
                            DigitKey("0") { viewModel.addDigit('0') }
                            DigitKey(".") { viewModel.addDot() }
                        }
                    }

                    // "=" occupe 1 case (même largeur qu’une touche)
                    EqualKeyTall(
                        modifier = Modifier
                            .weight(1f)                    // <<--- 1f (case unique)
                            .fillMaxHeight(),              // sur 2 rangées
                        onTap = {
                            viewModel.evaluate()
                            justDidEquals = true
                        },
                        onLong = {
                            when (validatePins(digitsOnlyFromExpression().trim())) {
                                PinResult.SECRET -> onUnlock()
                                PinResult.DURESS -> onDuress()
                                PinResult.NO_MATCH -> {}
                            }
                        }
                    )
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
    KeyBase(
        label,
        modifier = Modifier.weight(1f).fillMaxHeight(),
        container = KeyLight,
        onClick = onClick
    )

@Composable
private fun RowScope.FuncKey(label: String, onClick: () -> Unit) =
    KeyBase(
        label,
        modifier = Modifier.weight(1f).fillMaxHeight(),
        container = KeyLight,
        onClick = onClick
    )

@Composable
private fun RowScope.MemKey(label: String) =
    KeyBase(
        label,
        modifier = Modifier.weight(1f).fillMaxHeight(),
        container = KeyDark,
        contentColor = MemTextBlue,
        onClick = { /* mémoire à implémenter plus tard si besoin */ }
    )

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.EqualKey(onTap: () -> Unit, onLong: () -> Unit) {
    Surface(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        onClick = onTap
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .combinedClickable(onClick = onTap, onLongClick = onLong),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "=",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

/** “=” vertical qui remplit toute la hauteur disponible (deux rangées) */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EqualKeyTall(
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
            Modifier
                .fillMaxSize()
                .combinedClickable(onClick = onTap, onLongClick = onLong),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "=",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
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
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = container,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
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
