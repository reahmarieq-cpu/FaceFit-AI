package com.example.facefit.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordPolicyTest {
    @Test
    fun emptyPasswordShowsTheFullRule() {
        assertEquals(PasswordPolicy.HINT, PasswordPolicy.remainingHint(""))
    }

    @Test
    fun metRulesDropOut() {
        assertEquals("At least 8 characters, including a number and one special character (e.g. !@#\$%)",
            PasswordPolicy.remainingHint("Abc"))
        assertEquals("At least 8 characters, including lowercase and a number",
            PasswordPolicy.remainingHint("AB!"))
        assertEquals("At least 8 characters", PasswordPolicy.remainingHint("Ab1!"))
        assertEquals("Include uppercase", PasswordPolicy.remainingHint("abcdef1!"))
    }

    @Test
    fun nothingShownOnceEveryRuleIsMet() {
        assertNull(PasswordPolicy.remainingHint("Abcdef1!"))
    }
}
