package com.example.facefit

import com.example.facefit.ui.camera.VtoFaceLossGrace
import com.example.facefit.ui.camera.VtoLatencyEstimator
import org.junit.Assert.*
import org.junit.Test

class VtoLatencyEstimatorTest {
    @Test fun firstSlowCycleWidensWindowBeforeTheAverageCatchesUp() {
        val estimator = VtoLatencyEstimator()
        val cold = estimator.record(50_000_000L, 64_000_000L, 70_000_000L)
        val hot = estimator.record(810_000_000L, 824_000_000L, 850_000_000L)
        assertEquals(105_000_000L, cold.predictionWindowNanos)
        assertEquals(1_275_000_000L, hot.predictionWindowNanos)
        assertTrue(hot.slowed)
        assertTrue(hot.inferenceNanos < hot.currentCycleNanos)
    }

    @Test fun cheapNoFaceResultsCannotTriggerInferenceSlowdown() {
        val estimator = VtoLatencyEstimator()
        estimator.record(350_000_000L, 350_000_000L, 390_000_000L)
        repeat(4) { estimator.record(25_000_000L, 35_000_000L, 40_000_000L, faceDetected = false) }
        val tracked = estimator.record(360_000_000L, 380_000_000L, 400_000_000L)
        assertFalse(tracked.slowed)
        assertTrue(tracked.inferenceNanos >= 350_000_000L)
    }

    @Test fun repeatedDoubleMissesNeverRemoveAnchorAndSustainedLossDoes() {
        val grace = VtoFaceLossGrace()
        val hot = VtoLatencyEstimator.Snapshot(inferenceNanos = 350_000_000L,
            meshIntervalNanos = 350_000_000L, slowed = true)
        repeat(4) {
            assertEquals(VtoFaceLossGrace.State.TRACKING, grace.observe(true, hot))
            repeat(2) { assertEquals(VtoFaceLossGrace.State.TRACKING, grace.observe(false, hot)) }
        }
        grace.observe(true, hot)
        repeat(hot.requiredDrops - 1) { assertEquals(VtoFaceLossGrace.State.TRACKING, grace.observe(false, hot)) }
        assertEquals(VtoFaceLossGrace.State.GRACE, grace.observe(false, hot))
        val cheapMiss = VtoLatencyEstimator.Snapshot(inferenceNanos = 25_000_000L, meshIntervalNanos = 30_000_000L)
        repeat(hot.graceFrames - 1) { assertEquals(VtoFaceLossGrace.State.GRACE, grace.observe(false, cheapMiss)) }
        assertEquals(VtoFaceLossGrace.State.LOST, grace.observe(false, cheapMiss))
        assertEquals(VtoFaceLossGrace.State.TRACKING, grace.observe(true, hot))
    }
}