package com.example.facefit.logic

import kotlin.math.acos
import kotlin.math.hypot

/** Exact feature definitions/order from train_facefit_7classes_v2.py:extract_ratios. */
object FaceShapeFeatures {
    val names = listOf(
        "face_length_ratio", "upper_face_ratio", "jaw_ratio", "lower_jaw_width_ratio",
        "chin_width_ratio", "lower_face_taper_ratio", "cheek_prominence_ratio",
        "jaw_to_upper_ratio", "jaw_corner_angle_ratio", "jaw_contour_bend_ratio"
    )
    private val leftContour = listOf(172, 136, 150, 149, 176, 148, 152)
    private val rightContour = listOf(397, 365, 379, 378, 400, 377, 152)
    private val required = (leftContour + rightContour +
        listOf(10, 103, 332, 234, 454, 58, 288)).distinct()
    private const val EPS = 1e-6

    fun extract(points: List<FaceAnalyzer.LandmarkPoint>, width: Int, height: Int): List<Float>? {
        if (width <= 0 || height <= 0 || required.any { it !in points.indices }) return null
        if (points.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        // The training extractor uses float64 pixel coordinates, then ONNX accepts float32 ratios.
        fun dx(a: Int, b: Int) = (points[a].x.toDouble() - points[b].x.toDouble()) * width
        fun dy(a: Int, b: Int) = (points[a].y.toDouble() - points[b].y.toDouble()) * height
        fun distance(a: Int, b: Int) = hypot(dx(a, b), dy(a, b))
        fun angle(a: Int, pivot: Int, c: Int): Double {
            val denominator = distance(a, pivot) * distance(c, pivot)
            if (denominator <= EPS) return Double.NaN
            val dot = dx(a, pivot) * dx(c, pivot) + dy(a, pivot) * dy(c, pivot)
            return Math.toDegrees(acos((dot / denominator).coerceIn(-1.0, 1.0)))
        }
        fun bend(ids: List<Int>): Double {
            val chord = distance(ids.first(), ids.last())
            if (chord <= EPS) return Double.NaN
            return ids.zipWithNext().sumOf { (a, b) -> distance(a, b) } / chord
        }
        val cheek = distance(234, 454)
        val upper = distance(103, 332)
        val jaw = distance(172, 397)
        val lowerJaw = distance(150, 379)
        val chin = distance(176, 400)
        if (minOf(cheek, upper, jaw, lowerJaw) <= EPS) return null
        return listOf(
            distance(10, 152) / cheek,
            upper / cheek,
            jaw / cheek,
            lowerJaw / cheek,
            chin / cheek,
            chin / lowerJaw,
            cheek / maxOf(upper, jaw),
            jaw / upper,
            (angle(58, 172, 150) + angle(288, 397, 379)) / 2.0 / 180.0,
            (bend(leftContour) + bend(rightContour)) / 2.0
        ).map { it.toFloat() }.takeIf { isValid(it) }
    }

    fun isValid(features: List<Float>) = features.size == names.size && features.all { it.isFinite() }
}
