package com.example.facefit

import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.tryon.geometry.Vector3
import com.example.facefit.facefit.tryon.tracking.VtoHeadPose
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

internal fun anatomicalFace(transform: (Vector3) -> Vector3 = { it }): FaceAnalyzer.FaceMetrics {
    val skull = mapOf(33 to Vector3(-40f,0f,0f), 263 to Vector3(40f,0f,0f),
        10 to Vector3(0f,-100f,0f), 152 to Vector3(0f,100f,0f), 168 to Vector3(0f,0f,-15f))
    return FaceAnalyzer.FaceMetrics(0f,0f,0f,"",List(478) { index ->
        val p = transform(skull[index] ?: Vector3(0f,0f,0f)) + Vector3(400f,600f,10f)
        FaceAnalyzer.LandmarkPoint(p.x/1000f, p.y/1600f, p.z/1000f)
    },1000,1600,true)
}

class VtoHeadPoseTest {
    private fun rotate(p: Vector3, pitch: Float, yaw: Float, roll: Float): Vector3 {
        val a = Vector3(p.x, cos(pitch)*p.y-sin(pitch)*p.z, sin(pitch)*p.y+cos(pitch)*p.z)
        val b = Vector3(cos(yaw)*a.x+sin(yaw)*a.z, a.y, -sin(yaw)*a.x+cos(yaw)*a.z)
        return Vector3(cos(roll)*b.x-sin(roll)*b.y, sin(roll)*b.x+cos(roll)*b.y, b.z)
    }
    @Test fun tiltsAndProfileYawPreserveAxesAndPhysicalBridgeAtEveryScale() {
        for (size in listOf(.1f, 1f, 3f)) for (pitch in listOf(-.8f,.8f))
            for (yaw in listOf(-1.58f,0f,1.58f)) for (roll in listOf(-.5f,.5f)) {
                val face = anatomicalFace { rotate(it, pitch, yaw, roll) * size }
                val candidates = requireNotNull(VtoHeadPose.candidates(face,1000,1600,1f,0f))
                assertSame(candidates.fitted, candidates.fallback)
                val rig = requireNotNull(candidates.fitted)
                assertTrue(rig.xAxis.dot(rotate(Vector3(1f,0f,0f),pitch,yaw,roll)) > .99999f)
                assertTrue(rig.yAxis.dot(rotate(Vector3(0f,1f,0f),pitch,yaw,roll)) > .99999f)
                assertTrue(rig.zAxis.dot(rotate(Vector3(0f,0f,1f),pitch,yaw,roll)) > .99999f)
                val bridge = face.landmarks[168].let { Vector3(it.x*1000f,it.y*1600f,it.z*1000f) }
                assertTrue((rig.origin + rig.zAxis*(rig.size*.04f) - bridge).length() < .001f)
            }
    }
    @Test fun bridgeMovementImmediatelyMovesTheRigByTheSameAmount() {
        val face = anatomicalFace()
        val moved = face.copy(landmarks = face.landmarks.mapIndexed { i,p -> if(i==168) p.copy(x=p.x+.02f) else p })
        val first = requireNotNull(VtoHeadPose.rig(face,1000,1600,1f,0f))
        val next = requireNotNull(VtoHeadPose.rig(moved,1000,1600,1f,0f))
        assertEquals(20f, next.origin.x-first.origin.x, .001f)
        assertEquals(first.xAxis, next.xAxis)
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
