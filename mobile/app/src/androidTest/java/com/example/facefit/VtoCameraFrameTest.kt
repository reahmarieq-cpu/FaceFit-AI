package com.example.facefit

import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.Color
import androidx.camera.core.ImageProxy
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
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
    @Test fun sensorMappingIncludesMirrorExactlyOnce() {
        val sensorToBuffer = Matrix().apply { setScale(.5f, .5f) }
        val sensorToView = Matrix().apply { setScale(-2f, 2f); postTranslate(1920f, 0f) }
        val transform = requireNotNull(VtoCameraFrame.bufferToPreview(sensorToBuffer, sensorToView))
        val points = floatArrayOf(120f, 160f)
        transform.mapPoints(points)
        assertArrayEquals(floatArrayOf(1440f, 640f), points, .001f)
    }
    @Test fun rotatedSensorBufferMapsBackToTheSameSensorPoint() {
        for (degrees in listOf(0f, 90f, 180f, 270f)) {
            val sensorToBuffer = Matrix().apply { setRotate(degrees); postTranslate(400f, 600f) }
            val sensorToView = Matrix().apply { setScale(-2f, 2f); postTranslate(1080f, -90f) }
            val expected = floatArrayOf(75f, 130f)
            val actual = expected.copyOf()
            sensorToView.mapPoints(expected)
            sensorToBuffer.mapPoints(actual)
            requireNotNull(VtoCameraFrame.bufferToPreview(sensorToBuffer, sensorToView)).mapPoints(actual)
            assertArrayEquals(expected, actual, .001f)
        }
    }
    @Test fun singularSensorTransformIsRejected() {
        val singular = Matrix().apply { setScale(0f, 0f) }
        assertNull(VtoCameraFrame.bufferToPreview(singular, Matrix()))
    }
    @Test fun rgbaCopyHonorsBufferOffsetAndRowPadding() {
        val buffer = ByteBuffer.allocate(24)
        fun pixel(offset: Int, r: Int, g: Int, b: Int) {
            buffer.put(offset, r.toByte()); buffer.put(offset + 1, g.toByte())
            buffer.put(offset + 2, b.toByte()); buffer.put(offset + 3, 255.toByte())
        }
        pixel(4, 255, 0, 0); pixel(8, 0, 255, 0)
        pixel(16, 0, 0, 255); pixel(20, 255, 255, 0)
        buffer.position(4)
        val plane = Proxy.newProxyInstance(ImageProxy.PlaneProxy::class.java.classLoader,
            arrayOf(ImageProxy.PlaneProxy::class.java)) { _, method, _ ->
                when (method.name) {
                    "getBuffer" -> buffer
                    "getRowStride" -> 12
                    "getPixelStride" -> 4
                    else -> null
                }
            } as ImageProxy.PlaneProxy
        val image = Proxy.newProxyInstance(ImageProxy::class.java.classLoader,
            arrayOf(ImageProxy::class.java)) { _, method, _ ->
                when (method.name) {
                    "getWidth", "getHeight" -> 2
                    "getPlanes" -> arrayOf(plane)
                    else -> null
                }
            } as ImageProxy
        val bitmap = VtoCameraFrame.copyRgba(image)
        try {
            assertEquals(Color.RED, bitmap.getPixel(0, 0))
            assertEquals(Color.GREEN, bitmap.getPixel(1, 0))
            assertEquals(Color.BLUE, bitmap.getPixel(0, 1))
            assertEquals(Color.YELLOW, bitmap.getPixel(1, 1))
            assertEquals(4, buffer.position())
        } finally { bitmap.recycle() }
    }
    @Test fun mirroredViewportCropReturnsToBufferBeforeDisplayMirror() {
        val local=FaceAnalyzer.FaceMetrics(0f,0f,0f,"",listOf(FaceAnalyzer.LandmarkPoint(.25f,.4f,-.1f)),200,400,true)
        val restored=VtoCameraFrame.restoreCrop(local,640,480,Rect(220,40,420,440),true)
        assertEquals(370f/640,restored.landmarks.single().x,.00001f)
        assertEquals(200f/480,restored.landmarks.single().y,.00001f)
        assertEquals(-.1f*200/640,restored.landmarks.single().z,.00001f)
        val bufferToView=Matrix().apply { setScale(-2f,2f); postTranslate(840f,-80f) }
        val displayed=VtoCameraFrame.map(restored,bufferToView,400,800).landmarks.single()
        assertEquals(.25f,displayed.x,.00001f)
        assertEquals(.4f,displayed.y,.00001f)
        assertEquals(-.1f,displayed.z,.00001f)
    }
    @Test fun nativeRgbaBufferIsReusedAndBorrowed() {
        val bytes=ByteBuffer.allocate(16)
        for(i in 0..3) { bytes.put(i*4,255.toByte());bytes.put(i*4+3,255.toByte()) }
        val plane=Proxy.newProxyInstance(ImageProxy.PlaneProxy::class.java.classLoader,arrayOf(ImageProxy.PlaneProxy::class.java)) { _,method,_ ->
            when(method.name) { "getBuffer"->bytes; "getRowStride"->8; "getPixelStride"->4; else->null }
        } as ImageProxy.PlaneProxy
        val image=Proxy.newProxyInstance(ImageProxy::class.java.classLoader,arrayOf(ImageProxy::class.java)) { _,method,_ ->
            when(method.name) { "getWidth","getHeight"->2; "getPlanes"->arrayOf(plane); else->null }
        } as ImageProxy
        com.example.facefit.ui.camera.VtoRgbaBuffer().use { copier ->
            val first=copier.copy(image)
            assertEquals(Color.RED,first.getPixel(0,0))
            bytes.put(0,0);bytes.put(1,255.toByte())
            val second=copier.copy(image)
            assertSame(first,second);assertFalse(second.isRecycled)
            assertEquals(Color.GREEN,second.getPixel(0,0))
        }
    }
}
