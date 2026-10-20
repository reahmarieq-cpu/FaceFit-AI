package com.example.facefit

import com.example.facefit.logic.FaceAnalyzer
import com.example.facefit.logic.FaceShapeClassifier
import com.example.facefit.logic.FaceScanSamples
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class FaceShapeClassifierTest {
    private fun outline(square: Boolean, width: Int = 800, height: Int = 1200, rotation: Float = 0f): List<FaceAnalyzer.LandmarkPoint> {
        val coordinates = mapOf(
            10 to (0f to -90f), 152 to (0f to 150f),
            234 to (-100f to 0f), 454 to (100f to 0f),
            103 to (-90f to -65f), 332 to (90f to -65f),
            172 to (if (square) -88f to 90f else -76f to 70f),
            397 to (if (square) 88f to 90f else 76f to 70f),
            136 to (-50f to 115f), 365 to (50f to 115f)
        )
        return List(478) { index ->
            val (x, y) = coordinates[index] ?: (0f to 0f)
            FaceAnalyzer.LandmarkPoint(
                (400f + x * cos(rotation) - y * sin(rotation)) / width,
                (500f + x * sin(rotation) + y * cos(rotation)) / height
            )
        }
    }

    @Test fun sameHeightAndWidthCanHaveDifferentShapes() {
        val round = FaceShapeClassifier.measure(outline(false), 800, 1200)!!
        val square = FaceShapeClassifier.measure(outline(true), 800, 1200)!!
        assertEquals(round.lengthToWidth, square.lengthToWidth, 0.0001f)
        assertEquals("Round", FaceShapeClassifier.classify(round))
        assertEquals("Square", FaceShapeClassifier.classify(square))
    }

    @Test fun imageAspectRatioRollAndMirroringDoNotChangeMeasurements() {
        val expected = FaceShapeClassifier.measure(outline(true), 800, 1200)!!
        val rotated = outline(true, 1200, 800, 0.3f).map { it.copy(x = 1f - it.x) }
        val actual = FaceShapeClassifier.measure(rotated, 1200, 800)!!
        assertEquals(expected.lengthToWidth, actual.lengthToWidth, 0.0001f)
        assertEquals(expected.jawToCheek, actual.jawToCheek, 0.0001f)
        assertEquals(expected.jawAngle, actual.jawAngle, 0.001f)
        assertEquals("Square", FaceShapeClassifier.classify(actual))
    }

    @Test fun taperedAndElongatedFacesDoNotFallBackToSquare() {
        val oval = FaceShapeClassifier.Measurements(1.40f, 0.90f, 0.76f, 0.59f, 155f)
        assertEquals("Oval", FaceShapeClassifier.classify(oval))
        assertEquals("Long", FaceShapeClassifier.classify(oval.copy(lengthToWidth = 1.65f)))
        assertEquals("Unclear", FaceShapeClassifier.classify(oval.copy(lengthToWidth = 1.30f)))
    }

    @Test fun missingInvalidOrCollapsedPointsDoNotProduceAShape() {
        assertNull(FaceShapeClassifier.measure(emptyList(), 800, 1200))
        assertNull(FaceShapeClassifier.measure(outline(false), 0, 1200))
        val collapsed = List(478) { FaceAnalyzer.LandmarkPoint(0.5f, 0.5f) }
        assertNull(FaceShapeClassifier.measure(collapsed, 800, 1200))
        val invalid = outline(false).toMutableList().apply { this[172] = this[172].copy(x = Float.NaN) }
        assertNull(FaceShapeClassifier.measure(invalid, 800, 1200))
        assertEquals("Unclear", FaceShapeClassifier.classify(null))
    }

    @Test fun medianResistsAnOutlierWithoutReportingProbability() {
        val m = FaceShapeClassifier.Measurements(1.20f, 0.90f, 0.76f, 0.59f, 155f)
        val result = FaceShapeClassifier.median(listOf(m, m, m.copy(lengthToWidth = 1.8f)))!!
        assertEquals("Round", FaceShapeClassifier.classify(result))
    }

    private fun metrics(square: Boolean = false): FaceAnalyzer.FaceMetrics {
        val points = outline(square)
        val shape = FaceShapeClassifier.measure(points, 800, 1200)!!
        return FaceAnalyzer.FaceMetrics(0.9f, shape.jawToCheek, 0.25f,
            FaceShapeClassifier.classify(shape), points, 800, 1200, true, shape)
    }

    @Test fun captureRequiresSixFreshFramesAndMinimumDuration() {
        val collector = FaceScanSamples()
        for (time in listOf(0L, 140L, 280L, 420L, 560L)) {
            assertNull(collector.add(metrics(), true, time).captured)
        }
        val captured = collector.add(metrics(), true, 700L).captured!!
        assertEquals(6, captured.shapeSampleCount)
        assertEquals("Round", captured.faceShape)
        val tooFast = FaceScanSamples()
        repeat(6) { assertNull(tooFast.add(metrics(), true, it * 20L).captured) }
    }

    @Test fun losingAlignmentOrReceivingStaleFramesClearsPreviousSamples() {
        val collector = FaceScanSamples()
        repeat(5) { collector.add(metrics(), true, it * 150L) }
        assertEquals(0f, collector.add(metrics(), false, 750L).progress, 0f)
        assertNull(collector.add(metrics(true), true, 900L).captured)
        assertNull(collector.add(metrics(true), true, 2000L).captured)
        repeat(5) { i ->
            val result = collector.add(metrics(true), true, 2140L + i * 140L)
            if (i < 4) assertNull(result.captured)
            else assertEquals("Square", result.captured!!.faceShape)
        }
    }
}
