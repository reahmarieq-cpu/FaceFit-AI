package com.example.facefit.logic

import kotlin.math.abs

data class FaceShapePrediction(val label: String, val scores: Map<String, Float>)

/** Rejection rules for noisy/ambiguous predictions; scores are not calibrated accuracy. */
object FaceShapeDecision {
    const val POLICY_ID = "frameAgreementV1"
    const val MIN_FRAMES = 10
    const val MIN_SCORE = 0.60f
    const val MIN_MARGIN = 0.15f
    const val MIN_AGREEMENT = 0.80f
    private const val EPS = 0.000001f

    data class Assessment(
        val label: String,
        val scores: Map<String, Float>,
        val agreement: Float,
        val reason: String
    )

    fun assess(frames: List<FaceShapePrediction>, median: FaceShapePrediction): Assessment {
        fun rejected(reason: String) = Assessment("Uncertain", emptyMap(), 0f, reason)
        if (frames.size < MIN_FRAMES) return rejected("tooFewFrames")
        val classes = median.scores.keys
        fun valid(p: FaceShapePrediction) = classes.size == 7 && p.label in classes &&
            p.scores.keys == classes && p.scores.values.all { it.isFinite() && it in 0f..1.00001f } &&
            abs(p.scores.values.sum() - 1f) <= 0.01f
        if (!valid(median) || frames.any { !valid(it) }) return rejected("invalidScores")
        val mean = classes.associateWith { label -> frames.map { it.scores.getValue(label) }.average().toFloat() }
        val ordered = mean.entries.sortedByDescending { it.value }
        val candidate = ordered.first().key
        val agreement = frames.count { it.label == candidate }.toFloat() / frames.size
        val reason = when {
            agreement + EPS < MIN_AGREEMENT || median.label != candidate -> "unstablePredictions"
            ordered[0].value - ordered[1].value + EPS < MIN_MARGIN -> "closeScores"
            ordered[0].value + EPS < MIN_SCORE -> "lowScore"
            // The coordinate-wise median must independently support the same result.
            median.scores.getValue(candidate) + EPS < MIN_SCORE -> "lowScore"
            median.scores.getValue(candidate) - median.scores.filterKeys { it != candidate }
                .values.max() + EPS < MIN_MARGIN -> "closeScores"
            else -> "accepted"
        }
        return Assessment(if (reason == "accepted") candidate else "Uncertain", mean, agreement, reason)
    }
}
