
package com.facefit.kiosk.vto.facefit.tryon.geometry

import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer
import com.facefit.kiosk.vto.facefit.tryon.tracking.VtoHeadPose




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
            (sharedRig == null && face.landmarks.any {
                !it.x.isFinite() ||
                        !it.y.isFinite() ||
                        !it.z.isFinite()
            })
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

        val rig = sharedRig ?: VtoHeadPose.rig(face, width, height, scale, heightOffset) ?: return null

        // Eye spacing alone can place the hinges inside a wider face. Fit the rigid
        // frame across the temples as well; measure in head space to retain width on yaw.
        val eyeUnit = rig.size / (calibration?.span ?: halfExtent[0])
        val faceWidth = if (sharedRig != null || rig.faceWidth > 0f) rig.faceWidth else {
            val temple = kotlin.math.abs((point(127) - point(356)).dot(rig.xAxis))
            val cheek = kotlin.math.abs((point(234) - point(454)).dot(rig.xAxis))
            maxOf(temple, cheek)
        }
        val eyeSpacing = rig.size / scale
        val validWidth = faceWidth >= eyeSpacing * 1.4f && faceWidth <= eyeSpacing * 3f
        // Where the asset has hinge markers, fit the visible front using their span.
        // The complete bounding box can include arms or other distant geometry.
        val modelWidth = calibration?.hingeSpan ?: (2f * halfExtent[0])
        val unit = if (validWidth) {
            maxOf(eyeUnit, faceWidth * 1.02f * scale / modelWidth)
        } else eyeUnit
        val modelPivot = calibration?.bridge ?: calibration?.center ?: Vector3(center[0], center[1], center[2] + halfExtent[2])

        // Add hinge clearance as the head turns, without making the lenses taller
        // or extending the arms farther behind the head.
        val horizontalUnit = if (validWidth && calibration?.hingeSpan != null) {
            val turn = ((kotlin.math.abs(rig.xAxis.z) - .02f) / .13f).coerceIn(0f, 1f)
            val clearance = 1.02f + .08f * turn * turn * (3f - 2f * turn)
            maxOf(unit, faceWidth * clearance * scale / modelWidth)
        } else unit

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
        val x = worldDirection(rig.xAxis) * horizontalUnit

        val y = worldDirection(rig.yAxis) * -unit

        val z = worldDirection(rig.zAxis) * -unit

        // Seat the calibrated model pivot ahead of the face depth mask.
        val targetZ = -2000f - rig.origin.z + 12f
        val bridgeScreenPos = Vector3(rig.origin.x, height - rig.origin.y, targetZ)
        val origin = bridgeScreenPos - x * modelPivot.x - y * modelPivot.y - z * modelPivot.z

        return floatArrayOf(
            x.x, x.y, x.z, 0f,
            y.x, y.y, y.z, 0f,
            z.x, z.y, z.z, 0f,
            origin.x, origin.y, origin.z, 1f
        )
    }
}
