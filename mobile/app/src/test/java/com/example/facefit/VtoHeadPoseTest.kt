package com.example.facefit

import com.example.facefit.facefit.tryon.geometry.Vector3
import com.example.facefit.facefit.tryon.tracking.VtoHeadPose

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class VtoHeadPoseTest {
    private fun rotate(p: Vector3, pitch: Float, yaw: Float, roll: Float): Vector3 {
        val a=Vector3(p.x,cos(pitch)*p.y-sin(pitch)*p.z,sin(pitch)*p.y+cos(pitch)*p.z)
        val b=Vector3(cos(yaw)*a.x+sin(yaw)*a.z,a.y,-sin(yaw)*a.x+cos(yaw)*a.z)
        return Vector3(cos(roll)*b.x-sin(roll)*b.y,sin(roll)*b.x+cos(roll)*b.y,b.z)
    }
    private fun assertVector(a: Vector3,b: Vector3) {
        assertEquals(a.x,b.x,.001f); assertEquals(a.y,b.y,.001f); assertEquals(a.z,b.z,.001f)
    }
    @Test fun neutralCurvedFaceDoesNotTiltArmsUpwards() {
        val axes=requireNotNull(VtoHeadPose.fit(VtoHeadPose.reference.map { it*25f+Vector3(400f,600f,10f) }))
        assertVector(Vector3(1f,0f,0f),axes.right)
        assertVector(Vector3(0f,1f,0f),axes.down)
        assertVector(Vector3(0f,0f,1f),axes.behind)
        // The GLB's arm goes backward with a downward ear hook, never upward at neutral pose.
        val arm=axes.behind*125f+axes.down*7f
        assertTrue(arm.y>0f); assertTrue(arm.z>0f)
    }
    @Test fun combinedTurnsRecoverRigidPoseWithoutFaceCurvatureBias() {
        for(pitch in listOf(-.4f,0f,.4f)) for(yaw in listOf(-.6f,.6f)) for(roll in listOf(-.3f,.3f)) {
            val points=VtoHeadPose.reference.map { rotate(it,pitch,yaw,roll)*30f+Vector3(200f,400f,-5f) }
            val axes=requireNotNull(VtoHeadPose.fit(points))
            assertVector(rotate(Vector3(1f,0f,0f),pitch,yaw,roll),axes.right)
            assertVector(rotate(Vector3(0f,1f,0f),pitch,yaw,roll),axes.down)
            assertVector(rotate(Vector3(0f,0f,1f),pitch,yaw,roll),axes.behind)
        }
    }
    @Test fun collapsedOrInvalidPointsDoNotCreatePose() {
        assertNull(VtoHeadPose.fit(List(VtoHeadPose.reference.size) { Vector3(0f,0f,0f) }))
        assertNull(VtoHeadPose.fit(emptyList()))
        assertNull(VtoHeadPose.fit(VtoHeadPose.reference.map { it.copy(z=Float.NaN) }))
    }
}
