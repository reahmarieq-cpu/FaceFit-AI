package com.facefit.kiosk.vto.ui.tryon.tracking

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer
import com.facefit.kiosk.vto.facefit.camera.TrackingBackend
import java.util.IdentityHashMap

/** Debug-only, bounded timing correlation. No images or landmark coordinates are logged.
 * Capture starts at preview readback, not sensor exposure. Render timing is CPU submission,
 * not GPU completion or the moment the display presents pixels.
 * All registry access occurs on the main thread.
 */
internal object VtoDiagnostics {
    private const val TAG="FaceFitVTODiag"
    private var enabled=false
    private var session=0L
    private var sequence=0L
    private var lastLog=0L
    private var previousFace: Boolean?=null
    private val pending=IdentityHashMap<FaceAnalyzer.FaceMetrics,Sample>()
    data class Detection(val prepareMs: Long,val modelMs: Long,val extractMs: Long)
    data class Sample(val session: Long,val id: Long,val capture: Long,val delivered: Long,val selected: Boolean)
    fun start(context: Context) {
        enabled=context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        session++;sequence=0;lastLog=0;previousFace=null;pending.clear()
        if(enabled) Log.i(TAG,"session=$session event=start scope=previewReadbackToCpuSubmit excludes=sensorAge,gpuCompletion,displayPresentation")
    }
    fun delivered(face: FaceAnalyzer.FaceMetrics,backend: TrackingBackend,start: Long,captured: Long,
        workerStart: Long,modelStart: Long,finished: Long,timing: Detection?,inputWidth: Int,inputHeight: Int) {
        if(!enabled) return
        val now=SystemClock.uptimeMillis()
        val id=++sequence
        val transition=previousFace!=face.isFaceDetected
        val selected=transition || now-lastLog>=1000L
        previousFace=face.isFaceDetected
        // Bound references even when a result is superseded before the renderer sees it.
        if(pending.size>=32) pending.clear()
        pending[face]=Sample(session,id,start,now,selected)
        if(selected) {
            lastLog=now
            Log.i(TAG,"session=$session frame=$id stage=tracking backend=$backend face=${face.isFaceDetected} faceTransition=$transition input=${inputWidth}x$inputHeight captureMs=${captured-start} workerQueueMs=${workerStart-captured} setupMs=${modelStart-workerStart} prepareMs=${timing?.prepareMs} modelMs=${timing?.modelMs} extractMs=${timing?.extractMs} inferenceMs=${finished-modelStart} deliveryQueueMs=${now-finished} captureToDeliveryMs=${now-start}")
        }
    }
    fun render(face: FaceAnalyzer.FaceMetrics?,cpuStart: Long,poseDone: Long,end: Long,submitted: Boolean,visible: Boolean) {
        if(!enabled || face==null) return
        val sample=pending[face] ?: return
        if(submitted) pending.remove(face)
        if(!sample.selected) return
        // Retry failures are aggregated by time to avoid logging once per display refresh.
        if(!submitted && end-lastRenderRetry<1000L) return
        if(!submitted) lastRenderRetry=end
        Log.i(TAG,"session=${sample.session} frame=${sample.id} stage=render face=${face.isFaceDetected} visible=$visible submitted=$submitted deliveryToRenderMs=${cpuStart-sample.delivered} poseAndMaskCpuMs=${poseDone-cpuStart} submitCpuMs=${end-poseDone} captureToCpuSubmitMs=${end-sample.capture}")
    }
    private var lastRenderRetry=0L
    fun stop() { pending.clear(); if(enabled) Log.i(TAG,"session=$session event=stop"); enabled=false }
}
