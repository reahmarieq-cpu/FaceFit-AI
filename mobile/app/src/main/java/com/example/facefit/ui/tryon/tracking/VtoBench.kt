package com.example.facefit.ui.tryon.tracking

import android.util.Log
import com.example.facefit.BuildConfig
import kotlin.math.atan2
import kotlin.math.sqrt
import com.example.facefit.facefit.tryon.geometry.Vector3

/** Phase-zero instrumentation. Enable FaceFitBench at VERBOSE, and FaceFitBenchStill
 * only during a deliberate stationary hold. Never labels moving-pose variance as resting jitter.
 * Defaults are measurement controls, not tracking thresholds. All calls are on the main thread.
 */
internal object VtoBench {
    private object Config {
        const val WINDOW_NANOS = 5_000_000_000L
        const val CAPACITY = 2048
    }
    private val model = DoubleArray(Config.CAPACITY)
    private val latency = DoubleArray(Config.CAPACITY)
    private val sorted = DoubleArray(Config.CAPACITY)
    private val mean = DoubleArray(7)
    private val squares = DoubleArray(7)
    private val values = DoubleArray(7)
    private var modelCount = 0
    private var latencyCount = 0
    private var poses = 0
    private var valid = 0
    private var stillCount = 0
    private var start = 0L
    private var lastPose = 0L
    private var maxGap = 0L
    private var lastRenderSample = Long.MIN_VALUE
    private var backend = "unknown"
    private var overlay = true
    private var stillReference: FloatArray? = null
    private var anchorReference = Vector3(0f, 0f, 0f)
    private var spanReference = 1f

    private fun enabled() = BuildConfig.DEBUG && Log.isLoggable("FaceFitBench", Log.VERBOSE)

    fun pose(modelMs: Long, detected: Boolean, selectedBackend: String, now: Long) {
        if (!enabled()) return
        val rendering = !Log.isLoggable("FaceFitBenchHide", Log.VERBOSE)
        if (overlay != rendering) {
            modelCount=0; latencyCount=0; poses=0; valid=0; stillCount=0; maxGap=0L
            mean.fill(0.0); squares.fill(0.0); start=now; lastPose=0L
            lastRenderSample=Long.MIN_VALUE
            overlay=rendering
        }
        if (start == 0L) start = now
        backend = selectedBackend
        model[modelCount % Config.CAPACITY] = modelMs.toDouble()
        modelCount++
        poses++
        if (detected) valid++
        if (lastPose != 0L) maxGap = maxOf(maxGap, now - lastPose)
        lastPose = now
        report(now)
    }

    fun submitted(matrix: FloatArray?, eyeSpan: Float, sample: Long?, now: Long, anchor: Vector3 = Vector3(0f, 0f, 0f)) {
        if (!enabled()) return
        overlay = !Log.isLoggable("FaceFitBenchHide", Log.VERBOSE)
        if (sample != null && sample != lastRenderSample) {
            latency[latencyCount % Config.CAPACITY] = (now - sample).coerceAtLeast(0L) / 1e6
            latencyCount++
            lastRenderSample = sample
        }
        if (matrix != null && eyeSpan > 0f && Log.isLoggable("FaceFitBenchStill", Log.VERBOSE)) {
            val position = benchAnchor(matrix, anchor)
            if (stillReference == null || stillCount == 0) {
                stillReference = matrix.copyOf()
                anchorReference = position
                spanReference = eyeSpan
            }
            val displacement = (position - anchorReference) * (1f / spanReference)
            values[0] = displacement.x.toDouble()
            values[1] = displacement.y.toDouble()
            values[2] = displacement.z.toDouble()
            val reference = requireNotNull(stillReference)
            val rotation = benchRotationDelta(reference, matrix)
            values[3] = rotation.x.toDouble()
            values[4] = rotation.y.toDouble()
            values[5] = rotation.z.toDouble()
            values[6] = sqrt((matrix[0]*matrix[0]+matrix[1]*matrix[1]+matrix[2]*matrix[2]).toDouble())
            stillCount++
            for (i in values.indices) {
                val delta = values[i] - mean[i]
                mean[i] += delta / stillCount
                squares[i] += delta * (values[i] - mean[i])
            }
        }
        report(now)
    }

    private fun percentile(data: DoubleArray, count: Int, fraction: Double): Double {
        val size = minOf(count, Config.CAPACITY)
        if (size == 0) return Double.NaN
        data.copyInto(sorted, endIndex = size)
        java.util.Arrays.sort(sorted, 0, size)
        return sorted[((size - 1) * fraction).toInt()]
    }

    private fun report(now: Long) {
        if (start == 0L || now - start < Config.WINDOW_NANOS) return
        fun deviation(index: Int) = if (stillCount > 1) sqrt(squares[index] / (stillCount - 1)) else Double.NaN
        val translation = sqrt(deviation(0)*deviation(0)+deviation(1)*deviation(1)+deviation(2)*deviation(2))
        val rotation = sqrt(deviation(3)*deviation(3)+deviation(4)*deviation(4)+deviation(5)*deviation(5)) * 180 / Math.PI
        Log.i("FaceFitBench", "backend=$backend overlay=$overlay modelP50Ms=${percentile(model,modelCount,.5)} modelP95Ms=${percentile(model,modelCount,.95)} posesPerSec=${poses*1e9/(now-start)} maxGapMs=${maxGap/1e6} validPercent=${if(poses>0) valid*100.0/poses else Double.NaN} sensorToSubmitP50Ms=${percentile(latency,latencyCount,.5)} sensorToSubmitP95Ms=${percentile(latency,latencyCount,.95)} stillSamples=$stillCount translationStdEyeSpan=$translation rotationStdDeg=$rotation scaleStdPercent=${deviation(6)/mean[6]*100} latencyScope=timestampEstimate")
        modelCount=0; latencyCount=0; poses=0; valid=0; stillCount=0; maxGap=0L
        mean.fill(0.0); squares.fill(0.0); start=now
    }
}

/** Relative rotation avoids the +/- pi discontinuity of absolute Euler angles. */
internal fun benchRotationDelta(reference: FloatArray, matrix: FloatArray): Vector3 {
    var skew = Vector3(0f, 0f, 0f)
    var trace = 0f
    for (axis in 0..2) {
        val i = axis * 4
        val before = Vector3(reference[i], reference[i + 1], reference[i + 2]).normalized()
        val after = Vector3(matrix[i], matrix[i + 1], matrix[i + 2]).normalized()
        skew += before.cross(after) * .5f
        trace += before.dot(after)
    }
    val sine = skew.length()
    val angle = atan2(sine, ((trace - 1f) * .5f).coerceIn(-1f, 1f))
    return if (sine > 0f) skew * (angle / sine) else Vector3(0f, 0f, 0f)
}

internal fun benchAnchor(matrix: FloatArray, anchor: Vector3): Vector3 =
    Vector3(matrix[12], matrix[13], matrix[14]) +
        Vector3(matrix[0], matrix[1], matrix[2]) * anchor.x +
        Vector3(matrix[4], matrix[5], matrix[6]) * anchor.y +
        Vector3(matrix[8], matrix[9], matrix[10]) * anchor.z