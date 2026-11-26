package com.facefit.kiosk.ui.scan.mobile

import android.content.Context
import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfRect
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import kotlin.math.abs

/** Reproduces the crop and resize sequence in the supplied training scripts. */
internal class FaceShapeImageInput(context: Context) {
    private val cascade: CascadeClassifier

    init {
        check(OpenCVLoader.initLocal()) { "Face crop runtime could not start" }
        val path = context.cacheDir.resolve("max_classifier_frontalface.xml")
        context.assets.open("face_shape_max/haarcascade_frontalface_default.xml").use { input ->
            path.outputStream().use { input.copyTo(it) }
        }
        cascade = CascadeClassifier(path.absolutePath)
        check(!cascade.empty()) { "Face crop model could not load" }
    }

    /**
     * Crops the face into a consistent 256x256 square matching the EfficientNetB0 training pipeline.
     * When MediaPipe landmarks are available, uses landmark boundaries for 100% stable framing
     * that eliminates Haar cascade jitter and false fallback crops under varying lighting.
     */
    fun prepare(bitmap: Bitmap, landmarks: List<FaceAnalyzer.LandmarkPoint>? = null): ByteArray {
        val rgba = Mat()
        val gray = Mat()
        val faces = MatOfRect()
        val resized = Mat()
        val rgb = Mat()
        var crop: Mat? = null
        try {
            Utils.bitmapToMat(bitmap, rgba)

            val rect = if (landmarks != null && landmarks.size > 454) {
                // High-precision landmark-based face bounding box (immune to lighting and angle jitter):
                val leftCheek = landmarks[234]
                val rightCheek = landmarks[454]
                val forehead = landmarks[10]
                val chin = landmarks[152]

                val centerX = (leftCheek.x + rightCheek.x) / 2f * bitmap.width
                val centerY = (forehead.y + chin.y) / 2f * bitmap.height
                val faceW = abs(rightCheek.x - leftCheek.x) * bitmap.width
                val faceH = abs(chin.y - forehead.y) * bitmap.height

                val targetSize = maxOf(faceW * 1.55f, faceH * 1.25f).toInt().coerceAtLeast(50)
                var left = (centerX - targetSize / 2f).toInt()
                var top = (centerY - targetSize * 0.50f).toInt()

                if (left < 0) left = 0
                if (top < 0) top = 0
                if (left + targetSize > bitmap.width) {
                    left = (bitmap.width - targetSize).coerceAtLeast(0)
                }
                if (top + targetSize > bitmap.height) {
                    top = (bitmap.height - targetSize).coerceAtLeast(0)
                }
                val squareSide = minOf(targetSize, bitmap.width - left, bitmap.height - top)
                Rect(left, top, squareSide, squareSide)
            } else {
                // Fallback to Haar cascade if landmarks unavailable
                Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
                cascade.detectMultiScale(gray, faces, 1.1, 4, 0, Size(50.0, 50.0), Size())
                val face = faces.toArray().maxByOrNull { it.width.toLong() * it.height }
                if (face != null) {
                    val padX = (face.width * .22).toInt()
                    val padY = (face.height * .22).toInt()
                    val targetW = face.width + 2 * padX
                    val targetH = face.height + (padY * 2.0).toInt()
                    val targetSize = maxOf(targetW, targetH)

                    var left = face.x - padX
                    var top = (face.y - (padY * 1.3).toInt())

                    if (left < 0) left = 0
                    if (top < 0) top = 0
                    if (left + targetSize > bitmap.width) {
                        left = (bitmap.width - targetSize).coerceAtLeast(0)
                    }
                    if (top + targetSize > bitmap.height) {
                        top = (bitmap.height - targetSize).coerceAtLeast(0)
                    }

                    val actualW = minOf(targetSize, bitmap.width - left)
                    val actualH = minOf(targetSize, bitmap.height - top)
                    val squareSide = minOf(actualW, actualH)
                    Rect(left, top, squareSide, squareSide)
                } else {
                    val side = minOf((bitmap.width * .76).toInt(), (bitmap.height * .90).toInt())
                    val left = ((bitmap.width - side) / 2).coerceAtLeast(0)
                    val top = ((bitmap.height - side) / 2).coerceAtLeast(0)
                    Rect(left, top, side, side)
                }
            }

            require(rect.width >= 10 && rect.height >= 10)
            crop = rgba.submat(rect)
            Imgproc.resize(crop, resized, Size(256.0, 256.0), 0.0, 0.0, Imgproc.INTER_AREA)
            Imgproc.cvtColor(resized, rgb, Imgproc.COLOR_RGBA2RGB)
            return ByteArray(256 * 256 * 3).also { rgb.get(0, 0, it) }
        } finally {
            crop?.release()
            rgb.release()
            resized.release()
            faces.release()
            gray.release()
            rgba.release()
        }
    }
}
