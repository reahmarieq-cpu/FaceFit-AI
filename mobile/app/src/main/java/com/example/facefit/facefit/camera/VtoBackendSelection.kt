package com.example.facefit.facefit.camera

/** Same-frame, face-present samples only. A small apparent win must not trigger GPU use. */
internal class VtoBackendSelection {
    private var warmup = 0
    private val cpu = mutableListOf<Long>()
    private val gpu = mutableListOf<Long>()
    val sampleCount get() = cpu.size

    fun sample(cpuMs: Long, gpuMs: Long, bothHaveFace: Boolean): TrackingBackend? {
        if (!bothHaveFace || cpuMs <= 0 || gpuMs <= 0) return null
        if (warmup++ < 3) return null
        cpu.add(cpuMs)
        gpu.add(gpuMs)
        if (cpu.size < 8) return null
        // Compare median and tail: GPU needs a meaningful win without worse spikes.
        val c = cpu.sorted()
        val g = gpu.sorted()
        return if (g[4] < c[4] * .85 && g[7] <= c[7] * 1.10)
            TrackingBackend.GPU else TrackingBackend.CPU
    }
}
