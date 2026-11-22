package com.example.facefit.ui.camera

import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import com.example.facefit.facefit.analysis.FacialFeatureMeasurement
import com.example.facefit.facefit.analysis.ScanDistanceGuide
import com.example.facefit.facefit.recommendation.FacialFeatureThresholds
import android.view.View
import androidx.camera.core.UseCaseGroup
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import com.example.facefit.facefit.analysis.FaceScanGuide
import com.example.facefit.facefit.analysis.FacialGeometry
import com.example.facefit.facefit.analysis.getScanReadiness
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.facefit.facefit.analysis.FaceAnalyzer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Composable
fun CameraPreview(
    captureRequested: AtomicBoolean,
    onReady: () -> Unit,
    onError: (String) -> Unit = {},
    /** Smoothed live distance hint before capture; null when no single face is visible. */
    onDistanceHint: (ScanDistanceGuide.Hint?) -> Unit = {},
    onResult: (FaceAnalyzer.FaceMetrics, Boolean?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val onResultCallback by rememberUpdatedState(onResult)
    val onReadyCallback by rememberUpdatedState(onReady)
    val onErrorCallback by rememberUpdatedState(onError)
    val onDistanceHintCallback by rememberUpdatedState(onDistanceHint)
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { previewView }
    )
    DisposableEffect(lifecycleOwner, previewView, captureRequested) {
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var boundPreview: Preview? = null
        var boundAnalysis: ImageAnalysis? = null
        var analyzer: FaceAnalyzer? = null
        val featureTarget = featureStandardErrorTarget(context)
        val transformFactory = ImageProxyTransformFactory().apply {
            isUsingRotationDegrees = true
        }
        val bindCamera = Runnable {
            if (disposed || boundPreview != null || !cameraProviderFuture.isDone) return@Runnable
            val viewPort = previewView.viewPort ?: return@Runnable
            val rotation = previewView.display?.rotation ?: return@Runnable
            try {
                val cameraProvider = cameraProviderFuture.get()
                provider = cameraProvider

                val preview = Preview.Builder().setTargetRotation(rotation).build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                var receivedFrame = false
                // Accessed only on the analysis executor. Copy before posting the result.
                var sourceTransform: OutputTransform? = null
                // Burst state below is touched only on the main thread, except the atomics.
                val classifierFrames = mutableListOf<FaceAnalyzer.FaceMetrics>()
                val classifierRejectedReasons = mutableListOf<String>()
                var classifierFrozen = false
                var burstStartMs = 0L
                var featureCollector = FacialFeatureMeasurement.Collector(featureTarget)
                val isBurstActive = AtomicBoolean(false)
                val burstGeneration = AtomicInteger(0)
                var analysisAttempt = 0  // analysis executor only
                var analysisGeneration = 0  // analysis executor only
                var analysingPreview = false  // analysis executor only
                var lastPreviewMs = 0L  // analysis executor only
                val distanceGuide = ScanDistanceGuide()  // main thread only

                fun finishBurst(result: FaceAnalyzer.FaceMetrics, success: Boolean) {
                    isBurstActive.set(false)
                    onResultCallback(result, success)
                }

                fun failBurst(guidance: String?, stage: String, attempt: Int) {
                    logRejectedCapture(stage, attempt, guidance, classifierFrames.size,
                        classifierRejectedReasons, featureCollector)
                    finishBurst(FaceAnalyzer.FaceMetrics(foreheadWidth = 0f, jawline = 0f, eyeSpacing = 0f,
                        faceShape = "", isFaceDetected = false, captureGuidance = guidance), false)
                }

                val faceAnalyzer = FaceAnalyzer(context, reuseRgbaBuffers = true, maxFaces = 2,
                    scanQualityChecks = true) { result ->
                    val source = sourceTransform
                    val attempt = analysisAttempt
                    val generation = analysisGeneration
                    val preview = analysingPreview
                    mainExecutor.execute {
                        if (!disposed && preview) {
                            if (!isBurstActive.get()) {
                                val frame = result.featureFrame?.takeIf { result.isFaceDetected }
                                onDistanceHintCallback(distanceGuide.update(frame?.cheekDistanceCm,
                                    SystemClock.elapsedRealtime(), frame?.smileScore, frame?.upperLipRaise))
                            }
                        } else if (!disposed) {
                            val target = previewView.outputTransform
                            val insideGuide = if (source == null || target == null) {
                                null
                            } else {
                                val points = FloatArray(result.landmarks.size * 2)
                                result.landmarks.forEachIndexed { index, point ->
                                    points[index * 2] = point.x * result.imageWidth
                                    points[index * 2 + 1] = point.y * result.imageHeight
                                }
                                CoordinateTransform(source, target).mapPoints(points)
                                FaceScanGuide.containsFace(previewView.width, previewView.height, points)
                            }

                            if (isBurstActive.get() && generation == burstGeneration.get()) {
                                val readiness = result.getScanReadiness()
                                val framed = readiness.canCapture && insideGuide == true
                                // Face-shape classifier frames: same acceptance rule as before.
                                if (!classifierFrozen && attempt <= CLASSIFIER_ATTEMPTS) {
                                    if (framed && result.shapeImageRgb != null && result.shapeMeasurements != null) {
                                        val pose = result.headPose
                                        // After 20 attempts, accept any otherwise-valid frontal frame to
                                        // avoid a scan that never completes; timeout still requires >=4 frames.
                                        val isFrontal = pose == null || (kotlin.math.abs(pose.yaw) <= 4.0f && kotlin.math.abs(pose.pitch) <= 4.0f)
                                        if (isFrontal || attempt >= 20) {
                                            classifierFrames.add(result)
                                            if (classifierFrames.size >= CLASSIFIER_FRAMES) classifierFrozen = true
                                        } else {
                                            classifierRejectedReasons.add("Frame rejected: head turned/tilted (yaw=${pose?.yaw?.toInt()}°, pitch=${pose?.pitch?.toInt()}°)")
                                        }
                                    } else {
                                        classifierRejectedReasons.add(
                                            if (!readiness.canCapture) readiness.message else "Face moved outside guide outline")
                                    }
                                }
                                // Feature-measurement frames: separate pose, distance and motion gates.
                                if (framed) featureCollector.offer(result.featureFrame, result.featureRejection)
                                else featureCollector.breakContinuity()

                                // Time cap: finish (or give guidance) before ScanScreen's 10 s timeout.
                                val outOfTime = SystemClock.elapsedRealtime() - burstStartMs >= MAX_BURST_MS
                                if (!classifierFrozen && (attempt > CLASSIFIER_ATTEMPTS || outOfTime)) {
                                    if (classifierFrames.size >= 4) {
                                        classifierFrozen = true
                                    } else {
                                        failBurst(classifierRejectedReasons.groupingBy { it }.eachCount()
                                            .maxByOrNull { it.value }?.key?.takeUnless { it.startsWith("Frame rejected") },
                                            stage = "classifier_frames", attempt = attempt)
                                    }
                                }
                                if (isBurstActive.get() && classifierFrozen &&
                                    (featureCollector.isComplete() || attempt >= MAX_BURST_ATTEMPTS || outOfTime)) {
                                    val aggregate = featureCollector.aggregate()
                                    if (aggregate != null) {
                                        finishBurst(consolidateBurstFrames(classifierFrames.toList(),
                                            classifierRejectedReasons.toList(), aggregate), true)
                                    } else {
                                        // Not enough reliable feature frames: ask for another scan.
                                        failBurst(featureCollector.guidance() ?: "Hold still and look straight at the camera",
                                            stage = "feature_frames", attempt = attempt)
                                    }
                                }
                            }
                        }
                    }
                }
                analyzer = faceAnalyzer
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetRotation(rotation)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis.setAnalyzer(executor) { image ->
                    if (!receivedFrame) {
                        receivedFrame = true
                        mainExecutor.execute {
                            if (!disposed) onReadyCallback()
                        }
                    }
                    if (captureRequested.compareAndSet(true, false)) {
                        analysisAttempt = 0
                        analysisGeneration = burstGeneration.incrementAndGet()
                        // Posted before any result of this burst, so the reset runs first.
                        mainExecutor.execute {
                            classifierFrames.clear()
                            classifierRejectedReasons.clear()
                            classifierFrozen = false
                            burstStartMs = SystemClock.elapsedRealtime()
                            featureCollector = FacialFeatureMeasurement.Collector(featureTarget)
                        }
                        isBurstActive.set(true)
                    }
                    if (!isBurstActive.get()) {
                        // Before capture: a few lightweight frames per second for the distance guide.
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastPreviewMs < PREVIEW_INTERVAL_MS) {
                            image.close()
                            return@setAnalyzer
                        }
                        lastPreviewMs = now
                        analysingPreview = true
                        faceAnalyzer.previewOnly = true
                        var previewHanded = false
                        try {
                            sourceTransform = transformFactory.getOutputTransform(image)
                            previewHanded = true
                            faceAnalyzer.analyze(image)
                        } catch (error: Exception) {
                            if (!previewHanded) image.close()
                            Log.w("CameraPreview", "Preview distance frame failed", error)
                        }
                        return@setAnalyzer
                    }
                    if (analysisAttempt >= MAX_BURST_ATTEMPTS) {
                        image.close()
                        return@setAnalyzer
                    }
                    analysingPreview = false
                    faceAnalyzer.previewOnly = false
                    analysisAttempt++
                    var handedToAnalyzer = false
                    try {
                        sourceTransform = transformFactory.getOutputTransform(image)
                        handedToAnalyzer = true
                        faceAnalyzer.analyze(image)
                    } catch (error: Exception) {
                        if (!handedToAnalyzer) image.close()
                        Log.e("CameraPreview", "Scan frame analysis failed", error)
                        mainExecutor.execute {
                            if (!disposed) onErrorCallback("Camera frame could not be analyzed. Tap refresh to retry.")
                        }
                    }
                }
                boundPreview = preview
                boundAnalysis = imageAnalysis
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    UseCaseGroup.Builder()
                        .setViewPort(viewPort)
                        .addUseCase(preview)
                        .addUseCase(imageAnalysis)
                        .build()
                )
                faceAnalyzer.cameraIntrinsics = readCameraIntrinsics(camera)
                Log.i("FACEFIT_FEATURE_DEBUG", "Front camera intrinsics: ${faceAnalyzer.cameraIntrinsics}")
            } catch (error: Exception) {
                Log.e("CameraPreview", "Scan camera setup failed", error)
                onErrorCallback("Front camera could not start. Tap refresh to retry.")
            }
        }
        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            bindCamera.run()
        }
        previewView.addOnLayoutChangeListener(layoutListener)
        cameraProviderFuture.addListener(bindCamera, mainExecutor)
        onDispose {
            disposed = true
            previewView.removeOnLayoutChangeListener(layoutListener)
            boundAnalysis?.clearAnalyzer()
            boundPreview?.let { provider?.unbind(it) }
            boundAnalysis?.let { provider?.unbind(it) }
            executor.execute { analyzer?.close() }
            executor.shutdown()
        }
    }
}

