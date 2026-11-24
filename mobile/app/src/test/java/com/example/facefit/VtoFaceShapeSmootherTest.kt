package com.example.facefit

import com.example.facefit.facefit.analysis.FaceAnalyzer.LandmarkPoint
import com.example.facefit.facefit.tryon.tracking.VtoFaceShapeSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class VtoFaceShapeSmootherTest {
    private val w = 480
    private val h = 640
    private val frame = 55_000_000L // ~18 meshes per second

    /** A face of 468 points; eyes at indices 33/133 and 263/362. Placed with offset, tilt and scale. */
    private fun face(dx: Float = 0f, dy: Float = 0f, angle: Float = 0f, scale: Float = 1f, noise: (Int) -> Float = { 0f }): List<LandmarkPoint> =
        List(468) { i ->
            val (lx, ly) = when (i) {
                33 -> -70f to 0f; 133 -> -30f to 0f; 263 -> 70f to 0f; 362 -> 30f to 0f
                else -> (i % 20 - 10) * 9f to (i / 20 - 11) * 9f
            }
            val x = (lx + noise(i)) * scale; val y = (ly - noise(i + 1000)) * scale
            LandmarkPoint((240 + dx + cos(angle) * x - sin(angle) * y) / w, (300 + dy + sin(angle) * x + cos(angle) * y) / h, 0f)
        }

    @Test fun headMotionPassesWithoutLag() {
        val smoother = VtoFaceShapeSmoother()
        smoother.smooth(face(), w, h, 0L)
        // Moved, tilted and closer in one step: a rigid change must come through exactly.
        val moved = face(dx = 40f, dy = -25f, angle = .2f, scale = 1.15f)
        val out = smoother.smooth(moved, w, h, frame)
        for (i in listOf(0, 100, 250, 467)) {
            assertEquals(moved[i].x, out[i].x, 1e-4f)
            assertEquals(moved[i].y, out[i].y, 1e-4f)
        }
    }

    @Test fun pointJitterOnAStillFaceIsReduced() {
        val smoother = VtoFaceShapeSmoother()
        val random = java.util.Random(7)
        var rawSum = 0f; var outSum = 0f
        var previousOut: List<LandmarkPoint>? = null
        var previousRaw: List<LandmarkPoint>? = null
        for (step in 0 until 60) {
            val noisy = face(noise = { (random.nextGaussian() * 1.5).toFloat() }) // ~1.5 px point noise
            val out = smoother.smooth(noisy, w, h, step * frame)
            if (step > 20) {
                rawSum += (0 until 468).map { kotlin.math.abs(noisy[it].x - previousRaw!![it].x) * w }.average().toFloat()
                outSum += (0 until 468).map { kotlin.math.abs(out[it].x - previousOut!![it].x) * w }.average().toFloat()
            }
            previousOut = out; previousRaw = noisy
        }
        assertTrue("jitter raw=$rawSum smoothed=$outSum", outSum < rawSum * .4f)
    }

    @Test fun aRealExpressionChangeStillComesThrough() {
        val smoother = VtoFaceShapeSmoother()
        smoother.smooth(face(), w, h, 0L)
        // Point 0 moves 30 px relative to the eyes and stays (e.g. mouth opening).
        var out = smoother.smooth(face(noise = { if (it == 0) 30f else 0f }), w, h, frame)
        for (step in 2..10) out = smoother.smooth(face(noise = { if (it == 0) 30f else 0f }), w, h, step * frame)
        // After ~0.5 s most of the change is shown.
        assertTrue("shown ${(out[0].x * w) - 240 + 90} of 30 px", (out[0].x * w - (240 - 90)) > 20f)
    }
}
