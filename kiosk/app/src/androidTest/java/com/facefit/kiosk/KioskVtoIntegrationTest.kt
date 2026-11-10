package com.facefit.kiosk

import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.facefit.kiosk.data.frames.MockFrameCatalog
import com.facefit.kiosk.model.FaceScanResult
import com.facefit.kiosk.model.FaceShape
import com.facefit.kiosk.navigation.KioskDestination
import com.facefit.kiosk.vto.ui.catalog.sampleFrames
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KioskVtoIntegrationTest {
    @Test fun packagedTryOnAssetsExist() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        for (frame in sampleFrames) {
            listOfNotNull(frame.assetPath, frame.tryOnAssetPath, frame.templeAssetPath,
                frame.leftArmAssetPath, frame.rightArmAssetPath, frame.thumbnailAssetPath)
                .distinct().forEach { path -> assets.open(path).use { assertTrue(path, it.read() >= 0) } }
        }
        assets.open("vto/face_triangles.bin").use { assertTrue(it.available() > 0) }
        assets.open("face_landmarker.task").use { assertTrue(it.available() > 0) }
    }

    @Test fun selectedFrameOpensTryOnAndBackPreservesSelection() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val state = ViewModelProvider(activity)[KioskStateViewModel::class.java]
                val profile = FaceScanResult(FaceShape.Round)
                state.sessionStore.analysisResult = profile
                state.sessionStore.selectedRecommendation = MockFrameCatalog().recommendationsFor(profile, "demo-shop").first()
                activity.navigateTo(KioskDestination.FrameDetails)
                assertTrue(activity.findViewById<View>(R.id.details_try_on).isEnabled)
                activity.findViewById<View>(R.id.details_try_on).performClick()
                assertEquals(KioskDestination.VirtualTryOn, state.destination)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
                val state = ViewModelProvider(activity)[KioskStateViewModel::class.java]
                assertEquals(KioskDestination.FrameDetails, state.destination)
                assertEquals("Pax", state.sessionStore.selectedRecommendation?.frame?.name)
                val profile = state.sessionStore.analysisResult!!
                state.sessionStore.selectedRecommendation = MockFrameCatalog().recommendationsFor(profile, "demo-shop").first { it.frame.name == "Milo" }
                activity.navigateTo(KioskDestination.FrameDetails)
                assertFalse(activity.findViewById<View>(R.id.details_try_on).isEnabled)
            }
        }
    }
}
