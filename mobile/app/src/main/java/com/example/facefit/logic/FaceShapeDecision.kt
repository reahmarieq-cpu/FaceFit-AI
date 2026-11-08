package com.example.facefit.logic

import kotlin.math.abs

data class FaceShapePrediction(val label: String, val scores: Map<String, Float>)

/** Selects the largest average model score across valid scan frames. */
object FaceShapeDecision {
    const val MIN_FRAMES = 10

    data class Assessment(
        val label: String,
        val scores: Map<String, Float>,
        val agreement: Float,
        val reason: String
    )

    fun assess(frames: List<FaceShapePrediction>, median: FaceShapePrediction): Assessment {
        fun rejected(reason: String) = Assessment("Uncertain", emptyMap(), 0f, reason)
        if (frames.size < MIN_FRAMES) return rejected("tooFewFrames")
        val classes = FaceShapeOnnxModel.CLASSES.toSet()
        fun valid(p: FaceShapePrediction) = p.label in classes &&
            p.scores.keys == classes && p.scores.values.all { it.isFinite() && it in 0f..1.00001f } &&
            abs(p.scores.values.sum() - 1f) <= 0.01f
        if (!valid(median) || frames.any { !valid(it) }) return rejected("invalidScores")
        val mean = classes.associateWith { label -> frames.map { it.scores.getValue(label) }.average().toFloat() }
        // Exact ties follow the model's stable class order.
        val candidate = mean.maxBy { it.value }.key
        val agreement = frames.count { it.label == candidate }.toFloat() / frames.size
        return Assessment(candidate, mean, agreement, "highestScore")
    }
}
