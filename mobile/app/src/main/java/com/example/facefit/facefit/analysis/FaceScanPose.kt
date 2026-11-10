package com.example.facefit.facefit.analysis

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.atan2

object FaceScanPose {
    data class Angles(val pitch: Float, val yaw: Float, val roll: Float)

    /** MediaPipe Android provides its canonical-to-face matrix in column-major order. */
    fun angles(matrix: FloatArray): Angles? {
        if (matrix.size != 16 || matrix.any { !it.isFinite() } ||
            hypot(matrix[0], matrix[1]) < 0.001f) return null
        return Angles(
            Math.toDegrees(atan2(matrix[6], matrix[10]).toDouble()).toFloat(),
            Math.toDegrees(atan2(-matrix[2], hypot(matrix[0], matrix[1])).toDouble()).toFloat(),
            Math.toDegrees(atan2(matrix[1], matrix[0]).toDouble()).toFloat()
        )
    }

    fun cheekSpan(metrics: FaceAnalyzer.FaceMetrics): Float {
        if (metrics.imageWidth <= 0 || metrics.imageHeight <= 0 ||
            metrics.landmarks.size <= 454) return Float.NaN
        val left = metrics.landmarks[234]
        val right = metrics.landmarks[454]
        return hypot((left.x - right.x) * metrics.imageWidth,
            (left.y - right.y) * metrics.imageHeight)
    }

    /** Conservative pose proxies, not a calibrated head-pose/identity estimator. */
    fun isFacingCamera(metrics: FaceAnalyzer.FaceMetrics): Boolean {
        val points = metrics.landmarks
        if (metrics.imageWidth <= 0 || metrics.imageHeight <= 0 ||
            listOf(1, 234, 454).any { it !in points.indices }) return false
        fun distance(a: Int, b: Int) = hypot(
            (points[a].x - points[b].x) * metrics.imageWidth,
            (points[a].y - points[b].y) * metrics.imageHeight
        )
        val cheek = distance(234, 454)
        val leftNose = distance(234, 1)
        val rightNose = distance(454, 1)
        if (!cheek.isFinite() || cheek < 1f || leftNose + rightNose < 1f) return false
        val asymmetry = abs(leftNose - rightNose) / (leftNose + rightNose)
        val depthDifference = abs(points[234].z - points[454].z) * metrics.imageWidth / cheek
        return asymmetry.isFinite() && depthDifference.isFinite() &&
            asymmetry <= 0.12f && depthDifference <= 0.25f
    }
}
