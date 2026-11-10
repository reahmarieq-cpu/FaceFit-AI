package com.facefit.kiosk.vto.ui.tryon.tracking

import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer

/** Main-thread latest-result handoff. Deliberately not Compose state: the render frame
 * reads this directly, without waiting for recomposition or accumulating old results.
 */
class VtoTrackingFeed {
    var latest: FaceAnalyzer.FaceMetrics? = null
        private set
    private var receivedAt = 0L
    fun publish(face: FaceAnalyzer.FaceMetrics?, now: Long = System.nanoTime()) {
        latest = face
        receivedAt = now
    }
    /** Do not leave glasses floating over an old pose when tracking stalls. */
    fun freshAt(now: Long): FaceAnalyzer.FaceMetrics? =
        latest.takeIf { (now - receivedAt).coerceAtLeast(0L) <= 250_000_000L }
}
