package com.example.facefit.ui.tryon.tracking

import android.graphics.Matrix
import com.example.facefit.ui.camera.VtoEyeMotion
import com.example.facefit.ui.camera.VtoLatencyEstimator
import com.example.facefit.ui.camera.VtoFaceLossGrace
import com.example.facefit.facefit.analysis.FaceAnalyzer

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

    @Synchronized
    internal fun clear() {
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
