package com.example.facefit

import com.example.facefit.facefit.camera.TrackingBackend.CPU
import com.example.facefit.facefit.camera.TrackingBackend.GPU
import com.example.facefit.facefit.camera.VtoBackendProbe
import com.example.facefit.facefit.camera.VtoBackendProbe.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VtoBackendProbeTest {
    private fun VtoBackendProbe.feed(backend: com.example.facefit.facefit.camera.TrackingBackend, ms: Long, frames: Int = 13): Decision {
        var last: Decision = Decision.Continue
        repeat(frames) { last = sample(backend, ms, faceDetected = true) }
        return last
    }

    @Test fun fastGpuSettlesWithoutTryingCpu() {
        val probe = VtoBackendProbe(GPU)
        val decision = probe.feed(GPU, 20)
        assertEquals(Decision.Settled(GPU, mapOf(GPU to 20L)), decision)
        assertEquals(GPU, probe.settled)
    }

    @Test fun slowGpuTriesCpuAndKeepsTheFasterOne() {
        // Measured on the MT6769 test phone: ~310 ms per face frame on GPU.
        val probe = VtoBackendProbe(GPU)
        assertEquals(Decision.Switch(CPU), probe.feed(GPU, 310))
        assertEquals(Decision.Settled(CPU, mapOf(GPU to 310L, CPU to 40L)), probe.feed(CPU, 40))
    }

    @Test fun slowCpuThatIsNotFasterSwitchesBackToGpu() {
        val probe = VtoBackendProbe(GPU)
        probe.feed(GPU, 100)
        assertEquals(Decision.Settled(GPU, mapOf(GPU to 100L, CPU to 150L)), probe.feed(CPU, 150))
    }

    @Test fun nearTieKeepsTheBackendAlreadyRunning() {
        val probe = VtoBackendProbe(GPU)
        probe.feed(GPU, 100)
        assertEquals(CPU, (probe.feed(CPU, 95) as Decision.Settled).backend)
    }

    @Test fun framesWithoutAFaceOrFromTheOtherBackendAreIgnored() {
        val probe = VtoBackendProbe(GPU)
        repeat(50) { assertEquals(Decision.Continue, probe.sample(GPU, 20, faceDetected = false)) }
        repeat(50) { assertEquals(Decision.Continue, probe.sample(CPU, 20, faceDetected = true)) }
        assertNull(probe.settled)
    }

    @Test fun warmupFramesDoNotCount() {
        val probe = VtoBackendProbe(GPU)
        repeat(3) { probe.sample(GPU, 900, true) } // first-frame shader compile etc.
        assertEquals(Decision.Settled(GPU, mapOf(GPU to 20L)), probe.feed(GPU, 20, frames = 10))
    }
}
