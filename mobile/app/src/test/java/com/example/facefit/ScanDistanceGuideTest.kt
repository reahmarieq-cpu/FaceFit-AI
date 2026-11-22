package com.example.facefit

import com.example.facefit.facefit.analysis.ScanDistanceGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanDistanceGuideTest {
    @Test fun messagesMatchTheTargetRange() {
        assertEquals("41 cm · Move a little farther", ScanDistanceGuide.hintFor(41.2f).text)
        assertEquals("55 cm · Move a little closer", ScanDistanceGuide.hintFor(54.6f).text)
        val good = ScanDistanceGuide.hintFor(47.4f)
        assertEquals("47 cm · Good distance, hold here", good.text)
        assertTrue(good.inRange)
        assertTrue(ScanDistanceGuide.hintFor(45f).inRange)
        assertTrue(ScanDistanceGuide.hintFor(50f).inRange)
        assertFalse(ScanDistanceGuide.hintFor(44.9f).inRange)
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

    @Test fun distanceEstimateMatchesTheSyntheticCamera() {
        // The estimate the guide displays, from a projected average face at a known distance.
        val capture = SyntheticFace.canonical().capture(47.0)
        val frame = com.example.facefit.facefit.analysis.FacialFeatureMeasurement.measure(
            capture.points, 480, 640, capture.poseMatrix, 487.5f, "test")!!
        assertEquals("47 cm · Good distance, hold here", ScanDistanceGuide.hintFor(frame.cheekDistanceCm).text)
    }
}
