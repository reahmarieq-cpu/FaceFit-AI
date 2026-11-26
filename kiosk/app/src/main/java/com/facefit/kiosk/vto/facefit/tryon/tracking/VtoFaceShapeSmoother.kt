package com.facefit.kiosk.vto.facefit.tryon.tracking

import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Removes frame-to-frame jitter from the light face mesh's points without delaying head motion.
 *
 * Each point is expressed relative to the eyes (origin between them, x along the eye line, unit =
 * eye distance), so moving, tilting or approaching the camera leaves these face-relative coordinates
 * unchanged. Only they are smoothed (a One Euro filter per coordinate: slow at rest, fast when the
 * face shape really changes, e.g. a blink), and then placed back at the current eye position. The
 * head pose therefore gets no extra lag, while the face shape used for head fitting and for hiding
 * the temples behind the head stops flickering.
 *
 * Not thread-safe: use from the tracking worker.
 */
internal class VtoFaceShapeSmoother(
    // Meshes arrive ~18/s: a low resting cutoff, and shape changes slower than [noiseSpeed]
    // (face-relative eye spans per second, ~1-2 px point noise) do not count as movement.
    private val minCutoffHz: Double = .5,
    private val beta: Double = 4.0,
    private val derivativeCutoffHz: Double = 1.0,
    private val noiseSpeed: Double = .3,
) {
    private var values: FloatArray? = null
    private var rawValues: FloatArray? = null
    private var velocities: FloatArray? = null
    private var lastNanos = 0L

    /** Mean face-relative point movement between the last two frames, before and after smoothing (eye spans). */
    var lastRawJitter = Float.NaN
        private set
    var lastSmoothedJitter = Float.NaN
        private set

    fun reset() { values = null; rawValues = null; velocities = null; lastNanos = 0L }

    /** [points] normalized to an image of [width] x [height]; returns the same count, smoothed. */
    fun smooth(points: List<FaceAnalyzer.LandmarkPoint>, width: Int, height: Int, nowNanos: Long): List<FaceAnalyzer.LandmarkPoint> {
        if (points.size < 468 || width <= 0 || height <= 0) return points
        val n = points.size
        val px = FloatArray(n) { points[it].x * width }
        val py = FloatArray(n) { points[it].y * height }
        val previous = values
        val elapsed = (nowNanos - lastNanos) / 1e9
        // Face placement (centre, rotation, scale) mapping face-relative shape -> pixels.
        val cx: Float; val cy: Float; val mx: Float; val my: Float; val ta: Float; val tb: Float
        val fresh = previous == null || previous.size != n * 3 || elapsed <= 0.0 || elapsed > 1.0
        if (fresh) {
            // First frame: face-relative axes from the eyes (origin between them, unit = eye distance).
            val ax = (px[33] + px[133]) * .5f; val ay = (py[33] + py[133]) * .5f
            val bx = (px[263] + px[362]) * .5f; val by = (py[263] + py[362]) * .5f
            val span = hypot(bx - ax, by - ay)
            if (!(span > 1f)) { reset(); return points }
            val angle = atan2(by - ay, bx - ax)
            cx = (ax + bx) * .5f; cy = (ay + by) * .5f; mx = 0f; my = 0f
            ta = cos(angle) * span; tb = sin(angle) * span
        } else {
            // Best-fit similarity of the previous smoothed shape onto all current points: averaging
            // over every point keeps this placement steady while following head motion exactly.
            val shape = requireNotNull(previous)
            var sx = 0f; var sy = 0f; var qx = 0f; var qy = 0f
            for (i in 0 until n) { sx += shape[i * 3]; sy += shape[i * 3 + 1]; qx += px[i]; qy += py[i] }
            mx = sx / n; my = sy / n; cx = qx / n; cy = qy / n
            var a = 0.0; var b = 0.0; var norm = 0.0
            for (i in 0 until n) {
                val ux = shape[i * 3] - mx; val uy = shape[i * 3 + 1] - my
                val vx = px[i] - cx; val vy = py[i] - cy
                a += ux * vx + uy * vy; b += ux * vy - uy * vx; norm += ux * ux + uy * uy
            }
            if (!(norm > 0.0)) { reset(); return points }
            ta = (a / norm).toFloat(); tb = (b / norm).toFloat()
        }
        val scale2 = ta * ta + tb * tb
        val scale = kotlin.math.sqrt(scale2)
        if (!(scale > 1e-3f) || !scale.isFinite()) { reset(); return points }
        val local = FloatArray(n * 3)
        for (i in 0 until n) {
            val dx = px[i] - cx; val dy = py[i] - cy
            local[i * 3] = mx + (ta * dx + tb * dy) / scale2
            local[i * 3 + 1] = my + (-tb * dx + ta * dy) / scale2
            local[i * 3 + 2] = points[i].z * width / scale
        }
        val smoothed: FloatArray
        if (fresh) {
            smoothed = local.copyOf()
            velocities = FloatArray(local.size)
            lastRawJitter = Float.NaN; lastSmoothedJitter = Float.NaN
        } else {
            val last = requireNotNull(previous)
            val v = requireNotNull(velocities)
            smoothed = FloatArray(local.size)
            val dAlpha = alpha(elapsed, derivativeCutoffHz)
            var raw = 0.0; var out = 0.0
            val previousRaw = rawValues ?: last
            for (k in local.indices) {
                v[k] += (((local[k] - last[k]) / elapsed).toFloat() - v[k]) * dAlpha
                val a = alpha(elapsed, minCutoffHz + beta * (kotlin.math.abs(v[k]) - noiseSpeed).coerceAtLeast(0.0))
                smoothed[k] = last[k] + (local[k] - last[k]) * a
                if (k % 3 != 2) { raw += kotlin.math.abs(local[k] - previousRaw[k]); out += kotlin.math.abs(smoothed[k] - last[k]) }
            }
            lastRawJitter = (raw / (n * 2)).toFloat()
            lastSmoothedJitter = (out / (n * 2)).toFloat()
        }
        values = smoothed
        rawValues = local
        lastNanos = nowNanos
        return List(n) { i ->
            val lx = smoothed[i * 3] - mx; val ly = smoothed[i * 3 + 1] - my
            FaceAnalyzer.LandmarkPoint((cx + ta * lx - tb * ly) / width, (cy + tb * lx + ta * ly) / height,
                smoothed[i * 3 + 2] * scale / width)
        }
    }

    private fun alpha(elapsed: Double, cutoffHz: Double) =
        (1.0 - exp(-elapsed * 2 * Math.PI * cutoffHz)).toFloat().coerceIn(0f, 1f)
}
