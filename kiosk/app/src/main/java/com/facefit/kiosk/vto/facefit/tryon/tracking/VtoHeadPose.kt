package com.facefit.kiosk.vto.facefit.tryon.tracking

import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer
import com.facefit.kiosk.vto.facefit.tryon.geometry.GlassesRig
import com.facefit.kiosk.vto.facefit.tryon.geometry.Vector3
import kotlin.math.hypot

/** Builds a rigid orientation from central face landmarks. */
internal object VtoHeadPose {
    const val DEPTH_WEIGHT = .25f

    fun rig(face: FaceAnalyzer.FaceMetrics, width: Int, height: Int, scale: Float, heightOffset: Float): GlassesRig? {
        if (face.landmarks.size < 468 || width <= 0 || height <= 0 ||
            !scale.isFinite() || scale <= 0f || !heightOffset.isFinite()) return null
        fun p(i: Int) = face.landmarks[i].let { Vector3(it.x * width, it.y * height, it.z * width * DEPTH_WEIGHT) }

        val first = (p(33) + p(133)) * .5f
        val second = (p(263) + p(362)) * .5f
        val eyes = second - first
        val distance = hypot(eyes.x, eyes.y)
        val nose = p(168)
        val ridge = p(6) * .6f + nose * .4f
        val forehead = p(10)
        if (!eyes.isFinite() || !distance.isFinite() || distance < 10f || !nose.isFinite() ||
            !ridge.isFinite() || !forehead.isFinite()) return null

        // Order by screen X so mirroring preserves a right-handed basis.
        // Use the full relative eye depth for yaw. The reduced depth used to seat
        // the frame on the face would flatten that rotation and foreshorten the arms.
        val eyeDirection = Vector3(eyes.x, eyes.y, eyes.z / DEPTH_WEIGHT)
        val right = eyeDirection.normalized() * (if (eyes.x >= 0f) 1f else -1f)
        // The ridge can sit above the eye line. The forehead gives a longer, consistent down vector.
        val depthHint = right.cross(ridge - forehead)
        if (depthHint.length() < distance * .01f || depthHint.z <= 0f) return null
        val behind = depthHint.normalized()
        val down = behind.cross(right).normalized()

        val eyeCenter = (first + second) * .5f
        val bridge = nose * .6f + eyeCenter * .4f
        val origin = bridge + down * (distance * heightOffset)
        val templeWidth = kotlin.math.abs((p(127) - p(356)).dot(right))
        val cheekWidth = kotlin.math.abs((p(234) - p(454)).dot(right))
        val faceWidth = maxOf(templeWidth, cheekWidth).takeIf { it.isFinite() } ?: 0f
        return GlassesRig(origin, right, down, behind, distance * scale, faceWidth)
    }
}
