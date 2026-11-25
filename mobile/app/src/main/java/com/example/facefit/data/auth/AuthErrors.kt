package com.example.facefit.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException

/** Friendly messages for email sign-in, registration and password reset errors. */
object AuthErrors {
    enum class Action { SIGN_IN, REGISTER, RESET_PASSWORD }

    fun message(error: Throwable, action: Action): String = when (error) {
        is FirebaseNetworkException -> NETWORK
        is FirebaseTooManyRequestsException -> TOO_MANY
        is FirebaseAuthException -> messageForCode(error.errorCode, action)
        is FirebaseFirestoreException ->
            if (error.code == FirebaseFirestoreException.Code.UNAVAILABLE) NETWORK
            else "Your account was created, but your profile could not be saved. Please sign in and update it."
        else -> fallback(action)
    }

    internal fun messageForCode(code: String?, action: Action): String = when (code) {
        // With email-enumeration protection Firebase reports a wrong password and an
        // unknown email the same way, so the message covers both.
        "ERROR_INVALID_CREDENTIAL", "INVALID_LOGIN_CREDENTIALS" ->
            if (action == Action.SIGN_IN) "Incorrect email or password. Please try again."
            else fallback(action)
        "ERROR_WRONG_PASSWORD" -> "Incorrect password. Please try again."
        "ERROR_USER_NOT_FOUND" ->
            if (action == Action.RESET_PASSWORD) "No account was found with this email."
            else "No account was found with this email. Please register first."
        "ERROR_INVALID_EMAIL" -> "Please enter a valid email address."
        "ERROR_USER_DISABLED" -> "This account has been disabled. Please contact FaceFit support."
        "ERROR_TOO_MANY_REQUESTS" -> TOO_MANY
        "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists. Please sign in instead."
        "ERROR_WEAK_PASSWORD" -> "Password needs at least 8 characters with an uppercase letter, " +
            "a lowercase letter, a number and a special symbol."
        "ERROR_NETWORK_REQUEST_FAILED" -> NETWORK
        "ERROR_OPERATION_NOT_ALLOWED" -> "Email sign-in is currently unavailable. Please contact FaceFit support."
        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
            "This email uses Google sign-in. Please continue with Google."
        else -> fallback(action)
    }

    private fun fallback(action: Action) = when (action) {
        Action.SIGN_IN -> "Sign-in failed. Please try again."
        Action.REGISTER -> "Your account could not be created. Please try again."
        Action.RESET_PASSWORD -> "The reset email could not be sent. Please try again."
    }

    private const val NETWORK = "No internet connection. Check your connection and try again."
    private const val TOO_MANY = "Too many attempts. Please wait a few minutes and try again."

    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun isValidEmail(email: String): Boolean = emailPattern.matches(email.trim())
}
