package com.example.facefit

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.facefit.ui.camera.VtoPreviewFaceTracker
import org.junit.Assert.*
import org.junit.Test

class VtoCoordinateMappingTest {
    @Test fun compareGpuAndCpuTrackingLatency() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = BitmapFactory.decodeStream(instrumentation.context.assets.open("vto/portrait.jpg"))
        for (backend in com.example.facefit.facefit.camera.TrackingBackend.values()) {
            try {
                VtoPreviewFaceTracker(instrumentation.targetContext, backend).use { tracker ->
                    val times = mutableListOf<Long>()
                    for (i in 0..7) {
                        val start = System.nanoTime()
                        val face = tracker.detect(bitmap, i * 100L)
                        assertTrue(face.isFaceDetected)
                        if (i >= 3) times.add((System.nanoTime() - start) / 1_000_000L)
                    }
                    android.util.Log.i("VtoBenchmark", "$backend medianMs=${times.sorted()[times.size / 2]}")
                }
            } catch (failure: Exception) {
                if (backend == com.example.facefit.facefit.camera.TrackingBackend.CPU) throw failure
                android.util.Log.w("VtoBenchmark", "GPU unavailable; CPU fallback required", failure)
            }
        }
        bitmap.recycle()
    }
    @Test fun displayedPreviewLandmarksRemainInTheVisibleImage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = BitmapFactory.decodeStream(instrumentation.context.assets.open("vto/portrait.jpg"))
        VtoPreviewFaceTracker(instrumentation.targetContext).use { tracker ->
            val result = tracker.detect(bitmap, 1L)
            assertTrue("A face must be found in the reference camera image", result.isFaceDetected)
            assertEquals(bitmap.width, result.imageWidth)
            assertEquals(bitmap.height, result.imageHeight)
            val first = result.landmarks[33]
            val second = result.landmarks[263]
            assertTrue(first.x in 0f..1f && second.x in 0f..1f)
            assertTrue(first.y in 0f..1f && second.y in 0f..1f)
            // The two eyes in this frontal portrait must be in the same horizontal band.
            assertTrue(kotlin.math.abs(first.y - second.y) < .1f)
            assertTrue(kotlin.math.abs(first.x - second.x) in .05f.. .5f)
        }
        bitmap.recycle()
    }
}