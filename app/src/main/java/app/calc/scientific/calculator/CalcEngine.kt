// SPDX-License-Identifier: MIT
package app.calc.scientific.calculator

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Moteur de calcul pur (aucune dépendance Android) : tokenisation,
 * shunting-yard, évaluation RPN, formatage. Testé dans `CalcEngineTest`.
 *
 * Expressions internes : opérateurs ASCII (`+ - * / ^`), `π`, `e`, `!`,
 * fonctions `sin( cos( tan( asin( acos( atan( ln( log10( sqrt(`,
 * nombres décimaux avec point et exposant optionnel (`1.5E10`, `2E-7`).
 */
object CalcEngine {

    /** Évalue [expr] et renvoie le résultat formaté, ou "" si incomplet/invalide. */
    fun evaluate(expr: String, radians: Boolean): String {
        return try {
            val balanced = balanceRightParens(expr)
            val tokens = tokenize(balanced)
            if (tokens.isEmpty() || isSingleOperand(tokens)) return ""
            if (!isCompleteExpression(balanced)) return ""
            format(evalRpn(toRpn(tokens), radians))
        } catch (_: Throwable) {
            ""
        }
    }

    /** Valeur numérique de [expr] si elle est complète, sinon null. */
    fun valueOf(expr: String, radians: Boolean): Double? = try {
        val balanced = balanceRightParens(expr)
        if (!isCompleteExpression(balanced)) null
        else evalRpn(toRpn(tokenize(balanced)), radians).takeIf { it.isFinite() }
    } catch (_: Throwable) {
        null
    }

