package com.example.facefit

import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.tryon.tracking.VtoPoseSmoother
import org.junit.Assert.*
import org.junit.Test

class VtoPoseSmootherTest {
    private fun face(offset: Float = 0f) = FaceAnalyzer.FaceMetrics(0f,0f,0f,"",
        List(478) { FaceAnalyzer.LandmarkPoint((if (it == 33 || it == 133) .4f else .6f) + offset, .4f) },
        1000, 1600, true)
    @Test fun firstFaceAndReacquiredFaceHaveNoInterpolationDelay() {
        val filter = VtoPoseSmoother()
        val first = face()
        assertSame(first, filter.sample(first, 0L))
        assertNull(filter.sample(null, 16_000_000L))
        val next = face(.2f)
        assertSame(next, filter.sample(next, 32_000_000L))
    }
    @Test fun quickMovementRespondsWithinOneDisplayFrame() {
        val filter = VtoPoseSmoother()
        filter.sample(face(), 0L)
        val moved = requireNotNull(filter.sample(face(.05f), 16_000_000L))
        assertTrue(moved.landmarks[33].x > .43f)
        assertTrue(moved.landmarks[33].x <= face(.05f).landmarks[33].x)
    }
    @Test fun tiltWithStationaryEyeCenterRespondsWithinOneDisplayFrame() {
        val filter = VtoPoseSmoother()
        val first = face()
        filter.sample(first, 0L)
        val tilted = first.copy(landmarks = first.landmarks.mapIndexed { i, p ->
            when (i) {
                33 -> p.copy(y = p.y - .025f)
                263 -> p.copy(y = p.y + .025f)
                else -> p
            }
        })
        val result = requireNotNull(filter.sample(tilted, 16_000_000L))
        assertTrue(result.landmarks[33].y in .375f.. .39f)
        assertTrue(result.landmarks[263].y in .41f.. .425f)
    }
    @Test fun yawDepthChangesRespondWithoutCenterTranslation() {
        val filter = VtoPoseSmoother()
        val first = face()
        filter.sample(first, 0L)
        val turned = first.copy(landmarks = first.landmarks.mapIndexed { i, p ->
            if (i == 33) p.copy(z = -.03f) else if (i == 263) p.copy(z = .03f) else p
        })
        val result = requireNotNull(filter.sample(turned, 16_000_000L))
        assertTrue(result.landmarks[33].z in -.03f.. -.012f)
        assertTrue(result.landmarks[263].z in .012f.. .03f)
    }
    @Test fun stationaryJitterIsReduced() {
        val filter = VtoPoseSmoother()
        filter.sample(face(), 0L)
        val changed = requireNotNull(filter.sample(face(.001f), 16_000_000L))
        assertTrue(changed.landmarks[33].x in .4f.. .4009f)
    }
    @Test fun predictionStopsWhenUpdatesStop() {
        val filter = VtoPoseSmoother()
        filter.sample(face(), 0L)
        val moved = face(.04f)
        filter.sample(moved, 16_000_000L)
        for (i in 2..60) filter.sample(moved, i * 16_000_000L)
        val settled = requireNotNull(filter.sample(moved, 1_000_000_000L))
        assertEquals(.44f, settled.landmarks[33].x, .0001f)
    }
    @Test fun displayPoseDoesNotExtrapolateBetweenDetectorUpdates() {
        val filter = VtoPoseSmoother()
        filter.sample(face(), 0L)
        val latest = face(.004f)
        val first = requireNotNull(filter.sample(latest, 60_000_000L))
        val between = requireNotNull(filter.sample(latest, 76_000_000L))
        assertTrue(between.landmarks[33].x > first.landmarks[33].x)
        assertTrue(between.landmarks[33].x <= latest.landmarks[33].x)
    }
    @Test fun trackingLossImmediatelyHidesTheDisplayPose() {
        val filter = VtoPoseSmoother()
        filter.sample(face(), 0L)
        filter.sample(face(.04f), 60_000_000L)
        val lost = face().copy(isFaceDetected = false)
        assertSame(lost, filter.sample(lost, 76_000_000L))
        val reacquired = face(.1f)
        assertSame(reacquired, filter.sample(reacquired, 92_000_000L))
    }
    @Test fun returningFromPauseDoesNotAnimateFromAnOldPose() {
        val filter = VtoPoseSmoother()
        filter.sample(face(), 0L)
        filter.sample(face(.04f), 60_000_000L)
        val resumed = face(.08f)
        assertSame(resumed, filter.sample(resumed, 1_000_000_000L))
    }
    @Test fun highIsoSuppressesSyntheticJitterWhileAllowingTrueMotion() {
        val bright = VtoPoseSmoother()
        val dim = VtoPoseSmoother()
        val calibration = listOf(0f, .001f, -.001f, .001f)
        calibration.forEachIndexed { i, offset ->
            bright.sample(face(offset), i * 16_000_000L)
            dim.sample(face(offset), i * 16_000_000L)
        }
        val still = dim.sample(face(.001f), 49_000_000L)
        bright.sample(face(.001f), 49_000_000L)
        val noisy = face(.013f)
        val brightResult = requireNotNull(bright.sample(noisy, 64_000_000L))
        assertTrue(brightResult.landmarks[33].x > requireNotNull(still).landmarks[33].x)
        assertSame(still, dim.sample(noisy.copy(snrFactor = 4f), 64_000_000L))

        val moved = requireNotNull(dim.sample(face(.16f).copy(snrFactor = 4f), 80_000_000L))
        assertTrue(moved.landmarks[33].x > .48f)
    }
}