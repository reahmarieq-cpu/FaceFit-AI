package com.example.facefit.facefit.analysis

import kotlin.math.atan2
import kotlin.math.sqrt

/** Input order and normalized 3D measurements of the Ultra Optimized Random Forest. */
internal object FaceShapeFeatures {
    val names = listOf(
        "width_to_height", "jaw_to_forehead", "jaw_to_face", "forehead_to_face",
        "face_area", "jaw_angle"
    )

    fun extract(points: List<FaceAnalyzer.LandmarkPoint>): List<Double>? {
        if (points.size <= 454) return null
        fun distance(a: Int, b: Int): Double {
            val dx = points[a].x.toDouble() - points[b].x
            val dy = points[a].y.toDouble() - points[b].y
            val dz = points[a].z.toDouble() - points[b].z
            return sqrt(dx * dx + dy * dy + dz * dz)
        }

        val height = distance(10, 152)
        val width = distance(234, 454)
        val forehead = distance(21, 251)
        val jaw = distance(172, 397)
        if (minOf(height, width, forehead, jaw) <= 1e-10) return null
        return listOf(
            width / height,
            jaw / forehead,
            jaw / width,
            forehead / width,
            width * height,
            atan2(points[397].y.toDouble() - points[172].y, points[397].x.toDouble() - points[172].x)
        ).takeIf(::isValid)
    }

    fun isValid(features: List<Double>) = features.size == names.size &&
        features.all { it.isFinite() } && features.take(5).all { it > 0.0 } &&
        features.last() in -Math.PI..Math.PI
}
