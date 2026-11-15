package com.example.facefit

import com.example.facefit.facefit.camera.TrackingBackend
import com.example.facefit.facefit.camera.VtoBackendSelection
import org.junit.Assert.*
import org.junit.Test

class VtoBackendSelectionTest {
    @Test fun excludesNoFaceAndWarmup() {
        val policy = VtoBackendSelection()
        repeat(20) { assertNull(policy.sample(90, 30, false)) }
        repeat(3) { assertNull(policy.sample(90, 30, true)) }
        assertEquals(0, policy.sampleCount)
        repeat(7) { assertNull(policy.sample(90, 30, true)) }
        assertEquals(TrackingBackend.GPU, policy.sample(90, 30, true))
    }
    @Test fun retainsCpuWhenGpuIsSlower() {
        val policy = VtoBackendSelection()
        repeat(10) { policy.sample(45, 95, true) }
        assertEquals(TrackingBackend.CPU, policy.sample(45, 95, true))
    }
    @Test fun smallWinDoesNotSwitchBackend() {
        val policy = VtoBackendSelection()
        repeat(10) { policy.sample(50, 47, true) }
        assertEquals(TrackingBackend.CPU, policy.sample(50, 47, true))
    }
    @Test fun rejectsFastMedianWithBadSpike() {
        val policy = VtoBackendSelection()
        repeat(10) { policy.sample(50, 25, true) }
        assertEquals(TrackingBackend.CPU, policy.sample(50, 150, true))
    }
    @Test fun invalidTimingDoesNotCount() {
        val policy = VtoBackendSelection()
        repeat(20) { assertNull(policy.sample(0, 30, true)) }
        assertEquals(0, policy.sampleCount)
    }
}
