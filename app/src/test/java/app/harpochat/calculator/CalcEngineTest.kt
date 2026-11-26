// SPDX-License-Identifier: MIT
package app.harpochat.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcEngineTest {

    private fun eval(expr: String, radians: Boolean = false) = CalcEngine.evaluate(expr, radians)

    @Test fun precedence() = assertEquals("14", eval("2+3*4"))
    @Test fun parentheses() = assertEquals("20", eval("(2+3)*4"))
    @Test fun powerIsRightAssociative() = assertEquals("512", eval("2^3^2"))
    @Test fun division() = assertEquals("2.5", eval("5/2"))
    @Test fun divisionByZeroGivesNothing() = assertEquals("", eval("1/0"))

    @Test fun unaryMinusAtStart() = assertEquals("-2", eval("-5+3"))
    @Test fun unaryMinusInParens() = assertEquals("-10", eval("(-5)*2"))
    @Test fun unaryMinusAfterOperator() = assertEquals("-6", eval("2*-3"))

    @Test fun factorial() = assertEquals("120", eval("5!"))
    @Test fun factorialOfGroup() = assertEquals("120", eval("(2+3)!"))
    @Test fun squareRoot() = assertEquals("4", eval("sqrt(16)"))
    @Test fun unbalancedParensAreClosed() = assertEquals("4", eval("sqrt(16"))

    @Test fun sinDegrees() = assertEquals("0.5", eval("sin(30)"))
    @Test fun sin180IsExactlyZero() = assertEquals("0", eval("sin(180)"))
    @Test fun sinRadians() = assertEquals("1", eval("sin(π/2)", radians = true))
    @Test fun constantE() = assertEquals("2.7182818285", eval("e*1"))

    @Test fun singleNumberHasNoPreview() = assertEquals("", eval("42"))
    @Test fun incompleteExpressionHasNoPreview() = assertEquals("", eval("2+"))

    // --- Bug corrigé : un résultat en notation scientifique doit pouvoir être réutilisé ---

    @Test fun largeResultUsesScientificNotation() = assertEquals("1E12", eval("10^12"))
    @Test fun smallResultUsesScientificNotation() = assertEquals("2.5E-7", eval("1/4000000"))
    @Test fun scientificResultCanBeReused() = assertEquals("2E12", eval("1E12*2"))
    @Test fun negativeExponentCanBeReused() = assertEquals("5E-7", eval("2.5E-7*2"))

    @Test fun atomicDetection() {
        assertTrue(CalcEngine.isAtomic("42"))
        assertTrue(CalcEngine.isAtomic("-3.5"))
        assertTrue(CalcEngine.isAtomic("1.5E10"))
        assertFalse(CalcEngine.isAtomic("2+3"))
    }

    @Test fun canTakeOperator() {
        assertFalse(CalcEngine.canTakeOperator(""))
        assertFalse(CalcEngine.canTakeOperator("2+"))
        assertTrue(CalcEngine.canTakeOperator("2"))
        assertTrue(CalcEngine.canTakeOperator("(1+2)"))
    }
}
