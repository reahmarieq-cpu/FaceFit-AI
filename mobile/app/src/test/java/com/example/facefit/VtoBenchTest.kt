package com.example.facefit

import com.example.facefit.ui.tryon.tracking.benchRotationDelta
import com.example.facefit.ui.tryon.tracking.benchAnchor
import com.example.facefit.facefit.tryon.geometry.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

class VtoBenchTest {
    @Test fun restingVarianceDoesNotJumpAtEulerAngleWrap() {
        fun matrix(angle: Float) = floatArrayOf(
            cos(angle), sin(angle), 0f, 0f,
            -sin(angle), cos(angle), 0f, 0f,
            0f, 0f, 1f, 0f,
            200f, 300f, -2000f, 1f
        )
        val delta = benchRotationDelta(matrix(PI.toFloat() - .01f), matrix(-PI.toFloat() + .01f))
        assertEquals(.02f, delta.z, .00001f)
        assertEquals(0f, delta.x, .00001f)
        assertEquals(0f, delta.y, .00001f)
    }
    @Test fun resizingTheModelAroundItsBridgeDoesNotManufactureAnchorTranslation() {
        val anchor = Vector3(2f, 1f, .5f)
        fun matrix(scale: Float) = floatArrayOf(
            scale, 0f, 0f, 0f, 0f, scale, 0f, 0f, 0f, 0f, scale, 0f,
            200f - scale * anchor.x, 300f - scale * anchor.y, -2000f - scale * anchor.z, 1f
        )
        assertEquals(benchAnchor(matrix(1f), anchor), benchAnchor(matrix(2f), anchor))
    }
}
