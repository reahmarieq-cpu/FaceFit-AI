package com.example.facefit

import com.example.facefit.logic.FaceShapeDecision
import com.example.facefit.logic.FaceShapePrediction
import org.junit.Assert.*
import org.junit.Test

class FaceShapeDecisionTest {
    private val classes = listOf("Diamond", "Heart", "Oblong", "Oval", "Round", "Square", "Triangle")
    private fun prediction(label: String = "Round", top: Float = 0.75f,
        second: Float = 0.20f, runner: String = if (label == "Round") "Square" else "Round") =
        FaceShapePrediction(label, classes.associateWith {
            when (it) { label -> top; runner -> second; else -> (1f - top - second) / 5f }
        })

    @Test fun stableStrongPredictionsAreAcceptedForEverySupportedClass() {
        classes.forEach { label ->
            val p = prediction(label)
            val result = FaceShapeDecision.assess(List(10) { p }, p)
            assertEquals(label, result.label)
            assertEquals("accepted", result.reason)
            assertEquals(1f, result.agreement, 0f)
        }
    }

    @Test fun nearTiesAndWeakWinnersAreNotForcedIntoACategory() {
        val tied = prediction(top = 0.49f, second = 0.48f)
        val weak = prediction(top = 0.50f, second = 0.20f)
        assertEquals("closeScores", FaceShapeDecision.assess(List(10) { tied }, tied).reason)
        val rejected = FaceShapeDecision.assess(List(10) { weak }, weak)
        assertEquals("Uncertain", rejected.label)
        assertEquals("lowScore", rejected.reason)
    }

    @Test fun alternatingLabelsAreRejectedEvenWhenTheMedianLooksStrong() {
        val round = prediction(top = 0.90f, second = 0.05f)
        val square = prediction("Square", top = 0.90f, second = 0.05f)
        val result = FaceShapeDecision.assess(List(6) { round } + List(4) { square }, round)
        assertEquals("Uncertain", result.label)
        assertEquals("unstablePredictions", result.reason)
    }

    @Test fun aSmallNumberOfOutlierFramesDoesNotChangeAStrongConsensus() {
        val round = prediction(top = 0.85f, second = 0.10f)
        val square = prediction("Square", top = 0.70f, second = 0.25f)
        val result = FaceShapeDecision.assess(List(8) { round } + List(2) { square }, round)
        assertEquals("Round", result.label)
        assertEquals(0.8f, result.agreement, 0.00001f)
    }

    @Test fun medianDisagreementAndInsufficientFramesAreRejected() {
        val round = prediction()
        assertEquals("Uncertain", FaceShapeDecision.assess(List(10) { round }, prediction("Square")).label)
        assertEquals("tooFewFrames", FaceShapeDecision.assess(List(9) { round }, round).reason)
    }

    @Test fun invalidScoresCannotProduceAConfidentResult() {
        val good = prediction()
        val invalid = good.copy(scores = good.scores + ("Round" to Float.NaN))
        assertEquals("invalidScores", FaceShapeDecision.assess(List(10) { invalid }, good).reason)
        val unnormalized = good.copy(scores = good.scores.mapValues { it.value * 0.5f })
        assertEquals("invalidScores", FaceShapeDecision.assess(List(10) { unnormalized }, good).reason)
    }

    @Test fun aWeakMedianDoesNotOverrideTheFrameConsensus() {
        val good = prediction()
        val weak = prediction(top = 0.45f, second = 0.30f)
        assertEquals("Uncertain", FaceShapeDecision.assess(List(10) { good }, weak).label)
    }
}
