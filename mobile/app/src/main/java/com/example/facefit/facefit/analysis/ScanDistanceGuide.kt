package com.example.facefit.facefit.analysis

import kotlin.math.roundToInt

/**
 * Live distance guidance before capture, from FacialFeatureMeasurement's per-frame estimate
 * (focal length + cheek width, assuming MediaPipe's average 15.3 cm face). The estimate is
 * repeatable for one person on one phone; its absolute value can be off by a few percent for
 * faces wider or narrower than average.
 */
class ScanDistanceGuide(private val window: Int = 5, private val staleAfterMs: Long = 1000L) {
    data class Hint(val distanceCm: Int, val text: String, val inRange: Boolean)

    companion object {
        /** Range where the repeated phone scans were most consistent. */
        const val TARGET_MIN_CM = 45f
        const val TARGET_MAX_CM = 50f

        fun hintFor(distanceCm: Float): Hint {
            val cm = distanceCm.roundToInt()
            return when {
                distanceCm < TARGET_MIN_CM -> Hint(cm, "$cm cm · Move a little farther", false)
                distanceCm > TARGET_MAX_CM -> Hint(cm, "$cm cm · Move a little closer", false)
                else -> Hint(cm, "$cm cm · Good distance, hold here", true)
            }
        }
    }

    private val readings = ArrayDeque<Float>()
    private var lastAtMs = 0L

    /** Adds a reading (null when no single face was found) and returns the hint to show. */
    fun update(distanceCm: Float?, nowMs: Long): Hint? {
        if (nowMs - lastAtMs > staleAfterMs) readings.clear()
        if (distanceCm == null || !distanceCm.isFinite() || distanceCm <= 0f) {
            return current(nowMs)
        }
        lastAtMs = nowMs
        readings.addLast(distanceCm)
        while (readings.size > window) readings.removeFirst()
        return current(nowMs)
    }

    private fun current(nowMs: Long): Hint? {
        if (readings.isEmpty() || nowMs - lastAtMs > staleAfterMs) return null
        return hintFor(medianValue(readings.toList()))
    }

    fun reset() {
        readings.clear()
        lastAtMs = 0L
    }
}
