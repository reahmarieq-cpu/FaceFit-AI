package com.example.facefit

import com.example.facefit.facefit.analysis.FaceShapeImageModel
import com.example.facefit.facefit.analysis.FaceShapeDecision
import com.example.facefit.facefit.analysis.FaceShapePrediction
import com.example.facefit.facefit.analysis.FacialGeometry
import org.junit.Assert.*
import org.junit.Test

class FaceShapeDecisionTest {
    private fun prediction(label: String = "Round", top: Float = .75f,
        second: Float = .20f, runner: String = if (label == "Round") "Square" else "Round") =
        FaceShapePrediction(label, FaceShapeImageModel.CLASSES.associateWith {
            when (it) {
                label -> top
                runner -> second
                else -> (1f - top - second) / (FaceShapeImageModel.CLASSES.size - 2)
            }
        })

    @Test fun allFiveClassesCanBeAccepted() {
        FaceShapeImageModel.CLASSES.forEach { label ->
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

    @Test fun calibrateCorrectsFalseRoundToOvalWhenGeometryIsElongated() {
        val sample = FaceShapePrediction("Round", mapOf(
            "Round" to 0.42f,
            "Oval" to 0.36f,
            "Square" to 0.10f,
            "Oblong" to 0.07f,
            "Heart" to 0.05f
        ))
        val ovalGeometry = FacialGeometry.Measurements(
            lengthToWidth = 1.38f,
            foreheadToCheek = 0.85f,
            jawToCheek = 0.78f,
            chinToJaw = 0.55f,
            jawAngle = 150f
        )
        val calibrated = FaceShapeDecision.calibrate(sample, ovalGeometry)
        assertEquals("Oval", calibrated.label)
        assertTrue(calibrated.scores.getValue("Oval") > calibrated.scores.getValue("Round"))
    }

    @Test fun calibrateRetainsRoundWhenGeometryIsCompact() {
        val sample = FaceShapePrediction("Round", mapOf(
            "Round" to 0.45f,
            "Oval" to 0.30f,
            "Square" to 0.12f,
            "Oblong" to 0.05f,
            "Heart" to 0.08f
        ))
        val roundGeometry = FacialGeometry.Measurements(
            lengthToWidth = 1.18f,
            foreheadToCheek = 0.85f,
            jawToCheek = 0.88f,
            chinToJaw = 0.55f,
            jawAngle = 152f
        )
        val calibrated = FaceShapeDecision.calibrate(sample, roundGeometry)
        assertEquals("Round", calibrated.label)
        assertTrue(calibrated.scores.getValue("Round") > calibrated.scores.getValue("Oval"))
    }

    @Test fun calibrateFavorsOvalOverHeartWhenCheeksAreWidest() {
        val sample = FaceShapePrediction("Oval", mapOf(
            "Oval" to 0.45f,
            "Heart" to 0.35f,
            "Oblong" to 0.10f,
            "Round" to 0.08f,
            "Square" to 0.02f
        ))
        val ovalTaperGeometry = FacialGeometry.Measurements(
            lengthToWidth = 1.36f,
            foreheadToCheek = 0.86f, // Cheeks are wider than forehead (classic Oval)
            jawToCheek = 0.72f,
            chinToJaw = 0.38f,
            jawAngle = 148f
        )
        val calibrated = FaceShapeDecision.calibrate(sample, ovalTaperGeometry)
        assertEquals("Oval", calibrated.label)
        assertTrue(calibrated.scores.getValue("Oval") > calibrated.scores.getValue("Heart"))
    }

    @Test fun calibrateReturnsRawPredictionWhenMeasurementsAreNull() {
        val sample = FaceShapePrediction("Round", mapOf(
            "Round" to 0.50f,
            "Oval" to 0.30f,
            "Square" to 0.10f,
            "Oblong" to 0.05f,
            "Heart" to 0.05f
        ))
        val calibrated = FaceShapeDecision.calibrate(sample, null)
        assertEquals(sample, calibrated)
    }

    // ── New tests covering the when-expression weight blocks ──────────────────

    @Test fun calibrateAppliesHeavyPenaltyToOblongWhenFaceIsNotElongated() {
        // lw = 1.35 (well below 1.52 threshold) → Oblong should receive 0.05x penalty
        // The model has strong Oblong signal (0.60) but geometry strongly contradicts it
        val sample = FaceShapePrediction("Oblong", mapOf(
            "Oblong" to 0.60f,
            "Oval"   to 0.25f,
            "Round"  to 0.07f,
            "Square" to 0.05f,
            "Heart"  to 0.03f
        ))
        val normalGeometry = FacialGeometry.Measurements(
            lengthToWidth   = 1.35f,  // NOT oblong
            foreheadToCheek = 0.84f,
            jawToCheek      = 0.80f,
            chinToJaw       = 0.55f,
            jawAngle        = 148f
        )
        val calibrated = FaceShapeDecision.calibrate(sample, normalGeometry)
        // After penalty, Oblong score must be suppressed and something else must win
        assertTrue("Oblong should not win with lw=1.35",
            calibrated.label != "Oblong")
        assertTrue("Oblong score must be much lower than Oval after penalty",
            calibrated.scores.getValue("Oblong") < calibrated.scores.getValue("Oval"))
    }

    @Test fun calibrateAcceptsOblongWhenFaceIsGenuinelyElongated() {
        val sample = FaceShapePrediction("Oblong", mapOf(
            "Oblong" to 0.55f,
            "Oval"   to 0.25f,
            "Round"  to 0.10f,
            "Square" to 0.05f,
            "Heart"  to 0.05f
        ))
        val longGeometry = FacialGeometry.Measurements(
            lengthToWidth   = 1.60f,  // clearly elongated
            foreheadToCheek = 0.82f,
            jawToCheek      = 0.78f,
            chinToJaw       = 0.56f,
            jawAngle        = 150f
        )
        val calibrated = FaceShapeDecision.calibrate(sample, longGeometry)
        assertEquals("Oblong", calibrated.label)
    }

    @Test fun calibrateIsStableAcrossSmallMeasurementNoise() {
        // A person with stable Oval geometry; simulate ±2% noise on each measurement.
        // Despite noise, the label must remain Oval in every trial.
        val baseRaw = FaceShapePrediction("Oval", mapOf(
            "Oval"   to 0.52f,
            "Round"  to 0.20f,
            "Square" to 0.12f,
            "Oblong" to 0.10f,
            "Heart"  to 0.06f
        ))
        val baseGeometry = FacialGeometry.Measurements(
            lengthToWidth   = 1.34f,
            foreheadToCheek = 0.85f,
            jawToCheek      = 0.80f,
            chinToJaw       = 0.56f,
            jawAngle        = 150f
        )
        val noiseFactors = listOf(-0.02f, -0.01f, 0f, 0.01f, 0.02f)
        for (nlw in noiseFactors) {
            for (njaw in noiseFactors) {
                val noisyGeometry = baseGeometry.copy(
                    lengthToWidth = baseGeometry.lengthToWidth + nlw,
                    jawToCheek    = baseGeometry.jawToCheek    + njaw
                )
                val result = FaceShapeDecision.calibrate(baseRaw, noisyGeometry)
                assertEquals(
                    "Expected Oval for lw=${noisyGeometry.lengthToWidth} jaw=${noisyGeometry.jawToCheek}",
                    "Oval", result.label
                )
            }
        }
    }

    @Test fun calibrateRoundRequiresBothShortLengthAndWideJaw() {
        // Tapered jaw alone must NOT produce Round (jaw <= 0.83 → 0.05x penalty on Round)
        val roundSignal = FaceShapePrediction("Round", mapOf(
            "Round"  to 0.55f,
            "Oval"   to 0.25f,
            "Square" to 0.10f,
            "Oblong" to 0.05f,
            "Heart"  to 0.05f
        ))
        val taperedJaw = FacialGeometry.Measurements(
            lengthToWidth   = 1.18f,
            foreheadToCheek = 0.85f,
            jawToCheek      = 0.78f,  // narrow jaw → cannot be Round
            chinToJaw       = 0.55f,
            jawAngle        = 155f
        )
        val result = FaceShapeDecision.calibrate(roundSignal, taperedJaw)
        assertTrue("Round must be suppressed when jaw is tapered (jaw=0.78 ≤ 0.83)",
            result.label != "Round")
    }
}
