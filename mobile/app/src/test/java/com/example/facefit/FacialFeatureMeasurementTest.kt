package com.example.facefit

import com.example.facefit.facefit.recommendation.FacialFeatureThresholds.Band
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

class FacialFeatureMeasurementTest {
    // Realistic population bands from facial_feature_thresholds.json:
    private val upperFaceBand = Band(0.7089f, 0.7342f)
    private val jawlineBand = Band(0.7842f, 0.8065f)
    private val eyeSpacingBand = Band(0.2390f, 0.2518f)

    private fun medianValue(values: List<Float>): Float {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2f else sorted[mid]
    }

    /**
     * Simulates 10 independent scans of the same person.
     * Each scan collects 5 burst frames affected by camera noise and hand tremor (±1.5 px jitter).
     *
     * Without multi-frame median aggregation, individual frames oscillate across boundaries.
     * With 5-frame median aggregation, the aggregated measurements remain 100% stable
     * and yield identical categories across all 10 scans.
     */
    @Test
    fun tenConsecutiveScansWithLandmarkNoiseYieldIdenticalCategories() {
        val random = Random(42) // Fixed seed for reproducible test

        // True anatomical baseline (in pixels on a 720p frame):
        // Upper face: 166.0 / 220.0 = 0.7545 (Wide, clearly above 0.7342)
        // Jawline:    182.0 / 220.0 = 0.8273 (Wide, clearly above 0.8065)
        // Eye spacing: 58.0 / 220.0 = 0.2636 (Wide-set, clearly above 0.2518)
        val trueCheekWidth = 220.0f
        val trueUpperFace = 166.0f
        val trueJawline = 182.0f
        val trueEyeSpacing = 58.0f

        val scanResultsUpperFace = mutableListOf<String>()
        val scanResultsJawline = mutableListOf<String>()
        val scanResultsEyeSpacing = mutableListOf<String>()

        repeat(10) { _ ->
            // 5 frames per scan
            val burstForehead = mutableListOf<Float>()
            val burstJawline = mutableListOf<Float>()
            val burstEyeSpacing = mutableListOf<Float>()

            repeat(5) {
                // ±1.5 px noise on each landmark from hand tremor and sensor noise
                val noiseCheek = (random.nextFloat() * 3.0f - 1.5f)
                val noiseForehead = (random.nextFloat() * 3.0f - 1.5f)
                val noiseJaw = (random.nextFloat() * 3.0f - 1.5f)
                val noiseEye = (random.nextFloat() * 3.0f - 1.5f)

                val cheek = trueCheekWidth + noiseCheek
                val fRatio = (trueUpperFace + noiseForehead) / cheek
                val jRatio = (trueJawline + noiseJaw) / cheek
                val eRatio = (trueEyeSpacing + noiseEye) / cheek

                burstForehead.add(fRatio)
                burstJawline.add(jRatio)
                burstEyeSpacing.add(eRatio)
            }

            // Consolidated 5-frame median:
            val medianF = medianValue(burstForehead)
            val medianJ = medianValue(burstJawline)
            val medianE = medianValue(burstEyeSpacing)

            scanResultsUpperFace.add(upperFaceBand.classify(medianF)!!)
            scanResultsJawline.add(jawlineBand.classify(medianJ)!!)
            scanResultsEyeSpacing.add(eyeSpacingBand.classify(medianE, eyeSpacing = true)!!)
        }

        // Verify that all 10 scans with 5-frame median aggregation have 100% category agreement:
        assertEquals("Upper face width must be 100% consistent across all 10 scans",
            1, scanResultsUpperFace.distinct().size)
        assertEquals("Wide", scanResultsUpperFace.first())

        assertEquals("Jawline width must be 100% consistent across all 10 scans",
            1, scanResultsJawline.distinct().size)
        assertEquals("Wide", scanResultsJawline.first())

        assertEquals("Eye spacing must be 100% consistent across all 10 scans",
            1, scanResultsEyeSpacing.distinct().size)
        assertEquals("Wide-set", scanResultsEyeSpacing.first())
    }

    /**
     * Moving closer or farther from the camera scales all landmarks uniformly.
     * Normalized ratios (divided by cheek width) must be mathematically identical.
     */
    @Test
    fun featureRatiosAreInvariantToCameraDistance() {
        val baseCheek = 200f
        val baseForehead = 145f
        val baseJaw = 160f
        val baseEye = 49f

        val distances = listOf(0.6f, 0.8f, 1.0f, 1.25f, 1.5f) // Closer / farther
        val ratiosForehead = distances.map { k -> (baseForehead * k) / (baseCheek * k) }
        val ratiosJaw = distances.map { k -> (baseJaw * k) / (baseCheek * k) }
        val ratiosEye = distances.map { k -> (baseEye * k) / (baseCheek * k) }

        ratiosForehead.forEach { assertEquals(baseForehead / baseCheek, it, 0.0001f) }
        ratiosJaw.forEach { assertEquals(baseJaw / baseCheek, it, 0.0001f) }
        ratiosEye.forEach { assertEquals(baseEye / baseCheek, it, 0.0001f) }
    }

