package com.example.facefit.logic

import kotlin.math.hypot

/** Seven Experiment 18B descriptors matching compute_features in the Python v10 source.
 * These inputs are separate from the cheek-relative ratios displayed in results.
 */
object ScanStabilityFeatures {
    val names = listOf(
        "D1_face_aspect", "D2_upper_width", "D3_jaw_chord", "D4_chin_depth",
        "D5_jaw_arc_length", "D7_cheek_vertical_span", "D8_cheek_lateral_span"
    )
    private val face = listOf(10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288,
        397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58,
        132, 93, 234, 127, 162, 21, 54, 103, 67, 109)
    private val chin = listOf(172, 136, 150, 149, 176, 148, 152, 377, 400, 378, 379, 365, 397)
    private val leftCheek = listOf(234, 93, 132, 58, 172)
    private val rightCheek = listOf(454, 323, 361, 288, 397)

    fun extract(points: List<FaceAnalyzer.LandmarkPoint>, width: Int, height: Int): List<Float>? {
        if (width <= 0 || height <= 0 || points.size < 478) return null
        if (points.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        // Use actual camera pixel dimensions, never preview dimensions. D6 is excluded.
        fun x(i: Int) = points[i].x.toDouble() * width
        fun y(i: Int) = points[i].y.toDouble() * height
        fun spanX(ids: List<Int>) = ids.maxOf { x(it) } - ids.minOf { x(it) }
        fun spanY(ids: List<Int>) = ids.maxOf { y(it) } - ids.minOf { y(it) }
        fun distance(a: Int, b: Int) = hypot(x(a) - x(b), y(a) - y(b))
        val faceWidth = spanX(face)
        val faceHeight = spanY(face)
        val chord = distance(172, 397)
        if (faceWidth <= 2.0 || faceHeight <= 2.0 || chord <= 2.0) return null
        var nx = -(y(397) - y(172)) / chord
        var ny = (x(397) - x(172)) / chord
        fun projection(i: Int) = (x(i) - x(172)) * nx + (y(i) - y(172)) * ny
        if (projection(152) < 0.0) { nx = -nx; ny = -ny }
        val depth = chin.maxOf { projection(it) }
        if (depth <= 0.0) return null
        val arc = chin.zipWithNext().sumOf { (a, b) -> distance(a, b) }
        return listOf(
            faceHeight / faceWidth,
            distance(103, 332) / faceWidth,
            chord / faceWidth,
            depth / chord,
            arc / faceWidth,
            (spanY(leftCheek) + spanY(rightCheek)) / (2 * faceWidth),
            (spanX(leftCheek) + spanX(rightCheek)) / (2 * faceWidth)
        ).map { it.toFloat() }.takeIf { isValid(it) }
    }

    fun isValid(features: List<Float>) = features.size == names.size &&
        features.all { it.isFinite() && it >= 0f }
}
