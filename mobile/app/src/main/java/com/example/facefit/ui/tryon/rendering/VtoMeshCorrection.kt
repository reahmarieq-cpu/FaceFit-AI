package com.example.facefit.ui.tryon.rendering

import kotlin.math.exp
import kotlin.math.hypot

/**
 * Blends out the small position step at each new face mesh instead of showing it at once.
 *
 * Between meshes the glasses follow camera-rate eye tracking; each new mesh disagrees with that by
 * a few pixels (measured median ~10 px while moving), which showed as a 15-20 Hz twitch. At a mesh
 * change the step (minus the motion expected over one display frame) becomes an offset that decays
 * with [timeConstantNanos]. Steps larger than the snap distance are real corrections (a face found
 * again) and are shown at once. Real movement is never delayed: the offset only covers the step.
 */
internal class VtoMeshCorrection(private val timeConstantNanos: Long = 60_000_000L) {
    private var offsetX = 0f
    private var offsetY = 0f
    private var meshId = Long.MIN_VALUE
    private var lastTime = 0L
    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var lastShownX = Float.NaN
    private var lastShownY = Float.NaN
    private var velocityX = 0f
    private var velocityY = 0f

    fun reset() {
        offsetX = 0f; offsetY = 0f; meshId = Long.MIN_VALUE; lastTime = 0L
        lastX = Float.NaN; lastY = Float.NaN; lastShownX = Float.NaN; lastShownY = Float.NaN
        velocityX = 0f; velocityY = 0f
    }

    /** Writes the offset to add to this frame's target (x, y) into [out]. */
    fun apply(mesh: Long, x: Float, y: Float, now: Long, snapDistance: Float, out: FloatArray) {
        if (lastTime != 0L && now > lastTime) {
            val decay = exp(-(now - lastTime).toDouble() / timeConstantNanos).toFloat()
            offsetX *= decay; offsetY *= decay
        }
        if (mesh != meshId && lastShownX.isFinite()) {
            // Where the glasses would have been next without a new mesh: last shown + one frame of motion.
            val stepX = lastShownX + velocityX - x
            val stepY = lastShownY + velocityY - y
            if (hypot(stepX, stepY) <= snapDistance) { offsetX = stepX; offsetY = stepY }
            else { offsetX = 0f; offsetY = 0f }
        } else if (mesh == meshId && lastX.isFinite()) {
            velocityX = x - lastX; velocityY = y - lastY
        }
        meshId = mesh; lastTime = now; lastX = x; lastY = y
        lastShownX = x + offsetX; lastShownY = y + offsetY
        out[0] = offsetX; out[1] = offsetY
    }
}
