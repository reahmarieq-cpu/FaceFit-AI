package com.facefit.kiosk.vto.ui.tryon.tracking

import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer

/** Main-thread handoff from analysis to rendering. Misses retain the last usable sample briefly. */
class VtoTrackingFeed {
    var latest: FaceAnalyzer.FaceMetrics? = null
        private set
    private var lastTracked: FaceAnalyzer.FaceMetrics? = null
    private var trackedAt = 0L
    private var firstMissAt: Long? = null

    fun publish(face: FaceAnalyzer.FaceMetrics?, now: Long = System.nanoTime()) {
        latest = face
        when {
            face == null -> {
                lastTracked = null
                firstMissAt = null
            }
            face.isFaceDetected && face.landmarks.size >= 468 -> {
                lastTracked = face
                trackedAt = now
                firstMissAt = null
            }
            firstMissAt == null -> firstMissAt = now
        }
    }

    fun freshAt(now: Long): FaceAnalyzer.FaceMetrics? {
        val miss = firstMissAt
        if (miss != null && now - miss >= 250_000_000L) return null
        return lastTracked.takeIf { now - trackedAt < 350_000_000L }
    }
}
