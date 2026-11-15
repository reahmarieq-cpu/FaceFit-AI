package com.example.facefit.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
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
import kotlin.math.roundToInt

@Suppress("UNUSED_PARAMETER")
@Composable
fun TryOnCameraPreview(
    onResult: (FaceAnalyzer.FaceMetrics) -> Unit,
    onError: (String) -> Unit,
    backend: TrackingBackend = TrackingBackend.GPU,
    autoSelectBackend: Boolean = false, // Retained for source compatibility; no dual-detector probing.
    onBackendFallback: (String) -> Unit = {},
    onTrackingStatus: (String?) -> Unit = {}
) {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val resultCallback by rememberUpdatedState(onResult)
    val errorCallback by rememberUpdatedState(onError)
    val statusCallback by rememberUpdatedState(onTrackingStatus)
    val fallbackCallback by rememberUpdatedState(onBackendFallback)
    val previewView=remember(context) { PreviewView(context).apply {
        implementationMode=PreviewView.ImplementationMode.COMPATIBLE
        scaleType=PreviewView.ScaleType.FILL_CENTER
    } }
    AndroidView(factory={ previewView },modifier=Modifier.fillMaxSize())

    DisposableEffect(previewView,owner,backend) {
        VtoDiagnostics.start(context,"analysisCallbackToCpuSubmit")
        val executor=Executors.newSingleThreadExecutor()
        val main=Handler(Looper.getMainLooper())
        val disposed=AtomicBoolean(false)
        val unavailable=AtomicBoolean(false)
        val resumed=AtomicBoolean(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val mode=AtomicInteger(0) // 0 binding, 1 direct, 2 preview fallback, 3 disposed.
        val realtimeSensor=AtomicBoolean(false)
        val future=ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider?=null
        var cameraPreview: Preview?=null
        var cameraAnalysis: ImageAnalysis?=null
        var tracker: VtoPreviewFaceTracker?=null // All native tracker access stays on executor.
        var activeBackend=backend
        val rgba=VtoRgbaBuffer()
        val previewCapture=VtoPreviewCapture()
        val validator=VtoLandmarkStabilizer()
        val busy=AtomicBoolean(false)
        var missingTransforms=0

        fun report(error: Throwable) {
            unavailable.set(true)
            Log.e("FaceFitVTO","Tracking unavailable",error)
            main.post { if(!disposed.get()) errorCallback(
                if(error is LinkageError) "Face tracking is not supported by this device's native libraries."
                else "Face tracking failed. Reopen try-on to retry.") }
        }
        fun createTracker(which: TrackingBackend): VtoPreviewFaceTracker {
            val created=VtoPreviewFaceTracker(context,which)
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

        data class Completed(val result: FaceAnalyzer.FaceMetrics, val sensorToBuffer: Matrix?,
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
                val mapped=if(data.sensorToBuffer==null) data.result else {
                    val toView=previewView.sensorToViewTransform
                    val transform=toView?.let { VtoCameraFrame.bufferToPreview(data.sensorToBuffer,it) }
                    if(transform!=null && previewView.width>0 && previewView.height>0) {
                        missingTransforms=0
                        VtoCameraFrame.map(data.result,transform,previewView.width,previewView.height)
                    } else { if(++missingTransforms>=15) fallback("previewTransformUnavailable"); null }
                }
                if(mapped!=null) {
                    val validated=requireNotNull(validator.sample(mapped,data.started*1_000_000L))
                    VtoDiagnostics.delivered(validated,data.backend,data.started,data.captured,data.captured,
                        data.inferenceStart,data.finished,data.timing,data.width,data.height,
                        data.captureElapsedNs,data.sensorTimestampNs,data.sensorAgeMs)
                    statusCallback(null); resultCallback(validated)
                }
            }
            if(pending.get()!=null && deliveryScheduled.compareAndSet(false,true)) main.post(deliver)
        }
        val capture=object: Runnable {
            override fun run() {
                if(disposed.get() || unavailable.get() || mode.get()!=2) return
                if(previewView.previewStreamState.value!=PreviewView.StreamState.STREAMING ||
                    !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
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
                                activeBackend,tracker?.lastTiming,bitmap.width,bitmap.height,2,elapsed,null,null))
                        }
                    } catch(error: Exception) { report(error) }
                    catch(error: LinkageError) { report(error) }
                    finally { bitmap.recycle(); busy.set(false); if(!disposed.get()) main.post(this) }
                }
            }
        }
        fallback={ reason ->
            if(!disposed.get() && mode.getAndSet(2)!=2) {
                cameraAnalysis?.clearAnalyzer()
                cameraAnalysis?.let { provider?.unbind(it) }; cameraAnalysis=null
                cameraPreview?.let { provider?.unbind(it) }
                try {
                    val preview=requireNotNull(cameraPreview)
                    provider?.bindToLifecycle(owner,CameraSelector.DEFAULT_FRONT_CAMERA,preview)
                    Log.w("FaceFitVTODiag","event=inputSelected path=previewReadback reason=$reason")
                    main.post(capture)
                } catch(error: Exception) { report(error) }
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
                    Log.i("FaceFitVTODiag","event=trackerReady backend=$activeBackend warmed=true")
                }
            } catch(error: Exception) { report(error) }
            catch(error: LinkageError) { report(error) }
        }

        val analyzer=ImageAnalysis.Analyzer { image ->
            val started=SystemClock.uptimeMillis()
            val elapsed=SystemClock.elapsedRealtimeNanos()
            try {
                if(!disposed.get() && !unavailable.get() && resumed.get() && mode.get()==1) {
                    // Some camera combinations ignore size hints. Never process oversized frames.
                    if(maxOf(image.width,image.height)>640 || image.width.toLong()*image.height>307200 ||
                        image.imageInfo.rotationDegrees!=0) {
                        main.post { fallback("unsupportedAnalysisBuffer") }
                    } else {
                        val sensor=Matrix(image.imageInfo.sensorToBufferTransformMatrix)
                        val timestamp=image.imageInfo.timestamp
                        val age=if(realtimeSensor.get() && timestamp>0 && elapsed>=timestamp)
                            (elapsed-timestamp)/1_000_000.0 else null
                        val bitmap=rgba.copy(image)
                        val captured=SystemClock.uptimeMillis()
                        val inferenceStart=SystemClock.uptimeMillis()
                        val result=detect(bitmap,started,Rect(image.cropRect),true)
                        enqueue(Completed(result,sensor,started,captured,inferenceStart,SystemClock.uptimeMillis(),
                            activeBackend,tracker?.lastTiming,image.width,image.height,1,elapsed,timestamp,age))
                    }
                }
            } catch(error: IllegalArgumentException) {
                Log.w("FaceFitVTO","Analysis buffer unsupported",error)
                main.post { fallback("analysisBufferUnsupported") }
            } catch(error: Exception) { report(error) }
            catch(error: LinkageError) { report(error) }
            finally { image.close() }
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
                val preview=Preview.Builder().setTargetRotation(previewView.display.rotation).build()
                preview.setSurfaceProvider(previewView.surfaceProvider); cameraPreview=preview
                val selector=ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(Size(640,480),ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER))
                    .setResolutionFilter { sizes,_ -> sizes.filter { maxOf(it.width,it.height)<=640 && it.width.toLong()*it.height<=307200 } }
                    .build()
                val analysis=ImageAnalysis.Builder().setResolutionSelector(selector)
                    .setTargetRotation(previewView.display.rotation)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setOutputImageRotationEnabled(true)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor,analyzer); cameraAnalysis=analysis
                try {
                    val viewport=requireNotNull(previewView.viewPort)
                    val group=UseCaseGroup.Builder().setViewPort(viewport).addUseCase(preview).addUseCase(analysis).build()
                    timestampSource(ready.bindToLifecycle(owner,CameraSelector.DEFAULT_FRONT_CAMERA,group))
                    mode.set(1)
                    Log.i("FaceFitVTODiag","event=inputSelected path=directCamera maxPixels=307200 queue=keepOnlyLatest")
                } catch(unsupported: IllegalArgumentException) { fallback("unsupportedCameraCombination") }
            } catch(error: Exception) { report(error) }
        }
        val lifecycleObserver=LifecycleEventObserver { _,_ ->
            resumed.set(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            if(!resumed.get()) pending.set(null)
        }
        owner.lifecycle.addObserver(lifecycleObserver)
        val layout=android.view.View.OnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> if(future.isDone && cameraPreview==null) bind.run() }
        previewView.addOnLayoutChangeListener(layout)
        future.addListener(bind,ContextCompat.getMainExecutor(context))
        onDispose {
            disposed.set(true); mode.set(3); pending.set(null)
            VtoDiagnostics.stop(); main.removeCallbacks(capture); main.removeCallbacks(deliver)
            owner.lifecycle.removeObserver(lifecycleObserver)
            previewView.removeOnLayoutChangeListener(layout)
            cameraAnalysis?.let { it.clearAnalyzer(); provider?.unbind(it) }
            cameraPreview?.let { provider?.unbind(it) }
            previewCapture.close()
            executor.execute {
                try { tracker?.close() } catch(error: Exception) { Log.w("FaceFitVTO","Tracker cleanup failed",error) }
                finally { tracker=null; rgba.close() }
            }
            executor.shutdown()
        }
    }
}

