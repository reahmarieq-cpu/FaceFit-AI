package com.example.facefit

import com.example.facefit.facefit.recommendation.FrameCompatibility
import org.junit.Assert.*
import org.junit.Test

class FrameCompatibilityTest {
    @Test fun pdfExampleComputesMeanAndCompatibility() {
        val result = FrameCompatibility.calculate(listOf(5.0, 4.0, 4.0, 5.0, 4.0))!!
        assertEquals(4.4, result.meanRating, .000001)
        assertEquals(5, result.ratingCount)
        assertEquals(85.0, result.score, .000001)
    }

    @Test fun ratingEndpointsAndFractionsUseTheFullScale() {
        assertEquals(0.0, FrameCompatibility.calculate(listOf(1.0))!!.score, 0.0)
        assertEquals(100.0, FrameCompatibility.calculate(listOf(5.0))!!.score, 0.0)
        assertEquals(62.5, FrameCompatibility.calculate(listOf(3.0, 4.0))!!.score, 0.0)
    }

    @Test fun missingRatingsAndUnknownCombinationsAreUnrated() {
        assertNull(FrameCompatibility.calculate(emptyList()))
        val empty = FrameCompatibility.fromEntries(emptyList())
        assertNull(empty.lookup("Oval", "Rectangle"))
        val catalog = FrameCompatibility.fromEntries(listOf(
            FrameCompatibility.Entry("Round", "Rectangle", listOf(1.0))
        ))
        assertNotNull(catalog.lookup("Round", "Rectangle"))
        assertEquals(0.0, catalog.lookup("Round", "Rectangle")!!.score, 0.0)
        assertNull(catalog.lookup("Round", "Square"))
        assertNull(catalog.lookup("Oval", "Rectangle"))
        assertNull(catalog.lookup(null, "Rectangle"))
    }

    @Test fun invalidRatingsCannotProduceCompatibilityScores() {
        for (value in listOf(0.0, 5.1, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                FrameCompatibility.calculate(listOf(4.0, value))
            }
        }
    }

    @Test fun lookupMatchesFaceAndFrameCategoryAcrossCaseAndHyphenVariants() {
        val catalog = FrameCompatibility.fromEntries(listOf(
            FrameCompatibility.Entry("Oval", "Cat-Eye", listOf(4.0, 5.0)),
            FrameCompatibility.Entry("Round", "Cat-Eye", listOf(2.0))
        ))
        assertEquals(87.5, catalog.lookup(" OVAL ", "cat eye")!!.score, .000001)
        assertEquals(25.0, catalog.lookup("Round", "Cat-eye")!!.score, .000001)
    }

    @Test fun duplicateOrUnnamedPairsCannotSilentlyReplaceScores() {
        assertThrows(IllegalArgumentException::class.java) {
            FrameCompatibility.fromEntries(listOf(
                FrameCompatibility.Entry("Oval", "Cat-Eye", listOf(4.0)),
                FrameCompatibility.Entry("oval", "cat eye", listOf(1.0))
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FrameCompatibility.fromEntries(listOf(FrameCompatibility.Entry("", "Round", listOf(4.0))))
        }
    }
}
