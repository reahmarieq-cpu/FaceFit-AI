package com.example.facefit

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.facefit.facefit.tryon.geometry.VtoAutomaticFit
import com.example.facefit.ui.catalog.sampleFrames
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
    @Test fun newGlbCalibrationsMatchTheExportedLensAnchors() {
        val assets=InstrumentationRegistry.getInstrumentation().targetContext.assets
        for(path in listOf("frames/cat-eye/model.glb","frames/Round/model.glb")) {
            val bytes=assets.open(path).use { it.readBytes() }
            val buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(0x46546c67,buffer.getInt(0))
            val json=JSONObject(String(bytes,20,buffer.getInt(12),Charsets.UTF_8))
            val nodes=json.getJSONArray("nodes")
            val expected=requireNotNull(VtoAutomaticFit.forAsset(path))
            for(i in 0 until nodes.length()) {
                val node=nodes.getJSONObject(i)
                val anchor=when(node.optString("name")) {
                    "anchor_lens_image_left" -> expected.firstLens
                    "anchor_lens_image_right" -> expected.secondLens
                    else -> continue
                }
                val xyz=node.getJSONArray("translation")
                assertEquals(xyz.getDouble(0).toFloat(),anchor.x,.000001f)
                assertEquals(xyz.getDouble(1).toFloat(),anchor.y,.000001f)
                assertEquals(xyz.getDouble(2).toFloat(),anchor.z,.000001f)
            }
            assertTrue(expected.isValid())
        }
    }
}