/** Borrow input pixels, own one scaled Bitmap/MPImage for the lifetime of this tracker. */
internal class VtoPreviewFaceTracker(context: Context, backend: TrackingBackend = TrackingBackend.CPU): AutoCloseable {
    private val detector=FaceLandmarker.createFromOptions(context,FaceLandmarker.FaceLandmarkerOptions.builder()
        .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task")
            .setDelegate(if(backend==TrackingBackend.GPU) Delegate.GPU else Delegate.CPU).build())
        .setRunningMode(RunningMode.VIDEO).setNumFaces(1)
        .setMinFaceDetectionConfidence(.30f).setMinFacePresenceConfidence(.30f)
        .setMinTrackingConfidence(.25f).build())
    private var lastTimestamp=-1L
    private var input: Bitmap?=null
    private var image: MPImage?=null
    private var canvas: Canvas?=null
    private val paint=Paint(Paint.FILTER_BITMAP_FLAG)
    var lastTiming: VtoDiagnostics.Detection?=null
        private set

    fun warmUp() {
        val blank=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        try { detect(blank,SystemClock.uptimeMillis()) } finally { blank.recycle() }
    }

    fun detect(preview: Bitmap,timestampMs: Long,crop: Rect?=null,mirrorInput: Boolean=false): FaceAnalyzer.FaceMetrics {
        require(!preview.isRecycled)
        val prepareStart=SystemClock.uptimeMillis()
        val region=crop ?: Rect(0,0,preview.width,preview.height)
        require(!region.isEmpty && region.left>=0 && region.top>=0 && region.right<=preview.width && region.bottom<=preview.height)
        val ratio=(512f/maxOf(region.width(),region.height())).coerceAtMost(1f)
        val w=(region.width()*ratio).roundToInt().coerceAtLeast(1)
        val h=(region.height()*ratio).roundToInt().coerceAtLeast(1)
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
