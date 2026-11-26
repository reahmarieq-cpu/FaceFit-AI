package com.example.facefit.facefit.analysis

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * In-plane straightening of the face before the face-shape classifier crop: the image is rotated
 * about the face centre so the line through the outer eye corners (landmarks 33 and 263) is level.
 * Angles follow OpenCV's getRotationMatrix2D: positive is counter-clockwise on screen (y down).
 */
internal object FaceAlignment {
    /** Below this the face is already level; rotating would only resample the image. */
    const val MIN_DEGREES = 0.2f
    /** Larger tilts are refused by the scan checks; never rotate more than this. */
    const val MAX_DEGREES = 15f

    /** Angle of the eye line in degrees (0 = level), or null when the eye corners are missing. */
    fun eyeLineDegrees(landmarks: List<FaceAnalyzer.LandmarkPoint>, width: Int, height: Int): Float? {
        if (landmarks.size <= 263 || width <= 0 || height <= 0) return null
        var dx = (landmarks[263].x - landmarks[33].x) * width
        var dy = (landmarks[263].y - landmarks[33].y) * height
        if (dx < 0f) { dx = -dx; dy = -dy }  // mirrored image: same line, other direction
        if (dx < 1f) return null
        return Math.toDegrees(atan2(dy, dx).toDouble()).toFloat().takeIf { it.isFinite() && abs(it) <= 90f }
    }

    /** Where point (x, y) lands after rotating the image by [degrees] about (cx, cy). */
    fun rotate(x: Float, y: Float, cx: Float, cy: Float, degrees: Float): Pair<Float, Float> {
        val r = Math.toRadians(degrees.toDouble())
        val a = cos(r).toFloat()
        val b = sin(r).toFloat()
        return Pair(cx + a * (x - cx) + b * (y - cy), cy - b * (x - cx) + a * (y - cy))
    }
}
