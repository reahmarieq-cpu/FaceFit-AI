package com.facefit.kiosk

import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.facefit.kiosk.data.frames.MockFrameCatalog
import com.facefit.kiosk.data.auth.MockShopAuthRepository
import com.facefit.kiosk.model.FaceScanResult
import com.facefit.kiosk.model.FaceShape
import com.facefit.kiosk.navigation.KioskDestination
import com.facefit.kiosk.vto.ui.camera.VtoLightFaceMesh
import com.facefit.kiosk.vto.ui.catalog.sampleFrames
import com.facefit.kiosk.vto.ui.tryon.rendering.resolveGlbFitAnchors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KioskVtoIntegrationTest {
    @Test fun packagedMobileLightTrackerOpens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        VtoLightFaceMesh.open(context).use { assertFalse(it.tracking) }
    }

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

    @Test fun bundledGlbProvidesLensAndHingeFitMarkers() {
        val test = InstrumentationRegistry.getInstrumentation()
        val bytes = test.targetContext.assets.open("frames/aviator/model.glb").use { it.readBytes() }
        test.runOnMainSync {
            Utils.init()
            val engine = Engine.create()
            val materials = UbershaderProvider(engine)
            val loader = AssetLoader(engine, materials, EntityManager.get())
            try {
                val buffer = ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); flip() }
                val asset = requireNotNull(loader.createAsset(buffer))
                try {
                    val anchors = requireNotNull(resolveGlbFitAnchors(asset, engine.transformManager))
                    assertEquals(.073635f, anchors.span, .00001f)
                    assertEquals(.135f, requireNotNull(anchors.hingeSpan), .00001f)
                } finally {
                    loader.destroyAsset(asset)
                }
            } finally {
                loader.destroy()
                materials.destroyMaterials()
                materials.destroy()
                engine.destroy()
            }
        }
    }

    @Test fun selectedFrameOpensTryOnAndBackPreservesSelection() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val state = ViewModelProvider(activity)[KioskStateViewModel::class.java]
                state.demoMode = true
                state.sessionStore.activeShop = MockShopAuthRepository.demoShop
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
