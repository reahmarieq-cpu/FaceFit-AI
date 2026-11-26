package com.facefit.kiosk.vto.ui.camera

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer
import com.facefit.kiosk.vto.facefit.camera.TrackingBackend
import com.facefit.kiosk.vto.facefit.tryon.tracking.VtoDeviceProfile
import com.facefit.kiosk.vto.facefit.tryon.tracking.VtoLandmarkStabilizer
import com.facefit.kiosk.vto.ui.tryon.tracking.VtoDiagnostics
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@androidx.annotation.OptIn(androidx.camera.view.TransformExperimental::class)
@Composable
fun TryOnCameraPreview(
    onResult: (FaceAnalyzer.FaceMetrics) -> Unit,
    onError: (String) -> Unit,
    backend: TrackingBackend = TrackingBackend.CPU,
    onBackendFallback: (String) -> Unit = {},
    onTrackingStatus: (String?) -> Unit = {},
    stabilizeLandmarks: Boolean = false
) {
    val context = LocalContext.current
    val profile = remember(context) { VtoDeviceProfile.detect(context) }
    val owner = LocalLifecycleOwner.current
    val resultCallback by rememberUpdatedState(onResult)
    val errorCallback by rememberUpdatedState(onError)
    val statusCallback by rememberUpdatedState(onTrackingStatus)
    val fallbackCallback by rememberUpdatedState(onBackendFallback)
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

    DisposableEffect(previewView, owner, backend, profile, stabilizeLandmarks) {
        VtoDiagnostics.start(context)
        val executor = Executors.newSingleThreadExecutor { work ->
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
                work.run()
            }, "FaceFit-VTO")
        }
        val main = Handler(Looper.getMainLooper())
        val disposed = AtomicBoolean(false)
        val latest = AtomicReference<AnalysisResult?>()
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var cameraPreview: Preview? = null
        var cameraAnalysis: ImageAnalysis? = null
        var tracker: VtoPreviewFaceTracker? = null
        var activeBackend = backend
        var failed = false
        val capture = VtoPreviewCapture()
        val transformFactory = ImageProxyTransformFactory()
        val stabilizer = if (stabilizeLandmarks) {
            VtoLandmarkStabilizer(minCutoff = profile.filterMinCutoff, beta = profile.filterBeta)
        } else null

        val deliver = Runnable {
            val sample = latest.getAndSet(null)
            val target = previewView.outputTransform
            if (!disposed.get() && sample != null && target != null && previewView.width > 0 && previewView.height > 0) {
                val matrix = android.graphics.Matrix()
                CoordinateTransform(sample.transform, target).transform(matrix)
                val raw = VtoCameraFrame.map(sample.face, matrix, previewView.width, previewView.height)
                val stabilized = stabilizer?.sample(raw, sample.frameTime)
                val face = raw.copy(
                    vtoFrameTimeNanos = sample.frameTime,
                    vtoMaskLandmarks = stabilized?.landmarks
                )
                VtoDiagnostics.delivered(face, sample.backend, sample.started, sample.workerStart, sample.workerStart,
                    sample.inferenceStart, sample.finished, sample.timing, sample.face.imageWidth, sample.face.imageHeight)
                statusCallback(null)
                resultCallback(face)
            }
        }

        val bind = Runnable {
            if (!disposed.get() && cameraPreview == null && previewView.width > 0 && previewView.height > 0) {
                val viewport = previewView.viewPort ?: return@Runnable
                try {
                    val ready = future.get()
                    provider = ready
                    val rotation = previewView.display.rotation
                    val preview = Preview.Builder()
                        .setTargetRotation(rotation)
                        .setResolutionSelector(ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(ResolutionStrategy(
                                profile.previewSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            ))
                            .build())
                        .build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    val analysis = ImageAnalysis.Builder()
                        .setTargetRotation(rotation)
                        .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                            ResolutionStrategy(profile.analysisSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                        ).build())
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                    cameraAnalysis = analysis
                    analysis.setAnalyzer(executor) { frame ->
                        val workerStart = SystemClock.uptimeMillis()
                        val arrival = System.nanoTime()
                        try {
                            if (disposed.get() || failed) return@setAnalyzer
                            val active = tracker ?: VtoPreviewFaceTracker(context, activeBackend).also { tracker = it }
                            val transform = transformFactory.getOutputTransform(frame)
                            val inferenceStart = SystemClock.uptimeMillis()
                            val face = active.detect(frame, capture)
                            val finished = SystemClock.uptimeMillis()
                            val sample = AnalysisResult(
                                face = face,
                                transform = transform,
                                frameTime = arrival,
                                backend = activeBackend,
                                started = workerStart,
                                workerStart = workerStart,
                                inferenceStart = inferenceStart,
                                finished = finished,
                                timing = active.lastTiming
                            )
                            if (latest.getAndSet(sample) == null) main.post(deliver)
                        } catch (error: Exception) {
                            Log.e("FaceFitVTO", "Camera analysis failed", error)
                            if (activeBackend == TrackingBackend.GPU) {
                                runCatching { tracker?.close() }
                                tracker = null
                                activeBackend = TrackingBackend.CPU
                                main.post { if (!disposed.get()) fallbackCallback("Using compatible face tracking") }
                            } else {
                                failed = true
                                main.post { if (!disposed.get()) errorCallback("Face tracking failed. Reopen try-on to retry.") }
                            }
                        } finally {
                            frame.close()
                        }
                    }
                    ready.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, UseCaseGroup.Builder()
                        .setViewPort(viewport).addUseCase(preview).addUseCase(analysis).build())
                    cameraPreview = preview
                    VtoDiagnostics.cameraStreams(profile, preview.resolutionInfo?.resolution, analysis.resolutionInfo?.resolution)
                } catch (error: Exception) {
                    cameraAnalysis?.clearAnalyzer()
                    Log.e("FaceFitVTO", "Camera setup failed", error)
                    errorCallback("Camera could not start. Go back and reopen try-on.")
                }
            }
        }
        val layout = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (future.isDone && cameraPreview == null) bind.run()
        }
        previewView.addOnLayoutChangeListener(layout)
        future.addListener(bind, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed.set(true)
            VtoDiagnostics.stop()
            main.removeCallbacks(deliver)
            previewView.removeOnLayoutChangeListener(layout)
            cameraAnalysis?.clearAnalyzer()
            provider?.unbind(*listOfNotNull(cameraPreview, cameraAnalysis).toTypedArray())
            executor.execute {
                try {
                    tracker?.close()
                } finally {
                    capture.close()
                }
            }
            executor.shutdown()
        }
    }
}

