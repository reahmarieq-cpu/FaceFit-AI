package com.example.facefit.ui.tryon.tracking

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.camera.TrackingBackend
import java.util.IdentityHashMap

/** Debug-only, bounded timing correlation. No images or landmark coordinates are logged.
 * Capture starts at the analysis callback (or preview readback fallback), not sensor exposure. Render timing is CPU submission,
 * not GPU completion or the moment the display presents pixels.
 * All registry access occurs on the main thread.
 */
internal object VtoDiagnostics {
    private const val TAG="FaceFitVTODiag"
    private var enabled=false
    private var fullEnabled=false
    private var logEveryFrame=false
    private var lastLoggedSequence=0L
    private var lastVisibleSubmitMs: Long? = null
    private var warnedPose: FaceAnalyzer.FaceMetrics? = null
    private val fullPending=IdentityHashMap<FaceAnalyzer.FaceMetrics,FullSample>()
    data class FullSample(val session: Long,val id: Long,val captureElapsedNs: Long,
        val sensorTimestampNs: Long?,val sensorAgeMs: Double?)
    fun fullSample(face: FaceAnalyzer.FaceMetrics?): FullSample? = if(fullEnabled) fullPending[face] else null
    private var session=0L
    private var sequence=0L
    private var lastLog=0L
    private var previousFace: Boolean?=null
    private val pending=IdentityHashMap<FaceAnalyzer.FaceMetrics,Sample>()
    data class Detection(val prepareMs: Long,val modelMs: Long,val extractMs: Long)
    data class Sample(val session: Long,val id: Long,val capture: Long,val delivered: Long,val selected: Boolean)
    fun start(context: Context, scope: String = "previewReadbackToCpuSubmit") {
        enabled=context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        warnedPose = null
        session++;sequence=0;lastLog=0;previousFace=null;pending.clear();fullPending.clear()
        logEveryFrame=enabled && Log.isLoggable("FaceFitVTOFrames",Log.VERBOSE)
        fullEnabled=enabled && Log.isLoggable("FaceFitVTOTiming",Log.VERBOSE)
        if(fullEnabled) Log.i("FaceFitVTOTiming","session=$session event=start sensorAge=whenRealtimeClockKnown gpuCompletion=fenceObservedUpperBound displayPresentation=requiresPerfettoFrameTimeline")
        if(enabled) Log.i(TAG,"session=$session event=start scope=$scope logging=${if (logEveryFrame) "perResult" else "sampled"} excludes=sensorAge,gpuCompletion,displayPresentation")
    }
    fun delivered(face: FaceAnalyzer.FaceMetrics,backend: TrackingBackend,start: Long,captured: Long,
        workerStart: Long,modelStart: Long,finished: Long,timing: Detection?,inputWidth: Int,inputHeight: Int,
        captureElapsedNs: Long=SystemClock.elapsedRealtimeNanos(),sensorTimestampNs: Long?=null,sensorAgeMs: Double?=null) {
        if(!enabled) return
        val now=SystemClock.uptimeMillis()
        val id=++sequence
        val transition=previousFace!=face.isFaceDetected
        val sampled=transition || now-lastLog>=1000L
        val selected=logEveryFrame || sampled
        if(sampled) lastLog=now
        previousFace=face.isFaceDetected
        // Bound references even when a result is superseded before the renderer sees it.
        if(pending.size>=32) pending.clear()
        pending[face]=Sample(session,id,start,now,selected)
        if(fullEnabled && sampled) {
            if(fullPending.size>=32) fullPending.clear()
            fullPending[face]=FullSample(session,id,captureElapsedNs,sensorTimestampNs,sensorAgeMs)
            Log.i("FaceFitVTOTiming","session=$session frame=$id stage=input captureElapsedNs=$captureElapsedNs sensorTimestampNs=$sensorTimestampNs sensorAgeMs=${sensorAgeMs ?: "unavailable_clock_or_preview_input"}")
        }
        if(selected) {
            val deliveredSinceLastLog = id - lastLoggedSequence
            lastLoggedSequence = id
            Log.i(TAG,"session=$session frame=$id stage=tracking deliveredSinceLastLog=$deliveredSinceLastLog backend=$backend face=${face.isFaceDetected} faceTransition=$transition input=${inputWidth}x$inputHeight captureMs=${captured-start} workerQueueMs=${workerStart-captured} setupMs=${modelStart-workerStart} prepareMs=${timing?.prepareMs} modelMs=${timing?.modelMs} extractMs=${timing?.extractMs} inferenceMs=${finished-modelStart} deliveryQueueMs=${now-finished} captureToDeliveryMs=${now-start}")
        }
    }
    fun faceState(detected: Boolean, published: Boolean, state: String, slowed: Boolean) {
        if (fullEnabled && (!detected || logEveryFrame)) {
            Log.i(TAG, "session=$session event=faceState rawDetected=$detected published=$published state=$state inferenceSlowdown=$slowed")
        }
    }

    fun render(face: FaceAnalyzer.FaceMetrics?,cpuStart: Long,poseDone: Long,end: Long,submitted: Boolean,visible: Boolean, meshIntervalNanos: Long = 0L) {
        if (!enabled) return
        if (submitted) {
            if (visible && logEveryFrame) {
                val gapMs = lastVisibleSubmitMs?.let { end - it }
                lastVisibleSubmitMs = end
                Log.v(TAG, "session=$session event=visibleSubmit gapSincePreviousSubmitMs=$gapMs submitUptimeMs=$end")
            } else if (!visible) {
                lastVisibleSubmitMs = null
            }
        }
        if (face == null) return
        val poseCostMs = poseDone - cpuStart
        val budgetMs = meshIntervalNanos / 4_000_000.0
        if (fullEnabled && meshIntervalNanos > 0L && poseCostMs > budgetMs && face !== warnedPose) {
            warnedPose = face
            Log.w(TAG, "session=$session event=poseBudgetExceeded poseAndMaskCpuMs=$poseCostMs budgetMs=$budgetMs")
        }
        val sample=pending[face] ?: return
        if(submitted) pending.remove(face)
        if(!sample.selected) return
        // Retry failures are aggregated by time to avoid logging once per display refresh.
        if(!submitted && end-lastRenderRetry<1000L) return
        if(!submitted) lastRenderRetry=end
        Log.i(TAG,"session=${sample.session} frame=${sample.id} stage=render face=${face.isFaceDetected} visible=$visible submitted=$submitted deliveryToRenderMs=${cpuStart-sample.delivered} poseAndMaskCpuMs=${poseDone-cpuStart} submitCpuMs=${end-poseDone} captureToCpuSubmitMs=${end-sample.capture}")
    }
    private var lastRenderRetry=0L
    fun stop() { warnedPose=null;pending.clear();fullPending.clear();fullEnabled=false; if(enabled) Log.i(TAG,"session=$session event=stop"); enabled=false }
}
