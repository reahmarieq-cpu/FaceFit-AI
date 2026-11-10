package com.example.facefit.ui.camera

import com.example.facefit.ui.tryon.tracking.VtoDiagnostics

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.facefit.facefit.tryon.tracking.VtoLandmarkStabilizer
import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.camera.TrackingBackend
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

@Composable
fun TryOnCameraPreview(
    onResult: (FaceAnalyzer.FaceMetrics) -> Unit,
    onError: (String) -> Unit,
    backend: TrackingBackend = TrackingBackend.CPU,
    onBackendFallback: (String) -> Unit = {},
    onTrackingStatus: (String?) -> Unit = {}
) {
    val context = LocalContext.current
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

    DisposableEffect(previewView, owner, backend) {
        VtoDiagnostics.start(context)
        val executor = Executors.newSingleThreadExecutor()
        val main = Handler(Looper.getMainLooper())
        val disposed = AtomicBoolean(false)
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var cameraPreview: Preview? = null
        var tracker: VtoPreviewFaceTracker? = null
        var activeBackend = backend
        val previewCapture=VtoPreviewCapture()
        val stabilizer=VtoLandmarkStabilizer()
        val busy=AtomicBoolean(false)
        var measuredFrames=0
        val capture=object : Runnable {
            override fun run() {
                if(disposed.get()) return
                if(previewView.previewStreamState.value != PreviewView.StreamState.STREAMING ||
                    !owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) ||
                    !busy.compareAndSet(false,true)) {
                    main.postDelayed(this,16L)
                    return
                }
                val started=SystemClock.uptimeMillis()
                val bitmap=try { previewCapture.capture(previewView) } catch(error: Exception) {
                    Log.e("FaceFitVTO","Preview capture failed",error); null
                }
                if(bitmap==null) { busy.set(false);main.postDelayed(this,16L);return }
                val captured=SystemClock.uptimeMillis()
                executor.execute {
                    val workerStart=SystemClock.uptimeMillis()
                    try {
                        if(!disposed.get()) {
                            val active=tracker ?: run {
                                val created=try { VtoPreviewFaceTracker(context,activeBackend) }
                                catch(failure: Exception) {
                                    if(activeBackend!=TrackingBackend.GPU) throw failure
                                    activeBackend=TrackingBackend.CPU
                                    main.post { if(!disposed.get()) fallbackCallback("Using compatible face tracking") }
                                    VtoPreviewFaceTracker(context)
                                }
                                tracker=created
                                created
                            }
                            val inferenceStart=SystemClock.uptimeMillis()
                            val result=active.detect(bitmap,started)
                            val finished=SystemClock.uptimeMillis()
                            val inferenceMs=finished-inferenceStart
                            val detectionTiming=active.lastTiming
                            val inputWidth=bitmap.width; val inputHeight=bitmap.height
                            main.post {
                                if(!disposed.get()) {
                                    val stabilized=requireNotNull(stabilizer.sample(result,started*1_000_000L))
                                    VtoDiagnostics.delivered(stabilized,activeBackend,started,captured,workerStart,
                                        inferenceStart,finished,detectionTiming,inputWidth,inputHeight)
                                    statusCallback(null)
                                    resultCallback(stabilized)
                                    if(++measuredFrames%60==0) Log.i("FaceFitVTOLatency",
                                        "previewCamera backend=$activeBackend inferenceMs=$inferenceMs captureToResultMs=${SystemClock.uptimeMillis()-started}")
                                }
                            }
                        }
                    } catch(error: Exception) {
                        Log.e("FaceFitVTO","Preview tracking failed",error)
                        if(activeBackend==TrackingBackend.GPU) {
                            runCatching { tracker?.close() };tracker=null;activeBackend=TrackingBackend.CPU
                            main.post { if(!disposed.get()) fallbackCallback("Using compatible face tracking") }
                        } else main.post { if(!disposed.get()) errorCallback("Face tracking failed. Reopen try-on to retry.") }
                    } finally {
                        bitmap.recycle()
                        busy.set(false)
                        if(!disposed.get()) main.post(this)
                    }
                }
            }
        }
        val bind = Runnable {
            if (!disposed.get() && cameraPreview == null && previewView.width > 0 && previewView.height > 0) {
                try {
                    val ready = future.get()
                    provider = ready
                    val preview = Preview.Builder().setTargetRotation(previewView.display.rotation).build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    ready.bindToLifecycle(owner,CameraSelector.DEFAULT_FRONT_CAMERA,preview)
                    cameraPreview = preview
                    main.post(capture)
                } catch (error: Exception) {
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
            main.removeCallbacks(capture)
            previewCapture.close()
            previewView.removeOnLayoutChangeListener(layout)
            cameraPreview?.let { provider?.unbind(it) }
            executor.execute { tracker?.close() }
            executor.shutdown()
        }
    }
}

/** VTO-only detector: coordinates are normalized against the displayed camera image. */
internal class VtoPreviewFaceTracker(context: Context, backend: TrackingBackend = TrackingBackend.CPU) : AutoCloseable {
    private val detector = FaceLandmarker.createFromOptions(context,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task")
                .setDelegate(if (backend == TrackingBackend.GPU) Delegate.GPU else Delegate.CPU).build())
            .setRunningMode(RunningMode.VIDEO).setNumFaces(1)
            .setMinFaceDetectionConfidence(.35f).setMinFacePresenceConfidence(.35f)
            .setMinTrackingConfidence(.4f).build())
    private var lastTimestamp = -1L
    var lastTiming: VtoDiagnostics.Detection? = null
        private set

    fun detect(preview: Bitmap, timestampMs: Long): FaceAnalyzer.FaceMetrics {
        val prepareStart=SystemClock.uptimeMillis()
        val width = preview.width
        val height = preview.height
        // Preserve aspect ratio; normalized landmarks still match the full displayed preview.
        val ratio = (512f / maxOf(width, height)).coerceAtMost(1f)
        val input = if (ratio < 1f) Bitmap.createScaledBitmap(preview,
            (width * ratio).roundToInt().coerceAtLeast(1),
            (height * ratio).roundToInt().coerceAtLeast(1), true) else preview
        val image = BitmapImageBuilder(input).build()
        val modelStart=SystemClock.uptimeMillis()
        var modelEnd=modelStart
        val result = try {
            lastTimestamp = timestampMs.coerceAtLeast(lastTimestamp + 1L)
            detector.detectForVideo(image, lastTimestamp).also { modelEnd=SystemClock.uptimeMillis() }
        } finally { image.close(); if(input !== preview) input.recycle() }
        val points = result.faceLandmarks().firstOrNull()?.map {
            FaceAnalyzer.LandmarkPoint(it.x(), it.y(), it.z())
        }.orEmpty()
        lastTiming=VtoDiagnostics.Detection(modelStart-prepareStart,modelEnd-modelStart,SystemClock.uptimeMillis()-modelEnd)
        return FaceAnalyzer.FaceMetrics(0f, 0f, 0f, "", points, width, height, points.size >= 468)
    }
    override fun close() = detector.close()
}