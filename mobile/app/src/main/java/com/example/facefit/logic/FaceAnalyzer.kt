package com.example.facefit.logic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import kotlin.math.hypot

class FaceAnalyzer(
    context: Context,
    private val videoTracking: Boolean = false,
    private val backend: TrackingBackend = TrackingBackend.CPU,
    private val reuseRgbaBuffers: Boolean = false,
    private val onResult: (FaceMetrics) -> Unit
) : ImageAnalysis.Analyzer {

    private val faceLandmarker: FaceLandmarker
    private var lastTimestampMs = -1L
    private val imageBuffer = if (reuseRgbaBuffers) TryOnImageBuffer() else null

    init {
        val baseOptions = BaseOptions.builder()
            .setDelegate(if (backend == TrackingBackend.GPU) Delegate.GPU else Delegate.CPU)
            .setModelAssetPath("face_landmarker.task")
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(if (videoTracking) RunningMode.VIDEO else RunningMode.IMAGE)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.35f)
            .setMinFacePresenceConfidence(0.35f)
            .setMinTrackingConfidence(0.4f)
            .build()

        faceLandmarker = FaceLandmarker.createFromOptions(context, options)
    }

    override fun analyze(image: ImageProxy) {
        var sourceBitmap: Bitmap? = null
        var rotatedBitmap: Bitmap? = null
        try {
            val reusable = imageBuffer?.let {
                val plane = image.planes[0]
                require(plane.pixelStride == 4) { "VTO requires CameraX RGBA input" }
                it.prepare(plane.buffer, image.width, image.height, plane.rowStride,
                    image.imageInfo.rotationDegrees)
            }
            val bitmap = reusable?.bitmap ?: run {
                val source = image.toBitmap().also { sourceBitmap = it }
                source.rotate(image.imageInfo.rotationDegrees).also { rotatedBitmap = it }
            }
            val mpImage = reusable?.image ?: BitmapImageBuilder(bitmap).build()

            // Video mode reuses tracking rather than rerunning face detection for every image.
            val result = try {
                if (videoTracking) {
                    val timestampMs = (image.imageInfo.timestamp / 1_000_000L)
                        .coerceAtLeast(lastTimestampMs + 1L)
                    lastTimestampMs = timestampMs
                    faceLandmarker.detectForVideo(mpImage, timestampMs)
                } else faceLandmarker.detect(mpImage)
            } finally {
                if (reusable == null) mpImage.close()
            }
            if (result.faceLandmarks().isNotEmpty()) {
                val landmarks = result.faceLandmarks()[0]

                val landmarkPoints = landmarks.map { LandmarkPoint(it.x(), it.y(), it.z()) }
                // Pixel distances remove image aspect-ratio distortion. Store widths relative
                // to cheek width so moving the camera does not change feature labels.
                fun distance(a: Int, b: Int): Float = hypot(
                    (landmarkPoints[a].x - landmarkPoints[b].x) * bitmap.width,
                    (landmarkPoints[a].y - landmarkPoints[b].y) * bitmap.height
                )
                val cheekWidth = distance(234, 454)
                if (!cheekWidth.isFinite() || cheekWidth < 1f) {
                    onResult(noFaceMetrics())
                    return
                }
                val shapeMeasurements = FaceShapeClassifier.measure(landmarkPoints, bitmap.width, bitmap.height)

                onResult(
                    FaceMetrics(
                        foreheadWidth = distance(103, 332) / cheekWidth,
                        jawline = distance(172, 397) / cheekWidth,
                        eyeSpacing = distance(133, 362) / cheekWidth,
                        faceShape = FaceShapeClassifier.classify(shapeMeasurements),
                        shapeMeasurements = shapeMeasurements,
                        landmarks = landmarkPoints,
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height,
                        isFaceDetected = true,
                    )
                )
            } else {
                onResult(noFaceMetrics())
            }
        } finally {
            rotatedBitmap?.recycle()
            if (sourceBitmap !== rotatedBitmap) sourceBitmap?.recycle()
            image.close()
        }
    }

    private fun Bitmap.rotate(rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return this
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    fun close() {
        try {
            faceLandmarker.close()
        } finally {
            imageBuffer?.close()
        }
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

    data class LandmarkPoint(val x: Float, val y: Float, val z: Float = 0f)

    data class FaceMetrics(
        val foreheadWidth: Float,
        val jawline: Float,
        val eyeSpacing: Float,
        val faceShape: String,
        val landmarks: List<LandmarkPoint> = emptyList(),
        val imageWidth: Int = 0,
        val imageHeight: Int = 0,
        val isFaceDetected: Boolean = false,
        val shapeMeasurements: FaceShapeClassifier.Measurements? = null,
        val shapeSampleCount: Int = 1,
    )
}
