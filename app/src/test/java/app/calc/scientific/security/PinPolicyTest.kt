// SPDX-License-Identifier: MIT
package app.calc.scientific.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinPolicyTest {
    @Test fun acceptsAReasonablePin() = assertNull(PinPolicy.problemWith("482913"))
    @Test fun rejectsTooShort() = assertNotNull(PinPolicy.problemWith("48291"))
    @Test fun rejectsTooLong() = assertNotNull(PinPolicy.problemWith("4829134829134"))
    @Test fun rejectsNonDigits() = assertNotNull(PinPolicy.problemWith("48a913"))
    @Test fun rejectsRepeatedDigit() = assertNotNull(PinPolicy.problemWith("777777"))
    @Test fun rejectsAscendingSequence() = assertNotNull(PinPolicy.problemWith("123456"))
    @Test fun rejectsDescendingSequence() = assertNotNull(PinPolicy.problemWith("987654"))

    @Test fun validPair() = assertTrue(PinPolicy.check("482913", "482913", "771045", "771045").ok)

    @Test fun duressMustDiffer() {
        val c = PinPolicy.check("482913", "482913", "482913", "482913")
        assertFalse(c.ok)
        assertEquals("Doit être différent du code secret", c.duress)
    }

    @Test fun confirmationMustMatch() {
        val c = PinPolicy.check("482913", "482914", "771045", "771045")
        assertEquals("Ne correspond pas", c.secretConfirm)
    }
}
