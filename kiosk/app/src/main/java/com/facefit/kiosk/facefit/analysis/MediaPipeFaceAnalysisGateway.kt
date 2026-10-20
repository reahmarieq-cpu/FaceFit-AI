package com.facefit.kiosk.facefit.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import com.facefit.kiosk.model.FaceLandmarkPoint
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import java.io.Closeable
import java.io.File
import java.io.IOException

class MediaPipeFaceAnalysisGateway(
    context: Context
) : FaceAnalysisGateway, Closeable {

    private val appContext = context.applicationContext
    private var faceLandmarker: FaceLandmarker? = null

    override fun analyze(capturedImagePath: String): FaceAnalysisResult {
        val imageFile = File(capturedImagePath)
        if (!imageFile.exists() || !imageFile.canRead()) {
            Log.w(TAG, "Captured face image is missing or unreadable: $capturedImagePath")
            return FaceAnalysisResult.AnalysisFailure
        }

        val decodedBitmap = try {
            decodeBitmap(imageFile)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to decode captured face image.", error)
            return FaceAnalysisResult.AnalysisFailure
        }

        val analysisBitmap = try {
            rotateBitmapForExif(imageFile, decodedBitmap)
        } catch (error: Exception) {
            decodedBitmap.recycle()
            Log.e(TAG, "Unable to apply image orientation.", error)
            return FaceAnalysisResult.AnalysisFailure
        }

        return try {
            val mpImage = BitmapImageBuilder(analysisBitmap).build()
            val result = landmarker().detect(mpImage)
            FaceAnalysisResultMapper.fromLandmarks(
                detectedFaces = result.faceLandmarks().map { faceLandmarks ->
                    faceLandmarks.mapIndexed { index, point ->
                        FaceLandmarkPoint(
                            index = index,
                            x = point.x(),
                            y = point.y(),
                            z = point.z()
                        )
                    }
                },
                imageWidth = analysisBitmap.width,
                imageHeight = analysisBitmap.height,
                sourceImagePath = imageFile.absolutePath
            )
        } catch (error: Exception) {
            Log.e(TAG, "MediaPipe face landmark analysis failed.", error)
            FaceAnalysisResult.AnalysisFailure
        } finally {
            if (analysisBitmap !== decodedBitmap) {
                analysisBitmap.recycle()
            }
            decodedBitmap.recycle()
        }
    }

    override fun close() {
        faceLandmarker?.close()
        faceLandmarker = null
    }

    private fun landmarker(): FaceLandmarker {
        faceLandmarker?.let { return it }

        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(FaceLandmarkerConfig.MODEL_ASSET_PATH)
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(FaceLandmarkerConfig.MAX_FACE_COUNT)
            .setMinFaceDetectionConfidence(FaceLandmarkerConfig.MIN_FACE_DETECTION_CONFIDENCE)
            .setMinFacePresenceConfidence(FaceLandmarkerConfig.MIN_FACE_PRESENCE_CONFIDENCE)
            .setMinTrackingConfidence(FaceLandmarkerConfig.MIN_TRACKING_CONFIDENCE)
            .build()

        return FaceLandmarker.createFromOptions(appContext, options).also {
            faceLandmarker = it
        }
    }

    private fun decodeBitmap(imageFile: File): Bitmap {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imageFile.absolutePath, boundsOptions)
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            throw IOException("Captured image has invalid dimensions.")
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = calculateSampleSize(boundsOptions)
        }

        return BitmapFactory.decodeFile(imageFile.absolutePath, decodeOptions)
            ?: throw IOException("Captured image could not be decoded.")
    }

    private fun calculateSampleSize(options: BitmapFactory.Options): Int {
        var sampleSize = 1
        val longestEdge = maxOf(options.outWidth, options.outHeight)
        while (longestEdge / sampleSize > FaceLandmarkerConfig.MAX_DECODE_DIMENSION) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun rotateBitmapForExif(imageFile: File, bitmap: Bitmap): Bitmap {
        val exif = ExifInterface(imageFile.absolutePath)
        val rotationDegrees = when (
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        if (rotationDegrees == 0f) return bitmap

        val matrix = Matrix().apply { postRotate(rotationDegrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val TAG = "MediaPipeFaceAnalysis"
    }
}