private const val CLASSIFIER_FRAMES = 5
private const val CLASSIFIER_ATTEMPTS = 35
/** Upper bound on analysed frames per capture, including the extra feature-measurement frames. */
private const val MAX_BURST_ATTEMPTS = 70
/**
 * The test phone analyses about 6-7 frames per second, so 70 frames can outlast ScanScreen's
 * 10 s timeout. Ending by 8 s lets a scan that never gets level frames show its guidance instead.
 */
private const val MAX_BURST_MS = 8000L
/** Distance-guide sampling before capture (about 4 per second). */
private const val PREVIEW_INTERVAL_MS = 250L
/** Stop collecting feature frames early once each median's standard error is this share of its band. */
private const val TARGET_SE_BAND_FRACTION = 0.15f

private fun featureStandardErrorTarget(context: android.content.Context): FacialFeatureMeasurement.Ratios? =
    FacialFeatureThresholds.load(context)?.let {
        FacialFeatureMeasurement.Ratios(
            it.upperFace.width * TARGET_SE_BAND_FRACTION,
            it.jawline.width * TARGET_SE_BAND_FRACTION,
            it.eyeSpacing.width * TARGET_SE_BAND_FRACTION,
        )
    }

@OptIn(ExperimentalCamera2Interop::class)
private fun readCameraIntrinsics(camera: Camera): FacialFeatureMeasurement.CameraIntrinsics? = runCatching {
    val info = Camera2CameraInfo.from(camera.cameraInfo)
    val focal = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        ?.firstOrNull() ?: return null
    val physical = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
    val pixelArray = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE) ?: return null
    val active = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return null
    val calibration = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        info.getCameraCharacteristic(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION) else null
    FacialFeatureMeasurement.CameraIntrinsics(
        focalLengthMm = focal,
        sensorWidthMm = physical.width,
        pixelArrayWidth = pixelArray.width,
        activeArrayWidth = active.width(),
        activeArrayHeight = active.height(),
        calibratedFocalPx = calibration?.firstOrNull()?.takeIf { it.isFinite() && it > 0f },
    )
}.onFailure { Log.w("CameraPreview", "Camera intrinsics unavailable; using nominal focal length", it) }
    .getOrNull()

