package com.example.facefit

import com.example.facefit.facefit.recommendation.FacialFeatureThresholds.Band
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FacialFeatureThresholdsTest {
    // Population percentile bands from facial_feature_thresholds.json:
    // jawlineWidth: low = 0.7842, high = 0.8065 (width ≈ 0.0223)
    // eyeSpacing:   low = 0.2390, high = 0.2518 (width ≈ 0.0128)
    private val jaw = Band(0.7842f, 0.8065f)
    private val eye = Band(0.2390f, 0.2518f)

    @Test fun clearlyInsideTheBandClassifiesAsMedium() {
        assertEquals("Medium", jaw.classify(0.795f))
        assertEquals("Average", eye.classify(0.245f, eyeSpacing = true))
    }

    @Test fun clearlyBelowLowIsNarrow() {
        assertEquals("Narrow", jaw.classify(0.770f))
        assertEquals("Close-set", eye.classify(0.230f, eyeSpacing = true))
    }

    @Test fun clearlyAboveHighIsWide() {
        assertEquals("Wide", jaw.classify(0.820f))
        assertEquals("Wide-set", eye.classify(0.260f, eyeSpacing = true))
    }

    @Test fun boundaryExactValuesClassifyAsMiddle() {
        // Exact cutoffs belong to the inclusive middle band
        assertEquals("Medium", jaw.classify(0.7842f))
        assertEquals("Medium", jaw.classify(0.8065f))
        assertEquals("Average", eye.classify(0.2390f, eyeSpacing = true))
        assertEquals("Average", eye.classify(0.2518f, eyeSpacing = true))
    }

    @Test fun distanceToBoundaryIsCalculatedCorrectly() {
        // At midpoint 0.79535: dist to low (0.7842) is ~0.01115
        val distMid = jaw.distanceToBoundary(0.79535f)
        assertEquals(0.01115f, distMid, 0.001f)

        // At 0.8070: dist to high (0.8065) is 0.0005
        val distNearHigh = jaw.distanceToBoundary(0.8070f)
        assertEquals(0.0005f, distNearHigh, 0.0001f)
    }

    @Test fun borderlineDetectionFlagsMeasurementsNearThresholds() {
        // 0.8068 is only 0.0003 from high (0.8065); band width is ~0.0223 (1.3% of band)
        assertTrue(jaw.isBorderline(0.8068f))

        // 0.7840 is only 0.0002 from low (0.7842); (0.9% of band)
        assertTrue(jaw.isBorderline(0.7840f))

        // 0.7950 is at the center, far from boundaries (> 40% of band)
        assertFalse(jaw.isBorderline(0.7950f))
    }

    @Test fun statefulHysteresisPreventsOscillationWhenPriorProvided() {
        // Band width = ~0.0223; 5% margin = ~0.0011
        // Ratio 0.8060 is just below high (0.8065); if prior was Wide, hysteresis keeps Wide
        assertEquals("Wide", jaw.classify(0.8060f, previous = "Wide"))

        // If prior was Medium, it stays Medium
        assertEquals("Medium", jaw.classify(0.8060f, previous = "Medium"))

        // If ratio clearly drops beyond margin (e.g. 0.8040), it switches to Medium even if prior was Wide
        assertEquals("Medium", jaw.classify(0.8040f, previous = "Wide"))
    }

    @Test fun missingAndNonfiniteMeasurementsReceiveNoCategory() {
        assertNull(jaw.classify(0f))
        assertNull(jaw.classify(-0.5f))
        assertNull(jaw.classify(Float.NaN))
        assertNull(jaw.classify(Float.POSITIVE_INFINITY))
        assertNull(jaw.classify(Float.NEGATIVE_INFINITY))
    }
}
