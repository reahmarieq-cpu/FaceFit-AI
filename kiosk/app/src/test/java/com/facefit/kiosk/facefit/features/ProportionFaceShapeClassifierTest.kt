package com.facefit.kiosk.facefit.features

import com.facefit.kiosk.model.FaceShape
import com.facefit.kiosk.model.FacialFeatureMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

class ProportionFaceShapeClassifierTest {
    @Test fun representativeProportionsCoverAllSupportedIllustrations() {
        val samples = listOf(
            metrics(1.45, 0.90, 0.80) to FaceShape.Oval,
            metrics(1.25, 0.92, 0.80) to FaceShape.Round,
            metrics(1.25, 0.92, 0.90) to FaceShape.Square,
            metrics(1.45, 0.98, 0.70) to FaceShape.Heart,
            metrics(1.70, 0.92, 0.80) to FaceShape.Oblong
        )
        samples.forEach { (input, expected) ->
            assertEquals(expected, ProportionFaceShapeClassifier.classify(input))
        }
    }

    @Test fun invalidAndExtremeMeasurementsUseNeutralFallback() {
        val samples = listOf(
            metrics(Double.NaN, 0.9, 0.8), metrics(1.4, Double.POSITIVE_INFINITY, 0.8),
            metrics(1.4, 0.9, 0.0), metrics(8.0, 0.9, 0.8), metrics(1.4, 0.9, Double.NaN)
        )
        samples.forEach { assertEquals(FaceShape.Undetermined, ProportionFaceShapeClassifier.classify(it)) }
    }

    @Test fun newScanDoesNotReusePreviousShape() {
        val round = metrics(1.25, 0.92, 0.8)
        val oblong = metrics(1.7, 0.92, 0.8)
        assertEquals(FaceShape.Round, ProportionFaceShapeClassifier.classify(round))
        assertEquals(FaceShape.Oblong, ProportionFaceShapeClassifier.classify(oblong))
        assertEquals(FaceShape.Round, ProportionFaceShapeClassifier.classify(round))
    }

    private fun metrics(aspect: Double, forehead: Double, jaw: Double) = FacialFeatureMetrics(
        faceLengthImageRatio = 0.5,
        faceWidthToFaceLengthRatio = 1.0 / aspect,
        estimatedForeheadWidthToFaceLengthRatio = forehead / aspect,
        jawlineWidthToFaceLengthRatio = jaw / aspect,
        eyeSpacingToFaceLengthRatio = 0.45 / aspect,
        faceAspectRatio = aspect,
        foreheadToFaceWidthRatio = forehead,
        jawToFaceWidthRatio = jaw,
        eyeSpacingToFaceWidthRatio = 0.45,
        foreheadToJawRatio = forehead / jaw,
        faceShape = FaceShape.Undetermined
    )
}