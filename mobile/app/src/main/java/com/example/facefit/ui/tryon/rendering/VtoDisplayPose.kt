package com.example.facefit.ui.tryon.rendering

import com.example.facefit.facefit.tryon.geometry.Vector3
import com.example.facefit.facefit.tryon.tracking.VtoUnitQuaternion
import com.example.facefit.facefit.tryon.tracking.secondsBetween
import kotlin.math.expm1

/** Display-clock exponential interpolation toward the latest measured rigid pose. */
internal class VtoDisplayPose {
    private data class Pose(val position: Vector3, val rotation: VtoUnitQuaternion, val scale: Float) {
        fun matrix(anchor: Vector3): FloatArray {
            val basis = rotation.axes().map { it * scale }
            val origin = position - basis[0] * anchor.x - basis[1] * anchor.y - basis[2] * anchor.z
            return floatArrayOf(
                basis[0].x, basis[0].y, basis[0].z, 0f,
                basis[1].x, basis[1].y, basis[1].z, 0f,
                basis[2].x, basis[2].y, basis[2].z, 0f,
                origin.x, origin.y, origin.z, 1f
            )
        }
    }

    private var displayed: Pose? = null
    private var matrix: FloatArray? = null
    private var previousTime: Long? = null

    fun reset() {
        displayed = null
        matrix = null
        previousTime = null
    }

    fun sample(targetMatrix: FloatArray?, anchor: Vector3, now: Long): FloatArray? {
        if (targetMatrix == null || targetMatrix.size != 16 || targetMatrix.any { !it.isFinite() }) {
            reset()
            return null
        }
        val a = Vector3(targetMatrix[0], targetMatrix[1], targetMatrix[2])
        val b = Vector3(targetMatrix[4], targetMatrix[5], targetMatrix[6])
        val c = Vector3(targetMatrix[8], targetMatrix[9], targetMatrix[10])
        val scale = a.length()
        if (scale <= 0f || !scale.isFinite()) {
            reset()
            return null
        }
        val target = Pose(
            Vector3(targetMatrix[12], targetMatrix[13], targetMatrix[14]) +
                a * anchor.x + b * anchor.y + c * anchor.z,
            VtoUnitQuaternion.fromAxes(a * (1f / scale), b * (1f / scale), c * (1f / scale)), scale
        )
        val current = displayed
        val last = previousTime
        if (current == null || last == null) {
            displayed = target
            previousTime = now
            return targetMatrix.copyOf().also { matrix = it }
        }
        val elapsed = secondsBetween(now, last)
        if (elapsed <= 0.0) return matrix
        previousTime = now
        val alpha = (-expm1(-elapsed / RESPONSE_SECONDS)).toFloat().coerceIn(0f, 1f)
        val next = Pose(
            current.position + (target.position - current.position) * alpha,
            current.rotation.slerp(target.rotation, alpha),
            current.scale + (target.scale - current.scale) * alpha
        )
        displayed = next
        val result = next.matrix(anchor)
        if (matrix?.contentEquals(result) != true) matrix = result
        return matrix
    }

    private companion object {
        const val RESPONSE_SECONDS = .016
    }
}
