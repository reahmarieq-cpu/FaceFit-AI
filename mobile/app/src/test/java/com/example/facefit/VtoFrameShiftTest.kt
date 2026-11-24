package com.example.facefit

import com.example.facefit.facefit.tryon.tracking.VtoFrameShiftTracker
import com.example.facefit.facefit.tryon.tracking.VtoThumbnail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin

class VtoFrameShiftTest {
    private val step = 3
    private val sourceW = 640
    private val sourceH = 480
    private val cx = 320.0
    private val cy = 216.0

    /**
     * Smooth texture moved by a similarity transform about (cx, cy): translation (tx, ty)
     * source px, rotation [angle] rad and [scale]. Sampled by inverse mapping.
     */
    private fun frame(id: Long, tx: Double = 0.0, ty: Double = 0.0, angle: Double = 0.0, scale: Double = 1.0,
        brightness: Int = 0, flat: Boolean = false, blankSecondEye: Boolean = false): VtoThumbnail {
        val w = sourceW / step
        val h = sourceH / step
        val c = cos(-angle); val s = sin(-angle)
        val pixels = IntArray(w * h) { i ->
            val qx = (i % w) * step - cx - tx
            val qy = (i / w) * step - cy - ty
            val x = (c * qx - s * qy) / scale + cx
            val y = (s * qx + c * qy) / scale + cy
            val blank = blankSecondEye && kotlin.math.abs(qx + cx - (cx + 75 + tx)) < 50 && kotlin.math.abs(qy + cy - (cy + ty)) < 40
            val v = if (flat || blank) 128.0 else 128 + 50 * sin(x / 9.0) * sin(y / 7.0) + 30 * sin((x + 2 * y) / 13.0)
            (v + brightness).toInt().coerceIn(0, 255)
        }
        return VtoThumbnail(id, sourceW, sourceH, step, w, h, pixels)
    }

    // Eyes 150 source px apart, centred on (cx, cy).
    private fun tracker(reference: VtoThumbnail = frame(1)) = VtoFrameShiftTracker().also {
        assertTrue(it.setReference(reference, (cx - 75).toFloat() / sourceW, cy.toFloat() / sourceH,
            (cx + 75).toFloat() / sourceW, cy.toFloat() / sourceH))
    }

    @Test fun measuresTranslationSinceTheMeshFrame() {
        val shift = tracker().track(frame(2, tx = 12.0, ty = -9.0))!!
        assertEquals(1L, shift.referenceId)
        assertEquals(2L, shift.frameId)
        assertEquals(12.0, shift.dx.toDouble(), 1.5)
        assertEquals(-9.0, shift.dy.toDouble(), 1.5)
        assertEquals(1.0, shift.scale.toDouble(), .02)
        assertEquals(0.0, shift.roll.toDouble(), .02)
    }

    @Test fun measuresTiltAndDistanceFromTheTwoEyes() {
        val tilted = tracker().track(frame(2, angle = .08))!!
        assertEquals(.08, tilted.roll.toDouble(), .02)
        assertEquals(1.0, tilted.scale.toDouble(), .03)
        val closer = tracker().track(frame(2, scale = 1.08))!!
        assertEquals(1.08, closer.scale.toDouble(), .03)
        assertEquals(0.0, closer.roll.toDouble(), .02)
    }

    @Test fun followsALongMoveAcrossSeveralFramesWithoutDrift() {
        val tracker = tracker()
        var last: VtoFrameShiftTracker.Shift? = null
        // 15 source px per camera frame; the total far exceeds one search window.
        for (i in 1..8) last = tracker.track(frame(1L + i, tx = 15.0 * i))
        assertEquals(120.0, last!!.dx.toDouble(), 1.5)
        assertEquals(0.0, last.dy.toDouble(), 1.5)
    }

