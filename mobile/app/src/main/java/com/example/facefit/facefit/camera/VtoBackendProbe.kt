package com.example.facefit.facefit.camera

/**
 * Picks the faster face-tracking backend (GPU or CPU) on this phone by measuring it.
 *
 * Only one detector exists at a time: the probe measures the current backend on frames
 * that contain a face; if it is slow, it asks for the other backend, measures that too,
 * and settles on the one with the lower median model time. No-face frames are ignored
 * because they only run the small face detector, not the landmark model.
 *
 * Not thread-safe: call from the single tracking worker thread.
 */
internal class VtoBackendProbe(
    private val initial: TrackingBackend,
    private val slowMs: Long = SLOW_MODEL_MS,
    private val warmupFrames: Int = WARMUP_FRAMES,
    private val sampleFrames: Int = SAMPLE_FRAMES,
) {
    sealed interface Decision {
        /** Keep measuring the current backend. */
        data object Continue : Decision
        /** Close the current detector and create this one. */
        data class Switch(val to: TrackingBackend) : Decision
        /** Measuring is finished; use [backend] from now on. */
        data class Settled(val backend: TrackingBackend, val medians: Map<TrackingBackend, Long>) : Decision
    }

    private var current = initial
    private var skipped = 0
    private val samples = mutableListOf<Long>()
    private val medians = linkedMapOf<TrackingBackend, Long>()
    var settled: TrackingBackend? = null
        private set

    fun sample(backend: TrackingBackend, modelMs: Long, faceDetected: Boolean): Decision {
        if (settled != null) return Decision.Continue
        if (backend != current || !faceDetected || modelMs <= 0L) return Decision.Continue
        if (skipped < warmupFrames) { skipped++; return Decision.Continue }
        samples += modelMs
        if (samples.size < sampleFrames) return Decision.Continue

        medians[current] = samples.sorted()[samples.size / 2]
        samples.clear()
        skipped = 0
        val other = if (current == TrackingBackend.GPU) TrackingBackend.CPU else TrackingBackend.GPU
        return when {
            // Fast enough already: no reason to try the other backend.
            medians.size == 1 && medians.getValue(current) <= slowMs -> settle(current)
            medians.size == 1 -> { current = other; Decision.Switch(other) }
            else -> {
                // Both measured: keep the faster one; a near-tie keeps the backend now running.
                val best = medians.minBy { it.value }.key
                val winner = if (medians.getValue(best) * 1.15 < medians.getValue(current)) best else current
                settle(winner).also { if (winner != current) current = winner }
            }
        }
    }

    private fun settle(backend: TrackingBackend): Decision.Settled {
        settled = backend
        return Decision.Settled(backend, medians.toMap())
    }

    companion object {
        /** Above this landmark-model time the overlay visibly trails a moving head. */
        const val SLOW_MODEL_MS = 45L
        const val WARMUP_FRAMES = 3
        const val SAMPLE_FRAMES = 10
    }
}
