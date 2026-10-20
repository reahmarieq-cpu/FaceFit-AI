package com.facefit.kiosk.facefit.features

import com.facefit.kiosk.model.FaceShape
import com.facefit.kiosk.model.FacialFeatureMetrics

interface FaceShapeClassifier {
    fun classify(metricsWithoutShape: FacialFeatureMetrics): FaceShape
}

object UndeterminedFaceShapeClassifier : FaceShapeClassifier {
    override fun classify(metricsWithoutShape: FacialFeatureMetrics): FaceShape {
        return FaceShape.Undetermined
    }
}
