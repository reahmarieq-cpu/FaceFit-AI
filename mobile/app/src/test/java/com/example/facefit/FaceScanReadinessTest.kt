package com.example.facefit

import com.example.facefit.logic.FaceAnalyzer
import com.example.facefit.logic.FaceScanLighting
import com.example.facefit.logic.FaceScanPose
import com.example.facefit.logic.getScanReadiness
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class FaceScanReadinessTest {
    private fun face(width: Int = 640, height: Int = 480, size: Float = .36f): FaceAnalyzer.FaceMetrics {
        val span = minOf(width, height) * size
        val cx = width * .5f
        val cy = height * .5f
        fun point(x: Float, y: Float) = FaceAnalyzer.LandmarkPoint(x / width, y / height)
        val points = MutableList(478) { point(cx, cy) }
        val outline = listOf(10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288,
            397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132,
            93, 234, 127, 162, 21, 54, 103, 67, 109)
        outline.forEachIndexed { i, index ->
            val angle = 2 * Math.PI * i / outline.size
            points[index] = point(cx + (sin(angle) * span * .55).toFloat(),
                cy - (cos(angle) * span * .65).toFloat())
        }
        points[234] = point(cx - span / 2, cy)
        points[454] = point(cx + span / 2, cy)
        for ((index, offset) in listOf(33 to -.18f, 133 to -.07f, 263 to .18f, 362 to .07f)) {
            points[index] = point(cx + span * offset, cy - span * .12f)
        }
        return FaceAnalyzer.FaceMetrics(0.7f, .8f, .2f, "", landmarks = points,
            imageWidth = width, imageHeight = height, isFaceDetected = true,
            stabilityFeatures = List(7) { 1f }, headPose = FaceScanPose.Angles(0f, 0f, 0f),
            scanLighting = FaceScanLighting.Measurement(.5f, 0f, 0f, 0f))
    }

    @Test fun frontalFacePassesInPortraitLandscapeAndMirroredImages() {
        for ((width, height) in listOf(640 to 480, 480 to 640)) {
            val face = face(width, height)
            assertTrue(face.getScanReadiness().canCapture)
            assertTrue(face.copy(landmarks = face.landmarks.map { it.copy(x = 1f - it.x) })
                .getScanReadiness().canCapture)
        }
    }

    @Test fun distanceBandRequiresMovingCloserOrFarther() {
        assertEquals("Move closer to the camera", face(size = .28f).getScanReadiness().message)
        assertEquals("Move farther from the camera", face(size = .46f).getScanReadiness().message)
    }

    @Test fun headTurnsTiltsAndMissingPosePreventCapture() {
        val face = face()
        for (pose in listOf(FaceScanPose.Angles(11f, 0f, 0f), FaceScanPose.Angles(-11f, 0f, 0f),
            FaceScanPose.Angles(0f, 9f, 0f), FaceScanPose.Angles(0f, -9f, 0f),
            FaceScanPose.Angles(0f, 0f, 6f), FaceScanPose.Angles(Float.NaN, 0f, 0f))) {
            assertFalse(face.copy(headPose = pose).getScanReadiness().canCapture)
        }
        assertFalse(face.copy(headPose = null).getScanReadiness().canCapture)
    }

    @Test fun extremeAndUnevenLightingPreventCapture() {
        val face = face()
        for (light in listOf(FaceScanLighting.Measurement(.1f, .8f, 0f, 0f),
            FaceScanLighting.Measurement(.9f, 0f, .5f, 0f),
            FaceScanLighting.Measurement(.5f, 0f, 0f, .3f))) {
            assertFalse(face.copy(scanLighting = light).getScanReadiness().canCapture)
        }
    }

    @Test fun croppedInvalidAndMultipleFacesPreventCapture() {
        val face = face()
        val cropped = face.landmarks.toMutableList()
        cropped[152] = cropped[152].copy(y = .99f)
        assertFalse(face.copy(landmarks = cropped).getScanReadiness().canCapture)
        cropped[152] = cropped[152].copy(y = Float.NaN)
        assertFalse(face.copy(landmarks = cropped).getScanReadiness().canCapture)
        assertFalse(face.copy(detectedFaceCount = 2).getScanReadiness().canCapture)
        assertFalse(face.copy(imageWidth = 0).getScanReadiness().canCapture)
    }
}
