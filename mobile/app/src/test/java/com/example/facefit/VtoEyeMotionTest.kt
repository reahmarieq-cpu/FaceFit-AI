package com.example.facefit

import com.example.facefit.ui.camera.VtoEyeMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class VtoEyeMotionTest {
    @Test fun widerFaceSearchAndCoastUseMeasuredLatency() {
        val width = 120
        val height = 96
        val random = Random(19)
        val pixels = ByteArray(width * height) { random.nextInt(256).toByte() }
        val reference = VtoEyeMotion.Frame(width, height, pixels)
        val anchor = VtoEyeMotion.Anchor(reference, 48, 48, 20, 1L)
        fun shifted(dx: Int): VtoEyeMotion.Frame {
            val moved = ByteArray(pixels.size)
            for (y in 0 until height) for (x in 0 until width - dx) {
                moved[y * width + x + dx] = pixels[y * width + x]
            }
            return VtoEyeMotion.Frame(width, height, moved)
        }
        val flat = VtoEyeMotion.Frame(width, height, ByteArray(pixels.size))
        val tracker = VtoEyeMotion.Tracker()
        val latency = 300_000_000L
        val initialMiss = requireNotNull(tracker.update(anchor, flat, 267_000_000L, latency))
        assertTrue(initialMiss.predicted)
        assertTrue(!initialMiss.hasMeasurement)
        val first = requireNotNull(tracker.update(anchor, shifted(30), 300_000_000L, latency))
        assertTrue(first.hasMeasurement)
        assertEquals(30f / width, first.x, .0001f)
        // A single match can hold position through a miss before velocity is available.
        val held = requireNotNull(tracker.update(anchor, flat, 316_000_000L, latency))
        assertTrue(held.predicted)
        assertEquals(first.x, held.positionAt(316_000_000L).first, .0001f)

        val second = requireNotNull(tracker.update(anchor, shifted(33), 333_000_000L, latency))
        assertEquals(33f / width, second.x, .0001f)
        assertTrue(second.positionAt(345_000_000L).first > second.x)
        for (time in 366_000_000L..597_000_000L step 33_000_000L) {
            val coast = requireNotNull(tracker.update(anchor, flat, time, latency))
            assertTrue(coast.predicted)
            assertTrue(coast.positionAt(time).first > second.x)
            assertEquals(second.capturedAt + maxOf(latency + latency / 2, latency + 2 * coast.cameraIntervalNanos), coast.validUntilNanos)
        }
        val stopped = requireNotNull(tracker.update(anchor, shifted(33), 630_000_000L, latency))
        assertEquals(0f, stopped.velocityXPerNano, 0f)
        assertEquals(0f, stopped.accelerationXPerNanoSquared, 0f)
        assertEquals(stopped.x, stopped.positionAt(646_000_000L).first, .0001f)
        val heldAfterWindow = requireNotNull(tracker.update(anchor, flat, 1_100_000_000L, latency))
        assertTrue(heldAfterWindow.predicted)
        assertEquals(stopped.x, heldAfterWindow.positionAt(1_100_000_000L).first, .0001f)
    }

    @Test fun newMeshAnchorPreservesConfirmedPositionAndDoesNotRestartExpiry() {
        val width = 96
        val height = 80
        val random = Random(7)
        val pixels = ByteArray(width * height) { random.nextInt(256).toByte() }
        val image = VtoEyeMotion.Frame(width, height, pixels)
        val original = VtoEyeMotion.Anchor(image, 40, 40, 12, 1L)
        val updated = VtoEyeMotion.Anchor(image, 48, 44, 12, 260_000_000L)
        val flat = VtoEyeMotion.Frame(width, height, ByteArray(pixels.size))
        val tracker = VtoEyeMotion.Tracker()
        val first = requireNotNull(tracker.update(original, image, 300_000_000L, 280_000_000L))
        assertEquals(0f, first.x, .0001f)

        val rebased = requireNotNull(tracker.update(updated, flat, 333_000_000L, 280_000_000L))
        assertTrue(rebased.predicted)
        assertEquals(40f, updated.x + rebased.x * width, .0001f)
        assertEquals(40f, updated.y + rebased.y * height, .0001f)
        assertEquals(first.capturedAt + 420_000_000L, rebased.validUntilNanos)
        val stillHeld = requireNotNull(tracker.update(updated, flat, 800_000_000L, 280_000_000L))
        assertTrue(stillHeld.predicted)
        assertEquals(first.capturedAt, stillHeld.capturedAt)
        assertNull(tracker.update(null, flat, 833_000_000L, 280_000_000L))
    }

    @Test fun aMissKeepsTheSameAcceleratingTrajectoryAtDisplayTime() {
        val width = 120
        val height = 96
        val random = Random(3)
        val pixels = ByteArray(width * height) { random.nextInt(256).toByte() }
        val source = VtoEyeMotion.Frame(width, height, pixels)
        val anchor = VtoEyeMotion.Anchor(source, 48, 48, 20, 1L)
        fun move(dx: Int): VtoEyeMotion.Frame {
            val moved = ByteArray(pixels.size)
            for (y in 0 until height) for (x in 0 until width - dx) {
                moved[y * width + x + dx] = pixels[y * width + x]
            }
            return VtoEyeMotion.Frame(width, height, moved)
        }
        val tracker = VtoEyeMotion.Tracker()
        tracker.update(anchor, source, 300_000_000L, 350_000_000L)
        tracker.update(anchor, move(2), 330_000_000L, 350_000_000L)
        val last = requireNotNull(tracker.update(anchor, move(6), 360_000_000L, 350_000_000L))
        assertTrue(last.accelerationXPerNanoSquared > 0f)
        val flat = VtoEyeMotion.Frame(width, height, ByteArray(pixels.size))
        val predicted = requireNotNull(tracker.update(anchor, flat, 390_000_000L, 350_000_000L))
        assertTrue(predicted.predicted)
        assertEquals(last.capturedAt, predicted.capturedAt)
        assertEquals(last.positionAt(406_000_000L).first, predicted.positionAt(406_000_000L).first, .0001f)
    }

    @Test fun extrapolatedDecelerationCannotReverseWithoutAnotherMatch() {
        val frame = VtoEyeMotion.Frame(96, 80, ByteArray(96 * 80))
        val anchor = VtoEyeMotion.Anchor(frame, 48, 40, 16, 0L)
        val braking = VtoEyeMotion.Offset(anchor, 0f, 0f, 0L,
            validUntilNanos = 300_000_000L, cameraIntervalNanos = 33_000_000L,
            velocityXPerNano = 1e-9f, decayTimeNanos = 300_000_000L,
            accelerationXPerNanoSquared = -1e-15f)
        val early = braking.positionAt(16_000_000L).first
        val late = braking.positionAt(250_000_000L).first
        assertTrue(early > 0f)
        assertTrue(late >= early)
    }

    @Test fun recoversTranslationFromOriginalAnchorWithoutAccumulating() {
        val width = 96
        val height = 80
        val random = Random(42)
        val pixels = ByteArray(width * height) { random.nextInt(256).toByte() }
        val reference = VtoEyeMotion.Frame(width, height, pixels)
        val anchor = VtoEyeMotion.Anchor(reference, 48, 40, 16, 1L)
        val dx = 6
        val dy = -3
        val shifted = ByteArray(pixels.size)
        for (y in 3 until height) for (x in 0 until width - dx) {
            shifted[(y + dy) * width + x + dx] = pixels[y * width + x]
        }
        val live = VtoEyeMotion.Frame(width, height, shifted)
        for (time in listOf(250_000_000L, 283_000_000L)) {
            val offset = requireNotNull(VtoEyeMotion.match(anchor, live, time))
            assertEquals(dx.toFloat() / width, offset.x, 0.0001f)
            assertEquals(dy.toFloat() / height, offset.y, 0.0001f)
        }
        assertNull(VtoEyeMotion.match(anchor, live, anchor.capturedAt))
        assertNull(VtoEyeMotion.match(anchor, VtoEyeMotion.Frame(width, height, ByteArray(pixels.size)), 300_000_000L))
    }
}
