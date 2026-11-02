package com.example.facefit.logic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import java.nio.ByteBuffer
import kotlin.math.roundToInt

// Confined to the analyzer thread; inference must finish before the next prepare call.
internal class TryOnImageBuffer : AutoCloseable {
    private var source: Bitmap? = null
    private var prepared: Prepared? = null
    private var canvas: Canvas? = null
    private val rotation = Matrix()
    private var rotationDegrees = -1
    private var packedRows: ByteBuffer? = null

    data class Prepared(val bitmap: Bitmap, val image: MPImage)

    fun prepare(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int,
                degrees: Int): Prepared {
        require(width > 0 && height > 0 && rowStride >= width * 4)
        require(degrees == 0 || degrees == 90 || degrees == 180 || degrees == 270)
        if (source?.width != width || source?.height != height || rotationDegrees != degrees) {
            close()
            val input = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            source = input
            rotationDegrees = degrees
            val output = if (degrees == 0) input else {
                rotation.setRotate(degrees.toFloat())
                val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
                rotation.mapRect(bounds)
                rotation.postTranslate(-bounds.left, -bounds.top)
                Bitmap.createBitmap(bounds.width().roundToInt(), bounds.height().roundToInt(),
                    Bitmap.Config.ARGB_8888)
            }
            canvas = if (output !== input) Canvas(output) else null
            // Closing MPImage recycles its bitmap, so retain both until tracking stops.
            prepared = Prepared(output, BitmapImageBuilder(output).build())
        }
        val input = checkNotNull(source)
        val pixels = buffer.duplicate()
        pixels.rewind()
        val rowBytes = width * 4
        if (rowStride == rowBytes) {
            input.copyPixelsFromBuffer(pixels)
        } else {
            val byteCount = rowBytes * height
            val packed = packedRows?.takeIf { it.capacity() == byteCount }
                ?: ByteBuffer.allocateDirect(byteCount).also { packedRows = it }
            packed.clear()
            for (row in 0 until height) {
                pixels.limit(pixels.capacity())
                pixels.position(row * rowStride)
                pixels.limit(row * rowStride + rowBytes)
                packed.put(pixels)
            }
            packed.flip()
            input.copyPixelsFromBuffer(packed)
        }
        canvas?.drawBitmap(input, rotation, null)
        return checkNotNull(prepared)
    }

    override fun close() {
        val output = prepared?.bitmap
        prepared?.image?.close()
        if (source !== output) source?.recycle()
        prepared = null
        source = null
        canvas = null
        packedRows = null
        rotationDegrees = -1
    }
}
