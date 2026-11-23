package com.example.facefit

import com.example.facefit.facefit.tryon.geometry.Vector3
import com.example.facefit.ui.tryon.rendering.VtoAngularMotion
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class VtoAngularMotionTest {
    private val period = 40_000_000L
    private val anchor = Vector3(2f, 1f, .5f)

    private fun rotation(radians: Float): FloatArray = floatArrayOf(
        cos(radians), sin(radians), 0f, 0f,
        -sin(radians), cos(radians), 0f, 0f,
        0f, 0f, 1f, 0f,
        5f, 9f, 0f, 1f,
    )

    private fun angle(matrix: FloatArray) = atan2(matrix[1], matrix[0])
    private fun pivot(matrix: FloatArray) = FloatArray(3) { i ->
        matrix[12 + i] + anchor.x * matrix[i] + anchor.y * matrix[4 + i] + anchor.z * matrix[8 + i]
    }

    @Test fun ninetyDegreesPerSecondMatchesTheTargetVsyncWithoutPhaseLag() {
        val predictor = VtoAngularMotion()
        val rate = (PI / 2).toFloat()
        predictor.observe(rotation(0f), 0L)
        val sensor = rotation(rate * .04f)
        predictor.observe(sensor, period)
        val lead = 113_000_000L
        val shown = rotation(rate * .02f)
        val filteredPivot = pivot(shown)
        predictor.apply(shown, anchor, lead, useMeasuredBasis = true)
        assertEquals(rate * .056f, angle(shown), .000001f)
        assertArrayEquals(filteredPivot, pivot(shown), .00001f)
    }

    @Test fun projectedBridgeUsesWorldPivotAndIgnoresPriorScreenShift() {
        val predictor = VtoAngularMotion()
        predictor.observe(rotation(0f), 0L)
        predictor.observe(rotation(.25f), period)
        val expectedBridge = Vector3(120f, 430f, -1800f)
        val shifted = rotation(.15f).apply {
            this[12] += 70f
            this[13] -= 50f
        }
        assertTrue(predictor.apply(shifted, anchor, period, useMeasuredBasis = true, worldPivot = expectedBridge))
        assertArrayEquals(floatArrayOf(expectedBridge.x, expectedBridge.y, expectedBridge.z), pivot(shifted), .0001f)
        assertEquals(.35f, angle(shifted), .00001f)
    }

    @Test fun mildDecelerationKeepsFullAngularLead() {
        val predictor = VtoAngularMotion()
        predictor.observe(rotation(0f), 0L)
        predictor.observe(rotation(.04f), period)
        val latest = rotation(.0796f)
        predictor.observe(latest, 2 * period)
        val projected = latest.copyOf()
        assertTrue(predictor.apply(projected, anchor, 175_000_000L))
        assertEquals(.0796f + .99f * .016f, angle(projected), .000001f)
    }

    @Test fun brakingReachesTheStopWithoutOvershootOrBounce() {
        val predictor = VtoAngularMotion()
        predictor.observe(rotation(0f), 0L)
        predictor.observe(rotation(.12f), period)
        val braking = rotation(.18f)
        predictor.observe(braking, 2 * period)
        val stop = .21f
        val shown = braking.copyOf()
        predictor.apply(shown, anchor, 8 * period)
        val expected = .18f + .016f * (1.5f - 37.5f * .016f / 2f)
        val bounce = (angle(shown) - expected) / expected
        assertTrue(bounce in -.015f.. .015f)
        assertEquals(expected, angle(shown), .000001f)

        val stopped = rotation(stop)
        predictor.observe(stopped, 3 * period)
        predictor.observe(stopped, 4 * period)
        val stationary = stopped.copyOf()
        predictor.apply(stationary, anchor, 8 * period)
        assertArrayEquals(stopped, stationary, .000001f)
    }

    @Test fun reversalStaysContinuousAndKeepsAnOrthonormalBasis() {
        val predictor = VtoAngularMotion()
        val samples = listOf(0f, .08f, .14f, .16f, .16f, .14f, .08f, 0f, -.08f)
        val displayed = samples.mapIndexed { index, sample ->
            val raw = rotation(sample)
            predictor.observe(raw, index * period)
            val shown = raw.copyOf()
            predictor.apply(shown, anchor, period / 2)
            assertTrue(shown.all { it.isFinite() })
            assertArrayEquals(pivot(raw), pivot(shown), .00001f)
            assertEquals(1f, shown[0] * shown[5] - shown[1] * shown[4], .000001f)
            angle(shown)
        }
        assertTrue(displayed.zipWithNext().all { (a, b) -> kotlin.math.abs(a - b) <= .12f })
        assertTrue(displayed[4] > displayed[5] && displayed[5] > displayed[6])
    }

    @Test fun halfTurnAndLearnedNoiseRemainWellDefined() {
        val predictor = VtoAngularMotion()
        predictor.observe(rotation(0f), 0L)
        val halfTurn = rotation(PI.toFloat())
        predictor.observe(halfTurn, period)
        val shown = halfTurn.copyOf()
        predictor.apply(shown, anchor, period / 2)
        assertEquals(cos(PI.toFloat() * 1.4f), shown[0], .000001f)
        assertEquals(kotlin.math.abs(sin(PI.toFloat() * 1.4f)), kotlin.math.abs(shown[1]), .000001f)
        val suppressed = halfTurn.copyOf()
        assertFalse(predictor.apply(suppressed, anchor, period / 2, noiseLimit = 100f))
        assertArrayEquals(halfTurn, suppressed, 0f)
    }
}
