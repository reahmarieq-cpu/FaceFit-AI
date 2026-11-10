package com.example.facefit.facefit.tryon.tracking

import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.tryon.geometry.Vector3

import kotlin.math.abs

/** Stabilizes each new VTO result once, without a display-frame animation or prediction.
 * Thresholds are fractions of eye spacing, so behavior does not depend on screen resolution.
 */
internal class VtoLandmarkStabilizer {
    private var previous: FaceAnalyzer.FaceMetrics? = null
    private var timestamp=0L
    private val anchors=intArrayOf(33,133,263,362,168,10,152,127,356)

    fun sample(face: FaceAnalyzer.FaceMetrics?, now: Long): FaceAnalyzer.FaceMetrics? {
        if(face?.isFaceDetected!=true || face.landmarks.size<468 ||
            face.landmarks.any { !it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite() }) {
            previous=null
            return face
        }
        val old=previous
        val elapsed=now-timestamp
        timestamp=now
        if(old==null || old.landmarks.size!=face.landmarks.size || elapsed<=0L || elapsed>250_000_000L ||
            old.imageWidth!=face.imageWidth || old.imageHeight!=face.imageHeight) {
            previous=face
            return face
        }
        val aspect=if(face.imageWidth>0 && face.imageHeight>0) face.imageHeight.toFloat()/face.imageWidth else 1f
        fun point(p: FaceAnalyzer.LandmarkPoint)=Vector3(p.x,p.y*aspect,p.z)
        fun eye(points: List<FaceAnalyzer.LandmarkPoint>,a: Int,b: Int)=(point(points[a])+point(points[b]))*.5f
        val span=(eye(face.landmarks,263,362)-eye(face.landmarks,33,133)).length()
        if(!span.isFinite() || span<.02f) { previous=face;return face }
        var displacement=0f
        for(i in anchors) {
            val d=point(face.landmarks[i])-point(old.landmarks[i])
            displacement=maxOf(displacement,abs(d.x),abs(d.y),abs(d.z))
        }
        val motion=displacement/span
        // Tiny fluctuations are damped. Intentional movement immediately opens the filter.
        val t=((motion-.008f)/.032f).coerceIn(0f,1f)
        val adaptive=.25f+.75f*t*t*(3f-2f*t)
        // Bound added lag to 1.2% of eye spacing at the pose anchors (about 1-3 pixels).
        val bounded=if(motion>0f) 1f-.012f/motion else 0f
        val alpha=maxOf(adaptive,bounded).coerceIn(.25f,1f)
        if(alpha>=.999f) { previous=face;return face }
        val result=face.copy(landmarks=face.landmarks.mapIndexed { i,p ->
            val q=old.landmarks[i]
            FaceAnalyzer.LandmarkPoint(q.x+(p.x-q.x)*alpha,q.y+(p.y-q.y)*alpha,q.z+(p.z-q.z)*alpha)
        })
        previous=result
        return result
    }
}
