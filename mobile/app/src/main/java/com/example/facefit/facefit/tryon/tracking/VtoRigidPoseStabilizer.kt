package com.example.facefit.facefit.tryon.tracking

import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.tryon.geometry.Vector3

/** Filters the measured rigid transform about the GLB's front anchor. */
internal class VtoRigidPoseStabilizer {
    private data class Pose(
        val position: Vector3, val x: Vector3, val y: Vector3, val z: Vector3, val scale: Float
    ) {
        val rotation = VtoUnitQuaternion.fromAxes(x, y, z)

        fun matrix(anchor: Vector3): FloatArray {
            val a = x * scale
            val b = y * scale
            val c = z * scale
            val origin = position - a * anchor.x - b * anchor.y - c * anchor.z
            return floatArrayOf(a.x, a.y, a.z, 0f, b.x, b.y, b.z, 0f,
                c.x, c.y, c.z, 0f, origin.x, origin.y, origin.z, 1f)
        }

        fun displacement(previous: Pose, ipd: Float): List<Vector3> {
            val shift = (position - previous.position) * (1f / ipd)
            val scaleRatio = previous.scale / scale
            return listOf(shift, shift + x - previous.x * scaleRatio,
                shift + y - previous.y * scaleRatio, shift + z - previous.z * scaleRatio)
        }

        fun projectedFrom(measured: Pose, projected: Pose): Pose {
            fun rotate(axis: Vector3) =
                projected.x * measured.x.dot(axis) + projected.y * measured.y.dot(axis) +
                    projected.z * measured.z.dot(axis)
            return copy(position = position + (projected.position - measured.position),
                x = rotate(x), y = rotate(y), z = rotate(z))
        }
    }

    private val response = VtoOneEuro(minimumCutoff = 1.8)
    val restingNoiseLimit: Float get() = response.restingNoiseLimit
    private var measuredLandmarks: FaceAnalyzer.FaceMetrics? = null
    private var measurement: FloatArray? = null
    private var measuredPose: Pose? = null
    private var shownPose: Pose? = null
    private var lastFrame: Long? = null
    private var measuredAt = 0L
    private var rotationSpeed = 0f
    private var output: FloatArray? = null

    fun reset() {
        measuredLandmarks = null
        measurement = null
        measuredPose = null
        shownPose = null
        lastFrame = null
        output = null
        rotationSpeed = 0f
    }

    fun sample(matrix: FloatArray?, anchor: Vector3, ipd: Float, now: Long,
        measuredMatrix: FloatArray? = matrix, landmarks: FaceAnalyzer.FaceMetrics? = null): FloatArray? {
        if (matrix == null || measuredMatrix == null || !ipd.isFinite() || ipd <= 0f) {
            reset()
            return null
        }
        val previous = measuredPose
        val shown = shownPose
        val last = lastFrame
        if (previous == null || shown == null || last == null) {
            val pose = read(measuredMatrix, anchor)
            response.reset(now)
            measuredAt = now
            measuredLandmarks = landmarks
            measurement = measuredMatrix
            measuredPose = pose
            shownPose = pose
            lastFrame = now
            output = matrix
            return matrix
        }
        val elapsed = secondsBetween(now, last)
        if (elapsed <= 0.0) return output
        lastFrame = now

        val target = if (measurement === measuredMatrix) previous else read(measuredMatrix, anchor).also {
            val old = measuredLandmarks
            val motion = if (landmarks != null && old != null) {
                landmarkDisplacement(landmarks, old) ?: it.displacement(previous, ipd)
            } else it.displacement(previous, ipd)
            val dt = secondsBetween(now, measuredAt).toFloat()
            val translationSpeed = (it.position - previous.position).length() / ipd / dt
            rotationSpeed = (2.0 * kotlin.math.acos(kotlin.math.abs(it.rotation.dot(previous.rotation)).toDouble()
                .coerceIn(0.0, 1.0)) / dt).toFloat()
            val angularSpeed = rotationSpeed
            val responsiveness = if (translationSpeed < .05f && angularSpeed < .5f) 0f else 1f
            response.observe(motion, now, landmarks?.snrFactor ?: 1f, responsiveness)
            measuredAt = now
            measuredLandmarks = landmarks
            measurement = measuredMatrix
            measuredPose = it
        }
        val weight = if (target === shown) 0f else
            response.weight(VtoOneEuro.rms(target.displacement(shown, ipd)), elapsed)
        val baseCutoff = 1.8 + 2.5 * rotationSpeed
        val cutoff = if (rotationSpeed > .1f) maxOf(15.0, baseCutoff) else baseCutoff
        val rotationWeight = if (weight == 0f) 0f else
            (-kotlin.math.expm1(-2.0 * Math.PI * cutoff * elapsed)).toFloat().coerceIn(0f, 1f)
        val rotation = shown.rotation.slerp(target.rotation, rotationWeight)
        val axes = rotation.axes()
        val next = if (weight == 0f && rotationWeight == 0f) shown else Pose(
            mix(shown.position, target.position, weight), axes[0], axes[1], axes[2],
            shown.scale + (target.scale - shown.scale) * weight)
        shownPose = next
        val candidate = when {
            next === target -> matrix
            matrix === measuredMatrix -> next.matrix(anchor)
            else -> next.projectedFrom(target, read(matrix, anchor)).matrix(anchor)
        }
        if (output?.contentEquals(candidate) != true) output = candidate
        return output
    }

