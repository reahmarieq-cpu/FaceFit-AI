package com.example.facefit

import com.example.facefit.facefit.tryon.geometry.Vector3
import com.example.facefit.ui.tryon.rendering.VtoDisplayPose
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class VtoDisplayPoseTest {
    private val anchor = Vector3(2f, 1f, .5f)
    private fun pose(position: Float, angle: Float = 0f, scale: Float = 1f): FloatArray {
        val a = Vector3(cos(angle), sin(angle), 0f) * scale
        val b = Vector3(-sin(angle), cos(angle), 0f) * scale
        val c = Vector3(0f, 0f, scale)
        val origin = Vector3(position, 10f, 20f) - a * anchor.x - b * anchor.y - c * anchor.z
        return floatArrayOf(a.x,a.y,a.z,0f,b.x,b.y,b.z,0f,c.x,c.y,c.z,0f,origin.x,origin.y,origin.z,1f)
    }
    private fun bridge(matrix: FloatArray) =
        matrix[12] + matrix[0] * anchor.x + matrix[4] * anchor.y + matrix[8] * anchor.z

    @Test fun displayTicksBridgeDetectorGapsWithoutOvershootOrStoredMomentum() {
        val follower = VtoDisplayPose()
        follower.sample(pose(0f), anchor, 0L)
        val target = pose(100f, .4f, 2f)
        var previous = 0f
        for (tick in 1..120) {
            val shown = requireNotNull(follower.sample(target, anchor, tick * 8_333_333L))
            val x = bridge(shown)
            assertTrue(x >= previous - .00001f && x <= 100.00001f)
            assertTrue(atan2(shown[1], shown[0]) in 0f.. .40001f)
            assertTrue(shown.all { it.isFinite() })
            previous = x
        }
        assertEquals(100f, previous, .0001f)
        val reversed = pose(50f)
        val shown = requireNotNull(follower.sample(reversed, anchor, 1_100_000_000L))
        assertTrue(bridge(shown) < previous && bridge(shown) >= 50f)
    }

    @Test fun equalElapsedTimeHasEqualResponseAt60And120Hz() {
        val sixty = VtoDisplayPose()
        val faster = VtoDisplayPose()
        sixty.sample(pose(0f), anchor, 0L)
        faster.sample(pose(0f), anchor, 0L)
        val target = pose(100f)
        faster.sample(target, anchor, 8_000_000L)
        val a = requireNotNull(sixty.sample(target, anchor, 16_000_000L))
        val b = requireNotNull(faster.sample(target, anchor, 16_000_000L))
        assertEquals(bridge(a), bridge(b), .00001f)
    }

    @Test fun sixteenMillisecondResponseKeepsTheBridgeBounded() {
        val follower = VtoDisplayPose()
        follower.sample(pose(0f), anchor, 0L)
        val shown = requireNotNull(follower.sample(pose(100f), anchor, 16_000_000L))
        assertEquals(63.212f, bridge(shown), .001f)
    }

    @Test fun lossAndResetDiscardThePreviousDisplayPose() {
        val follower = VtoDisplayPose()
        follower.sample(pose(0f), anchor, 0L)
        assertNull(follower.sample(null, anchor, 16_000_000L))
        val next = pose(100f)
        assertArrayEquals(next, follower.sample(next, anchor, 32_000_000L), 0f)
        follower.reset()
        assertArrayEquals(next, follower.sample(next, anchor, 40_000_000L), 0f)
    }
}