/** One short line per failed capture, alongside the per-scan lines from ScanDiagnostics. */
private fun logRejectedCapture(
    stage: String,
    attempts: Int,
    guidance: String?,
    classifierFrames: Int,
    classifierReasons: List<String>,
    features: FacialFeatureMeasurement.Collector,
) {
    runCatching {
        val record = org.json.JSONObject()
            .put("type", "rejected_capture")
            .put("scan_id", "capture_${System.currentTimeMillis()}")
            .put("stage", stage)
            .put("attempts", attempts)
            .put("guidance_shown", guidance ?: "No face detected. Face the camera in good light.")
            .put("classifier_frames", classifierFrames)
            .put("classifier_rejections", org.json.JSONObject(
                classifierReasons.map { if (it.startsWith("Frame rejected")) "Head turned/tilted beyond 4°" else it }
                    .groupingBy { it }.eachCount() as Map<*, *>))
            .put("feature_frames", features.acceptedCount)
            .put("feature_rejections", org.json.JSONObject(
                features.rejectionCounts().mapKeys { it.key.name } as Map<*, *>))
        Log.i("FACEFIT_FEATURE_JSON", record.toString())
    }.onFailure { Log.w("CameraPreview", "Could not log rejected capture", it) }
}

private fun consolidateBurstFrames(
    frames: List<FaceAnalyzer.FaceMetrics>,
    rejectedReasons: List<String>,
    features: FacialFeatureMeasurement.Aggregate,
): FaceAnalyzer.FaceMetrics {
    // Classifier inputs: unchanged selection of the face-shape frame and geometry median.
    val medianMeasurements = FacialGeometry.median(
        frames.mapNotNull { it.shapeMeasurements }
    )
    val bestFrame = frames.minByOrNull { sample ->
        val m = sample.shapeMeasurements
        if (m == null || medianMeasurements == null) 100f
        else {
            kotlin.math.abs(m.lengthToWidth - medianMeasurements.lengthToWidth) +
            kotlin.math.abs(m.jawToCheek - medianMeasurements.jawToCheek)
        }
    } ?: frames.first()

    // Feature ratios: median of accepted feature frames only.
    val medianCheek = featureMedian(features.accepted.map { it.cheekWidthPx })
    val sampleRecords = features.accepted.map { f ->
        FaceAnalyzer.SampleMeasurement(
            foreheadWidth = f.ratios.upperFace,
            jawline = f.ratios.jawline,
            eyeSpacing = f.ratios.eyeSpacing,
            cheekWidthPx = f.cheekWidthPx,
            yawDeg = f.yawDeg,
            pitchDeg = f.pitchDeg
        )
    }

    return bestFrame.copy(
        foreheadWidth = features.median.upperFace,
        jawline = features.median.jawline,
        eyeSpacing = features.median.eyeSpacing,
        rawCheekWidthPx = medianCheek,
        rawForeheadWidthPx = features.median.upperFace * medianCheek,
        rawJawlineWidthPx = features.median.jawline * medianCheek,
        rawEyeSpacingPx = features.median.eyeSpacing * medianCheek,
        shapeMeasurements = medianMeasurements,
        shapeSampleCount = frames.size,
        shapeFeatureSamples = frames.map { it.stabilityFeatures },
        burstSampleMeasurements = sampleRecords,
        rejectedSampleCount = rejectedReasons.size,
        rejectedSampleReasons = rejectedReasons,
        featureAggregate = features,
    )
}

private fun featureMedian(values: List<Float>): Float {
    if (values.isEmpty()) return 0f
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2f else sorted[mid]
}
