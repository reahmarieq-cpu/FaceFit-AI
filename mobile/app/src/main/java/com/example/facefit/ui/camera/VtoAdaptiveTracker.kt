package com.example.facefit.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.camera.TrackingBackend
import com.example.facefit.ui.tryon.tracking.VtoDiagnostics

/**
 * Compatibility tracker while native backend probing is disabled.
 * On-device calibration crashed in MediaPipe Graph.nativeMovePacketToInputStream
 * during the GPU probe. SIGSEGV cannot be recovered with a Kotlin catch block.
 * Never initialize a GPU probe or reuse a cached GPU choice here.
 * Creation, detection and close must stay on the camera tracking executor.
 */
internal class VtoAdaptiveTracker(context: Context) : AutoCloseable {
    private val tracker = VtoPreviewFaceTracker(context, TrackingBackend.CPU)
    val backend: TrackingBackend = TrackingBackend.CPU
    val lastTiming: VtoDiagnostics.Detection? get() = tracker.lastTiming

    init {
        Log.i("FaceFitVTODiag", "event=backendSelected backend=CPU reason=nativeCalibrationDisabled")
    }

    fun takeFallbackNotice(): String? = null

    fun detect(bitmap: Bitmap, timestampMs: Long): FaceAnalyzer.FaceMetrics =
        tracker.detect(bitmap, timestampMs)

    override fun close() = tracker.close()
}
