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
    @Test fun fastTranslationOpensTheFilterMoreThanSmallMotion() {
        fun response(dx: Float): Float {
            val filter = VtoLandmarkStabilizer()
            filter.sample(face(), 0L)
            val result = requireNotNull(filter.sample(face(dx), 33_000_000L))
            return (result.landmarks[33].x - .4f) / dx
        }
        assertTrue(response(.1f) > response(.001f))
    }

    @Test fun lossReacquisitionAndLongPauseResetImmediately() {
        val filter=VtoLandmarkStabilizer();filter.sample(face(),0L)
        assertNull(filter.sample(null,16_000_000L))
        val next=face(.001f);assertSame(next,filter.sample(next,32_000_000L))
        val paused=face(.002f);assertSame(paused,filter.sample(paused,500_000_000L))
    }
    @Test fun resizeResetsFilterInTheNewPixelSpace() {
        val filter = VtoLandmarkStabilizer()
        filter.sample(face(), 0L)
        val resized = face(.1f).copy(imageWidth = 500, imageHeight = 800)
        assertSame(resized, filter.sample(resized, 16_000_000L))
    }
}

