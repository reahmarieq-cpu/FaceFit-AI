package com.example.facefit.ui.tryon.rendering

import com.example.facefit.facefit.tryon.geometry.Vector3
import com.example.facefit.facefit.tryon.tracking.VtoUnitQuaternion
import com.example.facefit.facefit.tryon.tracking.secondsBetween
import kotlin.math.expm1

/**
 * Display-clock exponential interpolation toward the latest measured rigid pose.
 *
 * With an eye span ([sample]'s spanPx), the response adapts like a One Euro filter: a slow
 * cutoff while the head is still (hides landmark jitter and small mesh-to-mesh jumps) that
 * rises with measured speed, so real movement is followed within one or two display frames.
 * Position, rotation and scale each adapt to their own speed. Never predicts or overshoots.
 */
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
    private var previousTarget: Pose? = null
    // Signed velocities: back-and-forth jitter averages out, real movement does not.
    private var positionVelocity = Vector3(0f, 0f, 0f)
    private var rotationVelocity = Vector3(0f, 0f, 0f)
    private var scaleVelocity = 0.0

    fun reset() {
        displayed = null
        matrix = null
        previousTime = null
        previousTarget = null
        positionVelocity = Vector3(0f, 0f, 0f); rotationVelocity = Vector3(0f, 0f, 0f); scaleVelocity = 0.0
    }

    fun sample(targetMatrix: FloatArray?, anchor: Vector3, now: Long, spanPx: Float = 0f): FloatArray? {
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
            previousTarget = target
            previousTime = now
            return targetMatrix.copyOf().also { matrix = it }
        }
        val elapsed = secondsBetween(now, last)
        if (elapsed <= 0.0) return matrix
        previousTime = now
        val fixed = (-expm1(-elapsed / RESPONSE_SECONDS)).toFloat().coerceIn(0f, 1f)
        var positionAlpha = fixed
        var rotationAlpha = fixed
        var scaleAlpha = fixed
        val before = previousTarget
        if (spanPx > 0f && spanPx.isFinite() && before != null) {
            // Velocities of the target itself (eye spans/s, rad/s, relative scale/s), low-passed so
            // jitter and a single mesh-to-mesh jump do not look like movement.
            val d = alpha(elapsed, SPEED_CUTOFF_HZ)
            // Turn/nod and size come from face meshes (~18/s) whose small mesh-to-mesh noise
            // otherwise reads as movement and opens the filter: slower speed estimate, a noise
            // deadband, and a lower resting cutoff keep a still head still.
            val slow = alpha(elapsed, MESH_SPEED_CUTOFF_HZ)
            val inverse = (1.0 / elapsed).toFloat()
            positionVelocity += ((target.position - before.position) * (inverse / spanPx) - positionVelocity) * d
            rotationVelocity += (angularStep(before.rotation, target.rotation) * inverse - rotationVelocity) * slow
            scaleVelocity += ((target.scale - before.scale) / before.scale / elapsed - scaleVelocity) * slow
            positionAlpha = alpha(elapsed, cutoff(positionVelocity.length().toDouble(), POSITION_BETA))
            rotationAlpha = alpha(elapsed, cutoff((rotationVelocity.length() - ROTATION_DEADBAND).coerceAtLeast(0.0),
                ROTATION_BETA, MESH_MIN_CUTOFF_HZ))
            scaleAlpha = alpha(elapsed, cutoff((kotlin.math.abs(scaleVelocity) - SCALE_DEADBAND).coerceAtLeast(0.0),
                SCALE_BETA, MESH_MIN_CUTOFF_HZ))
        }
        previousTarget = target
        val next = Pose(
            current.position + (target.position - current.position) * positionAlpha,
            current.rotation.slerp(target.rotation, rotationAlpha),
            current.scale + (target.scale - current.scale) * scaleAlpha
        )
        displayed = next
        val result = next.matrix(anchor)
        if (matrix?.contentEquals(result) != true) matrix = result
        return matrix
    }

    /** Small-angle rotation vector (radians) taking [from] to [to]. */
    private fun angularStep(from: VtoUnitQuaternion, to: VtoUnitQuaternion): Vector3 {
        // to * conjugate(from)
        val w = to.w * from.w + to.x * from.x + to.y * from.y + to.z * from.z
        val x = -to.w * from.x + to.x * from.w - to.y * from.z + to.z * from.y
        val y = -to.w * from.y + to.x * from.z + to.y * from.w - to.z * from.x
        val z = -to.w * from.z - to.x * from.y + to.y * from.x + to.z * from.w
        val sign = if (w < 0f) -2f else 2f
        return Vector3(x * sign, y * sign, z * sign)
    }

    private fun cutoff(speed: Double, beta: Double, minimum: Double = MIN_CUTOFF_HZ) =
        (minimum + beta * speed).coerceAtMost(MAX_CUTOFF_HZ)

    private fun alpha(elapsed: Double, cutoffHz: Double) =
        (-expm1(-elapsed * 2 * Math.PI * cutoffHz)).toFloat().coerceIn(0f, 1f)

    private companion object {
        const val RESPONSE_SECONDS = .016
        /** Still: about an 80 ms time constant (meshes now arrive ~18/s with little noise). */
        const val MIN_CUTOFF_HZ = 2.0
        /** Moving: about the old fixed 16 ms response. */
        const val MAX_CUTOFF_HZ = 10.0
        const val SPEED_CUTOFF_HZ = 5.0
        /** Cutoff gain per eye span per second. */
        const val POSITION_BETA = 12.0
        /** Cutoff gain per radian per second. */
        const val ROTATION_BETA = 15.0
        /** Cutoff gain per unit relative scale change per second. */
        const val SCALE_BETA = 30.0
        /** Turn/nod and size: about a 160 ms time constant at rest. */
        const val MESH_MIN_CUTOFF_HZ = 1.0
        const val MESH_SPEED_CUTOFF_HZ = 1.5
        /** Angular speed (rad/s) below this is mesh noise, not a head turn. */
        const val ROTATION_DEADBAND = .15
        /** Relative size change per second below this is mesh noise, not a move closer or farther. */
        const val SCALE_DEADBAND = .04
    }
}
