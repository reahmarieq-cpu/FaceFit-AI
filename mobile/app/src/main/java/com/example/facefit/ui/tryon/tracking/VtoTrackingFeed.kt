package com.example.facefit.ui.tryon.tracking

import com.example.facefit.facefit.analysis.FaceAnalyzer

/** Main-thread latest-result handoff. Deliberately not Compose state: the render frame
 * reads this directly, without waiting for recomposition or accumulating old results.
 */
class VtoTrackingFeed {
    var latest: FaceAnalyzer.FaceMetrics? = null
        private set
    fun publish(face: FaceAnalyzer.FaceMetrics?) { latest=face }
}
