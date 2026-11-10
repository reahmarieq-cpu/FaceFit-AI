package com.facefit.kiosk.mobilescan

import com.facefit.kiosk.facefit.mobilescan.FaceShapeOnnxModel
import com.facefit.kiosk.facefit.mobilescan.FaceShapeDecision
import com.facefit.kiosk.facefit.mobilescan.FaceShapePrediction
import org.junit.Assert.*
import org.junit.Test

class FaceShapeDecisionTest {
    private fun prediction(label: String = "Round", top: Float = .75f,
        second: Float = .20f, runner: String = if (label == "Round") "Square" else "Round") =
        FaceShapePrediction(label, FaceShapeOnnxModel.CLASSES.associateWith {
            when (it) {
                label -> top
                runner -> second
                else -> (1f - top - second) / (FaceShapeOnnxModel.CLASSES.size - 2)
            }
        })

    @Test fun allFiveClassesCanBeAccepted() {
        FaceShapeOnnxModel.CLASSES.forEach { label ->
            val sample = prediction(label)
            val result = FaceShapeDecision.assess(List(10) { sample }, sample)
            assertEquals(label, result.label)
            assertEquals("highestScore", result.reason)
            assertEquals(1f, result.agreement, 0f)
        }
    }

    @Test fun closeAndWeakScoresStillReturnTheirHighestShape() {
        val tie = prediction(top = .49f, second = .48f)
        assertEquals("Round", FaceShapeDecision.assess(List(10) { tie }, tie).label)
        val weak = prediction(top = .37f, second = .31f, runner = "Heart")
        val result = FaceShapeDecision.assess(List(10) { weak }, weak)
        assertEquals("Round", result.label)
        assertEquals("highestScore", result.reason)
    }

    @Test fun highestMeanWinsEvenWhenMostFrameLabelsAndMedianDisagree() {
        val weakRound = prediction(top = .40f, second = .39f)
        val strongSquare = prediction(label = "Square", top = .99f, second = .005f)
        val result = FaceShapeDecision.assess(List(8) { weakRound } + List(2) { strongSquare }, weakRound)
        assertEquals("Square", result.label)
        assertEquals(.51f, result.scores.getValue("Square"), .00001f)
        assertEquals(.2f, result.agreement, .00001f)
    }

    @Test fun incompleteCaptureIsRejected() {
        val good = prediction()
        assertEquals("tooFewFrames", FaceShapeDecision.assess(List(9) { good }, good).reason)
        assertEquals("tooFewFrames", FaceShapeDecision.assess(emptyList(), good).reason)
    }

    @Test fun invalidOrIncompleteClassScoresAreRejected() {
        val good = prediction()
        val invalid = listOf(
            good.copy(scores = good.scores + ("Round" to Float.NaN)),
            good.copy(scores = good.scores.mapValues { it.value * .5f }),
            good.copy(scores = good.scores - "Oval"),
            good.copy(scores = good.scores + ("Diamond" to 0f))
        )
        invalid.forEach {
            assertEquals("invalidScores", FaceShapeDecision.assess(List(9) { good } + it, good).reason)
        }
    }

    @Test fun validMedianScoresDoNotOverrideTheAverageWinner() {
        val strong = prediction(top = .90f, second = .05f)
        val weak = prediction(top = .45f, second = .30f)
        assertEquals("Round", FaceShapeDecision.assess(List(10) { strong }, weak).label)
        val close = prediction(top = .60f, second = .39f)
        assertEquals("Round", FaceShapeDecision.assess(List(10) { strong }, close).label)
    }

    @Test fun exactTiesUseStableModelClassOrder() {
        val sample = prediction(label = "Square", top = .4f, second = .4f, runner = "Round")
        assertEquals("Round", FaceShapeDecision.assess(List(10) { sample }, sample).label)
    }
}
