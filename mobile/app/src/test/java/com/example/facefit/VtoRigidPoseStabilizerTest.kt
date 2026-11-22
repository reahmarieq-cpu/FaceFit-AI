package com.example.facefit

import com.example.facefit.facefit.tryon.geometry.Vector3
import com.example.facefit.facefit.tryon.tracking.VtoRigidPoseStabilizer
import com.example.facefit.facefit.tryon.tracking.VtoUnitQuaternion
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VtoRigidPoseStabilizerTest {
    private val origin = Vector3(0f, 0f, 0f)

    private fun pose(x: Float, roll: Float) = floatArrayOf(
        cos(roll), sin(roll), 0f, 0f,
        -sin(roll), cos(roll), 0f, 0f,
        0f, 0f, 1f, 0f,
        x, 0f, 0f, 1f,
    )

    @Test fun learnsAlternatingJitterAndStillTracksIntentionalMotion() {
        val filter = VtoRigidPoseStabilizer()
        filter.sample(pose(0f, 0f), origin, 100f, 0L)
        filter.sample(pose(-.33f, -.0033f), origin, 100f, 16_000_000L)
        filter.sample(pose(.33f, .0033f), origin, 100f, 32_000_000L)
        val resting = filter.sample(pose(-.33f, -.0033f), origin, 100f, 48_000_000L)
        for (frame in 4..30) {
            val sign = if (frame % 2 == 0) 1f else -1f
            assertSame(resting, filter.sample(pose(sign * .33f, sign * .0033f),
                origin, 100f, frame * 16_000_000L))
        }
        val moving = requireNotNull(filter.sample(pose(10f, .1f), origin, 100f, 496_000_000L))
        assertTrue(moving[12] > 5f)
        assertTrue(atan2(moving[1], moving[0]) > .05f)
    }

    @Test fun sameMotionInIpdsHasTheSameResponseAtDifferentFaceScales() {
        val small = VtoRigidPoseStabilizer()
        val large = VtoRigidPoseStabilizer()
        small.sample(pose(0f, 0f), origin, 10f, 0L)
        large.sample(pose(0f, 0f), origin, 100f, 0L)
        val a = requireNotNull(small.sample(pose(1f, .03f), origin, 10f, 16_000_000L))
        val b = requireNotNull(large.sample(pose(10f, .03f), origin, 100f, 16_000_000L))
        assertTrue(abs(a[12] - b[12] / 10f) < .00001f)
        assertTrue(abs(a[1] - b[1]) < .00001f)
    }
    @Test fun fastRigidMotionRespondsInOneDisplayFrameAndNeverExtrapolatesPosition() {
        val filter = VtoRigidPoseStabilizer()
        filter.sample(pose(0f, 0f), origin, 100f, 0L)
        val target = pose(1.6f, .08f)
        val shown = requireNotNull(filter.sample(target, origin, 100f, 16_000_000L))
        assertTrue(shown[12] in 1.44f..1.6f)
        assertTrue(atan2(shown[1], shown[0]) in .06f.. .080001f)
        val held = requireNotNull(filter.sample(target, origin, 100f, 32_000_000L))
        assertTrue(held[12] in shown[12]..1.6f)
    }
    @Test fun quaternionSignAndHalfTurnsPreserveUnitOrientation() {
        val q = VtoUnitQuaternion(.5f, .5f, .5f, .5f)
        val antipodal = VtoUnitQuaternion(-.5f, -.5f, -.5f, -.5f)
        assertTrue(q.slerp(antipodal, .5f).dot(q) > .99999f)
        for (axis in listOf(Vector3(1f, 0f, 0f), Vector3(0f, 1f, 0f), Vector3(0f, 0f, 1f))) {
            val halfTurn = VtoUnitQuaternion(0f, axis.x, axis.y, axis.z)
            val basis = halfTurn.axes()
            val restored = VtoUnitQuaternion.fromAxes(basis[0], basis[1], basis[2])
            assertTrue(abs(restored.dot(halfTurn)) > .99999f)
        }
    }

    @Test fun gentleAngularNoiseIsAttenuatedWhileFastTurnsRespondImmediately() {
        val quiet = VtoRigidPoseStabilizer()
        val moving = VtoRigidPoseStabilizer()
        quiet.sample(pose(0f, 0f), origin, 100f, 0L)
        moving.sample(pose(0f, 0f), origin, 100f, 0L)
        val small = requireNotNull(quiet.sample(pose(0f, .0008f), origin, 100f, 16_000_000L))
        val fast = requireNotNull(moving.sample(pose(0f, .08f), origin, 100f, 16_000_000L))
        assertTrue(abs(atan2(small[1], small[0])) < .0002f)
        assertTrue(atan2(fast[1], fast[0]) > .06f)
        assertTrue(abs(Vector3(fast[0], fast[1], fast[2]).length() - 1f) < .00001f)
    }
}
