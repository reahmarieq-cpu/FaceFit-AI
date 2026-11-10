package com.facefit.kiosk.facefit.features

import com.facefit.kiosk.facefit.features.FacialFeatureThresholds.Band
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FacialFeatureThresholdsTest {
    @Test fun bothCutoffsBelongToTheMiddleBand() {
        val band = Band(0.21f, 0.25f)
        assertEquals("Narrow", band.classify(0.209f))
        assertEquals("Medium", band.classify(0.21f))
        assertEquals("Medium", band.classify(0.25f))
        assertEquals("Wide", band.classify(0.251f))
        assertEquals("Close-set", band.classify(0.209f, eyeSpacing = true))
        assertEquals("Average", band.classify(0.21f, eyeSpacing = true))
        assertEquals("Average", band.classify(0.25f, eyeSpacing = true))
        assertEquals("Wide-set", band.classify(0.251f, eyeSpacing = true))
    }

    @Test fun missingAndNonfiniteMeasurementsReceiveNoCategory() {
        val band = Band(0.21f, 0.25f)
        assertNull(band.classify(0f))
        assertNull(band.classify(Float.NaN))
        assertNull(band.classify(Float.POSITIVE_INFINITY))
    }
}
