package com.facefit.kiosk.model

import com.facefit.kiosk.data.frames.MockFrameCatalog
import com.facefit.kiosk.data.session.InMemoryKioskSessionStore
import com.facefit.kiosk.facefit.analysis.*
import org.junit.Assert.*
import org.junit.Test

class ResultContractTest {
    @Test fun externalLabelsMapWithoutOvalFallback() {
        assertEquals(FaceShape.Square, FaceShape.fromLabel(" SQUARE "))
        assertEquals(FaceShape.Heart, FaceShape.fromLabel("heart_shaped"))
        assertEquals(FaceShape.Diamond, FaceShape.fromLabel("diamond"))
        assertEquals(FaceShape.Undetermined, FaceShape.fromLabel("unrecognized"))
        assertEquals(FaceShape.Undetermined, FaceShape.fromLabel(null))
    }
    @Test fun externalAnalyzerCanReturnProfileWithoutLocalMetrics() {
        val expected = FaceScanResult(FaceShape.Round, 87, listOf("Rounded profile"), isEstimate = false)
        val analyzer = object : KioskAnalyzer {
            override fun analyze(capturedImagePath: String) = KioskAnalysisOutcome.Success(expected)
        }
        val outcome = analyzer.analyze("capture.jpg")
        assertEquals(FaceShape.Round, outcome.profile.faceShape)
        assertEquals(87, outcome.profile.confidencePercent)
        assertNull(outcome.profile.metrics)
    }
    @Test fun missingConfidenceRemainsOptionalAndPreviewScoresAreProvided() {
        val result = FaceScanResult(FaceShape.Heart)
        assertNull(result.confidencePercent)
        val matches = MockFrameCatalog().recommendationsFor(result, "demo-shop")
        assertEquals(12, matches.size)
        assertEquals(matches.size, matches.map { it.frame.id }.distinct().size)
        assertEquals(9, matches.count { it.frame.tryOn?.has3DModel == true })
        assertEquals(96, matches.first().compatibilityPercent)
        assertEquals(98, matches.first().faceShapeScore)
        assertEquals(95, matches.first().proportionFitScore)
        assertEquals(94, matches.first().styleColorScore)
        assertNull(FrameRecommendation(matches.first().frame).compatibilityPercent)
        assertTrue(MockFrameCatalog().recommendationsFor(result, "another-shop").isEmpty())
    }
    @Test fun rescanClearsPreviousResultAndSelection() {
        val session = InMemoryKioskSessionStore()
        session.analysisResult = FaceScanResult(FaceShape.Square)
        session.selectedRecommendation = MockFrameCatalog().recommendationsFor(session.analysisResult!!, "demo-shop").first()
        session.clearCustomerSession()
        assertNull(session.analysisResult)
        assertNull(session.selectedRecommendation)
    }
    @Test fun localAdapterPreservesNoFaceFailure() {
        val gateway = object : FaceAnalysisGateway {
            override fun analyze(capturedImagePath: String) = FaceAnalysisResult.NoFaceDetected
        }
        assertEquals(KioskAnalysisOutcome.NoFace, LocalKioskAnalyzer(gateway).analyze("capture.jpg"))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsConfidenceOutsideContract() { FaceScanResult(FaceShape.Oval, 101) }
}