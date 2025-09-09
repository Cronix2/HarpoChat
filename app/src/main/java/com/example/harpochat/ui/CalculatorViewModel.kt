package com.example.harpochat.calculator

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.*

class CalculatorViewModel : ViewModel() {

    private val _expression = MutableStateFlow("")
    val expression: StateFlow<String> = _expression.asStateFlow()

    private val _preview = MutableStateFlow("")
    val preview: StateFlow<String> = _preview.asStateFlow()

    private val _invMode = MutableStateFlow(false)
    val invMode: StateFlow<Boolean> = _invMode.asStateFlow()

    private val _radMode = MutableStateFlow(false) // false = Deg, true = Rad
    val radMode: StateFlow<Boolean> = _radMode.asStateFlow()

    private var justEvaluated = false

    // ---------- Inputs ----------

    fun addDigit(d: Char) {
        if (!d.isDigit()) return
        insertOrReplace(d.toString())
    }

    fun addDot() {
        val e = _expression.value
        val (start, end) = lastNumberRange(e)
        val current = if (start >= 0) e.substring(start, end) else ""
        if (current.contains('.')) return
        insertOrReplace(".")
    }

    fun addBinaryOp(op: String) {
        val mapped = when (op) {
            "×" -> "*"
            "÷" -> "/"
            "−" -> "-"
            else -> op
        }
        val e = _expression.value.trimEnd()
        if (e.isEmpty()) return
        if (e.last().isOperatorOrCaret()) {
            _expression.value = e.dropLast(1) + mapped
        } else {
            _expression.value = e + mapped
        }
        computePreview()
    }

    fun addLeftParen() {
        insertOrReplace("(")
    }

    fun addRightParen() {
        _expression.value += ")"
        computePreview()
    }

    /** name: "sin","cos","tan","asin","acos","atan","sqrt","ln","log10","lg" */
    fun addFunction(name: String) {
        val fn = when (name.lowercase()) {
            "lg" -> "log10"
            else -> name.lowercase()
        }
        insertOrReplace("$fn(")
    }

    /** name: "π" or "e" */
    fun addConstant(name: String) {
        when (name) {
            "π" -> insertOrReplace("π")
            "e" -> insertOrReplace("e")
        }
    }

    fun addFactorial() {
        val e = _expression.value
        if (e.isNotEmpty() && (e.last().isDigit() || e.last() == ')' || e.last() == 'π' || e.last() == 'e')) {
            _expression.value = e + "!"
            computePreview()
        }
    }

    fun reciprocalOfLastTerm() {
        wrapLastTermWith("1/(", ")")
    }

    fun toggleSignOfLastNumber() {
        val e = _expression.value
        val (ts, te) = lastTermRange(e)
        if (ts < 0) return
        val term = e.substring(ts, te)
        val newExpr = if (term.startsWith("(-") && term.endsWith(")")) {
            e.replaceRange(ts, te, term.removePrefix("(-").dropLast(1))
        } else {
            e.replaceRange(ts, te, "(-$term)")
        }
        _expression.value = newExpr
        computePreview()
    }

    fun toggleInvMode() {
        _invMode.value = !_invMode.value
    }

    fun toggleAngleMode() {
        _radMode.value = !_radMode.value
        computePreview()
    }

    fun backspace() {
        val e = _expression.value
        if (e.isNotEmpty()) {
            _expression.value = e.dropLast(1)
            computePreview()
        }
    }

    fun clearAll() {
        _expression.value = ""
        _preview.value = ""
        justEvaluated = false
    }

    fun evaluate() {
        val res = tryEvaluate(_expression.value, _radMode.value)
        if (res.isNotEmpty()) {
            _expression.value = res
            _preview.value = ""
            justEvaluated = true
        }
    }

    // ---------- Internals ----------

    private fun insertOrReplace(s: String) {
        if (justEvaluated && isAtomic(_expression.value)) {
            _expression.value = ""
        }
        _expression.value += s
        justEvaluated = false
        computePreview()
    }

    private fun wrapLastTermWith(prefix: String, suffix: String) {
        val e = _expression.value
        val (s, t) = lastTermRange(e)
        if (s < 0) return
        _expression.value = e.substring(0, s) + prefix + e.substring(s, t) + suffix + e.substring(t)
        computePreview()
    }

    private fun computePreview() {
        _preview.value = tryEvaluate(_expression.value, _radMode.value)
    }

    private fun isAtomic(s: String): Boolean =
        s.all { it.isDigit() || it == '.' } || s == "π" || s == "e"
}

/* ================== Parsing & Eval ================== */

private sealed interface Tok
private data class Num(val v: Double): Tok
private data class OpTok(val op: String): Tok
private data class FuncTok(val name: String): Tok
private data class Paren(val open: Boolean): Tok
private object ConstPi: Tok
private object ConstE: Tok

private val OP_PRECEDENCE = mapOf("+" to 1, "-" to 1, "*" to 2, "/" to 2, "^" to 3)
private fun rightAssoc(op: String) = op == "^"

private fun Char.isOperatorOrCaret() = this in charArrayOf('+','-','*','/','^','×','÷','−')

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
    return end.isDigit() || end == ')' || end == 'π' || end == 'e' || end == '!'
}

