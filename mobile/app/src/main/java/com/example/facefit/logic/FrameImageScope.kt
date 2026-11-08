package com.example.facefit.logic

import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage

/** MediaPipe owns/recycles a wrapped bitmap; all pixel consumers must finish first. */
internal inline fun <T> withFrameImage(
    bitmap: Bitmap,
    reusable: MPImage? = null,
    consume: (MPImage) -> T
): T {
    val image = reusable ?: BitmapImageBuilder(bitmap).build()
    try {
        return consume(image)
    } finally {
        if (reusable == null) image.close()
    }
}
