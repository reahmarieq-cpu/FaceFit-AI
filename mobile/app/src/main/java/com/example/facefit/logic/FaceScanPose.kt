package com.example.facefit.logic

import kotlin.math.abs
import kotlin.math.hypot

object FaceScanPose {
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
            asymmetry <= 0.16f && depthDifference <= 0.30f
    }
}
