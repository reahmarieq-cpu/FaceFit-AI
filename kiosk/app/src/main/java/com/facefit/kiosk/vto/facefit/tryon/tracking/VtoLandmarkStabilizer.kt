package com.facefit.kiosk.vto.facefit.tryon.tracking

import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer
import kotlin.math.PI
import kotlin.math.abs

/** One-Euro filter. Values and velocities are measured in preview pixels. */
internal class OneEuroFilter(
    private val minCutoff: Float = 0.25f,
    private val beta: Float = 0.05f
) {
    private var raw = 0f
    private var filtered = 0f
    private var speed = 0f

    fun sample(value: Float, dt: Float): Float {
        if (dt <= 0f) {
            raw = value
            filtered = value
            speed = 0f
        } else {
            val velocity = (value - raw) / dt
            speed += (velocity - speed) * alpha(dt, 2f)
            val dynamicCutoff = minCutoff + beta * abs(speed)
            filtered += (value - filtered) * alpha(dt, dynamicCutoff)
            raw = value
        }
        return filtered
    }

    companion object {
        fun alpha(dt: Float, cutoff: Float): Float {
            val rate = (2 * PI).toFloat() * dt * cutoff
            return (rate / (1f + rate)).coerceIn(0f, 1f)
        }
    }
}

/** Filters the deformable mesh independently of the glasses' rigid pose. */
internal class VtoLandmarkStabilizer(
    private val minCutoff: Float = 0.25f,
    private val beta: Float = 0.05f
) {
    private var filters = emptyArray<OneEuroFilter>()
    private var previous: FaceAnalyzer.FaceMetrics? = null
    private var timestamp = 0L

    fun sample(face: FaceAnalyzer.FaceMetrics?, now: Long): FaceAnalyzer.FaceMetrics? {
        if (face?.isFaceDetected != true || face.landmarks.size < 468 ||
            face.landmarks.any { !it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite() }) {
            previous = null
            return face
        }
        val old = previous
        val elapsed = now - timestamp
        previous = face
        timestamp = now
        val reset = old == null || elapsed !in 1L..250_000_000L ||
            old.imageWidth != face.imageWidth || old.imageHeight != face.imageHeight ||
            old.landmarks.size != face.landmarks.size
        if (reset) filters = Array(face.landmarks.size * 3) { OneEuroFilter(minCutoff, beta) }
        val dt = if (reset) 0f else elapsed / 1_000_000_000f
        val width = face.imageWidth.coerceAtLeast(1).toFloat()
        val height = face.imageHeight.coerceAtLeast(1).toFloat()
        val points = face.landmarks.mapIndexed { i, point ->
            FaceAnalyzer.LandmarkPoint(
                filters[i * 3].sample(point.x * width, dt) / width,
                filters[i * 3 + 1].sample(point.y * height, dt) / height,
                filters[i * 3 + 2].sample(point.z * width, dt) / width
            )
        }
        return if (reset) face else face.copy(landmarks = points)
    }
}
