package com.example.facefit.facefit.tryon.tracking

import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.tryon.geometry.GlassesRig
import kotlin.math.exp

/** Debounce solver failures on measurement updates, then blend rigid axes, never vertices.
 * Holds orientation only during brief fit failures; the bridge follows current measurements.
 */
internal class VtoRigResolver {
    private var key: Any? = null
    private var lastAxes: VtoHeadPose.Axes? = null
    private var good = 0
    private var bad = 0
    private var useFit = false
    private var lastTime: Long? = null
    private var width = 0
    private var height = 0
    var fitWeight = 0f
        private set

    val isTransitioning: Boolean get() = if(useFit) fitWeight<1f else fitWeight>0f

    fun reset() {
        key=null; lastAxes=null; good=0; bad=0; useFit=false
        lastTime=null; fitWeight=0f; width=0; height=0
    }

    fun resolve(face: FaceAnalyzer.FaceMetrics?, width: Int, height: Int, scale: Float,
        offset: Float, now: Long, measurementKey: Any? = face): GlassesRig? {
        if (face?.isFaceDetected != true) { reset(); return null }
        if (this.width != width || this.height != height) { reset(); this.width=width; this.height=height }
        val candidates = VtoHeadPose.candidates(face,width,height,scale,offset) ?: return null
        val first = lastTime == null
        val dt = lastTime?.let { (now-it).coerceAtLeast(0L)/1_000_000_000f } ?: 0f
        lastTime=now
        if ((measurementKey !== key && (measurementKey as? FaceAnalyzer.FaceMetrics)?.isFaceDetected == true) || first) {
            key=measurementKey
            if (candidates.fitted != null) { good++; bad=0 } else { bad++; good=0 }
            if (first) useFit=candidates.fitted!=null
            else if (good>=3) useFit=true else if (bad>=3) useFit=false
        }
        candidates.fitted?.let { lastAxes=VtoHeadPose.Axes(it.xAxis,it.yAxis,it.zAxis) }
        if (first) fitWeight=if(useFit) 1f else 0f
        else fitWeight += ((if(useFit) 1f else 0f)-fitWeight)*(1f-exp(-dt/.060f))
        if (fitWeight<.001f) fitWeight=0f else if(fitWeight>.999f) fitWeight=1f
        val fallback=candidates.fallback
        val axes=lastAxes ?: return fallback
        val fitted=candidates.fitted ?: VtoHeadPose.withAxes(face,width,height,scale,offset,axes)
        val w=fitWeight
        if (w==0f) return fallback
        if (w==1f) return fitted
        // Re-orthogonalize blended axes to avoid scale/shear in the GLB and head occluder.
        val x=(fallback.xAxis*(1f-w)+fitted.xAxis*w).normalized()
        val down=fallback.yAxis*(1f-w)+fitted.yAxis*w
        val y=(down-x*down.dot(x)).normalized()
        var z=x.cross(y).normalized()
        if(z.z<0f) z=z*-1f
        return GlassesRig(fallback.origin*(1f-w)+fitted.origin*w,x,y,z,
            fallback.size*(1f-w)+fitted.size*w)
    }
}
