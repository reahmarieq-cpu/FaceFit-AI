package com.facefit.kiosk.vto.ui.camera

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.ByteBufferImageBuilder
import com.google.mediapipe.framework.image.MPImage
import java.nio.ByteBuffer

/** Wraps the CameraX RGBA plane for the duration of synchronous video inference. */
internal class VtoPreviewCapture {
    private var packed: ByteBuffer? = null
    private var bitmap: Bitmap? = null

    fun wrap(frame: ImageProxy): MPImage =
        ByteBufferImageBuilder(pixels(frame), frame.width, frame.height, MPImage.IMAGE_FORMAT_RGBA).build()

    fun copyBitmap(frame: ImageProxy): Bitmap {
        val target = bitmap?.takeIf { it.width == frame.width && it.height == frame.height }
            ?: Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888).also {
                bitmap?.recycle()
                bitmap = it
            }
        target.copyPixelsFromBuffer(pixels(frame))
        return target
    }

    fun close() {
        bitmap?.recycle()
        bitmap = null
        packed = null
    }

    private fun pixels(frame: ImageProxy): ByteBuffer {
        val plane = frame.planes.single()
        val source = plane.buffer.duplicate()
        val rowBytes = frame.width * 4
        val size = rowBytes * frame.height
        return if (plane.rowStride == rowBytes && source.remaining() >= size) {
            source.limit(source.position() + size)
            source.slice()
        } else {
            val target = packed?.takeIf { it.capacity() == size }
                ?: ByteBuffer.allocateDirect(size).also { packed = it }
            val start = source.position()
            target.clear()
            for (row in 0 until frame.height) {
                val offset = start + row * plane.rowStride
                source.limit(offset + rowBytes)
                source.position(offset)
                target.put(source)
            }
            target.flip()
            target
        }
    }
}

