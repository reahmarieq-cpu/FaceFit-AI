package com.example.facefit

import com.example.facefit.ui.tryon.rendering.VtoMeshCorrection
import org.junit.Assert.assertEquals
import org.junit.Test

class VtoMeshCorrectionTest {
    private val frame = 16_666_667L

    @Test fun aSmallStepAtANewMeshIsBlendedOutAndRealMotionPassesThrough() {
        val c = VtoMeshCorrection(timeConstantNanos = 60_000_000L)
        val out = FloatArray(2)
        // Moving right 5 px per frame on mesh 1.
        for (i in 0..5) c.apply(1, 100f + 5f * i, 0f, i * frame, 80f, out)
        assertEquals(0f, out[0], 1e-4f)
        // Mesh 2 says the face is 12 px further left than tracking expected (next would be 130).
        c.apply(2, 118f, 0f, 6 * frame, 80f, out)
        assertEquals(12f, out[0], 1e-3f) // shown at 130: no visible step
        // Motion continues on mesh 2; the offset decays (e^-1 after 60 ms) while motion passes 1:1.
        for (i in 7..10) c.apply(2, 118f + 5f * (i - 6), 0f, i * frame, 80f, out)
        assertEquals(12f * kotlin.math.exp(-4 * frame / 60_000_000.0).toFloat(), out[0], .05f)
    }

    @Test fun aLargeStepIsARealCorrectionAndIsShownAtOnce() {
        val c = VtoMeshCorrection()
        val out = FloatArray(2)
        c.apply(1, 100f, 0f, 0L, 80f, out)
        c.apply(2, 300f, 0f, frame, 80f, out)
        assertEquals(0f, out[0], 1e-4f)
    }

    @Test fun resetForgetsThePreviousMesh() {
        val c = VtoMeshCorrection()
        val out = FloatArray(2)
        c.apply(1, 100f, 0f, 0L, 80f, out)
        c.reset()
        c.apply(2, 110f, 0f, frame, 80f, out)
        assertEquals(0f, out[0], 1e-4f)
    }
}
