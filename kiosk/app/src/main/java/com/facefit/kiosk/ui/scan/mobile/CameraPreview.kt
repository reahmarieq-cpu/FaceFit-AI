package com.facefit.kiosk.ui.scan.mobile

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
import com.facefit.kiosk.ui.scan.mobile.FacialFeatureMeasurement
import com.facefit.kiosk.ui.scan.mobile.ScanDistanceGuide
import com.facefit.kiosk.ui.scan.mobile.FacialFeatureThresholds
import android.view.View
import androidx.camera.core.UseCaseGroup
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import com.facefit.kiosk.ui.scan.mobile.FaceScanGuide
import com.facefit.kiosk.ui.scan.mobile.FacialGeometry
import com.facefit.kiosk.ui.scan.mobile.getScanReadiness
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
import com.facefit.kiosk.ui.scan.mobile.FaceAnalyzer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun CameraPreview(
    captureRequested: AtomicBoolean,
    onReady: () -> Unit,
    onError: (String) -> Unit = {},
    /** Smoothed live distance hint before capture; null when no single face is visible. */
    onDistanceHint: (ScanDistanceGuide.Hint?) -> Unit = {},
    /** Outline half-width (screen px) that an average face fills at 45 cm; null until known. */
    onGuideHalfWidth: (Float?) -> Unit = {},
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
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
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
                contentScale = ContentScale.Fit)
        }
    }
    DisposableEffect(lifecycleOwner, previewView, captureRequested) {
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val disposed = AtomicBoolean(false)
        var clearRecentFrames: (() -> Unit)? = null
        var provider: ProcessCameraProvider? = null
        var boundPreview: Preview? = null
        var boundAnalysis: ImageAnalysis? = null
        var analyzer: FaceAnalyzer? = null
        val featureTarget = featureStandardErrorTarget(context)
        val transformFactory = ImageProxyTransformFactory().apply {
            isUsingRotationDegrees = true
        }
        val bindCamera = Runnable {
            if (disposed.get() || boundPreview != null || !cameraProviderFuture.isDone) return@Runnable
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
                // Frozen capture: the last frames before the tap, processed afterwards.
                val isCapturing = AtomicBoolean(false)
                val recentFrames = RecentFrames(GRAB_FRAMES + 1)  // analysis executor only
                clearRecentFrames = recentFrames::clear
                var tapMs = 0L  // analysis executor only

                fun logTiming(success: Boolean, timing: CaptureTiming, classifierFrames: Int, featureFrames: Int) {
                    Log.i("FACEFIT_FEATURE_JSON", org.json.JSONObject()
                        .put("type", "capture_timing").put("scan_id", "timing_${System.currentTimeMillis()}")
                        .put("success", success)
                        .put("mode", "frozen_burst")
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
                            FaceScanGuide.containsFace(previewView.width, previewView.height, points, guideHalfWidth)
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
                        if (!disposed.get() && !isCapturing.get()) {
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
                    if (disposed.get()) {
                        image.close()
                        return@setAnalyzer
                    }
                    if (!receivedFrame) {
                        receivedFrame = true
                        mainExecutor.execute {
                            if (!disposed.get()) onReadyCallback()
                        }
                    }
                    val now = SystemClock.elapsedRealtime()
                    // Keep the most recent frames so Capture uses what was on screen before the tap.
                    try {
                        recentFrames.add(image, transformFactory.getOutputTransform(image), now)
                    } catch (error: Exception) {
                        Log.w("CameraPreview", "Could not keep camera frame", error)
                    }
                    if (captureRequested.compareAndSet(true, false)) {
                        tapMs = now
                        isCapturing.set(true)
                        // Freeze what the person sees at the moment of capture.
                        mainExecutor.execute {
                            if (!disposed.get()) frozenPhoto = previewView.bitmap?.asImageBitmap()
                        }
                    }
                    if (isCapturing.get()) {
                        image.close()
                        // Normally the frames from just before the tap are enough; right after the
                        // camera opens there may be fewer, so wait for the next ones.
                        val frames = recentFrames.since(tapMs - PRE_TAP_WINDOW_MS)
                        if (frames.size < GRAB_FRAMES && now - tapMs < MAX_WAIT_MS) return@setAnalyzer
                        val selected = frames.filter { it.timeMs <= tapMs }.takeLast(GRAB_FRAMES)
                            .takeIf { it.size == GRAB_FRAMES } ?: frames.take(GRAB_FRAMES)
                        val source = if (selected.all { it.timeMs <= tapMs }) "pre_tap" else "post_tap"
                        val processStart = SystemClock.elapsedRealtime()
                        val frameMs = mutableListOf<Long>()
                        // Frozen: run MediaPipe and the measurements on the kept frames.
                        val results = selected.map { frame ->
                            val t = SystemClock.elapsedRealtime()
                            val metrics = try {
                                val upright = frame.bitmap.rotated(frame.rotationDegrees)
                                try {
                                    faceAnalyzer.analyzeFrozen(upright)
                                } finally {
                                    if (upright !== frame.bitmap) upright.recycle()
                                }
                            } catch (error: Exception) {
                                Log.e("CameraPreview", "Frozen frame analysis failed", error)
                                FaceAnalyzer.FaceMetrics(0f, 0f, 0f, "", isFaceDetected = false)
                            }
                            frameMs += SystemClock.elapsedRealtime() - t
                            metrics to frame.transform
                        }
                        val spanMs = if (selected.isEmpty()) 0L else selected.last().timeMs - selected.first().timeMs
                        val timing = CaptureTiming(spanMs, SystemClock.elapsedRealtime() - processStart, frameMs,
                            source, waitMs = processStart - tapMs)
                        recentFrames.clear()
                        // Back to live preview frames; the result is evaluated on the main thread.
                        isCapturing.set(false)
                        mainExecutor.execute {
                            if (!disposed.get()) evaluateFrozen(results, timing)
                        }
                        return@setAnalyzer
                    }
                    // Before capture: a few lightweight frames per second for the live guide.
                    if (now - lastPreviewMs < PREVIEW_INTERVAL_MS) {
                        image.close()
                        return@setAnalyzer
                    }
                    lastPreviewMs = now
                    faceAnalyzer.previewOnly = true
                    var passedToAnalyzer = false
                    try {
                        val transform = transformFactory.getOutputTransform(image)
                        val turned = image.imageInfo.rotationDegrees % 180 != 0
                        val uprightWidth = if (turned) image.height else image.width
                        val uprightHeight = if (turned) image.width else image.height
                        val focalPx = faceAnalyzer.cameraIntrinsics?.focalLengthPx(uprightWidth, uprightHeight)
                            ?: FacialFeatureMeasurement.nominalFocalLengthPx(uprightWidth, uprightHeight)
                        mainExecutor.execute {
                            if (!disposed.get()) updateGuideSize(transform, uprightWidth, uprightHeight, focalPx)
                        }
                        passedToAnalyzer = true
                        faceAnalyzer.analyze(image)
                    } catch (error: Exception) {
                        if (!passedToAnalyzer) image.close()
                        Log.w("CameraPreview", "Preview distance frame failed", error)
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
            disposed.set(true)
            previewView.removeOnLayoutChangeListener(layoutListener)
            boundAnalysis?.clearAnalyzer()
            boundPreview?.let { provider?.unbind(it) }
            boundAnalysis?.let { provider?.unbind(it) }
            executor.execute {
                try { analyzer?.close() } finally { clearRecentFrames?.invoke() }
            }
            executor.shutdown()
        }
    }
}

private const val CLASSIFIER_FRAMES = 5
/** Fewest face-shape frames accepted, as with the old capture's timeout rule. */
private const val MIN_CLASSIFIER_FRAMES = 4
/**
 * Camera frames used per capture: the last ones before the tap (about 0.5 s at the test phone's
 * 13 fps). Seven leaves room for two rejected frames while the feature median gets its five.
 */
private const val GRAB_FRAMES = 7

/** Frames from up to this long before the tap may be used for the capture. */
private const val PRE_TAP_WINDOW_MS = 1200L
/** If too few frames were kept (camera just opened), wait at most this long for new ones. */
private const val MAX_WAIT_MS = 1500L

private class KeptFrame(val bitmap: Bitmap, val transform: OutputTransform?, val rotationDegrees: Int, val timeMs: Long)

/**
 * Rolling copies of the latest camera frames, so a capture uses what was on screen before the
 * tap. Analysis executor only; the oldest frame's bitmap is reused for the newest.
 */
private class RecentFrames(private val capacity: Int) {
    private val frames = ArrayDeque<KeptFrame>()

    fun add(image: androidx.camera.core.ImageProxy, transform: OutputTransform?, timeMs: Long) {
        val plane = image.planes[0]
        val reuse = if (frames.size >= capacity) frames.removeFirst().bitmap else null
        val bitmap = if (plane.pixelStride == 4 && plane.rowStride == image.width * 4) {
            (reuse?.takeIf { it.width == image.width && it.height == image.height && !it.isRecycled }
                ?: Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888))
                .also { it.copyPixelsFromBuffer(plane.buffer.duplicate().apply { rewind() }) }
        } else {
            image.toBitmap()  // rows are padded: let CameraX repack them
        }
        if (reuse != null && reuse !== bitmap) reuse.recycle()
        frames.addLast(KeptFrame(bitmap, transform, image.imageInfo.rotationDegrees, timeMs))
    }

    fun since(timeMs: Long): List<KeptFrame> = frames.filter { it.timeMs >= timeMs }

    fun clear() {
        while (frames.isNotEmpty()) frames.removeFirst().bitmap.recycle()
    }
}

private class CaptureTiming(
    /** Time between the first and last frame used. */
    val grabMs: Long,
    val processMs: Long,
    val frameMs: List<Long>,
    /** "pre_tap": frames from before the tap; "post_tap": camera had just opened. */
    val source: String,
    /** Time from the tap until processing started (0-ish when pre-tap frames were ready). */
    val waitMs: Long,
)

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

/** Records guidance for rejected captures without saving an image. */
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
