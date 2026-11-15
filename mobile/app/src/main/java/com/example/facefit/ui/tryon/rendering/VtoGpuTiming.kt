package com.example.facefit.ui.tryon.rendering

import android.os.Build
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.ui.tryon.tracking.VtoDiagnostics
import com.google.android.filament.Engine
import com.google.android.filament.Fence

/** Opt-in diagnostic only. Zero-timeout polling never waits on the UI thread.
 * Observation is an upper bound, NOT an exact hardware completion timestamp.
 * Actual display presentation is measured by SurfaceFlinger FrameTimeline in Perfetto.
 */
internal class VtoGpuTiming(private val engine: Engine): AutoCloseable {
    private data class Pending(val fence: Fence,val sample: VtoDiagnostics.FullSample,val submittedNs: Long)
    private val pending=mutableListOf<Pending>()
    private var lastSession=-1L
    private var lastId=-1L
    private var failureReported=false
    private var lastFenceNs=0L

    fun submitMarker(face: FaceAnalyzer.FaceMetrics?): Boolean {
        val sample=VtoDiagnostics.fullSample(face) ?: return false
        Trace.beginSection("FaceFitVTO.submit.s${sample.session}.m${sample.id}")
        return true
    }
    fun endMarker(marked: Boolean) { if(marked) Trace.endSection() }

    fun submitted(face: FaceAnalyzer.FaceMetrics?) {
        val sample=VtoDiagnostics.fullSample(face) ?: return
        if((sample.session==lastSession && sample.id==lastId) || pending.size>=4 || failureReported) return
        lastSession=sample.session;lastId=sample.id
        val now=SystemClock.elapsedRealtimeNanos()
        if(now-lastFenceNs<1_000_000_000L) return
        lastFenceNs=now
        try {
            val item=Pending(engine.createFence(),sample,now)
            pending.add(item)
            if(Build.VERSION.SDK_INT>=29) Trace.beginAsyncSection("FaceFitVTO.gpuPending",sample.id.toInt())
            engine.flush()
        } catch(error: Exception) {
            failureReported=true
            Log.w("FaceFitVTOTiming","gpuCompletion=unavailable_fence_error",error)
        }
    }

    fun poll() {
        val iterator=pending.iterator()
        while(iterator.hasNext()) {
            val item=iterator.next()
            val now=SystemClock.elapsedRealtimeNanos()
            val status=item.fence.wait(Fence.Mode.DONT_FLUSH,0L)
            if(status==Fence.FenceStatus.TIMEOUT_EXPIRED && now-item.submittedNs<2_000_000_000L) continue
            if(status==Fence.FenceStatus.CONDITION_SATISFIED) {
                Log.i("FaceFitVTOTiming","session=${item.sample.session} frame=${item.sample.id} stage=gpu gpuCompletionObservedNs=$now gpuCompletionObservedAfterSubmitMs=${(now-item.submittedNs)/1_000_000.0} captureToGpuCompletionUpperBoundMs=${(now-item.sample.captureElapsedNs)/1_000_000.0} displayPresentation=requiresPerfettoFrameTimeline")
            } else Log.w("FaceFitVTOTiming","session=${item.sample.session} frame=${item.sample.id} gpuCompletion=unavailable_$status")
            engine.destroyFence(item.fence)
            if(Build.VERSION.SDK_INT>=29) Trace.endAsyncSection("FaceFitVTO.gpuPending",item.sample.id.toInt())
            iterator.remove()
        }
    }
    override fun close() {
        for(item in pending) {
            engine.destroyFence(item.fence)
            if(Build.VERSION.SDK_INT>=29) Trace.endAsyncSection("FaceFitVTO.gpuPending",item.sample.id.toInt())
        }
        pending.clear()
    }
}
