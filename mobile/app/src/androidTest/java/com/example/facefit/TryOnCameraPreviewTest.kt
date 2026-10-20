package com.example.facefit

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.facefit.ui.components.TryOnCameraPreview
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TryOnCameraPreviewTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cameraStartsAndCanReopenWithoutDuplicateUseCases() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation.executeShellCommand("pm grant $packageName android.permission.CAMERA").close()
        val visible = mutableStateOf(true)
        val results = AtomicInteger()
        val errors = CopyOnWriteArrayList<String>()
        compose.setContent {
            if (visible.value) {
                TryOnCameraPreview(onResult = { results.incrementAndGet() }, onError = { errors.add(it) })
            }
        }
        compose.waitUntil(20_000) { results.get() > 0 || errors.isNotEmpty() }
        assertTrue(errors.joinToString(), errors.isEmpty())

        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        val previousResults = results.get()
        compose.runOnIdle { visible.value = true }
        compose.waitUntil(20_000) { results.get() > previousResults || errors.isNotEmpty() }
        assertTrue(errors.joinToString(), errors.isEmpty())
    }
}
