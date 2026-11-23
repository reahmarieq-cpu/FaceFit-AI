package com.example.facefit.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.camera.TrackingBackend
import com.example.facefit.ui.tryon.tracking.VtoDiagnostics

/** Own and use this tracker on one executor so the GPU context has a single owner. */
internal class VtoAdaptiveTracker(private val context: Context) : AutoCloseable {
    var backend = TrackingBackend.GPU
        private set
    private var fallbackNotice: String? = null
    private var tracker = createWithFallback()
    val lastTiming: VtoDiagnostics.Detection? get() = tracker.lastTiming

    private fun create(backend: TrackingBackend): VtoPreviewFaceTracker {
        val created = VtoPreviewFaceTracker(context, backend)
        try {
            created.warmUp()
            return created
        } catch (error: Exception) {
            created.close()
            throw error
        }
    }

    private fun createWithFallback(): VtoPreviewFaceTracker = try {
        create(TrackingBackend.GPU)
    } catch (error: Exception) {
        fallback(error)
    }

    private fun fallback(error: Exception): VtoPreviewFaceTracker {
        Log.w("FaceFitVTODiag", "event=backendFallback backend=CPU reason=gpuDelegateFailure", error)
        backend = TrackingBackend.CPU
        fallbackNotice = "Using compatible face tracking"
        return create(TrackingBackend.CPU)
    }

    fun takeFallbackNotice(): String? = fallbackNotice.also { fallbackNotice = null }

    fun detect(bitmap: Bitmap, timestampMs: Long): FaceAnalyzer.FaceMetrics = try {
        tracker.detect(bitmap, timestampMs)
    } catch (error: Exception) {
        if (backend != TrackingBackend.GPU) throw error
        tracker.close()
        tracker = fallback(error)
        tracker.detect(bitmap, timestampMs)
    }

    override fun close() = tracker.close()
}