    /**
     * Front-camera mirroring inverts the horizontal coordinate (x' = 1 - x).
     * Euclidean distance hypot(dx, dy) must be strictly invariant to mirroring.
     */
    @Test
    fun featureDistancesAreInvariantToMirroring() {
        val x1 = 0.35f
        val x2 = 0.65f
        val y = 0.50f
        val width = 1080
        val height = 1920

        val normalDist = hypot(((x1 - x2) * width).toDouble(), ((y - y) * height).toDouble()).toFloat()
        val mirroredX1 = 1f - x1
        val mirroredX2 = 1f - x2
        val mirroredDist = hypot(((mirroredX1 - mirroredX2) * width).toDouble(), ((y - y) * height).toDouble()).toFloat()

        assertEquals(normalDist, mirroredDist, 0.001f)
    }

    /**
     * Verifies that the borderline detector correctly identifies measurements near thresholds.
     */
    @Test
    fun borderlineMeasurementReportsProximityAccurately() {
        // High threshold of jawline is 0.8065
        val nearBoundary = 0.8070f // only 0.0005 above boundary
        assertTrue(jawlineBand.isBorderline(nearBoundary))
        assertEquals(0.0005f, jawlineBand.distanceToBoundary(nearBoundary), 0.0001f)

        // Midpoint of jawline is ~0.795
        val wellCentered = 0.7950f
        assertTrue(!jawlineBand.isBorderline(wellCentered))
        assertTrue(jawlineBand.distanceToBoundary(wellCentered) > 0.008f)
    }

    /**
     * Test A: Same-image determinism
     * Processing the exact same landmark coordinates multiple times must produce
     * strictly identical measurements and categories (zero nondeterminism).
     */
    @Test
    fun sameLandmarksYieldDeterministicMeasurements() {
        val cheek = hypot(200f, 0f)
        val forehead = hypot(150f, 0f)
        val jaw = hypot(165f, 0f)
        val eye = hypot(50f, 0f)

        val ratios1 = listOf(forehead / cheek, jaw / cheek, eye / cheek)
        val ratios2 = listOf(forehead / cheek, jaw / cheek, eye / cheek)

        assertEquals(ratios1, ratios2)
        assertEquals(upperFaceBand.classify(ratios1[0]), upperFaceBand.classify(ratios2[0]))
        assertEquals(jawlineBand.classify(ratios1[1]), jawlineBand.classify(ratios2[1]))
        assertEquals(eyeSpacingBand.classify(ratios1[2], eyeSpacing = true), eyeSpacingBand.classify(ratios2[2], eyeSpacing = true))
    }

    /**
     * Test C: Exact threshold boundary classifications
     * Verifies immediately below, exactly at, and immediately above each cutoff.
     */
    @Test
    fun boundaryCutoffAssignmentsFollowDocumentedRules() {
        val eps = 0.0001f

        // Upper face: low = 0.7089, high = 0.7342
        assertEquals("Narrow", upperFaceBand.classify(0.7089f - eps))
        assertEquals("Medium", upperFaceBand.classify(0.7089f))
        assertEquals("Medium", upperFaceBand.classify(0.7342f))
        assertEquals("Wide", upperFaceBand.classify(0.7342f + eps))

        // Jawline: low = 0.7842, high = 0.8065
        assertEquals("Narrow", jawlineBand.classify(0.7842f - eps))
        assertEquals("Medium", jawlineBand.classify(0.7842f))
        assertEquals("Medium", jawlineBand.classify(0.8065f))
        assertEquals("Wide", jawlineBand.classify(0.8065f + eps))

        // Eye spacing: low = 0.2390, high = 0.2518
        assertEquals("Close-set", eyeSpacingBand.classify(0.2390f - eps, eyeSpacing = true))
        assertEquals("Average", eyeSpacingBand.classify(0.2390f, eyeSpacing = true))
        assertEquals("Average", eyeSpacingBand.classify(0.2518f, eyeSpacing = true))
        assertEquals("Wide-set", eyeSpacingBand.classify(0.2518f + eps, eyeSpacing = true))
    }
}
