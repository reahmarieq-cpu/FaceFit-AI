package com.facefit.kiosk.vto

import com.facefit.kiosk.vto.ui.tryon.FramePixelBounds
import com.facefit.kiosk.vto.ui.tryon.cleanRetailerFramePixels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetailerFrameImageTest {
    @Test fun cropsUnevenTransparentMargins() {
        val pixels = IntArray(24)
        pixels[8] = 0xff000000.toInt()
        pixels[15] = 0xff555555.toInt()
        assertEquals(FramePixelBounds(2, 1, 2, 2), cleanRetailerFramePixels(pixels, 6, 4))
    }

    @Test fun removesBorderConnectedWhiteButPreservesEnclosedWhite() {
        val pixels = IntArray(25) { -1 }
        for (y in 1..3) for (x in 1..3) {
            if (x != 2 || y != 2) pixels[y * 5 + x] = 0xff000000.toInt()
        }
        assertEquals(FramePixelBounds(1, 1, 3, 3), cleanRetailerFramePixels(pixels, 5, 5))
        assertEquals(0, pixels[0])
        assertEquals(-1, pixels[12])
    }

    @Test fun returnsNoBoundsForBlankImage() {
        assertNull(cleanRetailerFramePixels(IntArray(9) { -1 }, 3, 3))
    }
}
