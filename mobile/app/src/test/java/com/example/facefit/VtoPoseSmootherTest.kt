package com.example.facefit

import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.tryon.tracking.VtoPoseSmoother
import org.junit.Assert.*
import org.junit.Test

class VtoPoseSmootherTest {
    private fun face(offset: Float = 0f) = FaceAnalyzer.FaceMetrics(0f,0f,0f,"",
        List(478) { FaceAnalyzer.LandmarkPoint((if (it == 33) .4f else .6f) + offset, .4f) },
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
        assertTrue(moved.landmarks[33].x > .44f)
        assertTrue(moved.landmarks[33].x <= .45f)
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
        assertEquals(.375f, result.landmarks[33].y, .001f)
        assertEquals(.425f, result.landmarks[263].y, .001f)
    }
    @Test fun yawDepthChangesRespondWithoutCenterTranslation() {
        val filter = VtoPoseSmoother()
        val first = face()
        filter.sample(first, 0L)
        val turned = first.copy(landmarks = first.landmarks.mapIndexed { i, p ->
            if (i == 33) p.copy(z = -.03f) else if (i == 263) p.copy(z = .03f) else p
        })
        val result = requireNotNull(filter.sample(turned, 16_000_000L))
        assertEquals(-.03f, result.landmarks[33].z, .001f)
        assertEquals(.03f, result.landmarks[263].z, .001f)
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
}