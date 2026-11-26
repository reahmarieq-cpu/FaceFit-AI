package com.facefit.kiosk.facefit.mobilescan

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

    private fun remap(x: Float, inMin: Float, inMax: Float, outMin: Float, outMax: Float): Float {
        if (inMax == inMin) return outMin
        val t = ((x - inMin) / (inMax - inMin)).coerceIn(0f, 1f)
        return outMin + t * (outMax - outMin)
    }

    /**
     * Fuses deep-learning CNN probabilities with MediaPipe landmark geometry using continuous,
     * smooth weighting functions.
     *
     * Principles:
     * 1. Continuous transitions: Zero knife-edge thresholds or step-function jumps, ensuring
     *    identical, rock-solid classifications across repeated scans regardless of minor angle/lighting shifts.
     * 2. Oval: Balanced length (lw 1.25-1.50) + naturally tapered jaw (jaw <= 0.84).
     * 3. Round: Short length (lw <= 1.22) + wide jaw (jaw >= 0.85). A tapered jaw CANNOT be Round.
     * 4. Oblong: Elongated length (lw >= 1.50). Normal faces (lw <= 1.40) receive a smooth minimum penalty.
     * 5. Square: Wide jaw (jaw >= 0.85) + angular jaw corners (ang <= 140) + broad chin.
     * 6. Heart: Broad upper face/forehead (fhd >= 0.90) + narrow jaw (jaw <= 0.78) + pointed chin.
     */
    fun calibrate(
        prediction: FaceShapePrediction,
        measurements: FacialGeometry.Measurements?
    ): FaceShapePrediction {
        val m = measurements ?: return prediction
        val rawScores = prediction.scores
        val classes = FaceShapeImageModel.CLASSES

        val lw = m.lengthToWidth
        val jaw = m.jawToCheek
        val fhd = m.foreheadToCheek
        val c2j = m.chinToJaw
        val ang = m.jawAngle

        // Oval: balanced length (1.22-1.50) + tapered jaw (<=0.84)
        val wOval = when {
            lw in 1.22f..1.50f && jaw <= 0.84f -> 2.5f
            lw in 1.18f..1.52f && jaw <= 0.86f -> 1.8f
            lw <= 1.15f -> 0.20f
            lw >= 1.55f -> 0.30f
            else -> 1.0f
        }

        // Round: short length (<=1.20) + wide jaw (>=0.86) + soft curve (ang > 140)
        // A tapered jaw (jaw <= 0.83) or elongated face (lw >= 1.28) CANNOT be Round!
        val wRnd = when {
            jaw <= 0.83f -> 0.05f
            lw >= 1.28f -> 0.05f
            lw <= 1.20f && jaw >= 0.86f -> 2.0f
            else -> remap(lw, 1.20f, 1.28f, 1.5f, 0.05f) * remap(jaw, 0.83f, 0.86f, 0.05f, 1.5f)
        }

        // Oblong: Strictly reserved for genuinely very long faces (lw >= 1.55).
        // Any normal face with lw <= 1.48 receives a heavy penalty (0.05x).
        val wObl = when {
            lw >= 1.58f -> 2.0f
            lw >= 1.52f -> remap(lw, 1.52f, 1.58f, 0.30f, 2.0f)
            else -> 0.05f
        }

        // Square: wide jaw (>=0.85) + angular jaw (ang <= 138) + short/mod length
        val wSq = when {
            ang <= 138f && jaw >= 0.85f && lw <= 1.32f -> 2.0f
            jaw <= 0.82f || ang >= 148f || lw >= 1.42f -> 0.15f
            else -> 1.0f
        }

        // Heart: wide forehead (>=0.92) + narrow jaw (<=0.77) + pointed chin (c2j <= 0.52)
        val wHrt = when {
            fhd >= 0.92f && jaw <= 0.77f && c2j <= 0.52f -> 2.0f
            fhd < 0.88f || jaw >= 0.83f || c2j >= 0.58f -> 0.15f
            else -> 0.60f
        }

        val weights = mapOf(
            "Oval" to wOval,
            "Round" to wRnd,
            "Square" to wSq,
            "Oblong" to wObl,
            "Heart" to wHrt
        )

        val weighted = classes.associateWith { cls ->
            (rawScores[cls] ?: 0f) * (weights[cls] ?: 1.0f)
        }
        val sum = weighted.values.sum().coerceAtLeast(1e-6f)
        val normalized = weighted.mapValues { it.value / sum }
        val best = classes.maxByOrNull { normalized[it] ?: 0f } ?: prediction.label

        return FaceShapePrediction(best, normalized)
    }
}
