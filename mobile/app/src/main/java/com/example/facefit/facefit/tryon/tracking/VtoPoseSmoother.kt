package com.example.facefit.facefit.tryon.tracking

import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.tryon.geometry.TryOnGeometry
import com.example.facefit.facefit.tryon.geometry.Vector3
import kotlin.math.PI
import kotlin.math.expm1
import kotlin.math.sqrt

internal fun secondsBetween(now: Long, previous: Long): Double = (now - previous) / 1_000_000_000.0

/** Shared 1€ response. Each vector is a probe displacement in IPDs, sampled once per measurement. */
internal class VtoOneEuro(private val minimumCutoff: Double = .5) {
    private var sampleTime: Long? = null
    private var interval = 0.0
    private var previousVelocity: List<Vector3>? = null
    private var previousReversed = false
    private var count = 0L
    private var mean = emptyList<Vector3>()
    private var sumSquares = 0.0
    private var speed = 0.0
    private var sensorNoiseScale = 1.0
    private var responsiveness = 1.0

    fun reset(now: Long) {
        sampleTime = now
        interval = 0.0
        previousVelocity = null
        previousReversed = false
        count = 0L
        mean = emptyList()
        sumSquares = 0.0
        speed = 0.0
        sensorNoiseScale = 1.0
        responsiveness = 1.0
    }

    val restingNoiseLimit: Float get() = (NOISE_SIGMAS * noiseSpeed * sensorNoiseScale).toFloat()

    private val noiseSpeed: Double
        get() = if (count > 1) sqrt((sumSquares / (count - 1) / mean.size).coerceAtLeast(0.0)) else 0.0

    fun observe(displacements: List<Vector3>, now: Long, snrFactor: Float = 1f, motionResponsiveness: Float = 1f) {
        val dt = sampleTime?.let { secondsBetween(now, it) } ?: 0.0
        if (dt <= 0.0) return
        if (previousVelocity?.size != displacements.size) reset(now)
        sampleTime = now
        interval = dt
        val velocity = displacements.map { it * (1.0 / dt).toFloat() }
        val instantSpeed = rms(velocity)
        responsiveness = motionResponsiveness.toDouble().coerceIn(0.0, 1.0)
        sensorNoiseScale = snrFactor.takeIf { it.isFinite() && it >= 1f }?.toDouble() ?: 1.0
        // Normalize Welford samples to nominal gain so the ISO factor is applied once.
        val nominalVelocity = velocity.map { it * (1.0 / sensorNoiseScale).toFloat() }
        val previous = previousVelocity
        val noiseLimit = NOISE_SIGMAS * noiseSpeed

        val reversed = previous != null &&
            nominalVelocity.indices.sumOf { nominalVelocity[it].dot(previous[it]).toDouble() } < 0.0

        // Bootstrap from repeated reversals so an isolated direction change cannot
        // become the resting baseline. After calibration, reject out-of-band velocities.
        if (noiseLimit == 0.0 && previous != null && previousReversed && reversed) {
            accumulate(previous)
            accumulate(nominalVelocity)
        } else if (noiseLimit > 0.0 && instantSpeed <= noiseLimit * sensorNoiseScale) {
            accumulate(nominalVelocity)
        }
        previousReversed = reversed
        previousVelocity = nominalVelocity
        speed = instantSpeed
    }

    fun weight(error: Double, elapsed: Double): Float {
        if (elapsed <= 0.0 || error <= 0.0 || interval <= 0.0) return 0f
        val noise = restingNoiseLimit.toDouble()
        val noiseDistance = noise * interval
        if (speed <= noise && error <= noiseDistance) return 0f
        // A 0.5 Hz resting cutoff retains small changes outside the learned band. The
        // adaptive term uses excess speed, including accumulated drift past the band.
        val minimum = minimumCutoff
        val velocity = maxOf(speed - noise, (error - noiseDistance) / interval, 0.0)
        val beta = 20.0 * responsiveness / (1.0 + noiseDistance)
        return (-expm1(-2.0 * PI * (minimum + beta * velocity) * elapsed)).toFloat()
    }

    private fun accumulate(velocity: List<Vector3>) {
        count++
        if (count == 1L) {
            mean = velocity
            return
        }
        mean = velocity.mapIndexed { i, value ->
            val delta = value - mean[i]
            val next = mean[i] + delta * (1f / count)
            sumSquares += delta.dot(value - next)
            next
        }
    }

