package com.example.facefit

import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.tryon.geometry.Vector3
import com.example.facefit.facefit.tryon.tracking.VtoPositionHandoff
import com.example.facefit.facefit.tryon.tracking.VtoDisplayFollower
import org.junit.Assert.*
import org.junit.Test

class VtoDisplayFollowerTest {
    private fun face(dx: Float=0f)=FaceAnalyzer.FaceMetrics(0f,0f,0f,"",List(478) { i ->
        FaceAnalyzer.LandmarkPoint((if(i==33 || i==133) .4f else .6f)+dx,.4f)
    },1000,1600,true)
    @Test fun firstMeasurementIsImmediate() {
        val f=VtoDisplayFollower();val target=face()
        assertSame(target,f.sample(target,0L).face)
        assertEquals(1f,f.sample(target,16_000_000L).opacity,0f)
    }
    @Test fun everyDetectionPassesThroughWithoutAccumulatedLag() {
        val follower = VtoDisplayFollower()
        follower.sample(face(), 0L)
        val target = face(.03f)
        assertSame(target, follower.sample(target, 16_000_000L).face)
    }

    @Test fun neverOvershootsOrRecoilsWhileTargetIsUnchanged() {
        val f=VtoDisplayFollower();f.sample(face(),0L)
        val target=face(.04f);var previous=.4f
        for(i in 1..120) {
            val x=f.sample(target,i*16_000_000L).face!!.landmarks[33].x
            assertTrue(x>=previous);assertTrue(x<=target.landmarks[33].x);previous=x
        }
    }
    @Test fun displaySubstepsIntegrateTheSameElapsedTime() {
        val regular = VtoDisplayFollower()
        val split = VtoDisplayFollower()
        regular.sample(face(), 0L)
        split.sample(face(), 0L)
        val target = face(.03f)
        regular.sample(target, 16_000_000L)
        split.sample(target, 16_000_000L)
        split.sample(target, 24_000_000L)
        val expected = regular.sample(target, 32_000_000L).face!!.landmarks[33].x
        assertEquals(expected, split.sample(target, 32_000_000L).face!!.landmarks[33].x, .000001f)
    }

    @Test fun tinyMovementPassesThroughWithoutAnotherFilter() {
        val follower = VtoDisplayFollower()
        follower.sample(face(), 0L)
        val target = face(.0001f)
        assertSame(target, follower.sample(target, 16_000_000L).face)
    }
    @Test fun latestRestingMeasurementIsUsedDirectly() {
        val follower = VtoDisplayFollower()
        follower.sample(face(), 0L)
        for (i in 1..30) {
            val target = face(if (i % 2 == 0) .00035f else -.00035f)
            assertSame(target, follower.sample(target, i * 16_000_000L).face)
        }
    }

    @Test fun shortLossHoldsExactLastDisplayedObject() {
        val f=VtoDisplayFollower();val target=face();val visible=f.sample(target,0L)
        val lost=target.copy(isFaceDetected=false)
        assertSame(visible,f.sample(lost,16_000_000L))
        assertSame(visible,f.sample(lost,40_000_000L))
        assertSame(target,f.sample(target,41_000_000L).face)
    }
    @Test fun graceEndsInFadeThenHides() {
        val f=VtoDisplayFollower();val target=face();f.sample(target,0L)
        f.sample(null,10_000_000L)
        assertEquals(1f,f.sample(null,34_000_000L).opacity,0f)
        val fading=f.sample(null,60_000_000L)
        assertSame(target,fading.face);assertEquals(.5f,fading.opacity,.001f)
        val hidden=f.sample(null,85_000_000L)
        assertNull(hidden.face);assertEquals(0f,hidden.opacity,0f)
    }
    @Test fun reacquireAfterGraceStartsFreshEvenDuringFade() {
        val f=VtoDisplayFollower();f.sample(face(),0L);f.sample(null,10_000_000L)
        f.sample(null,60_000_000L)
        val next=face(.1f)
        assertSame(next,f.sample(next,70_000_000L).face)
    }
    @Test fun sizeChangeNeverHoldsOldCoordinates() {
        val f=VtoDisplayFollower();f.sample(face(),0L)
        val resized=face(.08f).copy(imageWidth=500,imageHeight=800)
        assertSame(resized,f.sample(resized,16_000_000L).face)
        assertNull(f.sample(resized.copy(imageWidth=800,isFaceDetected=false),32_000_000L).face)
    }
    @Test fun proportionalResolutionHasSameFollowing() {
        val a=VtoDisplayFollower();val b=VtoDisplayFollower()
        a.sample(face(),0L);b.sample(face().copy(imageWidth=500,imageHeight=800),0L)
        val x=a.sample(face(.01f),16_000_000L).face!!
        val y=b.sample(face(.01f).copy(imageWidth=500,imageHeight=800),16_000_000L).face!!
        assertEquals(x.landmarks,y.landmarks)
    }
    @Test fun pauseResumeDoesNotAnimateOldPose() {
        val f=VtoDisplayFollower();f.sample(face(),0L)
        f.sample(face(.002f),16_000_000L)
        val target=face(.03f)
        assertSame(target,f.sample(target,1_000_000_000L).face)
    }
    @Test fun invalidCoordinatesAreNeverDisplayed() {
        val f=VtoDisplayFollower()
        val invalid=face().copy(landmarks=List(478) { FaceAnalyzer.LandmarkPoint(Float.NaN,0f) })
        assertNull(f.sample(invalid,0L).face)
    }
    @Test fun stoppingAfterReversalNeverContinuesHistoricalVelocity() {
        val follower = VtoDisplayFollower()
        follower.sample(face(), 0L)
        follower.sample(face(.04f), 16_000_000L)
        val stopped = face(.02f)
        for (i in 2..30) assertSame(stopped, follower.sample(stopped, i * 16_000_000L).face)
    }

    @Test fun identicalNewMeasurementsPreserveDisplayedIdentity() {
        val follower=VtoDisplayFollower();val first=face()
        val display=follower.sample(first,0L)
        val equivalent=face()
        assertSame(display,follower.sample(equivalent,16_000_000L))
        assertSame(display,follower.sample(equivalent,32_000_000L))
    }
    @Test fun missingDimensionsUseGraceInsteadOfResettingToNothing() {
        val follower=VtoDisplayFollower();val first=follower.sample(face(),0L)
        val invalid=face().copy(imageWidth=0,isFaceDetected=false)
        assertSame(first,follower.sample(invalid,16_000_000L))
    }
    @Test fun handoffNeverStoresMomentumAfterTheTargetStops() {
        val handoff = VtoPositionHandoff()
        val previous = Any()
        val next = Any()
        val period = 58_000_000L
        fun sample(x: Float, source: Any, ms: Long) =
            handoff.sample(Vector3(x, 0f, 0f), source, false, ms * 1_000_000L, period).x

        assertEquals(0f, sample(0f, previous, 0L), .0001f)
        assertEquals(16f, sample(16f, previous, 16L), .0001f)
        assertEquals(52f, sample(52f, next, 32L), .0001f)
        assertEquals(81f, sample(81f, next, 61L), .0001f)
        assertEquals(110f, sample(110f, next, 90L), .0001f)
        assertEquals(110f, sample(110f, next, 120L), .0001f)
    }
}
