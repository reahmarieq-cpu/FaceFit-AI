package com.facefit.kiosk.vto

import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer
import com.facefit.kiosk.vto.facefit.tryon.geometry.Vector3
import com.facefit.kiosk.vto.facefit.tryon.tracking.VtoHeadPose
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class VtoHeadPoseTest {
    private fun rotate(p: Vector3, pitch: Float, yaw: Float, roll: Float): Vector3 {
        val a = Vector3(p.x, cos(pitch) * p.y - sin(pitch) * p.z, sin(pitch) * p.y + cos(pitch) * p.z)
        val b = Vector3(cos(yaw) * a.x + sin(yaw) * a.z, a.y, -sin(yaw) * a.x + cos(yaw) * a.z)
        return Vector3(cos(roll) * b.x - sin(roll) * b.y, sin(roll) * b.x + cos(roll) * b.y, b.z)
    }

    private fun face(pitch: Float, yaw: Float, roll: Float, mirrored: Boolean = false): FaceAnalyzer.FaceMetrics {
        val points = MutableList(478) { FaceAnalyzer.LandmarkPoint(.5f, .5f) }
        val reference = mapOf(
            10 to Vector3(0f, -90f, -20f),
            33 to Vector3(-75f, 0f, 0f), 133 to Vector3(-40f, 0f, 0f),
            263 to Vector3(75f, 0f, 0f), 362 to Vector3(40f, 0f, 0f),
            6 to Vector3(0f, 15f, -20f), 168 to Vector3(0f, -5f, -20f)
        )
        reference.forEach { (index, point) ->
            val p = rotate(point, pitch, yaw, roll) * .001f
            points[index] = FaceAnalyzer.LandmarkPoint(.5f + if (mirrored) -p.x else p.x, .5f + p.y, p.z)
        }
        return FaceAnalyzer.FaceMetrics(0f, 0f, 0f, "", points, 1000, 1000, true)
    }

    private fun assertVector(expected: Vector3, actual: Vector3) {
        assertEquals(expected.x, actual.x, .001f)
        assertEquals(expected.y, actual.y, .001f)
        assertEquals(expected.z, actual.z, .001f)
    }

    @Test fun centralFrameFollowsCombinedYawPitchAndThirtyDegreeRoll() {
        val tilt = Math.toRadians(30.0).toFloat()
        for (pitch in listOf(-.4f, .4f)) for (yaw in listOf(-.6f, .6f)) for (roll in listOf(-tilt, tilt)) {
            val rig = requireNotNull(VtoHeadPose.rig(face(pitch, yaw, roll), 1000, 1000, 1f, 0f))
            val eyeLine = rotate(Vector3(1f, 0f, 0f), pitch, yaw, roll)
            assertEquals(roll, kotlin.math.atan2(rig.xAxis.y, rig.xAxis.x), .001f)
            assertEquals(115f * kotlin.math.hypot(eyeLine.x, eyeLine.y), rig.size, .001f)
            assertVector(eyeLine, rig.xAxis)
            assertVector(rig.zAxis, rig.xAxis.cross(rig.yAxis))
        }
    }

    @Test fun mirroredPoseIgnoresOuterDepthAndKeepsTheCompositeBridge() {
        val source = face(.15f, .4f, 0f, mirrored = true)
        val original = requireNotNull(VtoHeadPose.rig(source, 1000, 1000, 1f, 0f))
        val noise = source.landmarks.toMutableList()
        for (index in listOf(127, 356, 234, 454, 152)) noise[index] = noise[index].copy(z = .2f)
        val moved = requireNotNull(VtoHeadPose.rig(source.copy(landmarks = noise), 1000, 1000, 1f, 0f))
        assertVector(original.origin, moved.origin)
        assertVector(original.xAxis, moved.xAxis)
        assertVector(original.zAxis, moved.zAxis)
        assertTrue(original.zAxis.x < 0f)
        assertEquals(original.size, moved.size, .001f)

        val nose = source.landmarks[168]
        val bridge = Vector3(nose.x * 600f + 200f, nose.y * 600f + 200f, nose.z * 600f * VtoHeadPose.DEPTH_WEIGHT)
        assertVector(bridge, original.origin)
    }

    @Test fun depthPerturbationDoesNotChangeScreenAnchorOrFitSize() {
        val source = face(0f, 0f, 0f)
        val first = source.landmarks.toMutableList().apply { this[6] = this[6].copy(z = .01f) }
        val second = source.landmarks.toMutableList().apply { this[33] = this[33].copy(z = .15f) }
        val a = requireNotNull(VtoHeadPose.rig(source.copy(landmarks = first), 1000, 1000, 1f, 0f))
        val b = requireNotNull(VtoHeadPose.rig(source.copy(landmarks = second), 1000, 1000, 1f, 0f))
        assertEquals(a.origin.x, b.origin.x, .001f)
        assertEquals(a.origin.y, b.origin.y, .001f)
        assertEquals(a.size, b.size, .001f)
    }

    @Test fun collapsedOrInvalidLandmarksDoNotProduceARig() {
        val source = face(0f, 0f, 0f)
        val collapsed = List(478) { FaceAnalyzer.LandmarkPoint(.5f, .5f) }
        assertNull(VtoHeadPose.rig(source.copy(landmarks = collapsed), 1000, 1000, 1f, 0f))
        val invalid = source.landmarks.toMutableList().apply { this[6] = this[6].copy(z = Float.NaN) }
        assertNull(VtoHeadPose.rig(source.copy(landmarks = invalid), 1000, 1000, 1f, 0f))
    }
}
