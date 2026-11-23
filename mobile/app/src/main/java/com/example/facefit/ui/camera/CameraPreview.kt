package com.example.facefit.ui.camera

import android.hardware.camera2.CameraCharacteristics
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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

    // Shown from the moment of capture until the result is ready (or the capture is retried).
    var frozenPhoto by remember { mutableStateOf<ImageBitmap?>(null) }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView }
        )
        frozenPhoto?.let {
            Image(it, contentDescription = "Captured photo", modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds)
        }
    }
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
                val distanceGuide = ScanDistanceGuide()  // main thread only
                var lastPreviewMs = 0L  // analysis executor only
                // Frozen capture: frames grabbed right after the tap, processed afterwards.
                val isCapturing = AtomicBoolean(false)
                val grabbed = mutableListOf<FrozenFrame>()  // analysis executor only
                var captureStartMs = 0L  // analysis executor only

                fun logTiming(success: Boolean, timing: CaptureTiming, classifierFrames: Int, featureFrames: Int) {
                    Log.i("FACEFIT_FEATURE_JSON", org.json.JSONObject()
                        .put("type", "capture_timing").put("scan_id", "timing_${System.currentTimeMillis()}")
                        .put("success", success)
                        .put("mode", "frozen_burst")
                        .put("grab_ms", timing.grabMs)
                        .put("process_ms", timing.processMs)
                        .put("frame_ms", org.json.JSONArray(timing.frameMs))
                        .put("capture_ms", timing.grabMs + timing.processMs)
                        .put("classifier_frames", classifierFrames)
                        .put("feature_frames", featureFrames)
                        .toString())
                }

                /** Main thread: applies the same frame checks as before to the frozen frames. */
                fun evaluateFrozen(results: List<Pair<FaceAnalyzer.FaceMetrics, OutputTransform?>>, timing: CaptureTiming) {
                    val target = previewView.outputTransform
                    val classifierFrames = mutableListOf<FaceAnalyzer.FaceMetrics>()
                    val fallbackClassifierFrames = mutableListOf<FaceAnalyzer.FaceMetrics>()
                    val classifierRejectedReasons = mutableListOf<String>()
                    val featureCollector = FacialFeatureMeasurement.Collector(featureTarget)
                    for ((result, source) in results) {
                        val insideGuide = if (source == null || target == null || !result.isFaceDetected) {
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
                        val readiness = result.getScanReadiness()
                        val framed = readiness.canCapture && insideGuide == true
                        // Face-shape classifier frames: same rule as before (frontal within 4 degrees
                        // preferred; otherwise any framed frame, like the old fallback after 20 attempts).
                        if (framed && result.shapeImageRgb != null && result.shapeMeasurements != null) {
                            val pose = result.headPose
                            val isFrontal = pose == null || (kotlin.math.abs(pose.yaw) <= 4.0f && kotlin.math.abs(pose.pitch) <= 4.0f)
                            if (isFrontal) {
                                if (classifierFrames.size < CLASSIFIER_FRAMES) classifierFrames.add(result)
                            } else {
                                fallbackClassifierFrames.add(result)
                                classifierRejectedReasons.add("Frame rejected: head turned/tilted (yaw=${pose?.yaw?.toInt()}°, pitch=${pose?.pitch?.toInt()}°)")
                            }
                        } else {
                            classifierRejectedReasons.add(
                                if (!readiness.canCapture) readiness.message else "Face moved outside guide outline")
                        }
                        // Feature-measurement frames: separate pose, distance, expression and motion gates.
                        if (framed) featureCollector.offer(result.featureFrame, result.featureRejection)
                        else featureCollector.breakContinuity()
                    }
                    if (classifierFrames.size < MIN_CLASSIFIER_FRAMES) {
                        classifierFrames += fallbackClassifierFrames.take(CLASSIFIER_FRAMES - classifierFrames.size)
                    }
                    val aggregate = featureCollector.aggregate()
                    val success = classifierFrames.size >= MIN_CLASSIFIER_FRAMES && aggregate != null
                    logTiming(success, timing, classifierFrames.size, featureCollector.acceptedCount)
                    if (success) {
                        onResultCallback(consolidateBurstFrames(classifierFrames.toList(),
                            classifierRejectedReasons.toList(), requireNotNull(aggregate)), true)
                        return
                    }
                    val stage = if (classifierFrames.size < MIN_CLASSIFIER_FRAMES) "classifier_frames" else "feature_frames"
                    val guidance = if (stage == "classifier_frames") {
                        classifierRejectedReasons.groupingBy { it }.eachCount()
                            .maxByOrNull { it.value }?.key?.takeUnless { it.startsWith("Frame rejected") }
                    } else featureCollector.guidance()
                    logRejectedCapture(stage, results.size, guidance, classifierFrames.size,
                        classifierRejectedReasons, featureCollector)
                    frozenPhoto = null
                    onResultCallback(FaceAnalyzer.FaceMetrics(foreheadWidth = 0f, jawline = 0f, eyeSpacing = 0f,
                        faceShape = "", isFaceDetected = false,
                        captureGuidance = guidance ?: "Hold still and look straight at the camera"), false)
                }

                val faceAnalyzer = FaceAnalyzer(context, reuseRgbaBuffers = true, maxFaces = 2,
                    scanQualityChecks = true) { result ->
                    // Live analysis is only used for the distance/expression/pose guide before capture.
                    mainExecutor.execute {
                        if (!disposed && !isCapturing.get()) {
                            val frame = result.featureFrame?.takeIf { result.isFaceDetected }
                            onDistanceHintCallback(distanceGuide.update(frame?.cheekDistanceCm,
                                SystemClock.elapsedRealtime(), frame?.smileScore, frame?.upperLipRaise,
                                frame?.pitchDeg, frame?.yawDeg))
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
                        grabbed.forEach { it.bitmap.recycle() }
                        grabbed.clear()
                        captureStartMs = SystemClock.elapsedRealtime()
                        isCapturing.set(true)
                        // Freeze what the person sees at the moment of capture.
                        mainExecutor.execute {
                            if (!disposed) frozenPhoto = previewView.bitmap?.asImageBitmap()
                        }
                    }
                    if (!isCapturing.get()) {
                        // Before capture: a few lightweight frames per second for the live guide.
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastPreviewMs < PREVIEW_INTERVAL_MS) {
                            image.close()
                            return@setAnalyzer
                        }
                        lastPreviewMs = now
                        faceAnalyzer.previewOnly = true
                        try {
                            faceAnalyzer.analyze(image)
                        } catch (error: Exception) {
                            Log.w("CameraPreview", "Preview distance frame failed", error)
                        }
                        return@setAnalyzer
                    }
                    // Capture: copy consecutive camera frames as fast as they arrive (no analysis yet),
                    // so movement after this short window cannot affect the result.
                    try {
                        val transform = transformFactory.getOutputTransform(image)
                        val source = image.toBitmap()
                        val upright = source.rotated(image.imageInfo.rotationDegrees)
                        if (upright !== source) source.recycle()
                        grabbed += FrozenFrame(upright, transform)
                    } catch (error: Exception) {
                        Log.e("CameraPreview", "Could not copy capture frame", error)
                    } finally {
                        image.close()
                    }
                    if (grabbed.size < GRAB_FRAMES) return@setAnalyzer
                    val grabMs = SystemClock.elapsedRealtime() - captureStartMs
                    // Frozen: run MediaPipe and the measurements on the copied frames.
                    val processStart = SystemClock.elapsedRealtime()
                    val frameMs = mutableListOf<Long>()
                    val results = grabbed.map { frame ->
                        val t = SystemClock.elapsedRealtime()
                        val metrics = try {
                            faceAnalyzer.analyzeFrozen(frame.bitmap)
                        } catch (error: Exception) {
                            Log.e("CameraPreview", "Frozen frame analysis failed", error)
                            FaceAnalyzer.FaceMetrics(0f, 0f, 0f, "", isFaceDetected = false)
                        } finally {
                            frame.bitmap.recycle()
                        }
                        frameMs += SystemClock.elapsedRealtime() - t
                        metrics to frame.transform
                    }
                    grabbed.clear()
                    val timing = CaptureTiming(grabMs, SystemClock.elapsedRealtime() - processStart, frameMs)
                    // Back to live preview frames; the result is evaluated on the main thread.
                    isCapturing.set(false)
                    mainExecutor.execute {
                        if (!disposed) evaluateFrozen(results, timing)
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
/** Fewest face-shape frames accepted, as with the old capture's timeout rule. */
private const val MIN_CLASSIFIER_FRAMES = 4
/**
 * Consecutive camera frames copied at the tap (about 0.25 s at 30 fps). Seven leaves room for
 * two rejected frames while still giving the feature median its minimum of five.
 */
private const val GRAB_FRAMES = 7

private class FrozenFrame(val bitmap: Bitmap, val transform: OutputTransform?)

private class CaptureTiming(val grabMs: Long, val processMs: Long, val frameMs: List<Long>)

private fun Bitmap.rotated(rotationDegrees: Int): Bitmap {
    if (rotationDegrees == 0) return this
    val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
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
