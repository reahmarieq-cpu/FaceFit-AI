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
import com.example.facefit.facefit.analysis.facingDiagnostics
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
    /** Outline half-width (screen px) that an average face fills at 45 cm; null until known. */
    onGuideHalfWidth: (Float?) -> Unit = {},
    /** Called just before the result of an automatic capture (no tap) is delivered. */
    onAutoCapture: () -> Unit = {},
    onResult: (FaceAnalyzer.FaceMetrics, Boolean?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val onResultCallback by rememberUpdatedState(onResult)
    val onReadyCallback by rememberUpdatedState(onReady)
    val onErrorCallback by rememberUpdatedState(onError)
    val onDistanceHintCallback by rememberUpdatedState(onDistanceHint)
    val onGuideHalfWidthCallback by rememberUpdatedState(onGuideHalfWidth)
    val onAutoCaptureCallback by rememberUpdatedState(onAutoCapture)
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
                var guideHalfWidth: Float? = null  // main thread only

                /** Main thread: outline size so an average face fills it at 45 cm on this camera and screen. */
                fun updateGuideSize(source: OutputTransform, uprightWidth: Int, uprightHeight: Int, focalPx: Float) {
                    val target = previewView.outputTransform ?: return
                    val cx = uprightWidth / 2f
                    val cy = uprightHeight / 2f
                    val points = floatArrayOf(cx - 50f, cy, cx + 50f, cy)
                    CoordinateTransform(source, target).mapPoints(points)
                    val scale = kotlin.math.hypot(points[2] - points[0], points[3] - points[1]) / 100f
                    val halfWidth = FaceScanGuide.halfWidthForTargetDistance(focalPx, scale) ?: return
                    if (guideHalfWidth == null || kotlin.math.abs(halfWidth - guideHalfWidth!!) > 1f) {
                        guideHalfWidth = halfWidth
                        onGuideHalfWidthCallback(halfWidth)
                    }
                }
                var lastPreviewMs = 0L  // analysis executor only
                // Set from the tap until the capture is evaluated; pauses the live guide.
                val isCapturing = AtomicBoolean(false)
                var tapMs = 0L  // analysis executor only
                // Live frames already fully analysed while the person lines up (analysis executor
                // only). A tap uses the good ones from just before it, so capture is immediate and
                // movement after the tap is ignored.
                val liveResults = ArrayDeque<LiveResult>()
                var liveTransform: OutputTransform? = null  // analysis executor only
                var liveTimeMs = 0L  // analysis executor only

                fun logTiming(success: Boolean, timing: CaptureTiming, classifierFrames: Int, featureFrames: Int) {
                    Log.i("FACEFIT_FEATURE_JSON", org.json.JSONObject()
                        .put("type", "capture_timing").put("scan_id", "timing_${System.currentTimeMillis()}")
                        .put("success", success)
                        .put("mode", "live_frames")
                        .put("frames_from", timing.source)
                        .put("frame_span_ms", timing.grabMs)
                        .put("wait_after_tap_ms", timing.waitMs)
                        .put("process_ms", timing.processMs)
                        .put("frame_ms", org.json.JSONArray(timing.frameMs))
                        .put("capture_ms", timing.waitMs + timing.processMs)
                        .put("classifier_frames", classifierFrames)
                        .put("feature_frames", featureFrames)
                        .toString())
                }

                /** Main thread: whether the face in [result] is inside the dashed outline on screen. */
                fun insideGuide(result: FaceAnalyzer.FaceMetrics, source: OutputTransform?): Boolean? {
                    val target = previewView.outputTransform
                    if (source == null || target == null || !result.isFaceDetected) return null
                    val points = FloatArray(result.landmarks.size * 2)
                    result.landmarks.forEachIndexed { index, point ->
                        points[index * 2] = point.x * result.imageWidth
                        points[index * 2 + 1] = point.y * result.imageHeight
                    }
                    CoordinateTransform(source, target).mapPoints(points)
                    return FaceScanGuide.containsFace(previewView.width, previewView.height, points, guideHalfWidth)
                }

                /**
                 * Main thread: applies the same frame checks as before to the frozen frames. With
                 * [silent] (automatic capture), a failure is not reported; returns whether it succeeded.
                 */
                fun evaluateFrozen(
                    results: List<Pair<FaceAnalyzer.FaceMetrics, OutputTransform?>>, timing: CaptureTiming,
                    silent: Boolean = false,
                ): Boolean {
                    val classifierFrames = mutableListOf<FaceAnalyzer.FaceMetrics>()
                    val fallbackClassifierFrames = mutableListOf<FaceAnalyzer.FaceMetrics>()
                    val classifierRejectedReasons = mutableListOf<String>()
                    val featureCollector = FacialFeatureMeasurement.Collector(featureTarget)
                    for ((result, source) in results) {
                        val insideGuide = insideGuide(result, source)
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
                    if (silent && !success) return false
                    logTiming(success, timing, classifierFrames.size, featureCollector.acceptedCount)
                    if (success) {
                        if (silent) {
                            isCapturing.set(true)
                            frozenPhoto = previewView.bitmap?.asImageBitmap()
                            onAutoCaptureCallback()
                        }
                        onResultCallback(consolidateBurstFrames(classifierFrames.toList(),
                            classifierRejectedReasons.toList(), requireNotNull(aggregate)), true)
                        return true
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
                    return false
                }

                // Automatic capture (main thread only): consecutive live frames that pass every check.
                val autoRun = ArrayDeque<LiveResult>()
                var lastFacingLogMs = 0L  // main thread only

                val faceAnalyzer = FaceAnalyzer(context, reuseRgbaBuffers = true, maxFaces = 2,
                    scanQualityChecks = true) { result ->
                    // Called on the analysis executor, inside analyze(). Full results are kept for
                    // the next capture; they also drive the distance/expression/pose guide.
                    val live = LiveResult(result, liveTransform, liveTimeMs)
                    liveResults.addLast(live)
                    while (liveResults.size > LIVE_FRAMES_KEPT) liveResults.removeFirst()
                    mainExecutor.execute {
                        if (!disposed && !isCapturing.get()) {
                            val frame = result.featureFrame?.takeIf { result.isFaceDetected }
                            val inside = insideGuide(result, live.transform)
                            val hint = distanceGuide.update(frame?.cheekDistanceCm,
                                SystemClock.elapsedRealtime(), frame?.smileScore, frame?.upperLipRaise,
                                frame?.pitchDeg, frame?.yawDeg)
                            // The capture also checks the outline and the face checks (facing the
                            // camera, lighting...), so show their message live instead of green.
                            val readiness = result.getScanReadiness()
                            onDistanceHintCallback(when {
                                hint?.inRange == true && result.isFaceDetected && !readiness.canCapture ->
                                    hint.copy(text = readiness.message,
                                        inRange = false, poseRefusal = readiness.message)
                                hint?.inRange == true && inside == false ->
                                    hint.copy(text = "Keep your face inside the outline",
                                        inRange = false, poseRefusal = "Keep your full face inside the dashed outline.")
                                else -> hint
                            })
                            val nowMs = SystemClock.elapsedRealtime()
                            if (readiness.message == "Face the camera directly" && nowMs - lastFacingLogMs > 1000L) {
                                lastFacingLogMs = nowMs
                                Log.i("FACEFIT_FEATURE_JSON", result.facingDiagnostics()
                                    .put("type", "facing_rejected").put("scan_id", "live_$nowMs").toString())
                            }
                            if (live.metrics.passesCaptureChecks() && inside == true) autoRun.addLast(live)
                            else autoRun.clear()
                            while (autoRun.size > GRAB_FRAMES) autoRun.removeFirst()
                            if (autoRun.size >= MIN_LIVE_FRAMES) {
                                val used = autoRun.toList()
                                val timing = CaptureTiming(used.last().timeMs - used.first().timeMs, 0L,
                                    emptyList(), "auto", waitMs = 0L)
                                // Not good enough together (e.g. small movement): slide on, no retake.
                                if (evaluateFrozen(used.map { it.metrics to it.transform }, timing, silent = true)) {
                                    autoRun.clear()
                                } else autoRun.removeFirst()
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
                    val now = SystemClock.elapsedRealtime()
                    /**
                     * Uses the analysed frames that pass every per-frame check, from shortly before
                     * the tap onwards. Returns false while still waiting for enough of them.
                     */
                    fun finishCapture(): Boolean {
                        val window = liveResults.filter { it.timeMs >= tapMs - LIVE_WINDOW_MS }
                        val good = window.filter { it.metrics.passesCaptureChecks() }.takeLast(GRAB_FRAMES)
                        val waited = SystemClock.elapsedRealtime() - tapMs
                        if (good.size < MIN_LIVE_FRAMES && waited < MAX_WAIT_MS) return false
                        // Too few good frames in time: evaluate the latest ones for the guidance message.
                        val used = if (good.size >= MIN_LIVE_FRAMES) good else window.takeLast(GRAB_FRAMES)
                        val source = when {
                            used.isEmpty() -> "none"
                            used.last().timeMs <= tapMs -> "live_pre_tap"
                            else -> "live_after_tap"
                        }
                        val span = if (used.isEmpty()) 0L else used.last().timeMs - used.first().timeMs
                        val timing = CaptureTiming(span, 0L, emptyList(), source, waitMs = waited)
                        liveResults.clear()
                        isCapturing.set(false)
                        mainExecutor.execute {
                            if (!disposed) evaluateFrozen(used.map { it.metrics to it.transform }, timing)
                        }
                        return true
                    }
                    if (captureRequested.compareAndSet(true, false)) {
                        tapMs = now
                        isCapturing.set(true)
                        // Freeze what the person sees at the moment of capture.
                        mainExecutor.execute {
                            if (!disposed) frozenPhoto = previewView.bitmap?.asImageBitmap()
                        }
                        // Normal case: enough good frames from just before the tap; nothing to wait for.
                        if (finishCapture()) {
                            image.close()
                            return@setAnalyzer
                        }
                    }
                    // Waiting after a tap: analyse every frame until enough good ones arrive.
                    val waiting = isCapturing.get()
                    if (!waiting && now - lastPreviewMs < PREVIEW_INTERVAL_MS) {
                        image.close()
                        return@setAnalyzer
                    }
                    lastPreviewMs = now
                    // Full analysis (classifier image and lighting included) so this frame can be
                    // used directly if Capture is tapped next.
                    faceAnalyzer.previewOnly = false
                    try {
                        val transform = transformFactory.getOutputTransform(image)
                        liveTransform = transform
                        liveTimeMs = now
                        val turned = image.imageInfo.rotationDegrees % 180 != 0
                        val uprightWidth = if (turned) image.height else image.width
                        val uprightHeight = if (turned) image.width else image.height
                        val focalPx = faceAnalyzer.cameraIntrinsics?.focalLengthPx(uprightWidth, uprightHeight)
                            ?: FacialFeatureMeasurement.nominalFocalLengthPx(uprightWidth, uprightHeight)
                        mainExecutor.execute {
                            if (!disposed) updateGuideSize(transform, uprightWidth, uprightHeight, focalPx)
                        }
                        faceAnalyzer.analyze(image)
                    } catch (error: Exception) {
                        Log.w("CameraPreview", "Preview distance frame failed", error)
                    }
                    if (waiting) finishCapture()
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
/** Most good frames used per capture; five feed the feature median, the rest allow for outliers. */
private const val GRAB_FRAMES = 7
/** Analysed live frames kept (about 4 s at 4 per second), enough to pick good ones from. */
private const val LIVE_FRAMES_KEPT = 16
/** Good frames from up to this long before the tap may be used. */
private const val LIVE_WINDOW_MS = 2500L
/** Fewest good frames for a capture (the feature median needs five). */
private const val MIN_LIVE_FRAMES = 5
/** After a tap without enough good frames, keep collecting for at most this long. */
private const val MAX_WAIT_MS = 3000L

private class LiveResult(val metrics: FaceAnalyzer.FaceMetrics, val transform: OutputTransform?, val timeMs: Long)

/**
 * The per-frame checks a capture applies (readiness, plus pose, distance and expression for the
 * feature measurement), so only frames that will be accepted are picked. Guide-outline and
 * motion checks still run when the capture is evaluated.
 */
private fun FaceAnalyzer.FaceMetrics.passesCaptureChecks(): Boolean =
    getScanReadiness().canCapture && shapeImageRgb != null && featureRejection == null &&
        featureFrame?.let { FacialFeatureMeasurement.assess(it) == null } == true

private class CaptureTiming(
    /** Time between the first and last frame used. */
    val grabMs: Long,
    val processMs: Long,
    val frameMs: List<Long>,
    /** "live_pre_tap": all frames from before the tap; "live_after_tap": waited for more. */
    val source: String,
    /** Time from the tap until processing started (0-ish when pre-tap frames were ready). */
    val waitMs: Long,
)

/** Live analysis before capture (about 4 per second); every frame while waiting after a tap. */
private const val PREVIEW_INTERVAL_MS = 250L
/**
 * Stop collecting feature frames early once each median's standard error is this share of its band.
 * 0.10 keeps the jawline (P30-P70 band) at least as precise as 0.15 of its former P33-P67 band.
 */
private const val TARGET_SE_BAND_FRACTION = 0.10f

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
