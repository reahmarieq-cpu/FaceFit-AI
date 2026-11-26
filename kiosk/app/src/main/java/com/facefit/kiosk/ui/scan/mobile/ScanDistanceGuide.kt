package com.facefit.kiosk.ui.scan.mobile

import com.facefit.kiosk.facefit.mobilescan.medianValue
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Live guidance before capture: camera distance (FacialFeatureMeasurement's per-frame estimate
 * from focal length + cheek width), expression (MediaPipe smile and upper-lip scores) and head
 * pose (MediaPipe transformation matrix). The distance estimate is repeatable for one person on
 * one phone; its absolute value can be off by a few cm.
 */
class ScanDistanceGuide(private val window: Int = 5, private val staleAfterMs: Long = 1000L) {
    /** [inRange] means the person may capture: right distance, neutral expression and level head. */
    data class Hint(
        val distanceCm: Int, val text: String, val inRange: Boolean, val smiling: Boolean = false,
        /** Smoothed scores behind this hint, for diagnostics. */
        val smileScore: Float? = null,
        val upperLipRaise: Float? = null,
        val pitchDeg: Float? = null,
        val yawDeg: Float? = null,
    )

    companion object {
        private val targetDistanceCm = FacialFeatureMeasurement.TARGET_DISTANCE_CM.roundToInt()

        /**
         * The chip asks for a little more than the scan limits so frames stay inside them after a
         * tap despite small head movements (about 0.5 degrees between frames on the test phone).
         */
        private const val POSE_MARGIN_DEG = 0.5f
        private val LIMITS = FacialFeatureMeasurement.QualityLimits()
        val MAX_PITCH_DEG = LIMITS.maxPitchDeg - POSE_MARGIN_DEG
        val MAX_YAW_DEG = LIMITS.maxYawDeg - POSE_MARGIN_DEG

        // Positive pitch is the top of the head toward the camera (chin down), the same
        // convention as the capture readiness messages and the measured upper-face trend.
        const val LIFT_CHIN = "Lift your chin slightly"
        const val LOWER_CHIN = "Lower your chin slightly"
        const val FACE_CAMERA = "Turn to face the camera"

        fun hintFor(
            distanceCm: Float, smileScore: Float? = null, upperLipRaise: Float? = null,
            pitchDeg: Float? = null, yawDeg: Float? = null,
        ): Hint {
            val cm = distanceCm.roundToInt()
            val pose = when {
                pitchDeg != null && pitchDeg > MAX_PITCH_DEG -> LIFT_CHIN
                pitchDeg != null && pitchDeg < -MAX_PITCH_DEG -> LOWER_CHIN
                yawDeg != null && abs(yawDeg) > MAX_YAW_DEG -> FACE_CAMERA
                else -> null
            }
            val hint = when {
                cm < targetDistanceCm -> Hint(cm, "$cm cm · Move a little farther (aim for $targetDistanceCm)", false)
                cm > targetDistanceCm -> Hint(cm, "$cm cm · Move a little closer (aim for $targetDistanceCm)", false)
                FacialFeatureMeasurement.isSmiling(smileScore, upperLipRaise) ->
                    Hint(cm, "No smile please, relax your face", inRange = true, smiling = true)
                pose != null -> Hint(cm, "$cm cm · $pose", true)
                else -> Hint(cm, "$cm cm · Good, hold still and tap Capture", true)
            }
            return hint.copy(smileScore = smileScore, upperLipRaise = upperLipRaise,
                pitchDeg = pitchDeg, yawDeg = yawDeg)
        }
    }

    private val readings = ArrayDeque<Float>()
    private val smiles = ArrayDeque<Float>()
    private val lipRaises = ArrayDeque<Float>()
    private val pitches = ArrayDeque<Float>()
    private val yaws = ArrayDeque<Float>()
    private var lastAtMs = 0L

    /** Adds a reading (null distance when no single face was found) and returns the hint to show. */
    fun update(
        distanceCm: Float?, nowMs: Long, smileScore: Float? = null, upperLipRaise: Float? = null,
        pitchDeg: Float? = null, yawDeg: Float? = null,
    ): Hint? {
        if (nowMs - lastAtMs > staleAfterMs) clearReadings()
        if (distanceCm == null || !distanceCm.isFinite() || distanceCm <= 0f) {
            return current(nowMs)
        }
        lastAtMs = nowMs
        readings.addLast(distanceCm)
        smiles.addLast(smileScore?.takeIf { it.isFinite() } ?: 0f)
        lipRaises.addLast(upperLipRaise?.takeIf { it.isFinite() } ?: 0f)
        pitchDeg?.takeIf { it.isFinite() }?.let { pitches.addLast(it) }
        yawDeg?.takeIf { it.isFinite() }?.let { yaws.addLast(it) }
        while (readings.size > window) readings.removeFirst()
        // Expression and pose react faster than distance: use the latest 3 frames.
        listOf(smiles, lipRaises, pitches, yaws).forEach { while (it.size > 3) it.removeFirst() }
        return current(nowMs)
    }

    private fun current(nowMs: Long): Hint? {
        if (readings.isEmpty() || nowMs - lastAtMs > staleAfterMs) return null
        return hintFor(medianValue(readings.toList()), medianValue(smiles.toList()),
            medianValue(lipRaises.toList()),
            pitches.takeIf { it.isNotEmpty() }?.let { medianValue(it.toList()) },
            yaws.takeIf { it.isNotEmpty() }?.let { medianValue(it.toList()) })
    }

    private fun clearReadings() {
        listOf(readings, smiles, lipRaises, pitches, yaws).forEach { it.clear() }
    }

    fun reset() {
        clearReadings()
        lastAtMs = 0L
    }
}
