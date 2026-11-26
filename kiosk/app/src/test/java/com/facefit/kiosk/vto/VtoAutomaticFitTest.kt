package com.facefit.kiosk.vto

import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer
import com.facefit.kiosk.vto.facefit.tryon.geometry.GlbFaceTransform
import com.facefit.kiosk.vto.facefit.tryon.geometry.VtoAutomaticFit
import com.facefit.kiosk.vto.facefit.tryon.geometry.Vector3
import com.facefit.kiosk.vto.facefit.tryon.geometry.VtoHeadOcclusionGeometry

import com.facefit.kiosk.vto.facefit.tryon.tracking.VtoHeadPose

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
    @Test fun calibratedBridgeAndLensSpacingScaleAtDifferentDistances() {
        val anchors = VtoAutomaticFit.squareTortoise
        assertEquals(Vector3(0f, -3.715f, -2.75f), anchors.center)
        for (zoom in listOf(.6f, 1f, 1.3f)) {
            val m = requireNotNull(GlbFaceTransform.matrix(face(zoom), 1000, 1000,
                floatArrayOf(0f, -11.5f, -73.5f), floatArrayOf(70f, 24f, 76.25f), 1f, 0f, anchors))
            val saddle = anchors.center
            assertEquals(500f, m[0] * saddle.x + m[4] * saddle.y + m[8] * saddle.z + m[12], .01f)
            assertEquals(600f,
                m[1] * saddle.x + m[5] * saddle.y + m[9] * saddle.z + m[13], .01f)
            assertEquals(250f * zoom, m[0] * (anchors.secondLens.x - anchors.firstLens.x), .01f)
        }
    }

    @Test fun wideFacePlacesFrameEdgesOutsideTheTemples() {
        val base=face(1f)
        val points=base.landmarks.toMutableList()
        points[127]=FaceAnalyzer.LandmarkPoint(.20f,.4f)
        points[356]=FaceAnalyzer.LandmarkPoint(.80f,.4f)
        val m=requireNotNull(GlbFaceTransform.matrix(base.copy(landmarks=points),1000,1000,
            floatArrayOf(0f,-11.5f,-73.5f),floatArrayOf(70f,24f,76.25f),1f,0f,VtoAutomaticFit.squareTortoise))
        assertEquals(612f,140f*m[0],.01f)
        val c=VtoAutomaticFit.squareTortoise.center
        assertEquals(500f,m[0]*c.x+m[4]*c.y+m[8]*c.z+m[12],.01f)
        assertEquals(600f,m[1]*c.x+m[5]*c.y+m[9]*c.z+m[13],.01f)
    }
    @Test fun uncalibratedGlbUsesCheekWidthAtDifferentDistances() {
        val center = floatArrayOf(0f, -11.5f, -73.5f)
        val halfExtent = floatArrayOf(70f, 24f, 76.25f)
        for (zoom in listOf(.6f, 1f, 1.3f)) {
            val base = face(zoom)
            val points = base.landmarks.toMutableList()
            points[234] = FaceAnalyzer.LandmarkPoint(.5f - .3f * zoom, .45f)
            points[454] = FaceAnalyzer.LandmarkPoint(.5f + .3f * zoom, .45f)
            val transform = requireNotNull(GlbFaceTransform.matrix(
                base.copy(landmarks = points), 1000, 1000, center, halfExtent, 1f, 0f
            ))
            val modelWidth = halfExtent[0] * 2f * transform[0]
            assertEquals(612f * zoom, modelWidth, .01f)
            assertEquals(500f, transform[12], .01f)
        }
    }

    @Test fun hingeSpanFitsFrontEvenWhenModelBoundsAreWider() {
        val base = face(1f)
        val points = base.landmarks.toMutableList()
        points[127] = FaceAnalyzer.LandmarkPoint(.2f, .4f)
        points[356] = FaceAnalyzer.LandmarkPoint(.8f, .4f)
        val anchors = VtoAutomaticFit.ModelAnchors(Vector3(-32f, -8f, 0f), Vector3(48f, -8f, 0f))
            .withHinges(Vector3(-52f, -8f, 0f), Vector3(68f, -8f, 0f))
        val trackedFace = base.copy(landmarks = points)
        val rig = requireNotNull(VtoHeadPose.rig(trackedFace, 1000, 1000, 1f, 0f))
        val transform = requireNotNull(GlbFaceTransform.matrix(
            trackedFace, 1000, 1000,
            floatArrayOf(0f, 0f, 0f), floatArrayOf(110f, 30f, 60f), 1f, 0f, anchors, rig
        ))
        assertEquals(612f, 120f * transform[0], .01f)
        assertEquals(612f, 120f * Vector3(transform[4], transform[5], transform[6]).length(), .01f)
        assertEquals(500f, transform[0] * anchors.center.x + transform[12], .01f)
        for (yaw in listOf(-.20f, .20f)) {
            val turned = rig.copy(xAxis = Vector3(kotlin.math.sqrt(1f - yaw * yaw), 0f, yaw))
            val side = requireNotNull(GlbFaceTransform.matrix(
                trackedFace, 1000, 1000,
                floatArrayOf(0f, 0f, 0f), floatArrayOf(110f, 30f, 60f), 1f, 0f, anchors, turned
            ))
            assertEquals(660f, 120f * Vector3(side[0], side[1], side[2]).length(), .01f)
        }
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
    @Test fun turnedMaskInsetsOnlyTheCameraFacingSide() {
        val base = face(1f)
        val landmarks = base.landmarks.toMutableList()
        landmarks[234] = FaceAnalyzer.LandmarkPoint(.22f, .45f)
        landmarks[454] = FaceAnalyzer.LandmarkPoint(.78f, .45f)
        val tracked = base.copy(landmarks = landmarks)
        val rig = requireNotNull(VtoHeadPose.rig(tracked, 1000, 1000, 1f, 0f))
        for (yaw in listOf(-.15f, .15f)) {
            val right = Vector3(kotlin.math.sqrt(1f - yaw * yaw), 0f, yaw)
            val turned = rig.copy(xAxis = right, yAxis = Vector3(0f, 1f, 0f),
                zAxis = right.cross(Vector3(0f, 1f, 0f)))
            val vertices = requireNotNull(VtoHeadOcclusionGeometry.positions(tracked, 1000, 1000, turned))
            fun xFor(landmark: Int): Float =
                vertices[(504 + VtoHeadOcclusionGeometry.oval.indexOf(landmark)) * 3]
            val center = (xFor(234) + xFor(454)) / 2f
            val rearCenter = vertices[540 * 3]
            // The rear ring and rear cap differ in depth; recover the ring midpoint
            // before measuring each side's lateral extent.
            val ringCenter = rearCenter - turned.zAxis.x * (560f * right.x * .25f)
            val near = if (yaw > 0f) xFor(234) else xFor(454)
            val far = if (yaw > 0f) xFor(454) else xFor(234)
            assertTrue(kotlin.math.abs(near - ringCenter) < kotlin.math.abs(far - ringCenter))
            assertTrue(kotlin.math.abs(center - ringCenter) > 1f)
        }
    }

    @Test fun missingFaceDoesNotProduceAnOccluder() {
        assertNull(VtoHeadOcclusionGeometry.positions(null,1000,1000))
        assertNull(VtoHeadOcclusionGeometry.positions(face(1f).copy(isFaceDetected=false),1000,1000))
    }
}
