package com.example.facefit

import android.graphics.Bitmap
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.view.WindowManager
import androidx.test.filters.SdkSuppress
import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.ui.tryon.rendering.FilamentGlassesOverlay
import com.example.facefit.ui.tryon.tracking.VtoTrackingFeed
import com.example.facefit.ui.tryon.rendering.Glasses3DState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion = 29)
class FilamentVtoTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun glbRendersOverTransparencyAndClearsWhenFaceIsLost() = verifyRendering(false)
    @Test fun directFeedMovesAndHidesGlassesWithoutComposeStateUpdates() = verifyRendering(true)

    private fun verifyRendering(direct: Boolean) {
        compose.runOnUiThread {
            compose.activity.setTurnScreenOn(true)
            compose.activity.setShowWhenLocked(true)
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        val points = MutableList(478) { FaceAnalyzer.LandmarkPoint(0.5f, 0.4f, 0f) }
        points[33] = FaceAnalyzer.LandmarkPoint(0.35f, 0.4f)
        points[133] = FaceAnalyzer.LandmarkPoint(0.40f, 0.4f)
        points[263] = FaceAnalyzer.LandmarkPoint(0.65f, 0.4f)
        points[362] = FaceAnalyzer.LandmarkPoint(0.60f, 0.4f)
        points[152] = FaceAnalyzer.LandmarkPoint(0.5f, 0.7f)
        points[10] = FaceAnalyzer.LandmarkPoint(0.5f, 0.2f)
        points[168] = FaceAnalyzer.LandmarkPoint(0.5f, 0.4f, -0.05f)
        val state = mutableStateOf(Glasses3DState(
            FaceAnalyzer.FaceMetrics(0f, 0f, 0f, "Oval", points, 800, 1000, true)))
        val feed=if(direct) VtoTrackingFeed().apply { publish(state.value.metrics) } else null
        val errors = mutableListOf<String>()
        compose.setContent {
            FilamentGlassesOverlay("frames/square_tortoise/SquareTortoise.glb", state.value, {}, { errors.add(it) },trackingFeed=feed)
        }
        compose.waitUntil(30_000) {
            assertTrue(errors.joinToString(), errors.isEmpty())
            snapshot()?.let { image ->
                val pixels = pixels(image)
                image.recycle()
                pixels.count { (it ushr 24) > 200 } > 500 &&
                    pixels.count { (it ushr 24) == 0 } > pixels.size / 2 &&
                    pixels.count { ((it shr 16) and 255) > ((it and 255) * 1.2) } > 200
            } ?: false
        }
        val initial = requireNotNull(snapshot())
        val initialCenter = frameCenter(initial)
        assertEquals("Rendered frame must be horizontally centered on the eyes", .5f, initialCenter.first, .08f)
        assertEquals("Rendered frame must be at eye height", .4f, initialCenter.second, .08f)
        initial.recycle()
        compose.runOnIdle {
            val face = requireNotNull(state.value.metrics)
            val moved=face.copy(landmarks=face.landmarks.map { it.copy(x=it.x+.1f,y=it.y+.2f) })
            if(feed!=null) feed.publish(moved) else state.value=state.value.copy(metrics=moved)
        }
        compose.waitUntil(10_000) {
            snapshot()?.let { image ->
                val moved = frameCenter(image)
                image.recycle()
                kotlin.math.abs(moved.first - initialCenter.first - .1f) < .02f &&
                    kotlin.math.abs(moved.second - initialCenter.second - .2f) < .02f
            } ?: false
        }
        compose.runOnIdle { if(feed!=null) feed.publish(null) else state.value = Glasses3DState(null) }
        compose.waitUntil(10_000) {
            snapshot()?.let { image ->
                val alpha = pixels(image).maxOf { it ushr 24 }
                android.util.Log.d("VtoPixelTest", "Cleared maximum alpha=" + alpha)
                val clear = alpha <= 2
                image.recycle()
                clear
            } ?: false
        }
        assertTrue(errors.joinToString(), errors.isEmpty())
    }

    private fun frameCenter(image: Bitmap): Pair<Float, Float> {
        val data = pixels(image)
        var left = image.width; var right = -1
        var top = image.height; var bottom = -1
        data.forEachIndexed { index, color ->
            if ((color ushr 24) > 200) {
                val x = index % image.width; val y = index / image.width
                left = minOf(left, x); right = maxOf(right, x)
                top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        check(right >= left && bottom >= top) { "No rendered frame pixels" }
        return (left + right) / (2f * image.width) to (top + bottom) / (2f * image.height)
    }
    private fun pixels(image: Bitmap) = IntArray(image.width * image.height).also {
        image.getPixels(it, 0, image.width, 0, 0, image.width, image.height)
    }

    private fun snapshot(): Bitmap? {
        fun find(view: View): TextureView? {
            if (view is TextureView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) {
                find(view.getChildAt(i))?.let { return it }
            }
            return null
        }
        var image: Bitmap? = null
        compose.runOnIdle {
            image = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { find(it) }?.bitmap
        }
        return image
    }
}