private fun normalize(raw: String): String {
    if (raw.isBlank()) return raw
    var s = raw
        .replace('×', '*')
        .replace('÷', '/')
        .replace('−', '-')
        .replace(',', '.')

    // Remplacer "<terme>!" par "fact(<terme>)"
    val factRegex = Regex("""((?:\d+(?:\.\d+)?|π|e|\)))(!)""")
    while (true) {
        val m = factRegex.find(s) ?: break
        val before = s.substring(0, m.range.first)
        val term = m.groupValues[1]
        val after = s.substring(m.range.last + 1)
        val expanded = if (term == ")") {
            val open = findMatchingOpenParen(s, m.range.first - 1)
            if (open >= 0) {
                val inner = s.substring(open, m.range.first) // "(...)"
                s = before + "fact$inner" + after
                continue
            } else {
                "fact($term)"
            }
        } else {
            "fact($term)"
        }
        s = before + expanded + after
    }
    return s
}

private fun findMatchingOpenParen(s: String, closeIndex: Int): Int {
    var bal = 0
    for (i in closeIndex downTo 0) {
        val c = s[i]
        if (c == ')') bal++
        if (c == '(') {
            bal--
            if (bal < 0) return i
        }
    }
    return -1
}

private fun tokenize(raw: String): List<Tok> {
    val src = normalize(raw)

    val out = mutableListOf<Tok>()
    var i = 0
    while (i < src.length) {
        val c = src[i]
        when {
            c.isWhitespace() -> i++
            c.isDigit() || c == '.' -> {
                val start = i
                i++
                while (i < src.length && (src[i].isDigit() || src[i] == '.')) i++
                out += Num(src.substring(start, i).toDouble())
            }
            c == '(' -> { out += Paren(true); i++ }
            c == ')' -> { out += Paren(false); i++ }
            c == 'π' -> { out += ConstPi; i++ }
            c == 'e' -> { out += ConstE; i++ }
            "+-*/^".contains(c) -> { out += OpTok(c.toString()); i++ }
            else -> {
                val names = listOf(
                    "sin","cos","tan","asin","acos","atan",
                    "ln","log10","sqrt","fact"
                )
                val match = names.firstOrNull { src.regionMatches(i, it, 0, it.length, ignoreCase = true) }
                if (match != null) {
                    out += FuncTok(match.lowercase())
                    i += match.length
                } else {
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
            is ConstE -> st.addFirst(Math.E)
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
                    "asin" -> if (radians) asin(x) else Math.toDegrees(asin(x))
                    "acos" -> if (radians) acos(x) else Math.toDegrees(acos(x))
                    "atan" -> if (radians) atan(x) else Math.toDegrees(atan(x))
                    "ln"   -> ln(x)
                    "log10"-> log10(x)
                    "sqrt" -> sqrt(x)
                    "fact" -> factorialOf(x)
                    else -> Double.NaN
                }
                st.addFirst(v)
            }
            is Paren -> error("Paren in RPN")
        }
    }
    return st.first()
}

private fun factorialOf(x: Double): Double {
    if (x.isNaN() || x < 0) return Double.NaN
    val n = x.roundToInt()
    if (abs(x - n) > 1e-9) return Double.NaN
    if (n > 170) return Double.NaN
    var acc = 1.0
    for (i in 2..n) acc *= i
    return acc
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
                bd.toPlainString()
            }
        }
    } catch (_: Throwable) {
        ""
    }
}

/* ============== Range helpers (Pair) ============== */

/** Retourne (start, endExclusive) du dernier nombre décimal, sinon (-1,-1). */
private fun lastNumberRange(s: String): Pair<Int, Int> {
    var i = s.length - 1
    if (i < 0) return -1 to -1
    var dotSeen = false
    while (i >= 0) {
        val c = s[i]
        if (c.isDigit()) { i--; continue }
        if (c == '.') {
            if (dotSeen) break else { dotSeen = true; i--; continue }
        }
        break
    }
    val start = i + 1
    val end = s.length
    return if (start < end) start to end else -1 to -1
}

/** Retourne (start, endExclusive) du dernier “terme” (nombre, constante, groupe parenthésé). */
private fun lastTermRange(s: String): Pair<Int, Int> {
    if (s.isEmpty()) return -1 to -1
    var i = s.length - 1
    if (s[i] == ')') {
        var bal = 1
        i--
        while (i >= 0) {
            if (s[i] == ')') bal++
            if (s[i] == '(') {
                bal--
                if (bal == 0) break
            }
            i--
        }
        val start = if (i >= 0) i else 0
        return if (start < s.length) start to (s.length) else -1 to -1
    }
    if (s[i] == 'π' || s[i] == 'e') return i to (i + 1)

    var dotSeen = false
    while (i >= 0) {
        val c = s[i]
        if (c.isDigit()) { i--; continue }
        if (c == '.') {
            if (dotSeen) break else { dotSeen = true; i--; continue }
        }
        break
    }
    val start = i + 1
    return if (start < s.length) start to (s.length) else -1 to -1
}