private data class AnalysisResult(
    val face: FaceAnalyzer.FaceMetrics,
    val transform: androidx.camera.view.transform.OutputTransform,
    val frameTime: Long,
    val backend: TrackingBackend,
    val started: Long,
    val workerStart: Long,
    val inferenceStart: Long,
    val finished: Long,
    val timing: VtoDiagnostics.Detection?
)

internal class VtoPreviewFaceTracker(context: Context, backend: TrackingBackend) : AutoCloseable {
    private val detector = FaceLandmarker.createFromOptions(context,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task")
                .setDelegate(if (backend == TrackingBackend.GPU) Delegate.GPU else Delegate.CPU).build())
            .setRunningMode(RunningMode.VIDEO).setNumFaces(1)
            .setMinFaceDetectionConfidence(.25f).setMinFacePresenceConfidence(.20f)
            .setMinTrackingConfidence(.20f).build())
    private var lastTimestamp = -1L
    private var mesh = runCatching { VtoLightFaceMesh.open(context) }.onFailure {
        Log.w("FaceFitVTO", "Light face mesh unavailable; using the full tracker", it)
    }.getOrNull()
    private var meshWidth = 0
    private var meshHeight = 0
    var lastTiming: VtoDiagnostics.Detection? = null
        private set

    fun detect(frame: ImageProxy, capture: VtoPreviewCapture): FaceAnalyzer.FaceMetrics {
        val prepareStart = SystemClock.uptimeMillis()
        val bitmap = mesh?.let { capture.copyBitmap(frame) }
        val modelStart = SystemClock.uptimeMillis()
        if (bitmap != null) {
            if (meshWidth != frame.width || meshHeight != frame.height) {
                mesh?.reset()
                meshWidth = frame.width
                meshHeight = frame.height
            }
            val points = runCatching { mesh?.track(bitmap) }.getOrElse {
                disableLightMesh(it)
                null
            }
            if (points != null) return result(frame, points, prepareStart, modelStart)
        }

        val image = capture.wrap(frame)
        val detection = try {
            lastTimestamp = (frame.imageInfo.timestamp / 1_000_000L).coerceAtLeast(lastTimestamp + 1L)
            // CameraX maps raw-buffer landmarks into the preview.
            detector.detectForVideo(image, lastTimestamp)
        } finally {
            image.close()
        }
        val points = detection.faceLandmarks().firstOrNull()?.map {
            FaceAnalyzer.LandmarkPoint(it.x(), it.y(), it.z())
        }.orEmpty()
        if (bitmap != null && points.size >= 468) {
            runCatching { mesh?.seed(bitmap, points) }.onFailure(::disableLightMesh)
        }
        return result(frame, points, prepareStart, modelStart)
    }

    private fun result(
        frame: ImageProxy,
        points: List<FaceAnalyzer.LandmarkPoint>,
        prepareStart: Long,
        modelStart: Long
    ): FaceAnalyzer.FaceMetrics {
        lastTiming = VtoDiagnostics.Detection(
            modelStart - prepareStart, SystemClock.uptimeMillis() - modelStart, 0
        )
        return FaceAnalyzer.FaceMetrics(0f, 0f, 0f, "", points, frame.width, frame.height, points.size >= 468)
    }

    private fun disableLightMesh(error: Throwable) {
        Log.w("FaceFitVTO", "Light face mesh failed; using the full tracker", error)
        runCatching { mesh?.close() }
        mesh = null
    }

    override fun close() {
        try {
            mesh?.close()
        } finally {
            detector.close()
        }
    }
}
