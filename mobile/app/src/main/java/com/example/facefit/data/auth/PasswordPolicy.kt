package com.example.facefit.data.auth

/** Password requirements for new accounts. */
object PasswordPolicy {
    const val MIN_LENGTH = 8

    /** Shown under the password field and when a password is rejected. */
    const val HINT = "At least $MIN_LENGTH characters, including uppercase, lowercase, a number, " +
        "and one special character (e.g. !@#\$%)"

    data class Requirement(val label: String, val met: Boolean)

    fun requirements(password: String): List<Requirement> = listOf(
        Requirement("At least $MIN_LENGTH characters", password.length >= MIN_LENGTH),
        Requirement("An uppercase letter (A–Z)", password.any { it in 'A'..'Z' }),
        Requirement("A lowercase letter (a–z)", password.any { it in 'a'..'z' }),
        Requirement("A number (0–9)", password.any { it in '0'..'9' }),
        Requirement("A special symbol (e.g. ! @ # \$ %)", password.any { isSymbol(it) }),
    )

    fun isValid(password: String): Boolean = requirements(password).all { it.met }

    /**
     * [HINT] with the rules the password already meets taken out, e.g. after typing "Abc":
     * "At least 8 characters, including a number, and one special character (e.g. !@#$%)".
     * Null once every rule is met.
     */
    fun remainingHint(password: String): String? {
        val needsLength = password.length < MIN_LENGTH
        val missing = buildList {
            if (password.none { it in 'A'..'Z' }) add("uppercase")
            if (password.none { it in 'a'..'z' }) add("lowercase")
            if (password.none { it in '0'..'9' }) add("a number")
            if (password.none { isSymbol(it) }) add("one special character (e.g. !@#\$%)")
        }
        val list = when (missing.size) {
            0 -> null
            1 -> missing[0]
            2 -> "${missing[0]} and ${missing[1]}"
            else -> missing.dropLast(1).joinToString(", ") + ", and " + missing.last()
        }
        return when {
            needsLength && list != null -> "At least $MIN_LENGTH characters, including $list"
            needsLength -> "At least $MIN_LENGTH characters"
            list != null -> "Include $list"
            else -> null
        }
    }

    /** Any visible character that is not a letter or digit counts as a symbol. */
    private fun isSymbol(c: Char): Boolean = !c.isLetterOrDigit() && !c.isWhitespace() && !c.isISOControl()
}
