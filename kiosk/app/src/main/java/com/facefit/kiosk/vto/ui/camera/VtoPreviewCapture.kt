package com.facefit.kiosk.vto.ui.camera

import android.graphics.*
import android.util.Log
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import kotlin.math.abs
import kotlin.math.roundToInt

/** Low-resolution readback, validated against the working CameraX screenshot before use. */
internal class VtoPreviewCapture : AutoCloseable {
    private var raw: Bitmap? = null
    private var key: String? = null
    private var validated = false
    private var retry = 0
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun capture(preview: PreviewView): Bitmap? {
        val texture = findTexture(preview) ?: return referenceCapture(preview)
        if (!texture.isAvailable || texture.width <= 0 || texture.height <= 0 ||
            preview.width <= 0 || preview.height <= 0) return null
        val correction = texture.getTransform(Matrix())
        val child = Matrix(texture.matrix)
        val values = FloatArray(9)
        correction.getValues(values)
        val rotationKey = values.contentHashCode()
        child.getValues(values)
        val current = "${preview.width},${preview.height},${texture.width},${texture.height},$rotationKey,${values.contentHashCode()},${texture.left},${texture.top}"
        if (current != key) { key = current; validated = false; retry = 0 }
        if (!validated && retry++ % 20 != 0) return referenceCapture(preview)
        val ratio = (512f / maxOf(preview.width, preview.height)).coerceAtMost(1f)
        val width = (preview.width * ratio).roundToInt().coerceAtLeast(1)
        val height = (preview.height * ratio).roundToInt().coerceAtLeast(1)
        val sourceRatio = (512f / maxOf(texture.width, texture.height)).coerceAtMost(1f)
        val sourceWidth = (texture.width * sourceRatio).roundToInt().coerceAtLeast(1)
        val sourceHeight = (texture.height * sourceRatio).roundToInt().coerceAtLeast(1)
        var source = raw
        if (source == null || source.width != sourceWidth || source.height != sourceHeight) {
            source?.recycle()
            source = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)
            raw = source
        }
        texture.getBitmap(source) ?: return referenceCapture(preview)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val transform = bitmapToPreview(correction, child, texture.left, texture.top,
            texture.width, texture.height, sourceWidth, sourceHeight,
            preview.width, preview.height, width, height)
        Canvas(output).drawBitmap(source, transform, paint)
        if (validated) return output
        val reference = referenceCapture(preview) ?: run { output.recycle(); return null }
        val scaled = Bitmap.createScaledBitmap(reference, width, height, true)
        val matches = matchesReference(output, scaled)
        if (scaled !== reference) scaled.recycle()
        if (matches) {
            validated = true
            Log.i("FaceFitVTO", "Small capture validated: ${preview.width}x${preview.height} -> ${width}x${height}")
            reference.recycle()
            return output
        }
        output.recycle()
        return reference
    }
    // Fallback images must retain PreviewView's transform, but do not need screen resolution.
    private fun referenceCapture(preview: PreviewView): Bitmap? {
        val reference = preview.bitmap ?: return null
        val ratio = (512f / maxOf(reference.width, reference.height)).coerceAtMost(1f)
        if (ratio >= 1f) return reference
        val scaled = Bitmap.createScaledBitmap(reference,
            (reference.width * ratio).roundToInt().coerceAtLeast(1),
            (reference.height * ratio).roundToInt().coerceAtLeast(1), true)
        if (scaled !== reference) reference.recycle()
        return scaled
    }
    override fun close() { raw?.recycle(); raw = null }

    companion object {
        internal fun bitmapToPreview(correction: Matrix, child: Matrix, left: Int, top: Int,
            textureWidth: Int, textureHeight: Int, sourceWidth: Int, sourceHeight: Int,
            previewWidth: Int, previewHeight: Int, outputWidth: Int, outputHeight: Int): Matrix =
            Matrix(correction).apply {
                postConcat(child)
                postTranslate(left.toFloat(), top.toFloat())
                postScale(outputWidth.toFloat() / previewWidth, outputHeight.toFloat() / previewHeight)
                preScale(textureWidth.toFloat() / sourceWidth, textureHeight.toFloat() / sourceHeight)
            }
        internal fun matchesReference(candidate: Bitmap, reference: Bitmap): Boolean {
            if (candidate.width != reference.width || candidate.height != reference.height) return false
            var error = 0L; var min = 255; var max = 0; var count = 0
            for (row in 1 until 24) for (column in 1 until 16) {
                val x = column * (reference.width - 1) / 16
                val y = row * (reference.height - 1) / 24
                val a = candidate.getPixel(x, y); val b = reference.getPixel(x, y)
                val luminance = (((b ushr 16) and 255) * 77 + ((b ushr 8) and 255) * 150 + (b and 255) * 29) ushr 8
                min = minOf(min, luminance); max = maxOf(max, luminance)
                for (shift in intArrayOf(0, 8, 16)) {
                    val expected = (b ushr shift) and 255
                    error += abs(((a ushr shift) and 255) - expected)
                    count++
                }
            }
            // Flat/dark camera frames cannot establish whether orientation is correct.
            return max - min >= 35 && error.toDouble() / count < 10.0
        }
        private fun findTexture(view: View): TextureView? {
            if (view is TextureView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) {
                findTexture(view.getChildAt(i))?.let { return it }
            }
            return null
        }
    }
}