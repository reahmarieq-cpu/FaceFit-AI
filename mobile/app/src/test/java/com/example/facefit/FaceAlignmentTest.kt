package com.example.facefit

import com.example.facefit.facefit.analysis.FaceAlignment
import com.example.facefit.facefit.analysis.FaceAnalyzer.LandmarkPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaceAlignmentTest {
    private fun eyes(leftX: Float, leftY: Float, rightX: Float, rightY: Float) =
        List(478) { LandmarkPoint(0.5f, 0.5f) }.toMutableList().also {
            it[33] = LandmarkPoint(leftX, leftY)
            it[263] = LandmarkPoint(rightX, rightY)
        }

    @Test fun levelEyesNeedNoRotation() {
        assertEquals(0f, FaceAlignment.eyeLineDegrees(eyes(.4f, .4f, .6f, .4f), 480, 640)!!, 1e-4f)
    }

    @Test fun tiltedEyeLineIsMeasuredInPixels() {
        // 96 px across, 640 * 0.02 = 12.8 px down: atan(12.8 / 96) = 7.6 degrees.
        assertEquals(7.595f, FaceAlignment.eyeLineDegrees(eyes(.4f, .40f, .6f, .42f), 480, 640)!!, 0.01f)
        // A mirrored image gives the same line.
        assertEquals(7.595f, FaceAlignment.eyeLineDegrees(eyes(.6f, .42f, .4f, .40f), 480, 640)!!, 0.01f)
        assertNull(FaceAlignment.eyeLineDegrees(emptyList(), 480, 640))
    }

    @Test fun rotatingByTheEyeAngleLevelsTheEyes() {
        val face = eyes(.4f, .40f, .6f, .42f)
        val degrees = FaceAlignment.eyeLineDegrees(face, 480, 640)!!
        val left = FaceAlignment.rotate(.4f * 480, .40f * 640, 240f, 300f, degrees)
        val right = FaceAlignment.rotate(.6f * 480, .42f * 640, 240f, 300f, degrees)
        assertEquals(left.second, right.second, 1e-3f)
        // Rotation keeps distances.
        assertEquals(kotlin.math.hypot(96f, 12.8f), right.first - left.first, 1e-3f)
    }
}
