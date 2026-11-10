package com.example.facefit

import android.graphics.Bitmap
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.example.facefit.ui.catalog.ModelPreviewScreen
import com.example.facefit.ui.catalog.sampleFrames
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion = 29)
class ModelPreviewTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun texturedModelRotatesResetsAndReopens() {
        val visible = mutableStateOf(true)
        val frame = sampleFrames.first { it.name == "Square Tortoise" }
        compose.setContent {
            if (visible.value) ModelPreviewScreen(frame) { visible.value = false }
        }
        fun waitForModel() {
            compose.waitUntil(40_000) {
                compose.onAllNodesWithText("Drag to rotate · Pinch to zoom")
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
        waitForModel()
        val front = snapshot("preview-front")
        val pixels = IntArray(front.width * front.height)
        front.getPixels(pixels, 0, front.width, 0, 0, front.width, front.height)
        val warmPixels = pixels.filter {
            val r = (it shr 16) and 255
            val g = (it shr 8) and 255
            val b = it and 255
            r > g * 1.12 && g > b * 1.05 && r > 40
        }
        assertTrue("The brown textured frame should be visible", warmPixels.size > 500)
        assertTrue("The material should have color variation",
            warmPixels.map { it and 0x00F0F0F0 }.toSet().size > 8)
        compose.onNodeWithTag("modelPreview").performTouchInput {
            swipe(Offset(center.x - 100f, center.y), Offset(center.x + 100f, center.y), 700)
        }
        // Wait for a rendered image change rather than assuming gestures update synchronously.
        compose.waitUntil(10_000) {
            val rotated = snapshot()
            val changed = !front.sameAs(rotated)
            rotated.recycle()
            changed
        }
        snapshot("preview-rotated").recycle()
        front.recycle()
        compose.onNodeWithText("Reset").performClick()
        waitForModel()
        compose.onNodeWithText("Back").performClick()
        compose.waitForIdle()
        compose.runOnIdle { visible.value = true }
        waitForModel()
        snapshot("preview-reopened").recycle()
    }

    private fun snapshot(name: String? = null): Bitmap {
        fun findTexture(view: View): TextureView? {
            if (view is TextureView) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) {
                findTexture(view.getChildAt(index))?.let { return it }
            }
            return null
        }
        var image: Bitmap? = null
        compose.runOnIdle {
            val texture = WindowInspector.getGlobalWindowViews()
                .firstNotNullOfOrNull { findTexture(it) }
            image = requireNotNull(texture).bitmap
        }
        return requireNotNull(image).also { bitmap ->
            if (name != null) {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }
}