    @Test fun aNewMeshContinuesFromTheMotionAlreadyTracked() {
        val tracker = tracker()
        // 15 source px per frame; frame ids 2..9.
        for (i in 1..8) assertNotNull(tracker.track(frame(1L + i, tx = 15.0 * i)))
        // The mesh for frame 5 (moved 60 px) arrives while the camera is already at frame 9 (120 px):
        // 60 px since the mesh frame is far outside one search window.
        assertTrue(tracker.setReference(frame(5, tx = 60.0), (cx - 75 + 60).toFloat() / sourceW, cy.toFloat() / sourceH,
            (cx + 75 + 60).toFloat() / sourceW, cy.toFloat() / sourceH))
        val caughtUp = tracker.track(frame(9, tx = 120.0))!!
        assertEquals(5L, caughtUp.referenceId)
        assertEquals(60.0, caughtUp.dx.toDouble(), 1.5)
        val next = tracker.track(frame(10, tx = 135.0))!!
        assertEquals(75.0, next.dx.toDouble(), 1.5)
    }

    @Test fun whenOneEyeIsLostTheOtherCarriesBoth() {
        val shift = tracker().track(frame(2, tx = 9.0, blankSecondEye = true))!!
        assertEquals(9.0, shift.dx.toDouble(), 1.5)
        assertEquals(1.0, shift.scale.toDouble(), .02)
        assertEquals(0.0, shift.roll.toDouble(), .02)
    }

    @Test fun eyeMidpointHistoryGivesTheMotionBetweenTwoFrames() {
        val tracker = tracker()
        for (i in 1..5) tracker.track(frame(1L + i, tx = 15.0 * i, ty = -6.0 * i))
        val from = tracker.eyeMidpoint(2)!!
        val to = tracker.eyeMidpoint(6)!!
        assertEquals(60.0, (to[0] - from[0]).toDouble(), 1.5)
        assertEquals(-24.0, (to[1] - from[1]).toDouble(), 1.5)
        assertNull(tracker.eyeMidpoint(99))
    }

    @Test fun theMeshFrameItselfHasNoShift() {
        val reference = frame(1)
        val shift = tracker(reference).track(reference)!!
        assertEquals(0f, shift.dx); assertEquals(0f, shift.dy)
        assertEquals(1f, shift.scale); assertEquals(0f, shift.roll)
    }

    @Test fun toleratesAnExposureChange() {
        val shift = tracker().track(frame(2, tx = 6.0, brightness = 25))
        assertNotNull(shift)
        assertEquals(6.0, shift!!.dx.toDouble(), 1.5)
    }

    @Test fun rejectsAJumpBeyondTheSearchWindowAndAnUnrelatedImage() {
        assertNull(tracker().track(frame(2, tx = 90.0)))
        val unrelated = IntArray((sourceW / step) * (sourceH / step)) { (it * 7919) % 256 }
        assertNull(tracker().track(VtoThumbnail(3, sourceW, sourceH, step, sourceW / step, sourceH / step, unrelated)))
    }

    @Test fun flatOrOffFramePatchesAreNotUsedAsReference() {
        val t = VtoFrameShiftTracker()
        assertFalse(t.setReference(frame(1, flat = true), .38f, .45f, .62f, .45f))
        assertFalse(t.setReference(frame(1), .001f, .45f, .2f, .45f))
        assertFalse(t.setReference(frame(1), .5f, .45f, .505f, .45f)) // eyes too close to track
        assertFalse(t.hasReference())
        assertNull(t.track(frame(2)))
    }

    @Test fun thumbnailIsTakenFromAnRgbaPlaneWithRowPadding() {
        val w = 6; val h = 3; val rowStride = w * 4 + 8
        val buffer = ByteBuffer.allocate(rowStride * h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * rowStride + x * 4
            buffer.put(i, (x * 40).toByte()); buffer.put(i + 1, (y * 100).toByte()); buffer.put(i + 2, 0); buffer.put(i + 3, -1)
        }
        val thumb = VtoThumbnail.fromRgba(9, buffer, rowStride, w, h, 3)
        assertEquals(2, thumb.width); assertEquals(1, thumb.height)
        assertEquals(listOf(0, (120 + 0 + 0) shr 2), thumb.pixels.toList()) // (r + 2g + b) / 4 at x = 0 and x = 3
    }
}
