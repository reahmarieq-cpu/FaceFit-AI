package com.example.facefit

import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.tryon.geometry.Vector3
import com.example.facefit.facefit.tryon.tracking.VtoHeadPose
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

/**
 * A face built from the 13 MediaPipe canonical points that VtoHeadPose fits
 * (10 px per canonical unit), centred at (400, 600) on a 1000 x 1600 image.
 */
internal fun anatomicalFace(transform: (Vector3) -> Vector3 = { it }): FaceAnalyzer.FaceMetrics {
    val canonical = VtoHeadPose.landmarkIndices.indices.associate { i ->
        VtoHeadPose.landmarkIndices[i] to VtoHeadPose.reference[i] * 10f
    }
    return FaceAnalyzer.FaceMetrics(0f,0f,0f,"",List(478) { index ->
        val p = transform(canonical[index] ?: Vector3(0f,0f,0f)) + Vector3(400f,600f,10f)
        FaceAnalyzer.LandmarkPoint(p.x/1000f, p.y/1600f, p.z/1000f)
    },1000,1600,true)
}

class VtoHeadPoseTest {
    private fun rotate(p: Vector3, pitch: Float, yaw: Float, roll: Float): Vector3 {
        val a = Vector3(p.x, cos(pitch)*p.y-sin(pitch)*p.z, sin(pitch)*p.y+cos(pitch)*p.z)
        val b = Vector3(cos(yaw)*a.x+sin(yaw)*a.z, a.y, -sin(yaw)*a.x+cos(yaw)*a.z)
        return Vector3(cos(roll)*b.x-sin(roll)*b.y, sin(roll)*b.x+cos(roll)*b.y, b.z)
    }
    private fun FaceAnalyzer.FaceMetrics.point(i: Int) =
        landmarks[i].let { Vector3(it.x*1000f, it.y*1600f, it.z*1000f) }

    @Test fun tiltsAndTurnsGiveTheHeadAxesAndSitTheFrameOnTheEyeLine() {
        for (size in listOf(.5f, 1f, 3f)) for (pitch in listOf(-.8f, 0f, .8f))
            for (yaw in listOf(-1.2f, 0f, 1.2f)) for (roll in listOf(-.5f, .5f)) {
                val face = anatomicalFace { rotate(it, pitch, yaw, roll) * size }
                val rig = requireNotNull(requireNotNull(
                    VtoHeadPose.candidates(face,1000,1600,1f,0f)).fitted)
                assertTrue(rig.xAxis.dot(rotate(Vector3(1f,0f,0f),pitch,yaw,roll)) > .9999f)
                assertTrue(rig.yAxis.dot(rotate(Vector3(0f,1f,0f),pitch,yaw,roll)) > .9999f)
                assertTrue(rig.zAxis.dot(rotate(Vector3(0f,0f,1f),pitch,yaw,roll)) > .9999f)

                // Centred between the eye centres, and 8% of the eye distance in front of the bridge.
                val first = (face.point(33) + face.point(133)) * .5f
                val second = (face.point(263) + face.point(362)) * .5f
                val eyeCenter = (first + second) * .5f
                val distance = (second - first).length()
                val tolerance = .002f * distance
                assertEquals(distance, rig.size, tolerance)
                assertEquals(0f, (rig.origin - eyeCenter).dot(rig.xAxis), tolerance)
                assertEquals(0f, (rig.origin - eyeCenter).dot(rig.yAxis), tolerance)
                assertEquals(distance * .08f, (face.point(168) - rig.origin).dot(rig.zAxis), tolerance)
            }
    }
    @Test fun headMovementMovesTheRigByTheSameAmount() {
        val first = requireNotNull(VtoHeadPose.rig(anatomicalFace(),1000,1600,1f,0f))
        val next = requireNotNull(VtoHeadPose.rig(anatomicalFace { it + Vector3(20f,0f,0f) },1000,1600,1f,0f))
        assertEquals(20f, next.origin.x-first.origin.x, .01f)
        assertEquals(0f, next.origin.y-first.origin.y, .01f)
        assertTrue(next.xAxis.dot(first.xAxis) > .99999f)
    }
    @Test fun heightControlMovesTheFrameAlongTheFace() {
        val base = requireNotNull(VtoHeadPose.rig(anatomicalFace(),1000,1600,1f,0f))
        val raised = requireNotNull(VtoHeadPose.rig(anatomicalFace(),1000,1600,1f,.2f))
        assertEquals(base.size * .2f, (raised.origin - base.origin).dot(base.yAxis), .01f)
    }
    @Test fun malformedGeometryDoesNotManufactureAxes() {
        val face = anatomicalFace()
        val collapsed = face.landmarks.toMutableList().apply { this[152] = this[10] }
        assertNull(VtoHeadPose.rig(face.copy(landmarks=collapsed),1000,1600,1f,0f))
        assertNull(VtoHeadPose.rig(face.copy(landmarks=emptyList()),1000,1600,1f,0f))
        assertNull(VtoHeadPose.rig(face.copy(isFaceDetected=false),1000,1600,1f,0f))
    }
    @Test fun nonFiniteBridgeIsRejected() {
        val face = anatomicalFace()
        val bad = face.landmarks.toMutableList().apply { this[168] = this[168].copy(z=Float.NaN) }
        assertNull(VtoHeadPose.rig(face.copy(landmarks=bad),1000,1600,1f,0f))
    }
}
