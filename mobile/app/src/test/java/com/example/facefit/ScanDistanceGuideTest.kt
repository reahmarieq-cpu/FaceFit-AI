package com.example.facefit

import com.example.facefit.facefit.analysis.ScanDistanceGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanDistanceGuideTest {
    @Test fun messagesMatchTheTargetRange() {
        assertEquals("Move a little farther", ScanDistanceGuide.hintFor(44.2f).text)
        assertEquals("Move a little closer", ScanDistanceGuide.hintFor(45.8f).text)
        val good = ScanDistanceGuide.hintFor(45.2f)
        assertEquals("Good, hold still…", good.text)
        assertTrue(good.inRange)
        // Anything shown as 45 cm is accepted.
        assertTrue(ScanDistanceGuide.hintFor(44.6f).inRange)
        assertTrue(ScanDistanceGuide.hintFor(45.4f).inRange)
        assertFalse(ScanDistanceGuide.hintFor(44.4f).inRange)
        assertFalse(ScanDistanceGuide.hintFor(45.6f).inRange)
    }

    @Test fun medianSmoothingIgnoresASingleJump() {
        val guide = ScanDistanceGuide()
        listOf(47f, 47.5f, 46.8f, 62f, 47.2f).forEachIndexed { i, cm -> guide.update(cm, i * 250L) }
        assertEquals(47, guide.update(47.1f, 1250L)!!.distanceCm)
    }

    @Test fun hintDisappearsWhenTheFaceIsLost() {
        val guide = ScanDistanceGuide()
        assertEquals(48, guide.update(48f, 0L)!!.distanceCm)
        // Briefly lost: keep the last hint.
        assertEquals(48, guide.update(null, 500L)!!.distanceCm)
        // Lost for over a second: no hint.
        assertNull(guide.update(null, 1600L))
        // Back again: old readings are not reused.
        assertEquals(52, guide.update(52f, 1700L)!!.distanceCm)
    }

    @Test fun smilingBlocksCaptureAtAnyDistance() {
        val hint = ScanDistanceGuide.hintFor(47f, smileScore = 0.6f)
        assertTrue(hint.smiling)
        assertFalse(hint.inRange)
        assertEquals("Keep a neutral expression, no smile", hint.refusal)
        assertTrue(ScanDistanceGuide.hintFor(45f, smileScore = 0.005f).inRange)
        assertTrue(ScanDistanceGuide.hintFor(47f, smileScore = 0.025f).smiling)
        assertEquals(0.6f, hint.smileScore!!, 1e-6f)
        // Subtle smile caught by the upper-lip raise alone.
        assertTrue(ScanDistanceGuide.hintFor(47f, smileScore = 0.002f, upperLipRaise = 0.05f).smiling)
        assertTrue(ScanDistanceGuide.hintFor(45f, smileScore = 0.002f, upperLipRaise = 0.01f).inRange)
        assertEquals("Move slightly farther away", ScanDistanceGuide.hintFor(42f).refusal)
        assertEquals("Move slightly closer", ScanDistanceGuide.hintFor(53f).refusal)
    }

    @Test fun tiltedOrTurnedHeadBlocksCapture() {
        val down = ScanDistanceGuide.hintFor(45f, pitchDeg = 4f)
        assertFalse(down.inRange)
        assertEquals("Lift your chin slightly", down.text)
        assertEquals("Lift your chin slightly", down.refusal)
        assertEquals("Lower your chin slightly", ScanDistanceGuide.hintFor(45f, pitchDeg = -4f).refusal)
        assertEquals("Turn to face the camera", ScanDistanceGuide.hintFor(45f, pitchDeg = 0f, yawDeg = 7f).refusal)
        // Inside the chip's limit (scan limit minus 0.5 degrees): fine.
        assertTrue(ScanDistanceGuide.hintFor(45f, pitchDeg = -2.4f, yawDeg = 5f).inRange)
        assertFalse(ScanDistanceGuide.hintFor(45f, pitchDeg = -2.8f).inRange)
        // Distance and smile messages come first.
        assertEquals("Move slightly closer", ScanDistanceGuide.hintFor(49.5f, pitchDeg = 5f).refusal)
        assertTrue(ScanDistanceGuide.hintFor(45f, smileScore = 0.5f, pitchDeg = 5f).smiling)
    }

    @Test fun tiltIsSmoothedOverThreeFrames() {
        val guide = ScanDistanceGuide()
        listOf(0f, 0.5f, 6f).forEachIndexed { i, p -> guide.update(45f, i * 250L, pitchDeg = p) }
        assertTrue(guide.update(45f, 750L, pitchDeg = 0.2f)!!.inRange)
        guide.update(45f, 1000L, pitchDeg = 5f)
        assertEquals("Lift your chin slightly", guide.update(45f, 1250L, pitchDeg = 5f)!!.refusal)
    }

    @Test fun smileWarningClearsQuicklyAfterRelaxing() {
        val guide = ScanDistanceGuide()
        listOf(0.7f, 0.7f, 0.7f).forEachIndexed { i, s -> guide.update(47f, i * 250L, s) }
        assertTrue(guide.update(47f, 750L, 0.7f)!!.smiling)
        guide.update(47f, 1000L, 0.002f)
        assertFalse(guide.update(47f, 1250L, 0.002f)!!.smiling)
    }

    @Test fun distanceEstimateMatchesTheSyntheticCamera() {
        // The estimate the guide displays, from a projected average face at a known distance.
        val capture = SyntheticFace.canonical().capture(45.0)
        val frame = com.example.facefit.facefit.analysis.FacialFeatureMeasurement.measure(
            capture.points, 480, 640, capture.poseMatrix, 487.5f, "test")!!
        assertEquals("Good, hold still…", ScanDistanceGuide.hintFor(frame.cheekDistanceCm).text)
    }
}
