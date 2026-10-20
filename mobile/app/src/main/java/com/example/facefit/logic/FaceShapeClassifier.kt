package com.example.facefit.logic

import kotlin.math.acos
import kotlin.math.hypot

/** Geometric estimates, not a trained classifier or calibrated probabilities. */
object FaceShapeClassifier {
    data class Measurements(
        val lengthToWidth: Float,
        val foreheadToCheek: Float,
        val jawToCheek: Float,
        val chinToJaw: Float,
        val jawAngle: Float
    )

    fun measure(
        landmarks: List<FaceAnalyzer.LandmarkPoint>,
        imageWidth: Int,
        imageHeight: Int
    ): Measurements? {
        if (imageWidth <= 0 || imageHeight <= 0) return null
        val indices = listOf(10, 152, 103, 332, 234, 454, 172, 397, 136, 365)
        if (indices.any { it !in landmarks.indices }) return null
        val points = indices.associateWith { index ->
            val point = landmarks[index]
            Point(point.x * imageWidth, point.y * imageHeight)
        }
        if (points.values.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        fun distance(a: Int, b: Int) = points.getValue(a).distance(points.getValue(b))
        val cheek = distance(234, 454)
        val jaw = distance(172, 397)
        if (cheek < 1f || jaw < 1f) return null
        val leftAngle = angle(points.getValue(234), points.getValue(172), points.getValue(152))
        val rightAngle = angle(points.getValue(454), points.getValue(397), points.getValue(152))
        return Measurements(
            lengthToWidth = distance(10, 152) / cheek,
            foreheadToCheek = distance(103, 332) / cheek,
            jawToCheek = jaw / cheek,
            chinToJaw = distance(136, 365) / jaw,
            jawAngle = (leftAngle + rightAngle) / 2f
        ).takeIf { it.isUsable() }
    }

    fun classify(measurements: Measurements?): String {
        val m = measurements?.takeIf { it.isUsable() } ?: return "Unclear"
        // These conservative starting ranges require validation on labeled frontal scans.
        // Broad/angular jaws distinguish square from round at similar height/width ratios.
        val angularJaw = m.jawToCheek >= 0.80f && m.chinToJaw >= 0.52f && m.jawAngle <= 140f
        val softJaw = m.jawAngle >= 147f && m.jawToCheek <= 0.88f
        return when {
            m.lengthToWidth >= 1.55f -> "Long"
            angularJaw && m.foreheadToCheek >= 0.82f && m.lengthToWidth <= 1.40f -> "Square"
            softJaw && m.lengthToWidth <= 1.28f && m.chinToJaw >= 0.45f -> "Round"
            softJaw && m.lengthToWidth >= 1.34f && m.lengthToWidth <= 1.49f &&
                m.foreheadToCheek >= 0.78f && m.jawToCheek <= 0.82f -> "Oval"
            else -> "Unclear"
        }
    }

    fun median(samples: List<Measurements>): Measurements? {
        val valid = samples.filter { it.isUsable() }
        if (valid.isEmpty()) return null
        return Measurements(
            medianValue(valid.map { it.lengthToWidth }),
            medianValue(valid.map { it.foreheadToCheek }),
            medianValue(valid.map { it.jawToCheek }),
            medianValue(valid.map { it.chinToJaw }),
            medianValue(valid.map { it.jawAngle })
        )
    }

    private fun Measurements.isUsable() =
        lengthToWidth.isFinite() && lengthToWidth in 0.8f..2.2f &&
            foreheadToCheek.isFinite() && foreheadToCheek in 0.5f..1.3f &&
            jawToCheek.isFinite() && jawToCheek in 0.4f..1.15f &&
            chinToJaw.isFinite() && chinToJaw in 0.2f..1.1f &&
            jawAngle.isFinite() && jawAngle in 60f..180f

    private data class Point(val x: Float, val y: Float) {
        fun distance(other: Point) = hypot(x - other.x, y - other.y)
    }

    private fun angle(a: Point, vertex: Point, b: Point): Float {
        val denominator = a.distance(vertex) * b.distance(vertex)
        if (denominator < 0.001f) return Float.NaN
        val dot = (a.x - vertex.x) * (b.x - vertex.x) + (a.y - vertex.y) * (b.y - vertex.y)
        return Math.toDegrees(acos((dot / denominator).coerceIn(-1f, 1f)).toDouble()).toFloat()
    }
}

internal fun medianValue(values: List<Float>): Float {
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2f else sorted[middle]
}
