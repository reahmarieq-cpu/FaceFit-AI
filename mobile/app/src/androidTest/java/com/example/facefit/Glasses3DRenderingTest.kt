package com.example.facefit

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.GLES20
import androidx.test.platform.app.InstrumentationRegistry
import com.example.facefit.logic.FaceAnalyzer
import com.example.facefit.ui.components.Glasses3DRenderer
import com.example.facefit.ui.components.Glasses3DState
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class Glasses3DRenderingTest {
    @Test
    fun actualGpuRendersFrontAndBothHeadTurns() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        assertTrue(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0))
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val attributes = intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 16,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE)
        assertTrue(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, IntArray(1), 0))
        val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        val surface = EGL14.eglCreatePbufferSurface(display, configs[0],
            intArrayOf(EGL14.EGL_WIDTH, 512, EGL14.EGL_HEIGHT, 512, EGL14.EGL_NONE), 0)
        assertTrue(EGL14.eglMakeCurrent(display, surface, surface, context))
        try {
            val errors = mutableListOf<String>()
            val renderer = Glasses3DRenderer(true) { errors.add(it) }
            renderer.onSurfaceCreated(null, null)
            renderer.onSurfaceChanged(null, 512, 512)
            fun render(angle: Float, name: String): ByteArray {
                renderer.state = Glasses3DState(face(angle))
                renderer.onDrawFrame(null)
                GLES20.glFinish()
                val pixels = ByteBuffer.allocateDirect(512 * 512 * 4)
                GLES20.glReadPixels(0, 0, 512, 512, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
                val bytes = ByteArray(pixels.capacity())
                pixels.position(0); pixels.get(bytes)
                assertTrue(errors.joinToString(), errors.isEmpty())
                assertTrue("Blank 3D scene at $angle degrees", bytes.indices.count { it % 4 == 3 && bytes[it].toInt() != 0 } > 800)
                assertTrue("Clear lenses must remain translucent", (0 until 512 * 512).count {
                    (bytes[it * 4 + 3].toInt() and 255) in 1..40
                } > 200)
                val argb = IntArray(512 * 512) { i ->
                    val source = ((511 - i / 512) * 512 + i % 512) * 4
                    ((bytes[source + 3].toInt() and 255) shl 24) or
                        ((bytes[source].toInt() and 255) shl 16) or
                        ((bytes[source + 1].toInt() and 255) shl 8) or (bytes[source + 2].toInt() and 255)
                }
                val bitmap = Bitmap.createBitmap(argb, 512, 512, Bitmap.Config.ARGB_8888)
                val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
                File(directory, "vto3d-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                return bytes
            }
            val front = render(0f, "front")
            val left = render(-45f, "left")
            val right = render(45f, "right")
            assertFalse(front.contentEquals(left))
            assertFalse(front.contentEquals(right))
            assertFalse(left.contentEquals(right))
            renderer.state = Glasses3DState(null)
            renderer.onDrawFrame(null)
            val clear = ByteBuffer.allocateDirect(512 * 512 * 4)
            GLES20.glReadPixels(0, 0, 512, 512, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, clear)
            assertEquals(0, (0 until 512 * 512).count { clear.get(it * 4 + 3).toInt() != 0 })
            assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError())
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }

    private fun face(angle: Float): FaceAnalyzer.FaceMetrics {
        val radians = angle * PI.toFloat() / 180
        fun project(x: Float, y: Float, z: Float) = FaceAnalyzer.LandmarkPoint(
            0.5f + (cos(radians) * x + sin(radians) * z) * 0.30f,
            0.44f + y * 0.30f, (-sin(radians) * x + cos(radians) * z) * 0.30f)
        val points = MutableList(478) { FaceAnalyzer.LandmarkPoint(0.5f, 0.5f, 0.8f) }
        points[33] = project(-0.62f, 0f, 0f)
        points[133] = project(-0.38f, 0f, 0f)
        points[263] = project(0.62f, 0f, 0f)
        points[362] = project(0.38f, 0f, 0f)
        points[152] = project(0f, 1.5f, 0f)
        points[10] = project(0f, -0.8f, 0f)
        points[168] = project(0f, 0.05f, -0.2f)
        return FaceAnalyzer.FaceMetrics(0f, 0f, 0f, "Oval", points, 512, 512, true)
    }
}
