package com.facefit.kiosk.facefit.mobilescan

import kotlin.math.abs

/** Collects consecutive, fresh aligned frames; capture never completes on a frozen frame. */
class FaceScanSamples {
    companion object {
        const val MIN_SAMPLES = 15
        const val MIN_DURATION_MS = 2000L
        private const val MAX_SAMPLES = 25
    }
    private data class Sample(val metrics: FaceAnalyzer.FaceMetrics, val timeMs: Long)
    private val samples = ArrayDeque<Sample>()
    private var lastAt = 0L
    private var lastProgress = 0f
    private var waitingForStability = false

    data class Update(val progress: Float, val captured: FaceAnalyzer.FaceMetrics? = null,
        val waitingForStability: Boolean = false)

    fun reset() {
        samples.clear()
        lastAt = 0L
        lastProgress = 0f
        waitingForStability = false
    }

    fun add(metrics: FaceAnalyzer.FaceMetrics, aligned: Boolean, nowMs: Long): Update {
        if (!aligned || !metrics.isFaceDetected || !ScanStabilityFeatures.isValid(metrics.stabilityFeatures) ||
            metrics.landmarks.size <= 454 || metrics.imageWidth <= 0 || metrics.imageHeight <= 0) {
            reset()
            return Update(0f)
        }
        val previous = samples.lastOrNull()?.metrics
        if (previous != null && (nowMs <= lastAt || nowMs - lastAt > 500L ||
                metrics.imageWidth != previous.imageWidth || metrics.imageHeight != previous.imageHeight ||
                abs(metrics.stabilityFeatures[0] - previous.stabilityFeatures[0]) > 0.06f ||
                abs(metrics.stabilityFeatures[2] - previous.stabilityFeatures[2]) > 0.04f)) {
            reset()
        }
        // At most ten samples per second, with at least two seconds of fresh frames.
        if (samples.isNotEmpty() && nowMs - lastAt < 100L)
            return Update(lastProgress, waitingForStability = waitingForStability)
        lastAt = nowMs
        samples.addLast(Sample(metrics, nowMs))
        if (samples.size > MAX_SAMPLES) samples.removeFirst()
        val progress = minOf((nowMs - samples.first().timeMs).toFloat() / MIN_DURATION_MS,
            samples.size.toFloat() / MIN_SAMPLES).coerceIn(0f, 1f)
        lastProgress = progress
        if (progress < 1f) return Update(progress)
        val frames = samples.map { it.metrics }
        val medians = ScanStabilityFeatures.names.indices.map { index ->
            medianValue(frames.map { it.stabilityFeatures[index] })
        }
        val cheekSpans = frames.map(FaceScanPose::cheekSpan)
        val referenceSpan = medianValue(cheekSpans)
        val noseX = frames.map { it.landmarks[1].x * it.imageWidth }
        val noseY = frames.map { it.landmarks[1].y * it.imageHeight }
        val centerX = medianValue(noseX)
        val centerY = medianValue(noseY)
        val poses = frames.mapNotNull { it.headPose }
        val brightness = frames.mapNotNull { it.scanLighting?.mean }
        fun range(values: List<Float>) = if (values.isEmpty()) 0f else values.max() - values.min()
        val stable = medians.indices.all { index ->
            val scale = maxOf(abs(medians[index]), 0.05f)
            val deviations = frames.map { abs(it.stabilityFeatures[index] - medians[index]) / scale }
            medianValue(deviations) <= 0.01f && deviations.all { it <= 0.04f } &&
                deviations.count { it <= 0.025f }.toFloat() / frames.size >= 0.9f
        } && cheekSpans.all { it.isFinite() && it > 0f } &&
            cheekSpans.max() / cheekSpans.min() <= 1.05f &&
            noseX.all { abs(it - centerX) <= referenceSpan * .04f } &&
            noseY.all { abs(it - centerY) <= referenceSpan * .04f } &&
            range(poses.map { it.pitch }) <= 3f && range(poses.map { it.yaw }) <= 3f &&
            range(poses.map { it.roll }) <= 3f && range(brightness) <= .08f
        if (!stable) {
            lastProgress = 0.85f
            waitingForStability = true
            return Update(lastProgress, waitingForStability = true)
        }
        waitingForStability = false
        val aggregate = FacialGeometry.median(frames.mapNotNull { it.shapeMeasurements })
        return Update(1f, metrics.copy(
            foreheadWidth = medianValue(frames.map { it.foreheadWidth }),
            jawline = medianValue(frames.map { it.jawline }),
            eyeSpacing = medianValue(frames.map { it.eyeSpacing }),
            shapeMeasurements = aggregate,
            stabilityFeatures = medians,
            shapeSampleCount = samples.size,
            shapeFeatureSamples = frames.map { it.stabilityFeatures }
        ))
    }
}
