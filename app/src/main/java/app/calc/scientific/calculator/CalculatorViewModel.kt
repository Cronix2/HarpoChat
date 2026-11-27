// SPDX-License-Identifier: MIT
package app.calc.scientific.calculator

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * État de la façade calculatrice. Toute la logique de calcul vit dans [CalcEngine] ;
 * ce ViewModel ne fait que manipuler le texte de l'expression.
 */
class CalculatorViewModel : ViewModel() {

    private val _expression = MutableStateFlow("")
    val expression: StateFlow<String> = _expression.asStateFlow()

    private val _preview = MutableStateFlow("")
    val preview: StateFlow<String> = _preview.asStateFlow()

    private val _invMode = MutableStateFlow(false)
    val invMode: StateFlow<Boolean> = _invMode.asStateFlow()

    private val _radMode = MutableStateFlow(false) // false = degrés
    val radMode: StateFlow<Boolean> = _radMode.asStateFlow()

    private val _memory = MutableStateFlow<Double?>(null)
    val memory: StateFlow<Double?> = _memory.asStateFlow()

    private var justEvaluated = false

    // ---------- Mémoire ----------

    fun memoryClear() { _memory.value = null }

    fun memoryAdd() {
        val v = valueForMemory() ?: return
        _memory.value = (_memory.value ?: 0.0) + v
    }

    fun memorySubtract() {
        val v = valueForMemory() ?: return
        _memory.value = (_memory.value ?: 0.0) - v
    }

    fun memoryRecall() {
        val v = _memory.value ?: return
        insertOrReplace(formatForExpr(v))
    }

    /** Source de valeur pour M+/M- : aperçu, sinon expression complète, sinon dernier nombre. */
    private fun valueForMemory(): Double? {
        CalcEngine.valueOf(_preview.value, _radMode.value)?.let { return it }
        CalcEngine.valueOf(_expression.value, _radMode.value)?.let { return it }
        val (s, e) = CalcEngine.lastNumberRange(_expression.value)
        return if (s >= 0) _expression.value.substring(s, e).toDoubleOrNull() else null
    }

    private fun formatForExpr(v: Double): String =
        BigDecimal(v).setScale(10, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    // ---------- Saisie ----------

    fun addDigit(d: Char) {
        if (d.isDigit()) insertOrReplace(d.toString())
    }

    fun addDot() {
        val e = _expression.value
        val (start, end) = CalcEngine.lastNumberRange(e)
        if (start >= 0 && e.substring(start, end).contains('.')) return
        insertOrReplace(".")
    }

    fun addBinaryOp(op: String) {
        val mapped = when (op) { "×" -> "*"; "÷" -> "/"; "−" -> "-"; else -> op }
        val e = _expression.value.trimEnd()
        justEvaluated = false
        when {
            e.isEmpty() -> if (mapped == "-") _expression.value = "-" else return
            e.last() in "+-*/^" -> _expression.value = e.dropLast(1) + mapped
            e.last() == '(' -> if (mapped == "-") _expression.value = e + mapped else return
            else -> _expression.value = e + mapped
        }
        computePreview()
    }

    /** x², x³ : uniquement s'il y a un terme à élever (corrige « x² sur écran vide → 2 »). */
    fun addPower(exponent: String) {
        val e = _expression.value
        if (!CalcEngine.canTakeOperator(e)) return
        _expression.value = "$e^$exponent"
        justEvaluated = false
        computePreview()
    }

    /** ʸ√x : x^(1÷y), l'utilisateur saisit ensuite y. */
    fun addRootOfDegree() {
        val e = _expression.value
        if (!CalcEngine.canTakeOperator(e)) return
        _expression.value = "$e^(1/"
        justEvaluated = false
        computePreview()
    }

    /** % : divise le dernier terme par 100. */
    fun addPercent() {
        val e = _expression.value
        if (!CalcEngine.canTakeOperator(e)) return
        _expression.value = "$e/100"
        justEvaluated = false
        computePreview()
    }

    fun addLeftParen() = insertOrReplace("(")

    fun addRightParen() {
        val e = _expression.value
        val open = e.count { it == '(' } - e.count { it == ')' }
        if (open <= 0 || !CalcEngine.canTakeOperator(e)) return
        _expression.value = "$e)"
        computePreview()
    }

    /** name : sin, cos, tan, asin, acos, atan, sqrt, ln, log10 (ou lg). */
    fun addFunction(name: String) {
        val fn = if (name.equals("lg", ignoreCase = true)) "log10" else name.lowercase()
        insertOrReplace("$fn(")
    }

    fun addConstant(name: String) {
        if (name == "π" || name == "e") insertOrReplace(name)
    }

    fun addFactorial() {
        val e = _expression.value
        if (e.isNotEmpty() && (e.last().isDigit() || e.last() == ')' || e.last() == 'π' || e.last() == 'e')) {
            _expression.value = "$e!"
            computePreview()
        }
    }

    fun toggleSignOfLastNumber() {
        val e = _expression.value
        val (ts, te) = CalcEngine.lastTermRange(e)
        if (ts < 0) return
        val term = e.substring(ts, te)
        _expression.value = if (term.startsWith("(-") && term.endsWith(")")) {
            e.replaceRange(ts, te, term.removePrefix("(-").dropLast(1))
        } else {
            e.replaceRange(ts, te, "(-$term)")
        }
        computePreview()
    }

    fun toggleInvMode() { _invMode.value = !_invMode.value }

    fun toggleAngleMode() {
        _radMode.value = !_radMode.value
        computePreview()
    }

    fun backspace() {
        val e = _expression.value
        if (e.isNotEmpty()) {
            _expression.value = e.dropLast(1)
            justEvaluated = false
            computePreview()
        }
    }

    fun clearAll() {
        _expression.value = ""
        _preview.value = ""
        justEvaluated = false
    }

    fun evaluate() {
        val res = CalcEngine.evaluate(_expression.value, _radMode.value)
        if (res.isNotEmpty()) {
            _expression.value = res
            _preview.value = ""
            justEvaluated = true
        }
    }

    // ---------- Interne ----------

    private fun insertOrReplace(s: String) {
        if (justEvaluated && CalcEngine.isAtomic(_expression.value)) _expression.value = ""
        _expression.value += s
        justEvaluated = false
        computePreview()
    }

    private fun computePreview() {
        _preview.value = CalcEngine.evaluate(_expression.value, _radMode.value)
    }
}
