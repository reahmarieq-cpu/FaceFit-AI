package com.example.facefit

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.facefit.ui.catalog.sampleFrames
import org.junit.Assert.*
import org.junit.Test

class FrameCatalogAssetsTest {
    @Test fun catalogPathsResolveAndPngTryOnUsesTheActualImage() {
        val assets=InstrumentationRegistry.getInstrumentation().targetContext.assets
        for(frame in sampleFrames) {
            for(path in listOfNotNull(frame.assetPath,frame.tryOnAssetPath,frame.thumbnailAssetPath,frame.templeAssetPath,frame.leftArmAssetPath,frame.rightArmAssetPath).distinct()) {
                assets.open(path).use { assertTrue("Empty asset: $path",it.read()!=-1) }
            }
            if(frame.tryOnAssetPath!=null && !frame.tryOnAssetPath.endsWith(".glb")) {
                assertFalse("PNG frames must use image try-on",frame.has3DModel)
                val image=assets.open(frame.tryOnAssetPath).use { BitmapFactory.decodeStream(it) }
                assertNotNull(image);assertTrue(image.hasAlpha());image.recycle()
                assertTrue(frame.lensAnchors.leftX<frame.lensAnchors.rightX)
                assertEquals(frame.lensAnchors.leftY,frame.lensAnchors.rightY,0f)
                for(arm in listOfNotNull(frame.leftArmAssetPath,frame.rightArmAssetPath)) {
                    val layer=assets.open(arm).use { BitmapFactory.decodeStream(it) }
                    assertNotNull("Arm must decode as PNG despite extensionless filename: $arm",layer)
                    assertTrue(layer.hasAlpha());layer.recycle()
                }
            }
        }
    }
    @Test fun catalogHasTwoTwoDFramesPerStyle() {
        assertTrue(sampleFrames.none { it.has3DModel })
        sampleFrames.groupBy { it.category }.forEach { (style, frames) ->
            assertEquals(style, 2, frames.size)
        }
    }
}
