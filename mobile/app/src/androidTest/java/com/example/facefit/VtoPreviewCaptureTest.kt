package com.example.facefit

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import com.example.facefit.ui.camera.VtoPreviewCapture
import org.junit.Assert.*
import org.junit.Test

class VtoPreviewCaptureTest {
    @Test fun reducedReadbackPreservesCorrectionCropAndOutputCoordinates() {
        val correction = Matrix().apply {
            setValues(floatArrayOf(0f,-1f,300f, 1f,0f,0f, 0f,0f,1f))
        }
        val child = Matrix().apply {
            setValues(floatArrayOf(2f,0f,-40f, 0f,3f,25f, 0f,0f,1f))
        }
        val points = floatArrayOf(10f,20f,50f,70f)
        VtoPreviewCapture.bitmapToPreview(correction, child, 10, 20,
            400,300,100,75,800,900,400,450).mapPoints(points)
        assertArrayEquals(floatArrayOf(205f,82.5f,5f,322.5f), points, .001f)
    }
    @Test fun validationAcceptsMatchingImageAndRejectsMirrorMismatch() {
        val original = Bitmap.createBitmap(120,160,Bitmap.Config.ARGB_8888)
        val mirrored = Bitmap.createBitmap(120,160,Bitmap.Config.ARGB_8888)
        for (y in 0 until 160) for (x in 0 until 120) {
            val color = Color.rgb(x * 2, y, (x + y) % 256)
            original.setPixel(x,y,color)
            mirrored.setPixel(119-x,y,color)
        }
        assertTrue(VtoPreviewCapture.matchesReference(original,original))
        assertFalse(VtoPreviewCapture.matchesReference(mirrored,original))
        original.recycle(); mirrored.recycle()
    }
    @Test fun darkFramesCannotFalselyValidateWrongOrientation() {
        val black = Bitmap.createBitmap(120,160,Bitmap.Config.ARGB_8888)
        black.eraseColor(Color.BLACK)
        assertFalse(VtoPreviewCapture.matchesReference(black,black))
        black.eraseColor(Color.RED)
        assertFalse(VtoPreviewCapture.matchesReference(black,black))
        black.recycle()
    }
}