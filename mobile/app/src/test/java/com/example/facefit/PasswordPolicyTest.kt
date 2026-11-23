package com.example.facefit

import com.example.facefit.data.auth.PasswordPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordPolicyTest {
    @Test fun acceptsPasswordWithEveryRequirement() {
        assertTrue(PasswordPolicy.isValid("FaceFit1!"))
        assertTrue(PasswordPolicy.isValid("aB3#efgh"))
    }

    @Test fun rejectsEachMissingRequirement() {
        assertFalse("too short", PasswordPolicy.isValid("aB3#efg"))
        assertFalse("no uppercase", PasswordPolicy.isValid("facefit1!"))
        assertFalse("no lowercase", PasswordPolicy.isValid("FACEFIT1!"))
        assertFalse("no number", PasswordPolicy.isValid("FaceFitt!"))
        assertFalse("no symbol", PasswordPolicy.isValid("FaceFit12"))
        assertFalse("spaces are not symbols", PasswordPolicy.isValid("Face Fit1"))
        assertFalse(PasswordPolicy.isValid(""))
    }

    @Test fun checklistShowsWhichRequirementsAreMet() {
        val met = PasswordPolicy.requirements("abc1").map { it.met }
        assertEquals(listOf(false, false, true, true, false), met)
        assertTrue(PasswordPolicy.requirements("FaceFit1!").all { it.met })
    }
}
