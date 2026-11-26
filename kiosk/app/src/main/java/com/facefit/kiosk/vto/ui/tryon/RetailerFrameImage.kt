package com.facefit.kiosk.vto.ui.tryon

import android.graphics.Bitmap
import android.graphics.BitmapFactory

private const val MAX_FRAME_DIMENSION = 1024

/** Decodes a bounded image and removes padding before using the standard lens anchors. */
internal fun prepareRetailerFrame(bytes: ByteArray): Bitmap {
    val sizing = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, sizing)
    require(sizing.outWidth > 0 && sizing.outHeight > 0) { "Invalid retailer frame image" }

    var sampleSize = 1
    val longestSide = maxOf(sizing.outWidth, sizing.outHeight)
    while (longestSide / sampleSize > MAX_FRAME_DIMENSION) sampleSize *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) {
        "Invalid retailer frame image"
    }
    val width = decoded.width
    val height = decoded.height
    val pixels = IntArray(width * height)
    try {
        decoded.getPixels(pixels, 0, width, 0, 0, width, height)
    } finally {
        decoded.recycle()
    }

    val bounds = cleanRetailerFramePixels(pixels, width, height)
        ?: error("Retailer frame image has no visible pixels")
    return Bitmap.createBitmap(
        pixels, bounds.top * width + bounds.left, width, bounds.width, bounds.height,
        Bitmap.Config.ARGB_8888
    )
}

internal data class FramePixelBounds(val left: Int, val top: Int, val width: Int, val height: Int)

internal fun cleanRetailerFramePixels(pixels: IntArray, width: Int, height: Int): FramePixelBounds? {
    if (isOpaqueWhite(pixels[0]) && isOpaqueWhite(pixels[width - 1]) &&
        isOpaqueWhite(pixels[pixels.size - width]) && isOpaqueWhite(pixels.last())
    ) {
        removeBorderWhite(pixels, width, height)
    }

    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (pixels[y * width + x] ushr 24 <= 10) continue
            left = minOf(left, x)
            top = minOf(top, y)
            right = maxOf(right, x)
            bottom = y
        }
    }
    if (right < left) return null
    return FramePixelBounds(left, top, right - left + 1, bottom - top + 1)
}

private fun isOpaqueWhite(pixel: Int): Boolean =
    pixel ushr 24 > 240 && (pixel ushr 16 and 255) > 240 &&
        (pixel ushr 8 and 255) > 240 && (pixel and 255) > 240

private fun removeBorderWhite(pixels: IntArray, width: Int, height: Int) {
    val queue = IntArray(pixels.size)
    var head = 0
    var tail = 0

    fun visit(index: Int) {
        if (!isOpaqueWhite(pixels[index])) return
        pixels[index] = 0
        queue[tail++] = index
    }

    for (x in 0 until width) {
        visit(x)
        visit((height - 1) * width + x)
    }
    for (y in 0 until height) {
        visit(y * width)
        visit(y * width + width - 1)
    }
    while (head < tail) {
        val index = queue[head++]
        val x = index % width
        if (x > 0) visit(index - 1)
        if (x + 1 < width) visit(index + 1)
        if (index >= width) visit(index - width)
        if (index + width < pixels.size) visit(index + width)
    }
}
