package com.facefit.kiosk.facefit.analysis

import com.facefit.kiosk.facefit.features.FacialFeatureExtractionResult
import com.facefit.kiosk.facefit.features.FacialFeatureExtractor
import com.facefit.kiosk.facefit.features.ProportionFaceShapeClassifier
import com.facefit.kiosk.model.FaceScanResult

/** Temporary adapter: real landmarks plus the provisional proportion-based classifier. */
class LocalKioskAnalyzer(
    private val gateway: FaceAnalysisGateway,
    private val extractor: FacialFeatureExtractor = FacialFeatureExtractor(ProportionFaceShapeClassifier)
) : KioskAnalyzer {
    override fun analyze(capturedImagePath: String): KioskAnalysisOutcome =
        when (val result = gateway.analyze(capturedImagePath)) {
            is FaceAnalysisResult.SingleFace -> when (val features = extractor.extract(result.scan)) {
                is FacialFeatureExtractionResult.Success -> KioskAnalysisOutcome.Success(
                    FaceScanResult(
                        faceShape = features.metrics.faceShape,
                        metrics = features.metrics,
                        confidencePercent = null,
                        isEstimate = true
                    ), result.scan
                )
                is FacialFeatureExtractionResult.Failure -> KioskAnalysisOutcome.Failure
            }
            FaceAnalysisResult.NoFaceDetected -> KioskAnalysisOutcome.NoFace
            FaceAnalysisResult.MultipleFacesDetected -> KioskAnalysisOutcome.MultipleFaces
            FaceAnalysisResult.AnalysisFailure -> KioskAnalysisOutcome.Failure
        }
}