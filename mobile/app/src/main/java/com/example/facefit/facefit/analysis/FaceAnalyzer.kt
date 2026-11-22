package com.example.facefit.facefit.analysis

import com.example.facefit.facefit.camera.TrackingBackend
import com.example.facefit.facefit.camera.TryOnImageBuffer
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
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
    private val maxFaces: Int = 1,
    private val scanQualityChecks: Boolean = false,
    private val onResult: (FaceMetrics) -> Unit
) : ImageAnalysis.Analyzer {

    private val faceLandmarker: FaceLandmarker
    private val shapeImageInput by lazy { FaceShapeImageInput(context.applicationContext) }
    private var lastTimestampMs = -1L
    /** Camera focal-length data for perspective-corrected feature ratios; set after the camera binds. */
    @Volatile var cameraIntrinsics: FacialFeatureMeasurement.CameraIntrinsics? = null
    /** Preview-only frames (distance guide) skip the classifier image and lighting work. */
    @Volatile var previewOnly: Boolean = false
    private val imageBuffer = if (reuseRgbaBuffers) TryOnImageBuffer() else null

    init {
        val baseOptions = BaseOptions.builder()
            .setDelegate(if (backend == TrackingBackend.GPU) Delegate.GPU else Delegate.CPU)
            .setModelAssetPath("face_landmarker.task")
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(if (videoTracking) RunningMode.VIDEO else RunningMode.IMAGE)
            .setNumFaces(maxFaces)
            .setOutputFacialTransformationMatrixes(scanQualityChecks)
            .setMinFaceDetectionConfidence(if (scanQualityChecks) 0.5f else 0.35f)
            .setMinFacePresenceConfidence(if (scanQualityChecks) 0.5f else 0.35f)
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
                require(plane.pixelStride == 4) { "Expected CameraX RGBA input" }
                it.prepare(plane.buffer, image.width, image.height, plane.rowStride,
                    image.imageInfo.rotationDegrees)
            }
            val bitmap = reusable?.bitmap ?: run {
                val source = image.toBitmap().also { sourceBitmap = it }
                source.rotate(image.imageInfo.rotationDegrees).also { rotatedBitmap = it }
            }
            withFrameImage(bitmap, reusable?.image) { mpImage ->
                // Video mode reuses tracking rather than rerunning face detection for every image.
                val result = if (videoTracking) {
                    val timestampMs = (image.imageInfo.timestamp / 1_000_000L)
                        .coerceAtLeast(lastTimestampMs + 1L)
                    lastTimestampMs = timestampMs
                    faceLandmarker.detectForVideo(mpImage, timestampMs)
                } else faceLandmarker.detect(mpImage)
                if (result.faceLandmarks().size == 1) {
                    val landmarks = result.faceLandmarks()[0]

                    val landmarkPoints = landmarks.map { LandmarkPoint(it.x(), it.y(), it.z()) }
                    // Ratios to cheek width in pixels: invariant to resolution and uniform scale.
                    // Scan results use the multi-frame median from FacialFeatureMeasurement.
                    fun distance(a: Int, b: Int): Float = hypot(
                        (landmarkPoints[a].x - landmarkPoints[b].x) * bitmap.width,
                        (landmarkPoints[a].y - landmarkPoints[b].y) * bitmap.height
                    )
                    val cheekWidth = distance(234, 454)
                    if (!cheekWidth.isFinite() || cheekWidth < 1f) {
                        onResult(noFaceMetrics())
                        return
                    }
                    val rawForehead = distance(103, 332)
                    val rawJaw = distance(172, 397)
                    val rawEye = distance(133, 362)
                    val shapeMeasurements = FacialGeometry.measure(landmarkPoints, bitmap.width, bitmap.height)
                    val poseMatrix = result.facialTransformationMatrixes().orElse(emptyList()).firstOrNull()
                    val featureFrame = if (scanQualityChecks) measureFeatures(landmarkPoints,
                        bitmap.width, bitmap.height, poseMatrix) else null
                    val preview = previewOnly
                    val shapeImageRgb = if (scanQualityChecks && !videoTracking && !preview) {
                        shapeImageInput.prepare(bitmap, landmarkPoints)
                    } else null

                    onResult(
                        FaceMetrics(
                            foreheadWidth = rawForehead / cheekWidth,
                            jawline = rawJaw / cheekWidth,
                            eyeSpacing = rawEye / cheekWidth,
                            rawCheekWidthPx = cheekWidth,
                            rawForeheadWidthPx = rawForehead,
                            rawJawlineWidthPx = rawJaw,
                            rawEyeSpacingPx = rawEye,
                            faceShape = "",
                            stabilityFeatures = ScanStabilityFeatures.extract(landmarkPoints, bitmap.width, bitmap.height)
                                ?: emptyList(),
                            shapeMeasurements = shapeMeasurements,
                            landmarks = landmarkPoints,
                            imageWidth = bitmap.width,
                            imageHeight = bitmap.height,
                            isFaceDetected = true,
                            headPose = poseMatrix?.let(FaceScanPose::angles),
                            scanLighting = if (scanQualityChecks && !preview)
                                FaceScanLighting.measure(bitmap, landmarkPoints) else null,
                            shapeImageRgb = shapeImageRgb,
                            featureFrame = featureFrame,
                            featureRejection = if (scanQualityChecks && featureFrame == null)
                                FacialFeatureMeasurement.Rejection.POSE_UNAVAILABLE else null,
                        )
                    )
                } else {
                    onResult(noFaceMetrics(result.faceLandmarks().size))
                }
            }
        } finally {
            rotatedBitmap?.takeUnless { it.isRecycled }?.recycle()
            if (sourceBitmap !== rotatedBitmap) sourceBitmap?.takeUnless { it.isRecycled }?.recycle()
            image.close()
        }
    }

    private fun measureFeatures(
        points: List<LandmarkPoint>, width: Int, height: Int, poseMatrix: FloatArray?
    ): FacialFeatureMeasurement.Frame? {
        val intrinsics = cameraIntrinsics
        val calibrated = intrinsics?.focalLengthPx(width, height)
        return FacialFeatureMeasurement.measure(
            points, width, height, poseMatrix,
            focalLengthPx = calibrated ?: FacialFeatureMeasurement.nominalFocalLengthPx(width, height),
            focalSource = if (calibrated != null) intrinsics.source else "nominal"
        )
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

    private fun noFaceMetrics(detectedFaceCount: Int = 0): FaceMetrics {
        return FaceMetrics(
            foreheadWidth = 0f,
            jawline = 0f,
            eyeSpacing = 0f,
            faceShape = "",
            landmarks = emptyList(),
            imageWidth = 0,
            imageHeight = 0,
            isFaceDetected = false,
            detectedFaceCount = detectedFaceCount
        )
    }

    data class LandmarkPoint(val x: Float, val y: Float, val z: Float = 0f)

    data class SampleMeasurement(
        val foreheadWidth: Float,
        val jawline: Float,
        val eyeSpacing: Float,
        val cheekWidthPx: Float,
        val yawDeg: Float = 0f,
        val pitchDeg: Float = 0f
    )

    data class FaceMetrics(
        val foreheadWidth: Float,
        val jawline: Float,
        val eyeSpacing: Float,
        val faceShape: String,
        val landmarks: List<LandmarkPoint> = emptyList(),
        val imageWidth: Int = 0,
        val imageHeight: Int = 0,
        val isFaceDetected: Boolean = false,
        val shapeMeasurements: FacialGeometry.Measurements? = null,
        val shapeSampleCount: Int = 1,
        val stabilityFeatures: List<Float> = emptyList(),
        val shapeModelId: String? = null,
        val shapeModelScores: Map<String, Float> = emptyMap(),
        val shapeDecisionReason: String? = null,
        val shapeAgreement: Float? = null,
        val shapeFeatureSamples: List<List<Float>> = emptyList(),
        val detectedFaceCount: Int = if (isFaceDetected) 1 else 0,
        val shapeDecisionPolicy: String? = null,
        val headPose: FaceScanPose.Angles? = null,
        val scanLighting: FaceScanLighting.Measurement? = null,
        val shapeModelFeatures: List<Double> = emptyList(),
        val shapeImageRgb: ByteArray? = null,
        val rawCheekWidthPx: Float = 0f,
        val rawForeheadWidthPx: Float = 0f,
        val rawJawlineWidthPx: Float = 0f,
        val rawEyeSpacingPx: Float = 0f,
        val burstSampleMeasurements: List<SampleMeasurement> = emptyList(),
        val rejectedSampleCount: Int = 0,
        val rejectedSampleReasons: List<String> = emptyList(),
        /** Per-frame perspective-corrected feature measurement (scan capture only). */
        val featureFrame: FacialFeatureMeasurement.Frame? = null,
        val featureRejection: FacialFeatureMeasurement.Rejection? = null,
        /** Multi-frame result; when present, foreheadWidth/jawline/eyeSpacing are its medians. */
        val featureAggregate: FacialFeatureMeasurement.Aggregate? = null,
        /** Set on a failed capture to tell the person what to change. */
        val captureGuidance: String? = null,
    )
}
