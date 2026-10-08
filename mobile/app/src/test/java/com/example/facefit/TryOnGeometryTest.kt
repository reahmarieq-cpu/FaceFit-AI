package com.example.facefit

import com.example.facefit.logic.TryOnGeometry
import com.example.facefit.logic.TryOnPoint
import org.junit.Assert.*
import org.junit.Test

class TryOnGeometryTest {
    @Test
    fun forwardFaceKeepsArmsForeshortened() {
        val pose = TryOnGeometry.pose(TryOnPoint(200f, 300f), TryOnPoint(400f, 300f))!!
        assertEquals(0f, pose.templeVisibility, 0.001f)
        assertEquals(200f, pose.depthAwareDistance, 0.001f)
    }

    @Test
    fun oppositeTurnsRevealOppositeNearSides() {
        val leftNear = TryOnGeometry.pose(TryOnPoint(240f, 300f, -80f), TryOnPoint(360f, 300f, 80f))!!
        val rightNear = TryOnGeometry.pose(TryOnPoint(240f, 300f, 80f), TryOnPoint(360f, 300f, -80f))!!
        assertTrue(leftNear.yaw > 0f)
        assertTrue(rightNear.yaw < 0f)
        assertEquals(1f, leftNear.templeVisibility, 0.001f)
        assertEquals(1f, rightNear.templeVisibility, 0.001f)
        assertEquals(200f, leftNear.depthAwareDistance, 0.001f)
        assertEquals(120f, leftNear.eyeDistance, 0.001f)
    }

    @Test
    fun mirroringAndRollPreservePhysicalScale() {
        val first = TryOnPoint(200f, 300f, -20f)
        val second = TryOnPoint(360f, 420f, 20f)
        val pose = TryOnGeometry.pose(first, second)!!
        val mirrored = TryOnGeometry.pose(TryOnPoint(400f, first.y, first.z), TryOnPoint(240f, second.y, second.z))!!
        assertEquals(pose.depthAwareDistance, mirrored.depthAwareDistance, 0.001f)
        assertEquals(-pose.yaw, mirrored.yaw, 0.001f)
        assertEquals(1f, pose.downX * pose.downX + pose.downY * pose.downY, 0.001f)
    }

    @Test
    fun invalidAndCollapsedLandmarksAreNotRendered() {
        assertNull(TryOnGeometry.pose(TryOnPoint(Float.NaN, 0f), TryOnPoint(100f, 0f)))
        assertNull(TryOnGeometry.pose(TryOnPoint(10f, 20f), TryOnPoint(10f, 20f)))
        assertNull(TryOnGeometry.pose(TryOnPoint(0f, 0f), TryOnPoint(100f, 0f), scale = 0f))
    }
}
