package com.facefit.kiosk.vto

import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer
import com.facefit.kiosk.vto.facefit.tryon.tracking.VtoLandmarkStabilizer

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class VtoLandmarkStabilizerTest {
    private fun face(dx: Float=0f)=FaceAnalyzer.FaceMetrics(0f,0f,0f,"",
        List(478) { i -> FaceAnalyzer.LandmarkPoint((if(i==33 || i==133) .4f else .6f)+dx,.4f) },1000,1600,true)
    @Test fun alternatingNoiseIsReducedAtFastAndSlowDetectionRates() {
        for(interval in listOf(16_000_000L,100_000_000L)) {
            val filter=VtoLandmarkStabilizer();filter.sample(face(),0L)
            var raw=0f;var filtered=0f
            for(i in 1..30) {
                val dx=if(i%2==0) .001f else -.001f
                val result=requireNotNull(filter.sample(face(dx),i*interval))
                raw+=abs(dx);filtered+=abs(result.landmarks[33].x-.4f)
            }
            assertTrue(filtered<raw*.5f)
        }
    }
    @Test fun deliberateTranslationIsAppliedWithoutAnExtraInterpolationFrame() {
        val filter=VtoLandmarkStabilizer();filter.sample(face(),0L)
        val target=face(.03f)
        assertSame(target,filter.sample(target,16_000_000L))
    }
    @Test fun rollAndYawRespondEvenWhenTheEyeMidpointDoesNotMove() {
        for(depth in listOf(false,true)) {
            val filter=VtoLandmarkStabilizer();val initial=face();filter.sample(initial,0L)
            val target=initial.copy(landmarks=initial.landmarks.mapIndexed { i,p ->
                when(i) {
                    33,133 -> if(depth) p.copy(z=-.02f) else p.copy(y=.38f)
                    263,362 -> if(depth) p.copy(z=.02f) else p.copy(y=.42f)
                    else -> p
                }
            })
            assertSame(target,filter.sample(target,16_000_000L))
        }
    }
    @Test fun slowMovementCannotAccumulateLargePositionLag() {
        val filter=VtoLandmarkStabilizer();filter.sample(face(),0L)
        for(i in 1..50) {
            val target=face(i*.001f)
            val result=requireNotNull(filter.sample(target,i*33_000_000L))
            assertTrue(abs(target.landmarks[33].x-result.landmarks[33].x)<=.002401f)
        }
    }
    @Test fun lossReacquisitionAndLongPauseResetImmediately() {
        val filter=VtoLandmarkStabilizer();filter.sample(face(),0L)
        assertNull(filter.sample(null,16_000_000L))
        val next=face(.001f);assertSame(next,filter.sample(next,32_000_000L))
        val paused=face(.002f);assertSame(paused,filter.sample(paused,500_000_000L))
    }
    @Test fun proportionalResizeHasTheSameNormalizedFiltering() {
        val a=VtoLandmarkStabilizer();val b=VtoLandmarkStabilizer()
        a.sample(face(),0L);b.sample(face().copy(imageWidth=500,imageHeight=800),0L)
        val x=a.sample(face(.001f),16_000_000L)!!
        val y=b.sample(face(.001f).copy(imageWidth=500,imageHeight=800),16_000_000L)!!
        assertEquals(x.landmarks,y.landmarks)
    }
}
