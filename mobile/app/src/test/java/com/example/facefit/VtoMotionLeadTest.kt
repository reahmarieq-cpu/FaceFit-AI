package com.example.facefit

import com.example.facefit.ui.tryon.rendering.VtoMotionLead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VtoMotionLeadTest {
    private val ms = 1_000_000L
    private val out = FloatArray(2)

    /** Samples every 34 ms moving [pxPerSample]; returns the lead shown [ageMs] after the last one. */
    private fun lead(lead: VtoMotionLead, pxPerSample: Float, samples: Int, ageMs: Long): Float {
        for (i in 0 until samples) lead.apply(i * 34 * ms, i * pxPerSample, 0f, i * 34 * ms, out)
        val last = (samples - 1) * 34 * ms
        lead.apply(last, (samples - 1) * pxPerSample, 0f, last + ageMs * ms, out)
        return out[0]
    }

    @Test fun steadyMotionIsFilledInBetweenSamples() {
        // 17 px per 34 ms = 500 px/s; 17 ms after a sample the glasses should be ~8.5 px further on.
        assertEquals(8.5f, lead(VtoMotionLead(), 17f, 8, 17), .5f)
    }

    @Test fun leadIsCappedAt50Milliseconds() {
        assertEquals(25f, lead(VtoMotionLead(), 17f, 8, 200), .6f) // 500 px/s x 50 ms
    }

    @Test fun aStillFaceGetsNoLead() {
        assertEquals(0f, lead(VtoMotionLead(), .5f, 8, 30), 1e-4f) // ~15 px/s of noise
    }

    @Test fun aReversalDropsTheMomentumInsteadOfOvershooting() {
        val l = VtoMotionLead()
        for (i in 0 until 6) l.apply(i * 34 * ms, i * 17f, 0f, i * 34 * ms, out)
        // Head turns back: the next sample moves the other way.
        l.apply(6 * 34 * ms, 5 * 17f - 17f, 0f, 6 * 34 * ms + 20 * ms, out)
        assertTrue("lead after reversal ${out[0]}", out[0] <= 0f)
    }

    @Test fun aLongGapResetsTheMotion() {
        val l = VtoMotionLead()
        for (i in 0 until 6) l.apply(i * 34 * ms, i * 17f, 0f, i * 34 * ms, out)
        l.apply(600 * ms, 200f, 0f, 620 * ms, out)
        assertEquals(0f, out[0], 1e-4f)
    }
}
