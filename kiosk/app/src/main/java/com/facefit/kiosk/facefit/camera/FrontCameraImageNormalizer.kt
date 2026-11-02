package com.facefit.kiosk.facefit.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.IOException

/** Saves upright, mirrored pixels so capture review and analysis use the same coordinates. */
internal object FrontCameraImageNormalizer {
    fun normalize(imageFile: File) {
        val orientation = ExifInterface(imageFile.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
        val original = BitmapFactory.decodeFile(imageFile.absolutePath)
            ?: throw IOException("Unable to decode the captured image.")
        var normalized: Bitmap? = null
        var temporary: File? = null
        try {
            val mirrored = mirrorUpright(original, orientation)
            normalized = mirrored
            temporary = File.createTempFile("mirrored-capture-", ".jpg", imageFile.parentFile)
            temporary.outputStream().use { output ->
                if (!mirrored.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                    throw IOException("Unable to save the mirrored capture.")
                }
            }
            ExifInterface(temporary.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                saveAttributes()
            }
            // Both the pixels and EXIF now describe the same upright, mirrored image.
            if (!temporary.renameTo(imageFile)) {
                throw IOException("Unable to replace the captured image.")
            }
        } finally {
            temporary?.delete()
            if (normalized !== original) normalized?.recycle()
            original.recycle()
        }
    }

    internal fun mirrorUpright(bitmap: Bitmap, orientation: Int): Bitmap {
        val transform = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    setRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    setRotate(270f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            }
            // Mirror after rotation, along the horizontal axis of the upright selfie.
            postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, false)
    }
}
