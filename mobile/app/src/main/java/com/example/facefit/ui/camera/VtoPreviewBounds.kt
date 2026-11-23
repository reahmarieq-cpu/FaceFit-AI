package com.example.facefit.ui.camera

/** Reject sensor transforms that would replace the live view with a tiny/off-screen image. */
internal object VtoPreviewBounds {
    fun coversView(left: Float, top: Float, right: Float, bottom: Float, width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0 || !listOf(left, top, right, bottom).all { it.isFinite() }) return false
        val mappedWidth = right - left
        val mappedHeight = bottom - top
        if (mappedWidth <= 0f || mappedHeight <= 0f || mappedWidth > width * 16f || mappedHeight > height * 16f) return false
        val toleranceX = maxOf(3f, width * .01f)
        val toleranceY = maxOf(3f, height * .01f)
        return left <= toleranceX && top <= toleranceY &&
            right >= width - toleranceX && bottom >= height - toleranceY
    }
}
