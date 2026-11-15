package com.example.facefit

import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.tryon.tracking.VtoLandmarkStabilizer
import org.junit.Assert.*
import org.junit.Test

class VtoLandmarkStabilizerTest {
    private fun face()=FaceAnalyzer.FaceMetrics(0f,0f,0f,"",List(478) { FaceAnalyzer.LandmarkPoint(.5f,.4f) },1000,1600,true)
    @Test fun validMeasurementsPassThroughWithoutTemporalLag() {
        val filter=VtoLandmarkStabilizer()
        val first=face();assertSame(first,filter.sample(first,0L))
        val next=face().copy(landmarks=face().landmarks.map { it.copy(x=it.x+.001f) })
        assertSame(next,filter.sample(next,16_000_000L))
    }
    @Test fun nullAndLostFacesArePassedToDisplayGracePolicy() {
        val filter=VtoLandmarkStabilizer();assertNull(filter.sample(null,0L))
        val lost=face().copy(isFaceDetected=false)
        assertSame(lost,filter.sample(lost,16_000_000L))
    }
    @Test fun nonfiniteCoordinatesBecomeLostFace() {
        val filter=VtoLandmarkStabilizer()
        for(value in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY)) {
            val invalid=face().copy(landmarks=face().landmarks.map { it.copy(z=value) })
            val result=filter.sample(invalid,0L)!!
            assertFalse(result.isFaceDetected);assertTrue(result.landmarks.isEmpty())
        }
    }
    @Test fun badDimensionsAndTruncatedLandmarksAreRejected() {
        val filter=VtoLandmarkStabilizer()
        for(invalid in listOf(face().copy(imageWidth=0),face().copy(imageHeight=-1),face().copy(landmarks=emptyList()))) {
            assertFalse(filter.sample(invalid,0L)!!.isFaceDetected)
        }
    }
}
