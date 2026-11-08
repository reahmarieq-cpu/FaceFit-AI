package com.example.facefit

import android.graphics.Bitmap
import android.graphics.Color
import com.example.facefit.logic.withFrameImage
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import org.junit.Assert.*
import org.junit.Test

class FrameImageScopeTest {
    @Test fun ownedBitmapStaysAliveUntilFrameProcessingCompletes() {
        val bitmap = Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(80, 120, 160))
        try {
            withFrameImage(bitmap) { image ->
                assertSame(bitmap, BitmapExtractor.extract(image))
                assertFalse(bitmap.isRecycled)
                assertEquals(Color.rgb(80, 120, 160), bitmap.getPixel(10, 10))
            }
            assertTrue(bitmap.isRecycled)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    @Test fun reusableVtoImageIsNotClosedAfterAFrame() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val image = BitmapImageBuilder(bitmap).build()
        try {
            repeat(2) {
                withFrameImage(bitmap, image) { frame -> assertSame(image, frame) }
                assertFalse(bitmap.isRecycled)
            }
        } finally {
            image.close()
        }
        assertTrue(bitmap.isRecycled)
    }

    @Test fun failedFrameStillReleasesOwnedPixels() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            withFrameImage(bitmap) { throw IllegalStateException("Simulated consumer failure") }
            fail("Expected consumer failure")
        } catch (_: IllegalStateException) {
            assertTrue(bitmap.isRecycled)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}
