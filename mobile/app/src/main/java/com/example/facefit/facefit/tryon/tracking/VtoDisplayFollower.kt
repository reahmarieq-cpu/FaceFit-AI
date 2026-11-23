package com.example.facefit.facefit.tryon.tracking

import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.tryon.geometry.Vector3

/** Display-rate tracking, including face-loss grace and reacquisition. */
internal class VtoDisplayFollower {
    private companion object {
        const val LOSS_GRACE_NANOS = 25_000_000L
        const val LOSS_FADE_NANOS = 50_000_000L
    }

    data class Display(val face: FaceAnalyzer.FaceMetrics?, val opacity: Float)
    private var output = Display(null, 0f)
    private var previousTick: Long? = null
    private var lostAt: Long? = null
    private var width = 0
    private var height = 0

    fun reset() {
        output = Display(null, 0f); previousTick = null
        lostAt = null; width = 0; height = 0
    }

    @Suppress("UNUSED_PARAMETER")
    fun sample(face: FaceAnalyzer.FaceMetrics?, now: Long, snap: Boolean = false): Display {
        if (face != null && face.imageWidth>0 && face.imageHeight>0 &&
            (face.imageWidth != width || face.imageHeight != height)) {
            reset(); width = face.imageWidth; height = face.imageHeight
        }
        val previous = previousTick
        if (previous != null && now < previous) return output
        previousTick = now
        val valid = face?.isFaceDetected == true && face.landmarks.size >= 468 &&
            face.imageWidth > 0 && face.imageHeight > 0 &&
            face.landmarks.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }
        if (!valid) {
            val start = lostAt ?: now.also { lostAt = it }
            val age = (now - start).coerceAtLeast(0L)
            if (age < LOSS_GRACE_NANOS) return output
            // Grace has ended: discard the interpolation target, retaining pixels only for fade.
            val opacity = (1f - (age - LOSS_GRACE_NANOS).toFloat() / LOSS_FADE_NANOS).coerceIn(0f, 1f)
            return set(if (opacity > 0f) output.face else null, opacity)
        }
        val target = requireNotNull(face)
        lostAt = null
        return set(output.face?.takeIf { it == target } ?: target, 1f)
    }

    private fun set(face: FaceAnalyzer.FaceMetrics?, opacity: Float): Display {
        if (output.face === face && output.opacity == opacity) return output
        return Display(face, opacity).also { output = it }
    }
}

/** Compatibility handoff with no stored velocity or prediction. */
internal class VtoPositionHandoff {
    fun reset() = Unit

    @Suppress("UNUSED_PARAMETER")
    fun sample(position: Vector3, source: Any, live: Boolean, now: Long, meshPeriod: Long,
        reconcile: Boolean = true): Vector3 = position
}
