package com.example.facefit

import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.tryon.tracking.VtoDisplayFollower
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.exp

class VtoDisplayFollowerTest {
    private fun face(dx: Float=0f)=FaceAnalyzer.FaceMetrics(0f,0f,0f,"",List(478) { i ->
        FaceAnalyzer.LandmarkPoint((if(i==33 || i==133) .4f else .6f)+dx,.4f)
    },1000,1600,true)
    @Test fun firstMeasurementIsImmediate() {
        val f=VtoDisplayFollower();val target=face()
        assertSame(target,f.sample(target,0L).face)
        assertEquals(1f,f.sample(target,16_000_000L).opacity,0f)
    }
    @Test fun movesOnDisplayTicksWithoutNewInference() {
        val f=VtoDisplayFollower();f.sample(face(),0L)
        val target=face(.03f)
        val a=f.sample(target,16_000_000L).face!!
        val b=f.sample(target,32_000_000L).face!!
        assertTrue(b.landmarks[33].x>a.landmarks[33].x)
        assertTrue(b.landmarks[33].x<=target.landmarks[33].x)
    }
    @Test fun neverOvershootsOrRecoilsWhileTargetIsUnchanged() {
        val f=VtoDisplayFollower();f.sample(face(),0L)
        val target=face(.04f);var previous=.4f
        for(i in 1..120) {
            val x=f.sample(target,i*16_000_000L).face!!.landmarks[33].x
            assertTrue(x>=previous);assertTrue(x<=target.landmarks[33].x);previous=x
        }
    }
    @Test fun realDtMatchesExponentialAtTinyError() {
        val f=VtoDisplayFollower();f.sample(face(),0L)
        val target=face(.0015f)
        val actual=f.sample(target,20_000_000L).face!!.landmarks[33].x
        assertEquals(.4f+.0015f*(1f-exp(-.020f/.045f)),actual,.000001f)
    }
    @Test fun tinyErrorIsDampedMoreThanLargeMovement() {
        val small=VtoDisplayFollower();val large=VtoDisplayFollower()
        small.sample(face(),0L);large.sample(face(),0L)
        val a=(small.sample(face(.0015f),16_000_000L).face!!.landmarks[33].x-.4f)/.0015f
        val b=(large.sample(face(.04f),16_000_000L).face!!.landmarks[33].x-.4f)/.04f
        assertTrue(b>a)
    }
    @Test fun convergencePreservesIdentityAndStopsWork() {
        val f=VtoDisplayFollower();f.sample(face(),0L);val target=face(.01f)
        var output=f.sample(target,16_000_000L)
        for(i in 2..120) output=f.sample(target,i*16_000_000L)
        assertSame(target,output.face)
        assertSame(output,f.sample(target,121*16_000_000L))
    }
    @Test fun shortLossHoldsExactLastDisplayedObject() {
        val f=VtoDisplayFollower();val target=face();val visible=f.sample(target,0L)
        val lost=target.copy(isFaceDetected=false)
        assertSame(visible,f.sample(lost,16_000_000L))
        assertSame(visible,f.sample(lost,250_000_000L))
        assertSame(target,f.sample(target,255_000_000L).face)
    }
    @Test fun graceEndsInFadeThenHides() {
        val f=VtoDisplayFollower();val target=face();f.sample(target,0L)
        f.sample(null,10_000_000L)
        assertEquals(1f,f.sample(null,259_000_000L).opacity,0f)
        val fading=f.sample(null,310_000_000L)
        assertSame(target,fading.face);assertEquals(.5f,fading.opacity,.001f)
        val hidden=f.sample(null,360_000_000L)
        assertNull(hidden.face);assertEquals(0f,hidden.opacity,0f)
    }
    @Test fun reacquireAfterGraceStartsFreshEvenDuringFade() {
        val f=VtoDisplayFollower();f.sample(face(),0L);f.sample(null,10_000_000L)
        f.sample(null,310_000_000L)
        val next=face(.1f)
        assertSame(next,f.sample(next,320_000_000L).face)
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
        val target=face(.03f)
        assertSame(target,f.sample(target,1_000_000_000L).face)
    }
    @Test fun invalidCoordinatesAreNeverDisplayed() {
        val f=VtoDisplayFollower()
        val invalid=face().copy(landmarks=List(478) { FaceAnalyzer.LandmarkPoint(Float.NaN,0f) })
        assertNull(f.sample(invalid,0L).face)
    }
    @Test fun alternatingTinyMeasurementsAreDampedWithoutPrediction() {
        val f=VtoDisplayFollower();f.sample(face(),0L)
        var sum=0f;var count=0
        for(i in 1..30) {
            val target=face(if(i%2==0) .001f else -.001f)
            for(frame in 0..2) {
                val result=f.sample(target,(i*3L+frame)*16_000_000L).face!!
                if(i>5) { sum+=kotlin.math.abs(result.landmarks[33].x-.4f);count++ }
            }
        }
        assertTrue(sum/count<.0008f)
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
}
