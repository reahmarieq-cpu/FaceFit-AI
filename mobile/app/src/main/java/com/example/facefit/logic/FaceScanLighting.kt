package com.example.facefit.logic

import android.graphics.Bitmap
import kotlin.math.abs

object FaceScanLighting {
    data class Measurement(
        val mean: Float,
        val darkFraction: Float,
        val brightFraction: Float,
        val sideDifference: Float
    ) {
        fun guidance(): String? = when {
            mean < 0.16f || darkFraction > 0.65f -> "Move to brighter, even lighting"
            mean > 0.86f || brightFraction > 0.25f -> "Move away from strong light"
            sideDifference > 0.20f -> "Light both sides of your face evenly"
            else -> null
        }
    }

    /** Samples the inner face ellipse, excluding the background corners. Values are 0..1. */
    fun measure(bitmap: Bitmap, points: List<FaceAnalyzer.LandmarkPoint>): Measurement? {
        if (points.size <= 454 || bitmap.isRecycled) return null
        val left = minOf(points[234].x, points[454].x) * bitmap.width
        val right = maxOf(points[234].x, points[454].x) * bitmap.width
        val top = points[10].y * bitmap.height
        val bottom = points[152].y * bitmap.height
        if (!listOf(left, right, top, bottom).all { it.isFinite() } ||
            right - left < 2 || bottom - top < 2) return null
        var leftTotal = 0f
        var rightTotal = 0f
        var leftCount = 0
        var rightCount = 0
        var dark = 0
        var bright = 0
        for (row in 0 until 12) {
            for (column in 0 until 12) {
                val nx = (column + .5f) / 6f - 1f
                val ny = (row + .5f) / 6f - 1f
                if (nx * nx + ny * ny > .85f) continue
                val x = (left + (column + .5f) / 12f * (right - left))
                    .toInt().coerceIn(0, bitmap.width - 1)
                val y = (top + (row + .5f) / 12f * (bottom - top))
                    .toInt().coerceIn(0, bitmap.height - 1)
                val pixel = bitmap.getPixel(x, y)
                val value = (.2126f * (pixel shr 16 and 255) +
                    .7152f * (pixel shr 8 and 255) + .0722f * (pixel and 255)) / 255f
                if (column < 6) { leftTotal += value; leftCount++ }
                else { rightTotal += value; rightCount++ }
                if (value < .10f) dark++
                if (value > .96f) bright++
            }
        }
        val count = leftCount + rightCount
        return Measurement((leftTotal + rightTotal) / count, dark.toFloat() / count,
            bright.toFloat() / count, abs(leftTotal / leftCount - rightTotal / rightCount))
    }
}