    private fun read(matrix: FloatArray, anchor: Vector3): Pose {
        val a = Vector3(matrix[0], matrix[1], matrix[2])
        val b = Vector3(matrix[4], matrix[5], matrix[6])
        val c = Vector3(matrix[8], matrix[9], matrix[10])
        val origin = Vector3(matrix[12], matrix[13], matrix[14])
        return Pose(origin + a * anchor.x + b * anchor.y + c * anchor.z,
            a.normalized(), b.normalized(), c.normalized(), a.length())
    }

    private fun mix(a: Vector3, b: Vector3, weight: Float) = a + (b - a) * weight
}

/** Unit quaternion orientation, with shortest-arc sign alignment before interpolation. */
internal data class VtoUnitQuaternion(val w: Float, val x: Float, val y: Float, val z: Float) {
    fun dot(other: VtoUnitQuaternion) = w * other.w + x * other.x + y * other.y + z * other.z

    fun normalized(): VtoUnitQuaternion {
        val length = kotlin.math.sqrt(dot(this))
        return VtoUnitQuaternion(w / length, x / length, y / length, z / length)
    }

    fun slerp(target: VtoUnitQuaternion, weight: Float): VtoUnitQuaternion {
        val aligned = if (dot(target) < 0f) VtoUnitQuaternion(-target.w, -target.x, -target.y, -target.z) else target
        val cosine = dot(aligned).toDouble().coerceIn(0.0, 1.0)
        val angle = kotlin.math.acos(cosine)
        val sine = kotlin.math.sin(angle)
        // Near coincidence, normalized interpolation avoids division by a tiny sine.
        val a = if (sine <= kotlin.math.sqrt(Math.ulp(1f).toDouble())) 1.0 - weight
            else kotlin.math.sin((1.0 - weight) * angle) / sine
        val b = if (sine <= kotlin.math.sqrt(Math.ulp(1f).toDouble())) weight.toDouble()
            else kotlin.math.sin(weight * angle) / sine
        return VtoUnitQuaternion((a*w+b*aligned.w).toFloat(), (a*x+b*aligned.x).toFloat(),
            (a*y+b*aligned.y).toFloat(), (a*z+b*aligned.z).toFloat()).normalized()
    }

    fun axes(): List<Vector3> = listOf(
        Vector3(1f - 2f*(y*y+z*z), 2f*(x*y+w*z), 2f*(x*z-w*y)),
        Vector3(2f*(x*y-w*z), 1f - 2f*(x*x+z*z), 2f*(y*z+w*x)),
        Vector3(2f*(x*z+w*y), 2f*(y*z-w*x), 1f - 2f*(x*x+y*y))
    )

    companion object {
        fun fromAxes(a: Vector3, b: Vector3, c: Vector3): VtoUnitQuaternion {
            val trace = a.x + b.y + c.z
            val result = when {
                trace > 0f -> {
                    val s = 2f * kotlin.math.sqrt(trace + 1f)
                    VtoUnitQuaternion(s/4f, (b.z-c.y)/s, (c.x-a.z)/s, (a.y-b.x)/s)
                }
                a.x > b.y && a.x > c.z -> {
                    val s = 2f * kotlin.math.sqrt(1f+a.x-b.y-c.z)
                    VtoUnitQuaternion((b.z-c.y)/s, s/4f, (b.x+a.y)/s, (c.x+a.z)/s)
                }
                b.y > c.z -> {
                    val s = 2f * kotlin.math.sqrt(1f+b.y-a.x-c.z)
                    VtoUnitQuaternion((c.x-a.z)/s, (b.x+a.y)/s, s/4f, (c.y+b.z)/s)
                }
                else -> {
                    val s = 2f * kotlin.math.sqrt(1f+c.z-a.x-b.y)
                    VtoUnitQuaternion((a.y-b.x)/s, (c.x+a.z)/s, (c.y+b.z)/s, s/4f)
                }
            }
            return result.normalized()
        }
    }
}