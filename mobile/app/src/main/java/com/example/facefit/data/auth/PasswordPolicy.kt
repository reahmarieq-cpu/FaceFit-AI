package com.example.facefit.data.auth

/** Password requirements for new accounts. */
object PasswordPolicy {
    const val MIN_LENGTH = 8

    data class Requirement(val label: String, val met: Boolean)

    fun requirements(password: String): List<Requirement> = listOf(
        Requirement("At least $MIN_LENGTH characters", password.length >= MIN_LENGTH),
        Requirement("An uppercase letter (A–Z)", password.any { it in 'A'..'Z' }),
        Requirement("A lowercase letter (a–z)", password.any { it in 'a'..'z' }),
        Requirement("A number (0–9)", password.any { it in '0'..'9' }),
        Requirement("A special symbol (e.g. ! @ # \$ %)", password.any { isSymbol(it) }),
    )

    fun isValid(password: String): Boolean = requirements(password).all { it.met }

    /** Any visible character that is not a letter or digit counts as a symbol. */
    private fun isSymbol(c: Char): Boolean = !c.isLetterOrDigit() && !c.isWhitespace() && !c.isISOControl()
}
