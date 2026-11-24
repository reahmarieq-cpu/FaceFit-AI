package com.example.facefit.ui.tryon.rendering

import kotlin.math.hypot

/**
 * Short, bounded position prediction for the glasses.
 *
 * Positions arrive ~29 times a second but the screen draws ~57: without prediction the glasses hold
 * for a frame and then jump (measured ~20 px steps while moving), and every position is already
 * ~25-60 ms old. Each new sample updates a smoothed velocity; every display frame then moves the
 * glasses forward by velocity x the sample's age, capped at [maxLeadNanos].
 *
 * Guards: a direction reversal drops the momentum at once (no overshoot when the head turns back);
 * a sample gap longer than [staleNanos] resets it (lost tracking does not keep drifting); and
 * near-still motion gets no lead, so a still face stays as steady as before.
 */
internal class VtoMotionLead(
    private val maxLeadNanos: Long = 50_000_000L,
    private val staleNanos: Long = 150_000_000L,
    // Hand tremor stays below the start speed: predicting it would amplify the shake.
    private val stillSpeedPxPerSecond: Double = 70.0,
    private val fullSpeedPxPerSecond: Double = 250.0,
) {
    private var lastSample = Long.MIN_VALUE
    private var lastX = 0f
    private var lastY = 0f
    private var vx = 0.0 // px per nanosecond
    private var vy = 0.0

    fun reset() { lastSample = Long.MIN_VALUE; vx = 0.0; vy = 0.0 }

    /** [sampleNanos] is when the position (x, y) was captured; writes the lead to add into [out]. */
    fun apply(sampleNanos: Long, x: Float, y: Float, now: Long, out: FloatArray) {
        if (sampleNanos != lastSample) {
            val dt = sampleNanos - lastSample
            if (lastSample != Long.MIN_VALUE && dt in 1..staleNanos) {
                val nx = (x - lastX) / dt.toDouble()
                val ny = (y - lastY) / dt.toDouble()
                if (nx * vx + ny * vy < 0.0) { vx = 0.0; vy = 0.0 } // reversed: no momentum
                vx += (nx - vx) * VELOCITY_WEIGHT
                vy += (ny - vy) * VELOCITY_WEIGHT
            } else { vx = 0.0; vy = 0.0 }
            lastSample = sampleNanos
        }
        lastX = x; lastY = y
        val speed = hypot(vx, vy) * 1e9
        val gain = ((speed - stillSpeedPxPerSecond) / (fullSpeedPxPerSecond - stillSpeedPxPerSecond)).coerceIn(0.0, 1.0)
        val lead = (now - sampleNanos).coerceIn(0L, maxLeadNanos) * gain
        out[0] = (vx * lead).toFloat()
        out[1] = (vy * lead).toFloat()
    }

    private companion object {
        const val VELOCITY_WEIGHT = .5
    }
}