    companion object {
        // Three standard deviations of resting velocity define the noise envelope.
        private const val NOISE_SIGMAS = 3.0
        fun rms(vectors: List<Vector3>): Double =
            sqrt(vectors.sumOf { it.dot(it).toDouble() } / vectors.size)
    }
}

private val probes = intArrayOf(
    TryOnGeometry.FIRST_OUTER_EYE, TryOnGeometry.SECOND_OUTER_EYE, TryOnGeometry.NASAL_BRIDGE,
    TryOnGeometry.FIRST_INNER_EYE, TryOnGeometry.SECOND_INNER_EYE
)

internal fun landmarkDisplacement(
    current: FaceAnalyzer.FaceMetrics, previous: FaceAnalyzer.FaceMetrics
): List<Vector3>? {
    if (current.imageWidth <= 0 || current.imageHeight <= 0 ||
        current.imageWidth != previous.imageWidth || current.imageHeight != previous.imageHeight ||
        minOf(current.landmarks.size, previous.landmarks.size) <= TryOnGeometry.SECOND_INNER_EYE) return null
    val aspect = current.imageHeight.toFloat() / current.imageWidth
    fun point(face: FaceAnalyzer.FaceMetrics, index: Int) =
        face.landmarks[index].let { Vector3(it.x, it.y * aspect, it.z) }
    val ipd = TryOnGeometry.ipd { point(current, it) } ?: return null
    return probes.map { (point(current, it) - point(previous, it)) * (1f / ipd) }
}

/** Filters landmark updates at display rate using stable skull and IPD probes. */
internal class VtoPoseSmoother {
    private val response = VtoOneEuro()
    private var source: FaceAnalyzer.FaceMetrics? = null
    private var displayed: FaceAnalyzer.FaceMetrics? = null
    private var tick: Long? = null
    private var tickInterval = 0.0

    fun reset() {
        source = null
        displayed = null
        tick = null
        tickInterval = 0.0
    }

    fun sample(face: FaceAnalyzer.FaceMetrics?, now: Long): FaceAnalyzer.FaceMetrics? {
        if (face?.isFaceDetected != true || face.landmarks.size <= TryOnGeometry.SECOND_INNER_EYE ||
            face.imageWidth <= 0 || face.imageHeight <= 0) {
            reset()
            return face
        }
        val aspect = face.imageHeight.toFloat() / face.imageWidth
        fun point(metrics: FaceAnalyzer.FaceMetrics, i: Int) =
            metrics.landmarks[i].let { Vector3(it.x, it.y * aspect, it.z) }

        val ipd = TryOnGeometry.ipd { point(face, it) }
        if (ipd == null || probes.any { !point(face, it).isFinite() }) {
            reset()
            return null
        }
        val old = source
        val shown = displayed
        val lastTick = tick
        val elapsed = lastTick?.let { secondsBetween(now, it) } ?: 0.0
        val paused = tickInterval > 0.0 && elapsed > MISSED_VSYNC_INTERVALS * tickInterval
        if (old == null || shown == null || lastTick == null || paused ||
            face.imageWidth != old.imageWidth || face.imageHeight != old.imageHeight ||
            face.landmarks.size != old.landmarks.size) {
            response.reset(now)
            source = face
            displayed = face
            tick = now
            tickInterval = 0.0
            return face
        }
        if (elapsed <= 0.0) return shown
        tick = now
        tickInterval = if (tickInterval == 0.0) elapsed else (tickInterval + elapsed) / 2.0
        val inverseIpd = 1f / ipd
        if (face !== old) {
            landmarkDisplacement(face, old)?.let { response.observe(it, now, face.snrFactor) }
            source = face
        }
        if (face === shown) return shown
        val error = VtoOneEuro.rms(probes.map { (point(face, it) - point(shown, it)) * inverseIpd })
        val alpha = response.weight(error, elapsed)
        if (alpha <= 0f) return shown
        val next = if (alpha >= 1f) face else face.copy(landmarks = face.landmarks.mapIndexed { i, target ->
            val point = shown.landmarks[i]
            FaceAnalyzer.LandmarkPoint(
                point.x + (target.x - point.x) * alpha,
                point.y + (target.y - point.y) * alpha,
                point.z + (target.z - point.z) * alpha
            )
        })
        if (next.landmarks == shown.landmarks) return shown
        displayed = next
        return next
    }

    private companion object {
        // A display pause should reacquire the current measurement immediately.
        const val MISSED_VSYNC_INTERVALS = 4
    }
}
