package com.example.facefit.data.auth

import com.example.facefit.data.auth.AuthErrors.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthErrorsTest {
    @Test
    fun wrongPasswordAndUnknownEmailGiveAClearSignInMessage() {
        assertEquals("Incorrect email or password. Please try again.",
            AuthErrors.messageForCode("ERROR_INVALID_CREDENTIAL", Action.SIGN_IN))
        assertEquals("Incorrect email or password. Please try again.",
            AuthErrors.messageForCode("INVALID_LOGIN_CREDENTIALS", Action.SIGN_IN))
        assertEquals("Incorrect password. Please try again.",
            AuthErrors.messageForCode("ERROR_WRONG_PASSWORD", Action.SIGN_IN))
        assertTrue(AuthErrors.messageForCode("ERROR_USER_NOT_FOUND", Action.SIGN_IN).contains("register"))
    }

    @Test
    fun registrationAndLimitsHaveTheirOwnMessages() {
        assertTrue(AuthErrors.messageForCode("ERROR_EMAIL_ALREADY_IN_USE", Action.REGISTER).contains("already exists"))
        assertTrue(AuthErrors.messageForCode("ERROR_WEAK_PASSWORD", Action.REGISTER).contains("8 characters"))
        assertTrue(AuthErrors.messageForCode("ERROR_TOO_MANY_REQUESTS", Action.SIGN_IN).contains("Too many attempts"))
        assertTrue(AuthErrors.messageForCode("ERROR_USER_DISABLED", Action.SIGN_IN).contains("disabled"))
        assertTrue(AuthErrors.messageForCode("ERROR_NETWORK_REQUEST_FAILED", Action.REGISTER).contains("internet"))
    }

    @Test
    fun unknownCodesNeverShowRawFirebaseText() {
        assertEquals("Sign-in failed. Please try again.", AuthErrors.messageForCode("ERROR_SOMETHING_NEW", Action.SIGN_IN))
        assertEquals("Your account could not be created. Please try again.", AuthErrors.messageForCode(null, Action.REGISTER))
        assertEquals("The reset email could not be sent. Please try again.",
            AuthErrors.message(RuntimeException("internal detail"), Action.RESET_PASSWORD))
    }

    @Test
    fun emailFormatCheck() {
        assertTrue(AuthErrors.isValidEmail(" user@example.com "))
        assertFalse(AuthErrors.isValidEmail("user@example"))
        assertFalse(AuthErrors.isValidEmail("user example.com"))
        assertFalse(AuthErrors.isValidEmail(""))
    }
}
