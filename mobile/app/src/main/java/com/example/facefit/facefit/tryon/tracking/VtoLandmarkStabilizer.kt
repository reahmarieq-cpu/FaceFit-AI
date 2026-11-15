package com.example.facefit.facefit.tryon.tracking

import com.example.facefit.facefit.analysis.FaceAnalyzer

/** Validation only. Temporal filtering belongs exclusively to VtoDisplayFollower. */
internal class VtoLandmarkStabilizer {
    @Suppress("UNUSED_PARAMETER")
    fun sample(face: FaceAnalyzer.FaceMetrics?, now: Long): FaceAnalyzer.FaceMetrics? {
        if (face == null || !face.isFaceDetected) return face
        if (face.imageWidth <= 0 || face.imageHeight <= 0 || face.landmarks.size < 468 ||
            face.landmarks.any { !it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite() }) {
            return face.copy(isFaceDetected=false, landmarks=emptyList(), detectedFaceCount=0)
        }
        return face
    }
}
