package com.example.facefit

import com.example.facefit.facefit.tryon.geometry.Vector3
import com.example.facefit.ui.tryon.rendering.VtoDisplayPose
import com.example.facefit.ui.tryon.rendering.VtoMeshChoice
import com.example.facefit.ui.tryon.rendering.VtoMeshChoice.Pick
import com.example.facefit.ui.tryon.rendering.VtoShiftedPose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class VtoShiftedPoseTest {
    private val height = 1000f
    /** Identity-oriented glasses, scale 40, origin at the world point between the eyes (500, 600). */
    private fun mesh() = floatArrayOf(40f,0f,0f,0f, 0f,40f,0f,0f, 0f,0f,40f,0f, 500f,600f,-2000f,1f)

    // Eyes at preview (440, 400) and (560, 400): world y = 1000 - 400 = 600.
    private fun eyes(dx: Float = 0f, dy: Float = 0f, angle: Float = 0f, scale: Float = 1f): FloatArray {
        val half = 60f * scale
        val c = cos(angle); val s = sin(angle)
        return floatArrayOf(440f, 400f, 560f, 400f,
            500f + dx - half * c, 400f + dy - half * s, 500f + dx + half * c, 400f + dy + half * s)
    }

    @Test fun noMovementLeavesThePoseUnchanged() {
        val out = FloatArray(16)
        assertTrue(VtoShiftedPose.applyPreview(mesh(), eyes(), height, out))
        for (i in 0..15) assertEquals(mesh()[i], out[i], 1e-3f)
    }

    @Test fun translationMovesTheOriginAndFlipsPreviewY() {
        val out = FloatArray(16)
        VtoShiftedPose.applyPreview(mesh(), eyes(dx = 30f, dy = 20f), height, out)
        assertEquals(530f, out[12], 1e-3f)
        assertEquals(580f, out[13], 1e-3f) // 20 px down on screen is 20 down in a y-up world
        assertEquals(-2000f, out[14], 1e-3f)
    }

    @Test fun eyeDistanceScalesAndEyeLineRotatesAboutTheBridge() {
        val out = FloatArray(16)
        VtoShiftedPose.applyPreview(mesh(), eyes(scale = 1.1f), height, out)
        assertEquals(44f, hypot(out[0], out[1]), 1e-2f)
        assertEquals(500f, out[12], 1e-2f); assertEquals(600f, out[13], 1e-2f)
        // Screen-clockwise tilt (y down) is a negative rotation in the y-up world.
        VtoShiftedPose.applyPreview(mesh(), eyes(angle = .1f), height, out)
        assertEquals(-.1f, atan2(out[1], out[0]), 1e-3f)
        assertEquals(40f, hypot(out[0], out[1]), 1e-2f)
    }

    @Test fun degenerateEyePointsAreRejected() {
        val p = floatArrayOf(500f, 400f, 500f, 400f, 500f, 400f, 560f, 400f)
        assertFalse(VtoShiftedPose.applyPreview(mesh(), p, height, FloatArray(16)))
    }

    // Adaptive smoothing (VtoDisplayPose with an eye span).
    private val anchor = Vector3(0f, 0f, 0f)
    private fun at(x: Float) = floatArrayOf(1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,1f,0f, x,0f,0f,1f)

    @Test fun smallJitterIsDampedWhileStill() {
        val pose = VtoDisplayPose()
        pose.sample(at(0f), anchor, 0L, spanPx = 100f)
        var maxShown = 0f
        // +/-1 px alternating noise at 60 Hz on a still face.
        for (i in 1..120) {
            val shown = pose.sample(at(if (i % 2 == 0) 1f else -1f), anchor, i * 16_666_667L, spanPx = 100f)!!
            if (i > 30) maxShown = maxOf(maxShown, kotlin.math.abs(shown[12]))
        }
        assertTrue("still jitter shown as $maxShown px", maxShown < .35f)
    }

    @Test fun steadyMovementIsFollowedClosely() {
        val pose = VtoDisplayPose()
        pose.sample(at(0f), anchor, 0L, spanPx = 100f)
        var shown = 0f
        // 1 eye span (100 px) per second for half a second.
        for (i in 1..30) shown = pose.sample(at(100f * i / 60f), anchor, i * 16_666_667L, spanPx = 100f)!![12]
        val target = 100f * 30 / 60f
        assertTrue("trails by ${target - shown} px", target - shown < 4f)
        assertTrue(shown <= target + 1e-3f) // never overshoots
    }

    @Test fun meshChoiceNeverDrawsATrackedMeshWithoutItsCorrection() {
        fun pick(latestIsShown: Boolean, match: Boolean, waitedMs: Long = 10, hasShown: Boolean = true) =
            VtoMeshChoice.pick(latestIsShown, match, latestTracked = true, hasShown = hasShown, waitedNanos = waitedMs * 1_000_000)
        assertEquals(Pick.LATEST_WITH_NEW_SHIFT, pick(latestIsShown = false, match = true))
        // Next mesh's correction published, mesh itself not delivered yet: keep the shown mesh's correction
        // (this frame used to snap the glasses ~300 ms back).
        assertEquals(Pick.LATEST_WITH_LAST_SHIFT, pick(latestIsShown = true, match = false))
        assertEquals(Pick.SHOWN_WITH_LAST_SHIFT, pick(latestIsShown = false, match = false, waitedMs = 100))
        assertEquals(Pick.LATEST_UNTRACKED, pick(latestIsShown = false, match = false, waitedMs = 400))
        assertEquals(Pick.LATEST_UNTRACKED, pick(latestIsShown = false, match = false, hasShown = false))
    }

    private fun turned(angle: Float) = floatArrayOf(cos(angle), 0f, -sin(angle), 0f, 0f, 1f, 0f, 0f,
        sin(angle), 0f, cos(angle), 0f, 0f, 0f, 0f, 1f)
    private fun yawOf(m: FloatArray) = atan2(m[8], m[10])

    @Test fun meshTurnNoiseIsHeldStillWhileTheHeadIsStill() {
        val pose = VtoDisplayPose()
        pose.sample(turned(0f), anchor, 0L, spanPx = 100f)
        val noise = Math.toRadians(1.0).toFloat()
        var worst = 0f
        // A new mesh every 3 display frames (~18/s), alternating +/-1 degree of turn.
        for (i in 1..180) {
            val target = if ((i / 3) % 2 == 0) noise else -noise
            val shown = pose.sample(turned(target), anchor, i * 16_666_667L, spanPx = 100f)!!
            if (i > 60) worst = maxOf(worst, kotlin.math.abs(yawOf(shown)))
        }
        assertTrue("still head swings ${Math.toDegrees(worst.toDouble())} degrees", Math.toDegrees(worst.toDouble()) < .5)
    }

    @Test fun aRealHeadTurnIsStillFollowed() {
        val pose = VtoDisplayPose()
        pose.sample(turned(0f), anchor, 0L, spanPx = 100f)
        var shown = 0f
        // 1 rad/s turn for 0.4 s, in mesh steps every 3 display frames.
        for (i in 1..24) {
            val target = (i / 3 * 3) / 60f
            shown = yawOf(pose.sample(turned(target), anchor, i * 16_666_667L, spanPx = 100f)!!)
        }
        val target = (24 / 3 * 3) / 60f
        assertTrue("trails by ${Math.toDegrees((target - shown).toDouble())} degrees", Math.toDegrees((target - shown).toDouble()) < 6.0)
    }
}
