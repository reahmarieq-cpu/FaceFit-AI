package com.facefit.kiosk.facefit.features

import com.facefit.kiosk.model.FacialFeatureMetrics

sealed class FacialFeatureExtractionResult {
    data class Success(val metrics: FacialFeatureMetrics) : FacialFeatureExtractionResult()
    data class Failure(val reason: String) : FacialFeatureExtractionResult()
}
