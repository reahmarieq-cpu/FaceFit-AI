package com.example.facefit.ui.camera

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/** Worker-owned native bulk copy. Returned bitmap is borrowed, never recycled by inference. */
internal class VtoRgbaBuffer : AutoCloseable {
    private var bitmap: Bitmap? = null
    private var packed: ByteBuffer? = null
    fun copy(image: ImageProxy): Bitmap {
        require(image.width > 0 && image.height > 0)
        val plane = image.planes[0]
        require(plane.pixelStride == 4) { "Unsupported RGBA pixel stride" }
        val rowBytes = image.width*4
        require(plane.rowStride >= rowBytes)
        val source = plane.buffer.duplicate()
        val base = source.position()
        val target = bitmap?.takeIf { it.width==image.width && it.height==image.height && !it.isRecycled }
            ?: Bitmap.createBitmap(image.width,image.height,Bitmap.Config.ARGB_8888).also {
                bitmap?.recycle(); bitmap=it
            }
        if (plane.rowStride == rowBytes) {
            target.copyPixelsFromBuffer(source)
        } else {
            val size=rowBytes*image.height
            val rows=packed?.takeIf { it.capacity()==size }
                ?: ByteBuffer.allocateDirect(size).also { packed=it }
            rows.clear()
            for (y in 0 until image.height) {
                source.limit(source.capacity()); source.position(base+y*plane.rowStride)
                source.limit(source.position()+rowBytes); rows.put(source)
            }
            rows.flip(); target.copyPixelsFromBuffer(rows)
        }
        return target
    }
    override fun close() { bitmap?.recycle(); bitmap=null; packed=null }
}
