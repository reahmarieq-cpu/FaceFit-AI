package com.example.facefit.logic

import kotlin.math.abs

/** Collects consecutive, fresh aligned frames; capture never completes on a frozen frame. */
class FaceScanSamples {
    private val samples = ArrayDeque<FaceAnalyzer.FaceMetrics>()
    private var startedAt = 0L
    private var lastAt = 0L

    data class Update(val progress: Float, val captured: FaceAnalyzer.FaceMetrics? = null)

    fun reset() {
        samples.clear()
        startedAt = 0L
        lastAt = 0L
    }

    fun add(metrics: FaceAnalyzer.FaceMetrics, aligned: Boolean, nowMs: Long): Update {
        val measurement = metrics.shapeMeasurements
        if (!aligned || !metrics.isFaceDetected || measurement == null ||
            FaceShapeClassifier.median(listOf(measurement)) == null) {
            reset()
            return Update(0f)
        }
        val previous = samples.lastOrNull()
        if (previous != null && (nowMs <= lastAt || nowMs - lastAt > 900L ||
                metrics.imageWidth != previous.imageWidth || metrics.imageHeight != previous.imageHeight ||
                abs(measurement.lengthToWidth - previous.shapeMeasurements!!.lengthToWidth) > 0.12f ||
                abs(measurement.jawToCheek - previous.shapeMeasurements.jawToCheek) > 0.08f)) {
            reset()
        }
        if (samples.isEmpty()) startedAt = nowMs
        lastAt = nowMs
        samples.addLast(metrics)
        if (samples.size > 12) samples.removeFirst()
        val progress = minOf((nowMs - startedAt) / 700f, samples.size / 6f).coerceIn(0f, 1f)
        if (progress < 1f) return Update(progress)
        val aggregate = FaceShapeClassifier.median(samples.mapNotNull { it.shapeMeasurements })!!
        return Update(1f, metrics.copy(
            foreheadWidth = medianValue(samples.map { it.foreheadWidth }),
            jawline = medianValue(samples.map { it.jawline }),
            eyeSpacing = medianValue(samples.map { it.eyeSpacing }),
            shapeMeasurements = aggregate,
            faceShape = FaceShapeClassifier.classify(aggregate),
            shapeSampleCount = samples.size
        ))
    }
}
