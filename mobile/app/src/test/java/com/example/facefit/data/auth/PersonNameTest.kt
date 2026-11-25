package com.example.facefit.data.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonNameTest {
    @Test
    fun fullNameIncludesOptionalParts() {
        assertEquals("Juan A. Dela Cruz Jr.", PersonName.fullName("Juan", "a", "Dela Cruz", "Jr."))
        assertEquals("Juan Dela Cruz", PersonName.fullName("Juan", "", "Dela Cruz", ""))
        assertEquals("Juan Dela Cruz", PersonName.fullName("Juan", null, "Dela Cruz", "None"))
        assertEquals("Ana", PersonName.fullName("Ana", null, null, null))
    }

    @Test
    fun middleInitialKeepsOnlyUpToTwoLetters() {
        assertEquals("A", PersonName.cleanInitial("a."))
        assertEquals("MA", PersonName.cleanInitial("Ma. Lou"))
        assertEquals("", PersonName.cleanInitial("1."))
    }

    @Test
    fun typedSuffixIsCleaned() {
        assertEquals("III", PersonName.cleanSuffix(" III "))
        assertEquals("Jr.", PersonName.cleanSuffix("Jr."))
        assertEquals("Sr.", PersonName.typedSuffix("Sr. 2"))
        assertEquals("ABCDE", PersonName.typedSuffix("ABCDEFG"))
        assertEquals("", PersonName.cleanSuffix("None"))
        assertEquals("", PersonName.cleanSuffix("N/A"))
        assertEquals("", PersonName.cleanSuffix(null))
    }

    @Test
    fun namesGetACapitalFirstLetter() {
        assertEquals("Juan", PersonName.capitalizeName("juan"))
        assertEquals("Dela Cruz", PersonName.capitalizeName("dela cruz"))
        assertEquals("Santos-Reyes", PersonName.capitalizeName("santos-reyes"))
        assertEquals("McDonald", PersonName.capitalizeName("mcDonald"))
        assertEquals("", PersonName.capitalizeName(""))
    }

    @Test
    fun initialsSkipMiddleInitialAndSuffix() {
        assertEquals("JD", PersonName.initials("Juan", "Dela Cruz"))
        assertEquals("J", PersonName.initials("juan", ""))
    }
}
