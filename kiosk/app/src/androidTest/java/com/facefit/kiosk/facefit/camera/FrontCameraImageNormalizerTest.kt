package com.facefit.kiosk.facefit.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FrontCameraImageNormalizerTest {
    @Test fun mirrorsAfterApplyingEveryExifOrientation() {
        // Asymmetric pixels reveal wrong-axis flips, double flips and incorrect rotations.
        val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA)
        val original = Bitmap.createBitmap(colors, 3, 2, Bitmap.Config.ARGB_8888)
        val cases = listOf(
            Triple(ExifInterface.ORIENTATION_NORMAL, 3, listOf(2, 1, 0, 5, 4, 3)),
            Triple(ExifInterface.ORIENTATION_FLIP_HORIZONTAL, 3, listOf(0, 1, 2, 3, 4, 5)),
            Triple(ExifInterface.ORIENTATION_ROTATE_180, 3, listOf(3, 4, 5, 0, 1, 2)),
            Triple(ExifInterface.ORIENTATION_FLIP_VERTICAL, 3, listOf(5, 4, 3, 2, 1, 0)),
            Triple(ExifInterface.ORIENTATION_TRANSPOSE, 2, listOf(3, 0, 4, 1, 5, 2)),
            Triple(ExifInterface.ORIENTATION_ROTATE_90, 2, listOf(0, 3, 1, 4, 2, 5)),
            Triple(ExifInterface.ORIENTATION_TRANSVERSE, 2, listOf(2, 5, 1, 4, 0, 3)),
            Triple(ExifInterface.ORIENTATION_ROTATE_270, 2, listOf(5, 2, 4, 1, 3, 0))
        )
        try {
            for ((orientation, width, expected) in cases) {
                val result = FrontCameraImageNormalizer.mirrorUpright(original, orientation)
                try {
                    assertEquals("orientation=$orientation", width, result.width)
                    assertEquals(6 / width, result.height)
                    expected.forEachIndexed { index, colorIndex ->
                        assertEquals("orientation=$orientation pixel=$index", colors[colorIndex],
                            result.getPixel(index % width, index / width))
                    }
                } finally {
                    if (result !== original) result.recycle()
                }
            }
        } finally {
            original.recycle()
        }
    }

    @Test fun savedJpegContainsUprightMirroredPixelsWithoutPendingExifTransform() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("selfie-normalization-", ".jpg", context.cacheDir)
        val original = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until 80) for (x in 0 until 120) {
                val color = if (y < 40) {
                    if (x < 60) Color.RED else Color.GREEN
                } else {
                    if (x < 60) Color.BLUE else Color.YELLOW
                }
                original.setPixel(x, y, color)
            }
            file.outputStream().use { assertTrue(original.compress(Bitmap.CompressFormat.JPEG, 100, it)) }
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            FrontCameraImageNormalizer.normalize(file)
            val saved = BitmapFactory.decodeFile(file.absolutePath)
            try {
                assertEquals(80, saved.width)
                assertEquals(120, saved.height)
                val topLeft = saved.getPixel(20, 20)
                val topRight = saved.getPixel(60, 20)
                val bottomLeft = saved.getPixel(20, 100)
                val bottomRight = saved.getPixel(60, 100)
                assertTrue(Color.red(topLeft) > 200 && Color.blue(topLeft) < 40)
                assertTrue(Color.blue(topRight) > 200 && Color.red(topRight) < 40)
                assertTrue(Color.green(bottomLeft) > 200 && Color.red(bottomLeft) < 40)
                assertTrue(Color.red(bottomRight) > 200 && Color.green(bottomRight) > 200)
                assertEquals(ExifInterface.ORIENTATION_NORMAL, ExifInterface(file.absolutePath)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
            } finally {
                saved.recycle()
            }
        } finally {
            original.recycle()
            file.delete()
        }
    }
}
