package com.example.facefit.logic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import kotlin.math.pow
import kotlin.math.sqrt

class FaceAnalyzer(
    context: Context,
    private val onResult: (FaceMetrics) -> Unit
) : ImageAnalysis.Analyzer {

    private val faceLandmarker: FaceLandmarker

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .build()

        faceLandmarker = FaceLandmarker.createFromOptions(context, options)
    }

    override fun analyze(image: ImageProxy) {
        try {
            val bitmap = image.toBitmap().rotate(image.imageInfo.rotationDegrees)
            val mpImage = BitmapImageBuilder(bitmap).build()

            val result = faceLandmarker.detect(mpImage)

            if (result.faceLandmarks().isNotEmpty()) {
                val landmarks = result.faceLandmarks()[0]

                // 1. Forehead Width (Distance between landmarks 103 and 332)
                val foreheadWidth = calculateDistance(landmarks[103], landmarks[332])

                // 2. Eye Spacing (Distance between inner corners: 133 and 362)
                val eyeSpacing = calculateDistance(landmarks[133], landmarks[362])

                // 3. Jawline (Roughly landmark 234 to 454)
                val jawline = calculateDistance(landmarks[234], landmarks[454])

                // 4. Face Shape logic (Simplified: Width vs Height)
                val faceHeight = calculateDistance(landmarks[10], landmarks[152]) // Forehead top to Chin
                val faceWidth = calculateDistance(landmarks[234], landmarks[454]) // Cheek to cheek
                val faceShape = detectFaceShape(faceWidth, faceHeight)

                val landmarkPoints = landmarks.map { LandmarkPoint(it.x(), it.y()) }

                onResult(
                    FaceMetrics(
                        foreheadWidth = foreheadWidth,
                        jawline = jawline,
                        eyeSpacing = eyeSpacing,
                        faceShape = faceShape,
                        landmarks = landmarkPoints,
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height,
                        isFaceDetected = true
                    )
                )
            } else {
                onResult(noFaceMetrics())
            }
        } finally {
            image.close()
        }
    }

    private fun Bitmap.rotate(rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return this
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun noFaceMetrics(): FaceMetrics {
        return FaceMetrics(
            foreheadWidth = 0f,
            jawline = 0f,
            eyeSpacing = 0f,
            faceShape = "",
            landmarks = emptyList(),
            imageWidth = 0,
            imageHeight = 0,
            isFaceDetected = false
        )
    }

    private fun calculateDistance(p1: com.google.mediapipe.tasks.components.containers.NormalizedLandmark, p2: com.google.mediapipe.tasks.components.containers.NormalizedLandmark): Float {
        return sqrt((p1.x() - p2.x()).pow(2) + (p1.y() - p2.y()).pow(2))
    }

    private fun detectFaceShape(width: Float, height: Float): String {
        val ratio = height / width
        return when {
            ratio > 1.5f -> "Oval"
            ratio > 1.3f -> "Long"
            ratio < 1.1f -> "Round"
            else -> "Square"
        }
    }

    data class LandmarkPoint(val x: Float, val y: Float)

    data class FaceMetrics(
        val foreheadWidth: Float,
        val jawline: Float,
        val eyeSpacing: Float,
        val faceShape: String,
        val landmarks: List<LandmarkPoint> = emptyList(),
        val imageWidth: Int = 0,
        val imageHeight: Int = 0,
        val isFaceDetected: Boolean = false
    )
}
