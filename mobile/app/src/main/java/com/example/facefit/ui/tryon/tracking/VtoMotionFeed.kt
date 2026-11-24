package com.example.facefit.ui.tryon.tracking

import android.graphics.Matrix
import com.example.facefit.ui.camera.VtoEyeMotion
import com.example.facefit.ui.camera.VtoLatencyEstimator
import com.example.facefit.ui.camera.VtoFaceLossGrace
import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.tryon.tracking.VtoFrameShiftTracker

/** Shares mesh poses and lightweight motion offsets without retaining camera display pixels. */
class VtoMotionFeed internal constructor() {
    internal data class Frame(
        val face: FaceAnalyzer.FaceMetrics,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val toPreview: Matrix,
        val measuredLatencyNanos: Long,
        val deliveredAtNanos: Long,
        val timing: VtoLatencyEstimator.Snapshot,
        val motionAnchor: VtoEyeMotion.Anchor? = null,
        val recovering: Boolean = false,
        /** Camera frame the mesh came from; frame shifts measured against it carry the same id. */
        val referenceId: Long = 0L,
    ) {
        val measuredMeshNanos: Long get() = timing.cycleNanos
        val sampledAtNanos: Long get() = deliveredAtNanos - measuredLatencyNanos
    }

    @Volatile internal var latest: Frame? = null
        private set
    private val lossGrace = VtoFaceLossGrace()
    private val estimator = VtoLatencyEstimator()
    private var lastSampleAt: Long? = null
    private var recovering = false
    internal val faceState get() = lossGrace.state
    internal val holdingAnchor get() = recovering && latest?.face?.isFaceDetected == true

    /** Main thread, before deciding whether a miss should be published. */
    internal fun measure(inferenceNanos: Long, sampleAt: Long, deliveryAgeNanos: Long, faceDetected: Boolean): VtoLatencyEstimator.Snapshot {
        val interval = lastSampleAt?.let { sampleAt - it } ?: 0L
        lastSampleAt = sampleAt
        return estimator.record(inferenceNanos, interval, deliveryAgeNanos, faceDetected)
    }

    @Synchronized
    internal fun publish(frame: Frame): Boolean {
        val previous = latest
        if (!frame.face.isFaceDetected) recovering = previous?.face?.isFaceDetected == true || recovering
        val state = lossGrace.observe(frame.face.isFaceDetected, previous?.timing ?: frame.timing)
        if (!frame.face.isFaceDetected && state != VtoFaceLossGrace.State.LOST) return false
        latest = if (frame.face.isFaceDetected && recovering) frame.copy(recovering = true) else frame
        if (frame.face.isFaceDetected) recovering = false
        latest = latest?.copy(motionAnchor = null)
        motion = null
        return true
    }

    @Synchronized
    @Suppress("UNUSED_PARAMETER")
    internal fun updateMotion(sourceAnchor: VtoEyeMotion.Anchor?, offset: VtoEyeMotion.Offset?) {
        motion = null
    }

    /** Eye-region displacement since a mesh's camera frame, measured on every camera frame. */
    internal data class ShiftSample(val shift: VtoFrameShiftTracker.Shift, val capturedElapsedNanos: Long)

    @Volatile internal var shift: ShiftSample? = null
        private set
    private val shiftLock = Any()

    /** Camera/worker threads. A shift for an older mesh never replaces one for a newer mesh. */
    internal fun publishShift(next: VtoFrameShiftTracker.Shift?, capturedElapsedNanos: Long) {
        synchronized(shiftLock) {
            val current = shift
            if (next != null && current != null && next.referenceId < current.shift.referenceId) return
            shift = next?.let { ShiftSample(it, capturedElapsedNanos) }
        }
    }

    @Synchronized
    internal fun clear() {
        synchronized(shiftLock) { shift = null }
        lossGrace.reset()
        recovering = false
        estimator.reset()
        lastSampleAt = null
        latest = null
        motion = null
    }

    @Volatile internal var motion: VtoEyeMotion.Offset? = null
        private set
}
