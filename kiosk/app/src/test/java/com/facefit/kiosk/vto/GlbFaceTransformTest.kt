package com.facefit.kiosk.vto

import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer
import com.facefit.kiosk.vto.facefit.tryon.geometry.GlbFaceTransform
import com.facefit.kiosk.vto.facefit.tryon.geometry.Vector3

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GlbFaceTransformTest {
    private val center = floatArrayOf(0f, -11.5f, -73.5f)
    private val extent = floatArrayOf(70f, 24f, 76.25f)
    private fun face(yaw: Float = 0f, shiftX: Float = 0f): FaceAnalyzer.FaceMetrics {
        val a = yaw * PI.toFloat() / 180f
        fun p(x: Float, y: Float, z: Float = 0f) = FaceAnalyzer.LandmarkPoint(
            0.5f + shiftX + (cos(a) * x + sin(a) * z) * 0.25f,
            0.4f + y * 0.25f, (-sin(a) * x + cos(a) * z) * 0.25f)
        val points = MutableList(478) { p(0f, 0f) }
        points[33] = p(-0.6f, 0f); points[133] = p(-0.4f, 0f)
        points[263] = p(0.6f, 0f); points[362] = p(0.4f, 0f)
        points[152] = p(0f, 1f); points[10] = p(0f, -0.8f)
        points[168] = p(0f, 0f, -0.2f)
        return FaceAnalyzer.FaceMetrics(0f, 0f, 0f, "Oval", points, 800, 1000, true)
    }
    private fun matrix(face: FaceAnalyzer.FaceMetrics? = face(), scale: Float = 1f,
        offset: Float = 0f) = GlbFaceTransform.matrix(face, 800, 1000, center, extent, scale, offset)
    private fun transform(m: FloatArray, p: Vector3) = Vector3(
        m[0]*p.x + m[4]*p.y + m[8]*p.z + m[12],
        m[1]*p.x + m[5]*p.y + m[9]*p.z + m[13],
        m[2]*p.x + m[6]*p.y + m[10]*p.z + m[14])

    @Test fun frontFitsEyesAndNativeArmsExtendBehindFrame() {
        val m = matrix()!!
        val bridge = transform(m, Vector3(0f, center[1], 2.75f))
        assertEquals(400f, bridge.x, 0.01f)
        assertEquals(600f, bridge.y, 0.01f)
        val left = transform(m, Vector3(-70f, center[1], 2.75f))
        val right = transform(m, Vector3(70f, center[1], 2.75f))
        assertEquals(400f, right.x-left.x, 0.01f)
        assertTrue(transform(m, Vector3(65f, center[1], -140f)).z < bridge.z)
        assertTrue(transform(m, Vector3(0f, 12f, 2.75f)).y > bridge.y)
    }
    @Test fun translationScaleAndHeightFollowTrackingAndControls() {
        val p = Vector3(0f, center[1], 2.75f)
        val initial = transform(matrix()!!, p)
        val moved = transform(matrix(face(shiftX = 0.1f))!!, p)
        assertEquals(80f, moved.x-initial.x, 0.01f)
        assertEquals(initial.y-40f, transform(matrix(offset = 0.2f)!!, p).y, 0.01f)
        val m = matrix(scale = 1.2f)!!
        assertEquals(480f, (transform(m, Vector3(70f, center[1], 2.75f)) -
            transform(m, Vector3(-70f, center[1], 2.75f))).length(), 0.01f)
    }
    @Test fun turnsPreserveRigidMeshAndReverseTempleMotion() {
        val a = Vector3(65f, 0f, -140f)
        val b = Vector3(-65f, 0f, -140f)
        val baseline = (transform(matrix()!!, a)-transform(matrix()!!, b)).length()
        for (yaw in listOf(-45f, 45f)) {
            val m = matrix(face(yaw))!!
            assertTrue(m.all { it.isFinite() })
            assertEquals(baseline, (transform(m,a)-transform(m,b)).length(), 0.01f)
        }
        assertTrue(transform(matrix(face(-45f))!!, a).x < transform(matrix(face(45f))!!, a).x)
    }
    @Test fun missingOrInvalidFaceHidesModel() {
        assertNull(matrix(null))
        assertNull(matrix(face().copy(isFaceDetected = false)))
        assertNull(matrix(face().copy(landmarks = emptyList())))
        assertNull(matrix(scale = Float.NaN))
        assertNull(matrix(offset = Float.NaN))
        assertNull(GlbFaceTransform.matrix(face(), 0, 1000, center, extent, 1f, 0f))
    }
}
