package com.facefit.kiosk.vto

import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer
import com.facefit.kiosk.vto.facefit.tryon.geometry.GlbFaceTransform
import com.facefit.kiosk.vto.facefit.tryon.geometry.VtoAutomaticFit
import com.facefit.kiosk.vto.facefit.tryon.geometry.VtoHeadOcclusionGeometry

import org.junit.Assert.*
import org.junit.Test

class VtoAutomaticFitTest {
    private fun face(zoom: Float): FaceAnalyzer.FaceMetrics {
        fun p(x: Float,y: Float)=FaceAnalyzer.LandmarkPoint(.5f+(x-.5f)*zoom,.4f+(y-.4f)*zoom)
        val points=MutableList(478) { p(.5f,.4f) }
        points[33]=p(.35f,.4f); points[133]=p(.4f,.4f)
        points[263]=p(.65f,.4f); points[362]=p(.6f,.4f)
        points[10]=p(.5f,.2f); points[152]=p(.5f,.7f); points[168]=p(.5f,.4f)
        return FaceAnalyzer.FaceMetrics(0f,0f,0f,"",points,1000,1000,true)
    }
    @Test fun calibratedLensCentersFollowEyesAtDifferentDistances() {
        val anchors=VtoAutomaticFit.squareTortoise
        for(zoom in listOf(.6f,1f,1.3f)) {
            val m=requireNotNull(GlbFaceTransform.matrix(face(zoom),1000,1000,
                floatArrayOf(0f,-11.5f,-73.5f),floatArrayOf(70f,24f,76.25f),1f,0f,anchors))
            for((point,expectedX) in listOf(anchors.firstLens to (500f-125f*zoom),anchors.secondLens to (500f+125f*zoom))) {
                val x=m[0]*point.x+m[4]*point.y+m[8]*point.z+m[12]
                val y=m[1]*point.x+m[5]*point.y+m[9]*point.z+m[13]
                assertEquals(expectedX,x,.01f); assertEquals(600f,y,.01f)
            }
        }
    }
    @Test fun wideFacePlacesFrameEdgesOutsideTheTemples() {
        val base=face(1f)
        val points=base.landmarks.toMutableList()
        points[127]=FaceAnalyzer.LandmarkPoint(.20f,.4f)
        points[356]=FaceAnalyzer.LandmarkPoint(.80f,.4f)
        val m=requireNotNull(GlbFaceTransform.matrix(base.copy(landmarks=points),1000,1000,
            floatArrayOf(0f,-11.5f,-73.5f),floatArrayOf(70f,24f,76.25f),1f,0f,VtoAutomaticFit.squareTortoise))
        assertEquals(618f,140f*m[0],.01f)
        val c=VtoAutomaticFit.squareTortoise.center
        assertEquals(500f,m[0]*c.x+m[4]*c.y+m[8]*c.z+m[12],.01f)
        assertEquals(600f,m[1]*c.x+m[5]*c.y+m[9]*c.z+m[13],.01f)
    }
    @Test fun unknownAssetsDoNotReceiveSquareTortoiseCalibration() {
        assertNull(VtoAutomaticFit.forAsset("other.glb"))
        assertTrue(VtoAutomaticFit.squareTortoise.isValid())
    }
    @Test fun skullClosesBehindTheTrackedFace() {
        val p=requireNotNull(VtoHeadOcclusionGeometry.positions(face(1f),1000,1000))
        assertEquals(544*3,p.size)
        assertTrue(p.all { it.isFinite() })
        assertTrue(p[540*3+2] < p[10*3+2])
        val indices=java.nio.ByteBuffer.wrap(VtoHeadOcclusionGeometry.indices(byteArrayOf()))
            .order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        while(indices.hasRemaining()) assertTrue((indices.get().toInt() and 65535) < 544)
    }
    @Test fun rearMaskDoesNotNarrowAndExposeHiddenTempleTips() {
        val base=face(1f)
        val points=base.landmarks.toMutableList()
        val oval=VtoHeadOcclusionGeometry.oval
        for(i in oval.indices) {
            val a=2.0*Math.PI*i/oval.size
            points[oval[i]]=FaceAnalyzer.LandmarkPoint(.5f+.28f*kotlin.math.sin(a).toFloat(),.45f-.3f*kotlin.math.cos(a).toFloat())
        }
        points[234]=FaceAnalyzer.LandmarkPoint(.22f,.45f)
        points[454]=FaceAnalyzer.LandmarkPoint(.78f,.45f)
        val vertices=requireNotNull(VtoHeadOcclusionGeometry.positions(base.copy(landmarks=points),1000,1000))
        val frontX=oval.map { vertices[it*3] }
        val rearX=(504..539).map { vertices[it*3] }
        assertTrue(rearX.max()-rearX.min() >= frontX.max()-frontX.min())
        assertTrue(vertices[540*3+2] < -2700f)
    }
    @Test fun missingFaceDoesNotProduceAnOccluder() {
        assertNull(VtoHeadOcclusionGeometry.positions(null,1000,1000))
        assertNull(VtoHeadOcclusionGeometry.positions(face(1f).copy(isFaceDetected=false),1000,1000))
    }
}
