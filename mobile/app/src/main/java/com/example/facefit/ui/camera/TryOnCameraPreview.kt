package com.example.facefit.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Rect
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraCharacteristics
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.ImageProxy
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.camera.TrackingBackend
import com.example.facefit.facefit.tryon.tracking.VtoLandmarkStabilizer
import com.example.facefit.ui.tryon.tracking.VtoDiagnostics
import com.example.facefit.ui.tryon.tracking.VtoMotionFeed
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Suppress("UNUSED_PARAMETER")
@Composable
fun TryOnCameraPreview(
    onResult: (FaceAnalyzer.FaceMetrics) -> Unit,
    onError: (String) -> Unit,
    backend: TrackingBackend? = null,
    autoSelectBackend: Boolean = false, // Retained for source compatibility; no dual-detector probing.
    onBackendFallback: (String) -> Unit = {},
    onTrackingStatus: (String?) -> Unit = {},
    onTimedResult: (FaceAnalyzer.FaceMetrics, sampleNanos: Long, deliveryNanos: Long) -> Unit = { _, _, _ -> },
    motionFeed: VtoMotionFeed? = null,
    maxInputDimension: Int = VtoHardwareBudget.DEFAULT_ANALYSIS_DIMENSION
) {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val resultCallback by rememberUpdatedState(onResult)
    val timedResultCallback by rememberUpdatedState(onTimedResult)
    val errorCallback by rememberUpdatedState(onError)
    val statusCallback by rememberUpdatedState(onTrackingStatus)
    val fallbackCallback by rememberUpdatedState(onBackendFallback)
    val previewView=remember(context) { PreviewView(context).apply {
        implementationMode=PreviewView.ImplementationMode.COMPATIBLE
        scaleType=PreviewView.ScaleType.FILL_CENTER
    } }
    AndroidView(factory={ previewView },modifier=Modifier.fillMaxSize())

    DisposableEffect(previewView,owner,backend,motionFeed,maxInputDimension) {
        VtoDiagnostics.start(context,"analysisCallbackToCpuSubmit")
        val executor=Executors.newSingleThreadExecutor()
        val analyzerExecutor=Executors.newSingleThreadExecutor()
        val trackerReady=AtomicBoolean(false)
        val main=Handler(Looper.getMainLooper())
        val disposed=AtomicBoolean(false)
        val unavailable=AtomicBoolean(false)
        val resumed=AtomicBoolean(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val mode=AtomicInteger(0) // 0 binding, 1 direct, 2 preview fallback, 3 disposed.
        val realtimeSensor=AtomicBoolean(false)
        val logCadence = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
            Log.isLoggable("FaceFitVTOTiming", Log.VERBOSE)
        val cadence = VtoCameraCadence(logCadence)
        val thermal = VtoThermalMonitor.create(context)
        if (resumed.get()) thermal.start()
        val future=ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider?=null
        var cameraPreview: Preview?=null
        var cameraAnalysis: ImageAnalysis?=null
        var tracker: VtoPreviewFaceTracker?=null // All native tracker access stays on executor.
        var activeBackend=backend ?: preferredVtoBackend()
        val rgba=VtoRgbaBuffer()
        val previewCapture=VtoPreviewCapture(maxOf(maxInputDimension, VtoHardwareBudget.DEFAULT_ANALYSIS_DIMENSION))
        val validator=VtoLandmarkStabilizer()
        val busy=AtomicBoolean(false)

        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        fun report(error: Throwable) {
            unavailable.set(true)
            Log.e("FaceFitCamera", "Camera/tracking unavailable on $deviceName", error)
            main.post { if(!disposed.get()) errorCallback(
                if(error is LinkageError) "Face tracking is not supported by this device's native libraries."
                else "Face tracking failed. Reopen try-on to retry.") }
        }
        fun createTracker(which: TrackingBackend): VtoPreviewFaceTracker {
            val created=VtoPreviewFaceTracker(context, which, maxInputDimension) {
                thermal.budget.analysisDimension(maxOf(maxInputDimension, VtoHardwareBudget.DEFAULT_ANALYSIS_DIMENSION))
            }
            try { created.warmUp(); return created }
            catch(error: Throwable) { runCatching { created.close() }; throw error }
        }
        fun cpuFallback(error: Throwable) {
            Log.w("FaceFitVTO","GPU tracking failed; using CPU",error)
            val old=tracker; tracker=null
            runCatching { old?.close() }.onFailure { Log.w("FaceFitVTO","GPU tracker cleanup failed",it) }
            activeBackend=TrackingBackend.CPU
            main.post { if(!disposed.get()) fallbackCallback("Using compatible face tracking") }
            tracker=createTracker(TrackingBackend.CPU)
        }
        fun detect(bitmap: Bitmap, stamp: Long, crop: Rect?=null, mirror: Boolean=false): FaceAnalyzer.FaceMetrics {
            try { return requireNotNull(tracker).detect(bitmap,stamp,crop,mirror) }
            catch(error: Exception) {
                if(activeBackend!=TrackingBackend.GPU) throw error
                cpuFallback(error)
                // bitmap is borrowed. The previous tracker only closes its own scaled MPImage.
                return requireNotNull(tracker).detect(bitmap,stamp,crop,mirror)
            }
        }

        data class Completed(val result: FaceAnalyzer.FaceMetrics, val analysisCrop: Rect?,
            val started: Long,val captured: Long,val inferenceStart: Long,val finished: Long,
            val backend: TrackingBackend,val timing: VtoDiagnostics.Detection?,
            val width: Int,val height: Int,val sourceMode: Int,
            val captureElapsedNs: Long,val sensorTimestampNs: Long?,val sensorAgeMs: Double?)
        val pending=AtomicReference<Completed?>(null)
        val deliveryScheduled=AtomicBoolean(false)
        lateinit var fallback: (String)->Unit
        lateinit var deliver: Runnable
        fun enqueue(completed: Completed) {
            pending.set(completed)
            if(deliveryScheduled.compareAndSet(false,true)) main.post(deliver)
        }
        deliver=Runnable {
            val data=pending.getAndSet(null)
            deliveryScheduled.set(false)
            if(data!=null && !disposed.get() && mode.get()==data.sourceMode &&
                owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                val width = previewView.width
                val height = previewView.height
                val transform = when {
                    width <= 0 || height <= 0 -> null
                    data.analysisCrop != null -> {
                        // Preview and upright analysis share one CameraX viewport. Use its crop
                        // rather than composing independent sensor matrices across the two streams.
                        if (previewView.sensorToViewTransform == null) null
                        else VtoCameraFrame.frontViewportToPreview(data.analysisCrop, width, height)
                    }
                    else -> Matrix().apply {
                        setScale(width.toFloat() / data.result.imageWidth, height.toFloat() / data.result.imageHeight)
                    }
                }
                val mapped = when {
                    transform != null -> VtoCameraFrame.map(data.result, transform, width, height)
                    else -> null // Wait for preview geometry without rebinding the camera.
                }
                if(mapped!=null) {
                    val validated=requireNotNull(validator.sample(mapped.copy(snrFactor = cadence.snrFactor(data.sensorTimestampNs, data.captureElapsedNs)),
                        data.started * 1_000_000L))
                    VtoDiagnostics.delivered(validated,data.backend,data.started,data.captured,data.inferenceStart,
                        data.inferenceStart,data.finished,data.timing,data.width,data.height,
                        data.captureElapsedNs,data.sensorTimestampNs,data.sensorAgeMs)
                    val deliveryNanos = System.nanoTime()
                    com.example.facefit.ui.tryon.tracking.VtoBench.pose(data.timing?.modelMs ?: (data.finished - data.inferenceStart), validated.isFaceDetected, data.backend.toString(), deliveryNanos)
                    // Convert capture age to the same monotonic clock used by Choreographer.
                    val ageNanos = (SystemClock.elapsedRealtimeNanos() - data.captureElapsedNs)
                        .coerceAtLeast(0L) + ((data.sensorAgeMs ?: 0.0) * 1_000_000).toLong()
                    val sampleNanos = deliveryNanos - ageNanos
                    var publishResult = true
                    if (motionFeed != null && transform != null) {
                        val inferenceMs = data.timing?.modelMs ?: (data.finished - data.inferenceStart)
                        val estimate = motionFeed.measure(inferenceMs.coerceAtLeast(0L) * 1_000_000L, sampleNanos, ageNanos, validated.isFaceDetected)
                        publishResult = motionFeed.publish(VtoMotionFeed.Frame(validated,
                            data.result.imageWidth, data.result.imageHeight,
                            transform, ageNanos, deliveryNanos, estimate))
                        VtoDiagnostics.faceState(validated.isFaceDetected, publishResult, motionFeed.faceState.name, estimate.slowed)
                    }
                    if (publishResult) {
                        timedResultCallback(validated, sampleNanos, deliveryNanos)
                        statusCallback(null)
                        resultCallback(validated)
                    }
                }
            }
            if(pending.get()!=null && deliveryScheduled.compareAndSet(false,true)) main.post(deliver)
        }
        val capture=object: Runnable {
            override fun run() {
                if(disposed.get() || unavailable.get() || mode.get()!=2) return
                if(previewView.previewStreamState.value!=PreviewView.StreamState.STREAMING ||
                    !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || !trackerReady.get() ||
                    !busy.compareAndSet(false,true)) { main.postDelayed(this,16L); return }
                val started=SystemClock.uptimeMillis()
                val elapsed=SystemClock.elapsedRealtimeNanos()
                val bitmap=try { previewCapture.capture(previewView) } catch(error: Exception) {
                    Log.w("FaceFitVTO","Preview capture unavailable",error); null
                }
                if(bitmap==null) { busy.set(false); main.postDelayed(this,16L); return }
                val captured=SystemClock.uptimeMillis()
                executor.execute {
                    try {
                        if(!disposed.get() && !unavailable.get()) {
                            val inferenceStart=SystemClock.uptimeMillis()
                            val result=detect(bitmap,started)
                            enqueue(Completed(result,null,started,captured,inferenceStart,SystemClock.uptimeMillis(),
                                activeBackend,tracker?.lastTiming,tracker?.inputWidth ?: 0, tracker?.inputHeight ?: 0,
                                2,elapsed,null,null))
                        }
                    } catch(error: Exception) { report(error) }
                    catch(error: LinkageError) { report(error) }
                    finally { bitmap.recycle(); busy.set(false); if(!disposed.get()) main.post(this) }
                }
            }
        }
        @Suppress("UnsafeOptInUsageError")
        fun previewBuilder(): Preview.Builder {
            val builder = Preview.Builder()
                .setTargetRotation(previewView.display.rotation)
                .setResolutionSelector(displayResolutionSelector())
            val interop = Camera2Interop.Extender(builder)
            val frontInfo = provider?.availableCameraInfos?.firstOrNull {
                Camera2CameraInfo.from(it).getCameraCharacteristic(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT
            }
            val cameraInfo = frontInfo?.let { Camera2CameraInfo.from(it) }
            val steadyVideoRate = Range(30, 30)
            val availableRates = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            if (availableRates?.contains(steadyVideoRate) == true) {
                builder.setTargetFrameRate(steadyVideoRate)
                interop.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, steadyVideoRate)
            }
            val modes = cameraInfo?.getCameraCharacteristic(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
            val af = when {
                modes?.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) == true -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                modes?.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) == true -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                else -> null // Fixed-focus front cameras must retain their supported default.
            }
            if (af != null) interop.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, af)
            interop.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            interop.setSessionCaptureCallback(cadence)
            return builder
        }

        fallback={ reason ->
            if(!disposed.get() && mode.getAndSet(2)!=2) {
                pending.set(null)
                motionFeed?.clear()
                main.removeCallbacks(capture)
                try {
                    cameraAnalysis?.clearAnalyzer()
                    cameraAnalysis?.let { provider?.unbind(it) }; cameraAnalysis=null
                    cameraPreview?.let { provider?.unbind(it) }
                    val ready = requireNotNull(provider)
                    @Suppress("UnsafeOptInUsageError")
                    fun bindPreviewOnly(preferQuality: Boolean): Preview {
                        val builder = if (preferQuality) previewBuilder()
                        else Preview.Builder().setTargetRotation(previewView.display.rotation).also {
                            Camera2Interop.Extender(it).setSessionCaptureCallback(cadence)
                        }
                        val preview = builder.build()
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        cameraPreview = preview
                        ready.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview)
                        return preview
                    }
                    val preview = try {
                        bindPreviewOnly(preferQuality = true)
                    } catch (unsupported: IllegalArgumentException) {
                        Log.w("FaceFitCamera", "Preferred preview unsupported on $deviceName; retrying CameraX defaults", unsupported)
                        cameraPreview?.let { ready.unbind(it) }
                        bindPreviewOnly(preferQuality = false)
                    }
                    Log.i("FaceFitCamera", "Camera bound on $deviceName mode=previewOnly reason=$reason " +
                        "preview=${preview.resolutionInfo?.resolution} trackerReadbackMax=${maxInputDimension * 2}")
                    Log.w("FaceFitVTODiag","event=inputSelected path=previewReadback reason=$reason")
                    main.post(capture)
                } catch(error: Exception) {
                    Log.e("FaceFitCamera", "Preview-only camera binding failed on $deviceName", error)
                    report(error)
                }
            }
        }
        // Eager initialization and blank warm-up precede analyzer work on the same executor.
        executor.execute {
            try {
                if(!disposed.get()) {
                    tracker=try { createTracker(activeBackend) } catch(error: Exception) {
                        if(activeBackend!=TrackingBackend.GPU) throw error
                        cpuFallback(error); requireNotNull(tracker)
                    }
                    trackerReady.set(true)
                    Log.i("FaceFitVTODiag","event=trackerReady backend=$activeBackend warmed=true maxInputDimension=$maxInputDimension")
                }
            } catch(error: Exception) { report(error) }
            catch(error: LinkageError) { report(error) }
        }

        data class AnalysisFrame(
            val bitmap: Bitmap,
            val crop: Rect,
        )

        fun copyAnalysisFrame(image: ImageProxy): AnalysisFrame? = try {
            if (disposed.get() || unavailable.get() || !resumed.get() || mode.get() != 1) {
                null

            } else if (image.imageInfo.rotationDegrees != 0) {
                main.post { fallback("unsupportedAnalysisBuffer") }
                null
            } else {
                AnalysisFrame(rgba.copy(image), Rect(image.cropRect))
            }
        } finally {
            // The inference worker only sees the copied bitmap and metadata.
            image.close()
        }

        var busyDrops = 0
        var dropsSinceAccepted = 0
        var acceptedInWindow = 0
        var lastAcceptedNs: Long? = null
        var lastIntakeLogNs = 0L
        val analyzer = ImageAnalysis.Analyzer { image ->
            if (disposed.get() || unavailable.get() || !resumed.get() || mode.get() != 1 || !trackerReady.get()) {
                image.close()
                return@Analyzer
            }
            val elapsed = SystemClock.elapsedRealtimeNanos()
            val timestamp = image.imageInfo.timestamp
            val started = SystemClock.uptimeMillis()
            val age = if (realtimeSensor.get() && timestamp > 0L && elapsed >= timestamp) {
                (elapsed - timestamp) / 1_000_000.0
            } else null

            // Reserve the reusable bitmap before copying. There is no queue of inference inputs.
            if (!busy.compareAndSet(false, true)) {
                image.close()
                busyDrops++
                dropsSinceAccepted++
                return@Analyzer
            }

            var handedOff = false
            try {
                val frame = copyAnalysisFrame(image) ?: return@Analyzer
                val captured = SystemClock.uptimeMillis()

                executor.execute {
                    try {
                        if (disposed.get() || unavailable.get() || !resumed.get() || mode.get() != 1) return@execute
                        val inferenceStart = SystemClock.uptimeMillis()
                        val result = detect(frame.bitmap, started, mirror = true)
                        enqueue(Completed(result, frame.crop, started, captured, inferenceStart,
                            SystemClock.uptimeMillis(), activeBackend, tracker?.lastTiming,
                            tracker?.inputWidth ?: 0, tracker?.inputHeight ?: 0,
                            1, elapsed, timestamp, age))
                    } catch (error: Exception) {
                        report(error)
                    } catch (error: LinkageError) {
                        report(error)
                    } finally {
                        busy.set(false)
                    }
                }
                handedOff = true
                if (logCadence) {
                    acceptedInWindow++
                    val previous = lastAcceptedNs
                    lastAcceptedNs = elapsed
                    if (lastIntakeLogNs == 0L) lastIntakeLogNs = elapsed
                    val windowNs = elapsed - lastIntakeLogNs
                    if (windowNs >= 1_000_000_000L) {
                        cadence.logAnalyzerEntry(timestamp, elapsed, age, busyDrops, captured - started,
                            windowNs, acceptedInWindow, dropsSinceAccepted, previous?.let { elapsed - it })
                        lastIntakeLogNs = elapsed
                        busyDrops = 0
                        acceptedInWindow = 0
                    }
                }
                dropsSinceAccepted = 0
            } catch (error: IllegalArgumentException) {
                Log.w("FaceFitVTO", "Analysis buffer unsupported", error)
                main.post { fallback("analysisBufferUnsupported") }
            } catch (error: Exception) {
                report(error)
            } finally {
                if (!handedOff) busy.set(false)
            }
        }
        @Suppress("UnsafeOptInUsageError")
        fun timestampSource(camera: androidx.camera.core.Camera) {
            realtimeSensor.set(runCatching { Camera2CameraInfo.from(camera.cameraInfo)
                .getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE)==
                    CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME }.getOrDefault(false))
        }
        val bind=Runnable {
            if(disposed.get() || cameraPreview!=null || previewView.width<=0 || previewView.height<=0) return@Runnable
            try {
                val ready=future.get(); provider=ready
                val preview=previewBuilder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider); cameraPreview=preview
                val selector=ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    // Inference uses the full native-aspect frame; viewport cropping is display-only.
                    .setResolutionFilter { sizes, _ -> sizes.filter {
                        minOf(it.width, it.height) >= VtoHardwareBudget.MIN_ANALYSIS_DIMENSION
                    } }
                    .setResolutionStrategy(ResolutionStrategy(
                        Size(480, 360),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    ))
                    .build()
                val analysis=ImageAnalysis.Builder().setResolutionSelector(selector)
                    .setTargetRotation(previewView.display.rotation)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setOutputImageRotationEnabled(true)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(analyzerExecutor,analyzer); cameraAnalysis=analysis
                try {
                    val viewport=requireNotNull(previewView.viewPort)
                    val group=UseCaseGroup.Builder().setViewPort(viewport).addUseCase(preview).addUseCase(analysis).build()
                    timestampSource(ready.bindToLifecycle(owner,CameraSelector.DEFAULT_FRONT_CAMERA,group))
                    mode.set(1)
                    Log.i("FaceFitCamera", "Camera bound on $deviceName mode=previewAndAnalysis " +
                        "preview=${preview.resolutionInfo?.resolution} analysis=${analysis.resolutionInfo?.resolution}")
                    Log.i("FaceFitVTODiag","event=inputSelected path=directCamera queue=keepOnlyLatest")
                } catch(unsupported: IllegalArgumentException) {
                    Log.e("FaceFitCamera", "Preview+analysis binding failed on $deviceName; retrying preview only", unsupported)
                    fallback("unsupportedCameraCombination")
                }
            } catch(error: Exception) { report(error) }
        }
        val lifecycleObserver=LifecycleEventObserver { _,_ ->
            resumed.set(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            if (resumed.get()) thermal.start() else {
                thermal.close()
                pending.set(null)
                motionFeed?.clear()
            }
        }
        owner.lifecycle.addObserver(lifecycleObserver)
        val layout=android.view.View.OnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> if(future.isDone && cameraPreview==null) bind.run() }
        previewView.addOnLayoutChangeListener(layout)
        future.addListener(bind,ContextCompat.getMainExecutor(context))
        onDispose {
            disposed.set(true); mode.set(3); pending.set(null)
            thermal.close()
            motionFeed?.clear()
            VtoDiagnostics.stop(); main.removeCallbacks(capture); main.removeCallbacks(deliver)
            owner.lifecycle.removeObserver(lifecycleObserver)
            previewView.removeOnLayoutChangeListener(layout)
            cameraAnalysis?.let { it.clearAnalyzer(); provider?.unbind(it) }
            cameraPreview?.let { provider?.unbind(it) }
            previewCapture.close()
            // Let the last analyzer finish handing off before scheduling worker cleanup.
            analyzerExecutor.execute {
                executor.execute {
                    try { tracker?.close() } catch(error: Exception) { Log.w("FaceFitVTO","Tracker cleanup failed",error) }
                    finally { tracker=null; rgba.close() }
                }
                executor.shutdown()
            }
            analyzerExecutor.shutdown()
        }
    }
}

