package com.example.facefit.facefit.tryon.tracking

import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.tryon.geometry.GlassesRig

/** Resolves each measurement directly using one anatomical rig and one bridge origin. */
internal class VtoRigResolver {
    fun reset() = Unit

    @Suppress("UNUSED_PARAMETER")
    fun resolve(face: FaceAnalyzer.FaceMetrics?, width: Int, height: Int, scale: Float,
        offset: Float, now: Long, measurementKey: Any? = face): GlassesRig? {
        if (face?.isFaceDetected != true) return null
        return VtoHeadPose.rig(face, width, height, scale, offset)
    }
}
