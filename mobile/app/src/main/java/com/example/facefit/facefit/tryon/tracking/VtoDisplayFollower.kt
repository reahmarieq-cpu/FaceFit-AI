package com.example.facefit.facefit.tryon.tracking

import com.example.facefit.facefit.analysis.FaceAnalyzer
import kotlin.math.exp
import kotlin.math.sqrt

/** Display-rate following only: no velocity estimate, extrapolation, or pose queue. */
internal class VtoDisplayFollower {
    data class Display(val face: FaceAnalyzer.FaceMetrics?, val opacity: Float)
    private var output = Display(null, 0f)
    private var measurement: FaceAnalyzer.FaceMetrics? = null
    private var previousTick: Long? = null
    private var lostAt: Long? = null
    private var width = 0
    private var height = 0
    private val anchors = intArrayOf(33, 133, 263, 362, 168, 10, 152, 127, 356)

    fun reset() {
        output = Display(null, 0f); measurement = null; previousTick = null
        lostAt = null; width = 0; height = 0
    }

    fun sample(face: FaceAnalyzer.FaceMetrics?, now: Long): Display {
        if (face != null && face.imageWidth>0 && face.imageHeight>0 &&
            (face.imageWidth != width || face.imageHeight != height)) {
            reset(); width = face.imageWidth; height = face.imageHeight
        }
        val previous = previousTick
        previousTick = now
        val valid = face?.isFaceDetected == true && face.landmarks.size >= 468 &&
            face.imageWidth > 0 && face.imageHeight > 0 &&
            face.landmarks.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }
        if (!valid) {
            val start = lostAt ?: now.also { lostAt = it }
            val ageMs = (now - start).coerceAtLeast(0L) / 1_000_000f
            if (ageMs < 250f) return output
            // Grace has ended: discard the interpolation target, retaining pixels only for fade.
            measurement = null
            val opacity = (1f - (ageMs - 250f) / 100f).coerceIn(0f, 1f)
            return set(if (opacity > 0f) output.face else null, opacity)
        }
        val target = requireNotNull(face)
        val expired = lostAt?.let { now - it >= 250_000_000L } == true
        lostAt = null
        if (output.face == null || expired || previous == null || now - previous > 250_000_000L ||
            output.face?.landmarks?.size != target.landmarks.size) {
            measurement = target
            return set(target, 1f)
        }
        if (measurement === target && output.face === target) return set(target, 1f)
        measurement = target
        val shown = requireNotNull(output.face)
        val aspect = target.imageHeight.toFloat() / target.imageWidth
        fun distance(a: FaceAnalyzer.LandmarkPoint, b: FaceAnalyzer.LandmarkPoint): Float {
            val dx = a.x-b.x; val dy = (a.y-b.y)*aspect; val dz = a.z-b.z
            return sqrt(dx*dx + dy*dy + dz*dz)
        }
        fun eye(a: Int, b: Int): FaceAnalyzer.LandmarkPoint {
            val x = target.landmarks[a]; val y = target.landmarks[b]
            return FaceAnalyzer.LandmarkPoint((x.x+y.x)*.5f, (x.y+y.y)*.5f, (x.z+y.z)*.5f)
        }
        val span = distance(eye(33,133), eye(263,362)).coerceAtLeast(.02f)
        val error = anchors.maxOf { distance(shown.landmarks[it],target.landmarks[it]) } / span
        val allError = target.landmarks.indices.maxOf { distance(shown.landmarks[it],target.landmarks[it]) } / span
        // Snap at subpixel error, then preserve object identity to stop native mesh uploads.
        if (allError == 0f) return set(shown, 1f)
        if (allError <= .0005f) return set(target, 1f)
        val motion = ((error-.008f)/.112f).coerceIn(0f,1f)
        val blend = motion*motion*(3f-2f*motion)
        val tau = .045f + (.008f-.045f)*blend
        val dt = (now-previous).coerceAtLeast(0L)/1_000_000_000f
        val k = (1f-exp(-dt/tau)).coerceIn(0f,1f)
        if (k == 0f) return set(shown, 1f)
        val next = target.copy(landmarks=target.landmarks.mapIndexed { i,p ->
            val q = shown.landmarks[i]
            FaceAnalyzer.LandmarkPoint(q.x+(p.x-q.x)*k, q.y+(p.y-q.y)*k, q.z+(p.z-q.z)*k)
        })
        return set(next, 1f)
    }

    private fun set(face: FaceAnalyzer.FaceMetrics?, opacity: Float): Display {
        if (output.face === face && output.opacity == opacity) return output
        return Display(face, opacity).also { output = it }
    }
}
