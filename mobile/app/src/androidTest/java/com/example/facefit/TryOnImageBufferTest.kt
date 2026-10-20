package com.example.facefit

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import com.example.facefit.logic.TryOnImageBuffer
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class TryOnImageBufferTest {
    private val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE,
        Color.WHITE, Color.BLACK, Color.YELLOW)

    private fun pixels(colors: IntArray, rowStride: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(rowStride * 2)
        colors.forEachIndexed { index, color ->
            buffer.position(index / 3 * rowStride + index % 3 * 4)
            buffer.put(Color.red(color).toByte())
            buffer.put(Color.green(color).toByte())
            buffer.put(Color.blue(color).toByte())
            buffer.put(255.toByte())
        }
        buffer.rewind()
        return buffer
    }

    @Test
    fun allRotationsMatchOriginalBitmapPathWithAndWithoutRowPadding() {
        val reference = Bitmap.createBitmap(colors, 3, 2, Bitmap.Config.ARGB_8888)
        try {
            for (degrees in listOf(0, 90, 180, 270)) {
                val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
                val expected = Bitmap.createBitmap(reference, 0, 0, 3, 2, matrix, true)
                try {
                    for (stride in listOf(12, 20)) {
                        TryOnImageBuffer().use { buffer ->
                            val actual = buffer.prepare(pixels(colors, stride), 3, 2, stride, degrees)
                            assertEquals(expected.width, actual.image.width)
                            assertEquals(expected.height, actual.image.height)
                            assertTrue("Rotation $degrees, stride $stride", expected.sameAs(actual.bitmap))
                        }
                    }
                } finally {
                    if (expected !== reference) expected.recycle()
                }
            }
        } finally {
            reference.recycle()
        }
    }

    @Test
    fun buffersAreReusedButPixelsRefreshAndCloseReleasesBitmap() {
        for (degrees in listOf(0, 90, 180, 270)) {
            val buffer = TryOnImageBuffer()
            val first = buffer.prepare(pixels(colors, 20), 3, 2, 20, degrees)
            val snapshot = first.bitmap.copy(Bitmap.Config.ARGB_8888, false)!!
            try {
                val next = buffer.prepare(pixels(colors.reversedArray(), 20), 3, 2, 20, degrees)
                assertSame(first, next)
                assertFalse(next.bitmap.isRecycled)
                assertFalse(snapshot.sameAs(next.bitmap))
            } finally {
                snapshot.recycle()
                buffer.close()
            }
            assertTrue(first.bitmap.isRecycled)
            buffer.close()
        }
    }

    @Test
    fun rotationAndResolutionChangesReleaseOldBufferAndRebuild() {
        TryOnImageBuffer().use { buffer ->
            val first = buffer.prepare(pixels(colors, 12), 3, 2, 12, 90)
            val second = buffer.prepare(pixels(colors, 12), 3, 2, 12, 270)
            assertNotSame(first, second)
            assertTrue(first.bitmap.isRecycled)
            val resized = buffer.prepare(ByteBuffer.allocateDirect(16), 2, 2, 8, 0)
            assertTrue(second.bitmap.isRecycled)
            assertEquals(2, resized.bitmap.width)
            assertEquals(2, resized.bitmap.height)
        }
    }
}
