package com.example.facefit.ui.tryon.rendering

import com.example.facefit.facefit.tryon.geometry.Vector3
import com.example.facefit.facefit.tryon.tracking.secondsBetween
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Spatial angular velocity from consecutive sensor poses, in radians per second. */
internal class VtoAngularMotion {
    private val zero = Vector3(0f, 0f, 0f)
    private var previousBasis: List<Vector3>? = null
    private var previousAt = 0L
    private var velocity: Vector3? = null
    private var acceleration = zero

    fun reset() {
        previousBasis = null
        velocity = null
        acceleration = zero
    }

    fun observe(matrix: FloatArray, sensorTimestampNanos: Long) {
        val previous = previousBasis
        val dt = secondsBetween(sensorTimestampNanos, previousAt)
        if (previous != null && dt <= 0.0) return
        val basis = List(3) { i ->
            val axis = column(matrix, i)
            val length = axis.length()
            if (!length.isFinite() || length <= 0f) {
                reset()
                return
            }
            axis * (1f / length)
        }
        if (previous != null) {
            val next = logarithm(previous, basis) * (1.0 / dt).toFloat()
            acceleration = velocity?.let { (next - it) * (1.0 / dt).toFloat() } ?: zero
            velocity = next
        }
        previousBasis = basis
        previousAt = sensorTimestampNanos
    }

    /** Returns whether the measured orientation is moving above the resting noise floor. */
    fun apply(matrix: FloatArray, anchor: Vector3, leadNanos: Long,
        noiseLimit: Float = 0f, normalizedProbes: List<Vector3>? = null,
        useMeasuredBasis: Boolean = false, worldPivot: Vector3 = transformPoint(matrix, anchor)): Boolean {
        val omega = velocity ?: return false
        val speed = omega.length()
        if (speed <= 0f || !speed.isFinite()) return false
        val probeSpeed = normalizedProbes?.let { probes ->
            sqrt(probes.sumOf { probe -> omega.cross(probe).let { it.dot(it).toDouble() } } / probes.size).toFloat()
        } ?: speed
        if (probeSpeed <= noiseLimit) return false
        var lead = secondsBetween(leadNanos.coerceIn(0L, 16_000_000L), 0L).toFloat()
        val axis = omega * (1f / speed)
        val braking = axis.dot(acceleration)
        // Relative deceleration has units of inverse seconds.
        val angle = if (braking < -0.5f * speed) {
            lead = minOf(lead, speed / acceleration.length())
            // Integrate the along-track deceleration up to the estimated stop.
            lead * (speed + braking * lead / 2f)
        } else speed * lead
        if (!angle.isFinite() || !worldPivot.isFinite()) return false

        val cosine = cos(angle)
        val sine = sin(angle)
        fun rotate(v: Vector3) =
            v * cosine + axis.cross(v) * sine + axis * (axis.dot(v) * (1f - cosine))
        val scale = column(matrix, 0).length()
        val basis = previousBasis ?: return false
        for (i in 0..2) {
            val before = column(matrix, i)
            val after = rotate(if (useMeasuredBasis) basis[i] * scale else before)
            matrix[i * 4] = after.x
            matrix[i * 4 + 1] = after.y
            matrix[i * 4 + 2] = after.z
        }
        // Place the authored bridge point at the measured bridge in world space.
        val origin = worldPivot - basisOffset(matrix, anchor)
        matrix[12] = origin.x
        matrix[13] = origin.y
        matrix[14] = origin.z
        return true
    }

    /** Principal log of R_current R_previous^T, including the half-turn case. */
    private fun logarithm(previous: List<Vector3>, current: List<Vector3>): Vector3 {
        var skew = zero
        var trace = 0f
        for (i in current.indices) {
            skew += previous[i].cross(current[i]) * .5f
            trace += previous[i].dot(current[i])
        }
        val sine = skew.length()
        val cosine = ((trace - 1f) * .5f).coerceIn(-1f, 1f)
        val angle = atan2(sine, cosine)
        if (cosine >= 0f) return if (sine > 0f) skew * (angle / sine) else zero
        if (sine > sqrt(Math.ulp(1f))) return skew * (angle / sine)

        // Near pi the skew part loses precision. Recover the axis from the
        // largest column of the symmetric part of the relative rotation.
        fun component(v: Vector3, i: Int) = when (i) { 0 -> v.x; 1 -> v.y; else -> v.z }
        fun entry(row: Int, col: Int) =
            current.indices.sumOf { (component(current[it], row) * component(previous[it], col)).toDouble() }.toFloat()
        val index = (0..2).maxBy { entry(it, it) }
        fun symmetric(i: Int) = (entry(i, index) + entry(index, i)) * .5f - if (i == index) cosine else 0f
        val axis = Vector3(symmetric(0), symmetric(1), symmetric(2)).normalized()
        return axis * if (axis.dot(skew) < 0f) -angle else angle
    }

    private fun basisOffset(matrix: FloatArray, point: Vector3): Vector3 =
        column(matrix, 0) * point.x + column(matrix, 1) * point.y + column(matrix, 2) * point.z

    private fun transformPoint(matrix: FloatArray, point: Vector3): Vector3 =
        Vector3(matrix[12], matrix[13], matrix[14]) + basisOffset(matrix, point)

    private fun column(matrix: FloatArray, index: Int) =
        Vector3(matrix[index * 4], matrix[index * 4 + 1], matrix[index * 4 + 2])
}
