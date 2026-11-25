package com.example.facefit.data.auth

/** Optional middle initial and suffix, shared by Register and Edit Profile. */
object PersonName {
    const val SUFFIX_MAX_LENGTH = 5

    /** Capital first letter for each word of a name ("dela cruz" -> "Dela Cruz"); the rest stays as typed. */
    fun capitalizeName(raw: String): String = buildString(raw.length) {
        var startOfWord = true
        for (c in raw) {
            append(if (startOfWord) c.uppercaseChar() else c)
            startOfWord = c == ' ' || c == '-'
        }
    }

    /** Letters only, upper case, at most two (e.g. "A" or "MA"). Stored without a period. */
    fun cleanInitial(raw: String): String = raw.filter { it.isLetter() }.take(2).uppercase()

    /** Typed suffix such as "Jr.", "Sr." or "III": letters and periods only, at most 5 characters. */
    fun typedSuffix(raw: String): String = raw.filter { it.isLetter() || it == '.' }.take(SUFFIX_MAX_LENGTH)

    /** Saved form of a suffix; blank, "None" or "N/A" are stored as no suffix. */
    fun cleanSuffix(raw: String?): String = typedSuffix(raw?.trim().orEmpty())
        .takeUnless { it.equals("none", true) || it.equals("na", true) || it.equals("n.a.", true) }
        .orEmpty()

    /** "Juan A. Dela Cruz Jr." */
    fun fullName(first: String?, middleInitial: String?, last: String?, suffix: String?): String {
        val initial = cleanInitial(middleInitial.orEmpty()).takeIf { it.isNotEmpty() }?.let { "$it." }
        return listOf(first?.trim(), initial, last?.trim(), cleanSuffix(suffix))
            .filter { !it.isNullOrBlank() }
            .joinToString(" ")
    }

    /** Avatar letters: first and last name only, never the initial or suffix. */
    fun initials(first: String?, last: String?): String =
        (first?.trim()?.take(1).orEmpty() + last?.trim()?.take(1).orEmpty()).uppercase()
}