    /** Formate un double comme le ferait une calculatrice (notation scientifique si besoin). */
    fun format(v: Double): String {
        require(v.isFinite()) { "non-finite" }
        val bd = BigDecimal(v).setScale(10, RoundingMode.HALF_UP).stripTrailingZeros()
        val a = bd.abs()
        val useSci = a > BigDecimal("1E9") || (a.signum() != 0 && a < BigDecimal("1E-6"))
        return if (useSci) {
            // Locale.ROOT : point décimal + « E », que le tokeniseur sait relire.
            DecimalFormat("0.######E0", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(v)
        } else {
            if (bd.signum() == 0) "0" else bd.toPlainString()
        }
    }

    /** Vrai si [s] est un nombre « atomique » (résultat affiché, sans opérateur). */
    fun isAtomic(s: String): Boolean =
        s == "π" || s == "e" || ATOMIC_NUMBER.matches(s)

    private val ATOMIC_NUMBER = Regex("""-?\d*\.?\d+(E-?\d+)?""")

    // ------------------------------------------------------------------
    // Tokenisation
    // ------------------------------------------------------------------

    internal sealed interface Tok
    internal data class Num(val v: Double) : Tok
    internal data class OpTok(val op: String) : Tok
    internal data class FuncTok(val name: String) : Tok
    internal data class Paren(val open: Boolean) : Tok
    internal data object ConstPi : Tok
    internal data object ConstE : Tok

    private val OP_PRECEDENCE = mapOf("+" to 1, "-" to 1, "*" to 2, "/" to 2, "^" to 3)
    private val FUNCTIONS = listOf("asin", "acos", "atan", "sin", "cos", "tan", "ln", "log10", "sqrt", "fact")

    private fun normalize(raw: String): String {
        var s = raw.replace('×', '*').replace('÷', '/').replace('−', '-').replace(',', '.')
        // "<terme>!" -> "fact(<terme>)"
        val factRegex = Regex("""(\d+(?:\.\d+)?|π|e|\))!""")
        while (true) {
            val m = factRegex.find(s) ?: break
            val before = s.substring(0, m.range.first)
            val term = m.groupValues[1]
            val after = s.substring(m.range.last + 1)
            s = if (term == ")") {
                val open = findMatchingOpenParen(s, m.range.first)
                if (open >= 0) {
                    s.substring(0, open) + "fact" + s.substring(open, m.range.first + 1) + after
                } else before + "fact($term)" + after
            } else before + "fact($term)" + after
        }
        return s
    }

    private fun findMatchingOpenParen(s: String, closeIndex: Int): Int {
        var bal = 0
        for (i in closeIndex downTo 0) {
            when (s[i]) {
                ')' -> bal++
                '(' -> { bal--; if (bal == 0) return i }
            }
        }
        return -1
    }

    internal fun tokenize(raw: String): List<Tok> {
        val src = normalize(raw)
        val out = mutableListOf<Tok>()
        var i = 0
        fun prevAllowsUnary(): Boolean {
            val p = out.lastOrNull() ?: return true
            return p is OpTok || (p is Paren && p.open) || p is FuncTok
        }
        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < src.length && (src[i].isDigit() || src[i] == '.')) i++
                    // Exposant scientifique : E, E-, E+
                    if (i < src.length && src[i] == 'E') {
                        var j = i + 1
                        if (j < src.length && (src[j] == '-' || src[j] == '+')) j++
                        if (j < src.length && src[j].isDigit()) {
                            while (j < src.length && src[j].isDigit()) j++
                            i = j
                        }
                    }
                    out += Num(src.substring(start, i).toDouble())
                }
                c == '(' -> { out += Paren(true); i++ }
                c == ')' -> { out += Paren(false); i++ }
                c == 'π' -> { out += ConstPi; i++ }
                c == '-' && prevAllowsUnary() -> { out += FuncTok("neg"); i++ }
                c == '+' && prevAllowsUnary() -> i++ // plus unaire : ignoré
                "+-*/^".contains(c) -> { out += OpTok(c.toString()); i++ }
                else -> {
                    val match = FUNCTIONS.firstOrNull { src.regionMatches(i, it, 0, it.length) }
                    when {
                        match != null -> { out += FuncTok(match); i += match.length }
                        c == 'e' -> { out += ConstE; i++ }
                        else -> throw IllegalArgumentException("caractère inattendu '$c'")
                    }
                }
            }
        }
        return out
    }

    // ------------------------------------------------------------------
    // Shunting-yard + RPN
    // ------------------------------------------------------------------

    internal fun toRpn(tokens: List<Tok>): List<Tok> {
        val out = mutableListOf<Tok>()
        val stack = ArrayDeque<Tok>()
        for (t in tokens) {
            when (t) {
                is Num, ConstPi, ConstE -> out += t
                is FuncTok -> stack.addFirst(t)
                is OpTok -> {
                    while (stack.isNotEmpty()) {
                        val top = stack.first()
                        val pop = when (top) {
                            is FuncTok -> true
                            is OpTok -> {
                                val pTop = OP_PRECEDENCE.getValue(top.op)
                                val pCur = OP_PRECEDENCE.getValue(t.op)
                                if (t.op == "^") pCur < pTop else pCur <= pTop
                            }
                            else -> false
                        }
                        if (pop) out += stack.removeFirst() else break
                    }
                    stack.addFirst(t)
                }
                is Paren -> if (t.open) stack.addFirst(t) else {
                    while (stack.isNotEmpty() && stack.first() !is Paren) out += stack.removeFirst()
                    require(stack.isNotEmpty()) { "parenthèse fermante orpheline" }
                    stack.removeFirst()
                    if (stack.isNotEmpty() && stack.first() is FuncTok) out += stack.removeFirst()
                }
            }
        }
        while (stack.isNotEmpty()) {
            val t = stack.removeFirst()
            require(t !is Paren) { "parenthèse non fermée" }
            out += t
        }
        return out
    }

    internal fun evalRpn(rpn: List<Tok>, radians: Boolean): Double {
        val st = ArrayDeque<Double>()
        for (t in rpn) {
            when (t) {
                is Num -> st.addFirst(t.v)
                ConstPi -> st.addFirst(Math.PI)
                ConstE -> st.addFirst(Math.E)
                is OpTok -> {
                    val b = st.removeFirst()
                    val a = st.removeFirst()
                    st.addFirst(
                        when (t.op) {
                            "+" -> a + b
                            "-" -> a - b
                            "*" -> a * b
                            "/" -> a / b
                            "^" -> a.pow(b)
                            else -> Double.NaN
                        }
                    )
                }
                is FuncTok -> {
                    val x = st.removeFirst()
                    fun toRad(v: Double) = if (radians) v else Math.toRadians(v)
                    fun fromRad(v: Double) = if (radians) v else Math.toDegrees(v)
                    st.addFirst(
                        when (t.name) {
                            "neg" -> -x
                            "sin" -> cleanTrig(sin(toRad(x)))
                            "cos" -> cleanTrig(cos(toRad(x)))
                            "tan" -> cleanTrig(tan(toRad(x)))
                            "asin" -> fromRad(asin(x))
                            "acos" -> fromRad(acos(x))
                            "atan" -> fromRad(atan(x))
                            "ln" -> ln(x)
                            "log10" -> log10(x)
                            "sqrt" -> sqrt(x)
                            "fact" -> factorialOf(x)
                            else -> Double.NaN
                        }
                    )
                }
                is Paren -> error("parenthèse dans la RPN")
            }
        }
        require(st.size == 1) { "expression mal formée" }
        return st.first()
    }

    /** sin(180°) doit afficher 0, pas 1.2246E-16. */
    private fun cleanTrig(v: Double) = if (abs(v) < 1e-12) 0.0 else v

    private fun factorialOf(x: Double): Double {
        if (x.isNaN() || x < 0) return Double.NaN
        val n = x.roundToInt()
        if (abs(x - n) > 1e-9 || n > 170) return Double.NaN
        var acc = 1.0
        for (i in 2..n) acc *= i
        return acc
    }

    private fun isSingleOperand(tokens: List<Tok>) =
        tokens.all { it is Num || it is ConstPi || it is ConstE }

    // ------------------------------------------------------------------
    // Aides sur le texte de l'expression
    // ------------------------------------------------------------------

    fun balanceRightParens(s: String): String {
        var bal = 0
        for (c in s) {
            if (c == '(') bal++ else if (c == ')') bal--
        }
        return if (bal > 0) s + ")".repeat(bal) else s
    }

    fun isCompleteExpression(s: String): Boolean {
        if (s.isBlank()) return false
        var bal = 0
        for (c in s) {
            if (c == '(') bal++
            if (c == ')') bal--
            if (bal < 0) return false
        }
        if (bal != 0) return false
        val end = s.trim().last()
        return end.isDigit() || end == ')' || end == 'π' || end == 'e' || end == '!'
    }

    /** (début, fin exclusive) du dernier nombre décimal, sinon (-1, -1). */
    fun lastNumberRange(s: String): Pair<Int, Int> {
        var i = s.length - 1
        if (i < 0) return -1 to -1
        var dotSeen = false
        while (i >= 0) {
            val c = s[i]
            if (c.isDigit()) { i--; continue }
            if (c == '.' && !dotSeen) { dotSeen = true; i--; continue }
            break
        }
        val start = i + 1
        return if (start < s.length) start to s.length else -1 to -1
    }

    /** (début, fin exclusive) du dernier terme : nombre, constante ou groupe parenthésé. */
    fun lastTermRange(s: String): Pair<Int, Int> {
        if (s.isEmpty()) return -1 to -1
        val last = s.length - 1
        if (s[last] == ')') {
            val open = findMatchingOpenParen(s, last)
            return if (open >= 0) open to s.length else -1 to -1
        }
        if (s[last] == 'π' || s[last] == 'e') return last to s.length
        val (start, end) = lastNumberRange(s)
        return if (start >= 0) max(0, start) to end else -1 to -1
    }

    /** Vrai si un opérateur binaire ou une puissance peut suivre la fin de [s]. */
    fun canTakeOperator(s: String): Boolean {
        val c = s.trimEnd().lastOrNull() ?: return false
        return c.isDigit() || c == ')' || c == 'π' || c == 'e' || c == '!'
    }
}
