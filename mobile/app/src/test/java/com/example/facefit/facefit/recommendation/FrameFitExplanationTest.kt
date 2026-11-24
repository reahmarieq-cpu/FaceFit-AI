package com.example.facefit.facefit.recommendation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameFitExplanationTest {
    private val faces = listOf("Round", "Oval", "Square", "Heart", "Oblong")
    private val frames = listOf("Square", "Rectangular", "Round", "Oval", "Aviator", "Wayfarer", "Cat-Eye", "Browline")

    @Test
    fun everyFaceAndCatalogStyleHasAnExplanation() {
        faces.forEach { face -> frames.forEach { frame ->
            assertNotNull("$face/$frame", FrameFitExplanation.explain(face, frame, 60.0))
        } }
    }

    @Test
    fun explanationNamesTheScoreAndFaceShape() {
        val text = FrameFitExplanation.explain("Round", "Round", 58.4)!!
        assertTrue(text, text.startsWith("Your round face"))
        assertTrue(text, text.contains("Fair match: people rated this style 58 out of 100 on round faces."))
        assertTrue(FrameFitExplanation.explain("Heart", "Aviator", 71.0)!!.contains("on heart-shaped faces."))
    }

    @Test
    fun missingScoreSaysSoAndUnknownInputsGiveNull() {
        assertTrue(FrameFitExplanation.explain("Square", "Browline", null)!!.contains("no survey rating"))
        assertNotNull(FrameFitExplanation.explain("Long", "Square", 60.0))
        assertNull(FrameFitExplanation.explain(null, "Square", 60.0))
        assertNull(FrameFitExplanation.explain("Diamond", "Square", 60.0))
        assertNull(FrameFitExplanation.explain("Round", "Rimless", 60.0))
        assertEquals("Strong match", FrameFitExplanation.matchLabel(70.0))
    }
}
