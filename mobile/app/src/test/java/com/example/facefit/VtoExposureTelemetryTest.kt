package com.example.facefit

import com.example.facefit.ui.camera.VtoExposureTelemetry
import org.junit.Assert.assertEquals
import org.junit.Test

class VtoExposureTelemetryTest {
    @Test fun matchesCaptureAndUsesObservedMinimumIso() {
        val telemetry = VtoExposureTelemetry()
        telemetry.record(100L, 1000L, 640, 15L, 2, 30L)
        telemetry.record(130L, 1030L, 160, 15L, 2, 30L)
        telemetry.record(160L, 1060L, null, 15L, 2, 30L)
        assertEquals(2f, telemetry.factor(100L, 1000L), 0f)
        assertEquals(1f, telemetry.factor(130L, 1030L), 0f)
        assertEquals(1f, telemetry.factor(160L, 1060L), 0f)
        assertEquals(2f, telemetry.factor(115L, 1015L), 0f)
        assertEquals(2f, telemetry.factor(null, 1015L), 0f)
        assertEquals(1f, telemetry.factor(null, 2000L), 0f)
    }
}
