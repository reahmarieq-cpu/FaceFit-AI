package com.example.facefit

import com.example.facefit.logic.FaceAnalyzer
import com.example.facefit.logic.FaceScanPose
import org.junit.Assert.*
import org.junit.Test

class FaceScanPoseTest {
    private fun face(noseX: Float = 0.5f, depth: Float = 0f): FaceAnalyzer.FaceMetrics {
        val points = MutableList(478) { FaceAnalyzer.LandmarkPoint(0.5f, 0.5f) }
        points[1] = FaceAnalyzer.LandmarkPoint(noseX, 0.52f)
        points[234] = FaceAnalyzer.LandmarkPoint(0.35f, 0.5f, -depth)
        points[454] = FaceAnalyzer.LandmarkPoint(0.65f, 0.5f, depth)
        return FaceAnalyzer.FaceMetrics(0f, 0f, 0f, "", points, 640, 960, true)
    }

    @Test fun centeredFaceAndItsMirrorPassTheSamePoseCheck() {
        val frontal = face()
        assertTrue(FaceScanPose.isFacingCamera(frontal))
        assertTrue(FaceScanPose.isFacingCamera(frontal.copy(
            landmarks = frontal.landmarks.map { it.copy(x = 1f - it.x) })))
    }

    @Test fun stronglyTurnedAndInvalidFacesDoNotPass() {
        assertFalse(FaceScanPose.isFacingCamera(face(noseX = 0.61f)))
        assertFalse(FaceScanPose.isFacingCamera(face(depth = 0.10f)))
        assertFalse(FaceScanPose.isFacingCamera(face(depth = Float.NaN)))
        assertFalse(FaceScanPose.isFacingCamera(face().copy(imageWidth = 0)))
    }
}
