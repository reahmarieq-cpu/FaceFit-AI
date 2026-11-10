package com.facefit.kiosk.mobilescan

import com.facefit.kiosk.facefit.mobilescan.FaceAnalyzer
import com.facefit.kiosk.facefit.mobilescan.FaceScanPose
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

    @Test fun columnMajorPoseMatrixPreservesPitchYawAndRoll() {
        fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f, 3f, 4f, -35f, 1f)
        val angle = Math.toRadians(12.0)
        val c = kotlin.math.cos(angle).toFloat()
        val s = kotlin.math.sin(angle).toFloat()
        val pitch = identity().apply { this[5] = c; this[6] = s; this[9] = -s; this[10] = c }
        val yaw = identity().apply { this[0] = c; this[2] = -s; this[8] = s; this[10] = c }
        val roll = identity().apply { this[0] = c; this[1] = s; this[4] = -s; this[5] = c }
        assertEquals(12f, FaceScanPose.angles(pitch)!!.pitch, .001f)
        assertEquals(12f, FaceScanPose.angles(yaw)!!.yaw, .001f)
        assertEquals(12f, FaceScanPose.angles(roll)!!.roll, .001f)
        assertNull(FaceScanPose.angles(FloatArray(16)))
        assertNull(FaceScanPose.angles(FloatArray(2)))
    }
}
