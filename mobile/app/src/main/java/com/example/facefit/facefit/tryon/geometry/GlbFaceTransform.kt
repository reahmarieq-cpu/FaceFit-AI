
package com.example.facefit.facefit.tryon.geometry

import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.tryon.tracking.VtoHeadPose




/**
 * Maps a Y-up GLB glasses model into the mirrored
 * CameraX preview using MediaPipe face landmarks.
 *
 * This class is used only for 3D Virtual Try-On.
 */
object GlbFaceTransform {

    internal fun matrix(
        face: FaceAnalyzer.FaceMetrics?,
        width: Int,
        height: Int,
        center: FloatArray,
        halfExtent: FloatArray,
        scale: Float,
        heightOffset: Float,
        calibration: VtoAutomaticFit.ModelAnchors? = null,
        sharedRig: GlassesRig? = null
    ): FloatArray? {

        if (
            (calibration != null && !calibration.isValid()) ||
            face?.isFaceDetected != true ||
            face.landmarks.size < 468 ||
            width <= 0 ||
            height <= 0 ||
            center.size != 3 ||
            halfExtent.size != 3 ||
            halfExtent[0] <= 0f ||
            !scale.isFinite() ||
            scale <= 0f ||
            !heightOffset.isFinite() ||
            (center + halfExtent).any { !it.isFinite() } ||
            face.landmarks.any {
                !it.x.isFinite() ||
                        !it.y.isFinite() ||
                        !it.z.isFinite()
            }
        ) {
            return null
        }

        fun point(index: Int): Vector3 {
            val landmark = face.landmarks[index]

            return Vector3(
                landmark.x * width,
                landmark.y * height,
                landmark.z * width
            )
        }

        val leftEye =
            (point(33) + point(133)) * 0.5f

        val rightEye =
            (point(263) + point(362)) * 0.5f

        val eyeCenter =
            (leftEye + rightEye) * 0.5f

        val rig = sharedRig ?: VtoHeadPose.rig(face, width, height, scale, heightOffset) ?: return null

        // Eye spacing alone can place the hinges inside a wider face. Fit the rigid
        // frame across the temples as well; measure in head space to retain width on yaw.
        val eyeUnit = rig.size / (calibration?.span ?: halfExtent[0])
        val templeWidth = kotlin.math.abs((point(127) - point(356)).dot(rig.xAxis))
        val cheekWidth = kotlin.math.abs((point(234) - point(454)).dot(rig.xAxis))
        val faceWidth = maxOf(templeWidth, cheekWidth)
        val validWidth = faceWidth >= rig.size * 1.4f && faceWidth <= rig.size * 3f
        val unit = if (calibration != null && validWidth) {
            maxOf(eyeUnit, faceWidth * 1.03f * scale / (2f * halfExtent[0]))
        } else eyeUnit
        val anchor = calibration?.center ?: Vector3(center[0], center[1], center[2] + halfExtent[2])

        // Filament uses Y-up coordinates.
        // The camera preview uses Y-down coordinates.
        fun worldDirection(v: Vector3): Vector3 {
            return Vector3(
                v.x,
                -v.y,
                -v.z
            )
        }

        /*
         * CameraX already maps landmarks into the mirrored
         * preview. Use the rig's screen-space orientation.
         *
         * GLB local X is horizontal.
         * GLB local Y is vertical.
         * GLB local Z is depth.
         */
        val x = worldDirection(rig.xAxis) * unit

        val y = worldDirection(rig.yAxis) * -unit

        val z = worldDirection(rig.zAxis) * -unit

        /*
         * The bounding box includes the glasses' temples.
         * Its center is not necessarily the center of the
         * front frame.
         *
         * For a -Z-temple GLB, the front frame is near
         * the maximum Z side of the bounding box.
         */
        val frontZ = anchor.z

        /*
         * Position the GLB's front-frame reference at
         * the eye center rather than at the model's
         * overall bounding-box center.
         */
        val targetX = rig.origin.x
        val targetY = height - rig.origin.y

        // Keep the existing face-depth placement.
        val targetZ = -2000f - rig.origin.z

        val origin = Vector3(
            targetX,
            targetY,
            targetZ
        ) -
                x * anchor.x -
                y * anchor.y -
                z * frontZ

        return floatArrayOf(
            x.x, x.y, x.z, 0f,
            y.x, y.y, y.z, 0f,
            z.x, z.y, z.z, 0f,
            origin.x, origin.y, origin.z, 1f
        )
    }
}
