package com.example.facefit

import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.tryon.tracking.VtoHeadPose
import com.example.facefit.facefit.tryon.tracking.VtoRigResolver
import org.junit.Assert.*
import org.junit.Test

class VtoRigResolverTest {
    private fun face(bad: Boolean=false,dx: Float=0f): FaceAnalyzer.FaceMetrics {
        val points=MutableList(478) { FaceAnalyzer.LandmarkPoint(.4f,.4f) }
        VtoHeadPose.landmarkIndices.forEachIndexed { index,i ->
            val p=VtoHeadPose.reference[index]
            points[i]=FaceAnalyzer.LandmarkPoint((400f+p.x*20f)/1000f+dx,(600f+p.y*20f)/1600f,p.z*20f/1000f)
        }
        if(bad) points[152]=points[152].copy(x=.95f)
        return FaceAnalyzer.FaceMetrics(0f,0f,0f,"",points,1000,1600,true)
    }
    @Test fun isolatedFitFailureDoesNotSwitchOrientationMode() {
        val resolver=VtoRigResolver();resolver.resolve(face(),1000,1600,1f,0f,0L)
        assertEquals(1f,resolver.fitWeight,0f)
        val bad=face(true)
        assertNull(VtoHeadPose.candidates(bad,1000,1600,1f,0f)!!.fitted)
        repeat(10) { resolver.resolve(bad,1000,1600,1f,0f,(it+1)*16_000_000L,bad) }
        assertEquals(1f,resolver.fitWeight,0f)
    }
    @Test fun repeatedFailuresBlendInsteadOfPopping() {
        val resolver=VtoRigResolver();resolver.resolve(face(),1000,1600,1f,0f,0L)
        for(i in 1..3) resolver.resolve(face(true),1000,1600,1f,0f,i*16_000_000L)
        assertTrue(resolver.fitWeight>0f && resolver.fitWeight<1f)
        assertTrue(resolver.isTransitioning)
    }
    @Test fun holdingOrientationDoesNotFreezeBridgeTranslation() {
        val resolver=VtoRigResolver()
        val first=resolver.resolve(face(),1000,1600,1f,0f,0L)!!
        val moved=resolver.resolve(face(true,.02f),1000,1600,1f,0f,16_000_000L)!!
        assertEquals(first.origin.x+20f,moved.origin.x,.01f)
    }
    @Test fun blendedAxesStayRigidAndSharedRigIsWellFormed() {
        val resolver=VtoRigResolver();resolver.resolve(face(),1000,1600,1f,0f,0L)
        for(i in 1..20) {
            val rig=resolver.resolve(face(true),1000,1600,1f,0f,i*16_000_000L)!!
            assertEquals(1f,rig.xAxis.length(),.001f);assertEquals(1f,rig.yAxis.length(),.001f)
            assertEquals(1f,rig.zAxis.length(),.001f);assertEquals(0f,rig.xAxis.dot(rig.yAxis),.001f)
        }
    }
    @Test fun lossClearsTheModeAndReacquisitionStartsFresh() {
        val resolver=VtoRigResolver();resolver.resolve(face(),1000,1600,1f,0f,0L)
        assertNull(resolver.resolve(null,1000,1600,1f,0f,16_000_000L))
        resolver.resolve(face(true),1000,1600,1f,0f,32_000_000L)
        assertEquals(0f,resolver.fitWeight,0f)
    }
    @Test fun lostMeasurementDuringGraceCannotCountAsAnotherFitFailure() {
        val resolver=VtoRigResolver();resolver.resolve(face(),1000,1600,1f,0f,0L)
        resolver.resolve(face(true),1000,1600,1f,0f,16_000_000L)
        val held=face(true)
        resolver.resolve(held,1000,1600,1f,0f,32_000_000L)
        repeat(5) { resolver.resolve(held,1000,1600,1f,0f,(it+3)*16_000_000L,held.copy(isFaceDetected=false)) }
        assertEquals(1f,resolver.fitWeight,0f)
    }
}