/** Borrow input pixels, own one scaled Bitmap/MPImage for the lifetime of this tracker. */
internal class VtoPreviewFaceTracker(
    context: Context,
    backend: TrackingBackend = TrackingBackend.GPU,
    private val maxInputDimension: Int = VtoHardwareBudget.DEFAULT_ANALYSIS_DIMENSION,
    private val analysisDimension: () -> Int = { maxInputDimension },
) : AutoCloseable {
    init { require(maxInputDimension > 0) }
    private val detector=FaceLandmarker.createFromOptions(context,FaceLandmarker.FaceLandmarkerOptions.builder()
        .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task")
            .setDelegate(if(backend==TrackingBackend.GPU) Delegate.GPU else Delegate.CPU).build())
        .setRunningMode(RunningMode.VIDEO).setNumFaces(1)
        .setOutputFaceBlendshapes(false)
        .setOutputFacialTransformationMatrixes(false)
        .setMinFaceDetectionConfidence(.5f).setMinFacePresenceConfidence(.5f)
        .setMinTrackingConfidence(.25f).build())
    private var lastTimestamp=-1L
    private var input: Bitmap?=null
    private var image: MPImage?=null
    private var canvas: Canvas?=null
    private val paint=Paint(Paint.FILTER_BITMAP_FLAG)
    val inputWidth: Int get() = input?.width ?: 0
    val inputHeight: Int get() = input?.height ?: 0
    var lastTiming: VtoDiagnostics.Detection?=null
        private set

    fun warmUp() {
        val blank=Bitmap.createBitmap(maxOf(maxInputDimension, VtoHardwareBudget.MIN_ANALYSIS_DIMENSION),
            maxOf(maxInputDimension, VtoHardwareBudget.MIN_ANALYSIS_DIMENSION), Bitmap.Config.ARGB_8888)
        try { detect(blank,SystemClock.uptimeMillis()) } finally { blank.recycle() }
    }

    fun detect(preview: Bitmap,timestampMs: Long,crop: Rect?=null,mirrorInput: Boolean=false): FaceAnalyzer.FaceMetrics {
        require(!preview.isRecycled)
        val prepareStart=SystemClock.uptimeMillis()
        val region=crop ?: Rect(0,0,preview.width,preview.height)
        require(!region.isEmpty && region.left>=0 && region.top>=0 && region.right<=preview.width && region.bottom<=preview.height)
        val (w, h) = VtoHardwareBudget.inputSize(region.width(), region.height(), analysisDimension())
        if(input?.width!=w || input?.height!=h) {
            image?.close() // MPImage owns/recycles only OUR bitmap, never preview.
            val owned=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            input=owned; canvas=Canvas(owned); image=BitmapImageBuilder(owned).build()
        }
        val target=requireNotNull(canvas)
        val save=target.save()
        try {
            // Preserve inference-on-mirrored-preview semantics without another Bitmap allocation.
            if(mirrorInput) target.scale(-1f,1f,w*.5f,h*.5f)
            target.drawBitmap(preview,region,RectF(0f,0f,w.toFloat(),h.toFloat()),paint)
        } finally { target.restoreToCount(save) }
        val modelStart=SystemClock.uptimeMillis()
        lastTimestamp=timestampMs.coerceAtLeast(lastTimestamp+1L)
        val result=detector.detectForVideo(requireNotNull(image),lastTimestamp)
        val modelEnd=SystemClock.uptimeMillis()
        val points=result.faceLandmarks().firstOrNull()?.map { FaceAnalyzer.LandmarkPoint(it.x(),it.y(),it.z()) }.orEmpty()
        lastTiming=VtoDiagnostics.Detection(modelStart-prepareStart,modelEnd-modelStart,SystemClock.uptimeMillis()-modelEnd)
        val face=FaceAnalyzer.FaceMetrics(0f,0f,0f,"",points,region.width(),region.height(),points.size>=468)
        return VtoCameraFrame.restoreCrop(face,preview.width,preview.height,region,mirrorInput)
    }
    override fun close() {
        try { detector.close() } finally { image?.close(); image=null; input=null; canvas=null }
    }
}

/** Prefer the best normal video output the camera can bind with the other use cases. */
private fun displayResolutionSelector(): ResolutionSelector = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
    .build()
/** VTO uses GPU by default. CPU is reserved for explicit selection or a delegate failure. */
private fun preferredVtoBackend(): TrackingBackend = TrackingBackend.GPU
