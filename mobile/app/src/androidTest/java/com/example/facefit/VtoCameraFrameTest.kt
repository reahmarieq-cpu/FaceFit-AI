package com.example.facefit

import android.graphics.Matrix
import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.ui.camera.VtoCameraFrame
import org.junit.Assert.*
import org.junit.Test

class VtoCameraFrameTest {
    private val face=FaceAnalyzer.FaceMetrics(0f,0f,0f,"",listOf(
        FaceAnalyzer.LandmarkPoint(.25f,.4f,-.1f)),480,640,true)
    @Test fun frontCameraMirrorAndCropMapOnce() {
        val transform=Matrix().apply { setScale(-2f,2f); postTranslate(960f,-100f) }
        val result=VtoCameraFrame.map(face,transform,960,1180,mirroredInput=true)
        val point=result.landmarks.single()
        assertEquals(.25f,point.x,.0001f)
        assertEquals((.4f*1280-100)/1180,point.y,.0001f)
        assertEquals(-.1f,point.z,.0001f)
    }
    @Test fun portraitFillCenterPreservesCropAndDepthScale() {
        val transform=Matrix().apply { setScale(3f,3f); postTranslate(-180f,0f) }
        val point=VtoCameraFrame.map(face,transform,1080,1920).landmarks.single()
        assertEquals((.25f*1440-180)/1080,point.x,.0001f)
        assertEquals(.4f,point.y,.0001f)
        assertEquals(-.1f*1440/1080,point.z,.0001f)
    }
}
