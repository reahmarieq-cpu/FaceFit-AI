package com.example.facefit.facefit.analysis

import kotlin.math.roundToInt

/**
 * Live guidance before capture: camera distance (FacialFeatureMeasurement's per-frame estimate
 * from focal length + cheek width) and expression (MediaPipe smile score). The distance estimate
 * is repeatable for one person on one phone; its absolute value can be off by a few cm.
 */
class ScanDistanceGuide(private val window: Int = 5, private val staleAfterMs: Long = 1000L) {
    /** [inRange] means the person may capture: right distance and not smiling. */
    data class Hint(
        val distanceCm: Int, val text: String, val inRange: Boolean, val smiling: Boolean = false,
        /** Smoothed smile and upper-lip-raise scores behind this hint, for diagnostics. */
        val smileScore: Float? = null,
        val upperLipRaise: Float? = null,
    ) {
        /** Message for a Capture tap that is refused. */
        val refusal: String get() = when {
            smiling -> FacialFeatureMeasurement.Rejection.SMILING.guidance
            distanceCm < TARGET_MIN_CM -> FacialFeatureMeasurement.Rejection.TOO_CLOSE.guidance
            else -> FacialFeatureMeasurement.Rejection.TOO_FAR.guidance
        }
    }

    companion object {
        const val TARGET_MIN_CM = FacialFeatureMeasurement.TARGET_MIN_CM
        const val TARGET_MAX_CM = FacialFeatureMeasurement.TARGET_MAX_CM

        fun hintFor(distanceCm: Float, smileScore: Float? = null, upperLipRaise: Float? = null): Hint {
            val cm = distanceCm.roundToInt()
            val hint = when {
                FacialFeatureMeasurement.isSmiling(smileScore, upperLipRaise) ->
                    Hint(cm, "No smile please, relax your face", inRange = false, smiling = true)
                distanceCm < TARGET_MIN_CM -> Hint(cm, "$cm cm · Move a little farther", false)
                distanceCm > TARGET_MAX_CM -> Hint(cm, "$cm cm · Move a little closer", false)
                else -> Hint(cm, "$cm cm · Good distance, hold here", true)
            }
            return hint.copy(smileScore = smileScore, upperLipRaise = upperLipRaise)
        }
    }

    private val readings = ArrayDeque<Float>()
    private val smiles = ArrayDeque<Float>()
    private val lipRaises = ArrayDeque<Float>()
    private var lastAtMs = 0L

    /** Adds a reading (null distance when no single face was found) and returns the hint to show. */
    fun update(distanceCm: Float?, nowMs: Long, smileScore: Float? = null, upperLipRaise: Float? = null): Hint? {
        if (nowMs - lastAtMs > staleAfterMs) {
            readings.clear()
            smiles.clear()
            lipRaises.clear()
        }
        if (distanceCm == null || !distanceCm.isFinite() || distanceCm <= 0f) {
            return current(nowMs)
        }
        lastAtMs = nowMs
        readings.addLast(distanceCm)
        smiles.addLast(smileScore?.takeIf { it.isFinite() } ?: 0f)
        lipRaises.addLast(upperLipRaise?.takeIf { it.isFinite() } ?: 0f)
        while (readings.size > window) readings.removeFirst()
        // Expression reacts faster than distance: use the latest 3 frames.
        while (smiles.size > 3) smiles.removeFirst()
        while (lipRaises.size > 3) lipRaises.removeFirst()
        return current(nowMs)
    }

    private fun current(nowMs: Long): Hint? {
        if (readings.isEmpty() || nowMs - lastAtMs > staleAfterMs) return null
        return hintFor(medianValue(readings.toList()), medianValue(smiles.toList()),
            medianValue(lipRaises.toList()))
    }

    fun reset() {
        readings.clear()
        smiles.clear()
        lipRaises.clear()
        lastAtMs = 0L
    }
}
