package com.example.facefit.facefit.recommendation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FrameExplanationsTest {
    private val explanations = FrameExplanations(mapOf(
        "Round" to mapOf(
            "Square" to listOf("Main square.", "Alt square 1.", "Alt square 2."),
            "Rectangle" to listOf("Main rectangle.", "Alt rectangle 1.", "Alt rectangle 2.")
        ),
        "Oblong" to mapOf("Round" to listOf("Oblong round."))
    ))

    @Test
    fun looksUpByFaceShapeAndCategoryIgnoringCaseAndSpelling() {
        assertEquals("Main square.", explanations.variants("round", "SQUARE").first())
        assertEquals("Main rectangle.", explanations.variants("Round", "Rectangular").first())
        assertEquals("Oblong round.", explanations.variants("Long", "Round").first())
        assertTrue(explanations.variants(null, "Square").isEmpty())
        assertTrue(explanations.variants("Round", "Cat-Eye").isEmpty())
    }

    @Test
    fun framesInTheSameCategoryGetDifferentWording() {
        val catalog = listOf("b" to "Square", "a" to "Square", "c" to "Square", "d" to "Square", "x" to "Round")
        val texts = listOf("a", "b", "c").map { explanations.forFrame("Round", it, "Square", catalog) }
        assertEquals(listOf("Main square.", "Alt square 1.", "Alt square 2."), texts)
        assertEquals("Main square.", explanations.forFrame("Round", "d", "Square", catalog))
    }

    @Test
    fun choiceIsStableWhateverTheOrderAndSingleFramesGetTheMainText() {
        val ranked = listOf("c" to "Square", "a" to "Square", "b" to "Square")
        val reordered = ranked.reversed()
        listOf("a", "b", "c").forEach { id ->
            assertEquals(explanations.forFrame("Round", id, "Square", ranked),
                explanations.forFrame("Round", id, "Square", reordered))
        }
        assertEquals("Main rectangle.", explanations.forFrame("Round", "only", "Rectangle", listOf("only" to "Rectangle")))
        assertEquals("Main rectangle.", explanations.forFrame("Round", "new", "Rectangle", emptyList()))
        assertNull(explanations.forFrame("Round", "cat", "Cat-Eye", listOf("cat" to "Cat-Eye")))
    }

    @Test
    fun assetCoversEveryFaceShapeAndCategoryWithThreeTexts() {
        val asset = File("src/main/assets/frame_explanations.json").readText()
        listOf("Oval", "Square", "Oblong", "Round", "Heart").forEach { face ->
            assertTrue(face, asset.contains("\"$face\": {"))
        }
        listOf("Oval", "Round", "Rectangle", "Square", "Aviator", "Wayfarer").forEach { category ->
            assertEquals(category, 5, Regex("\"$category\": \\[").findAll(asset).count())
        }
        assertTrue(asset.contains("Circular lenses highlight your naturally rounded features."))
    }
}
