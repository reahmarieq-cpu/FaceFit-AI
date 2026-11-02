package com.facefit.kiosk.facefit.recommendation

import com.facefit.kiosk.model.FaceScanResult
import com.facefit.kiosk.model.FrameRecommendation

interface FrameRecommendationGateway {
    fun recommendationsFor(scanResult: FaceScanResult): RecommendationResult
}

sealed class RecommendationResult {
    data class Success(val recommendations: List<FrameRecommendation>) : RecommendationResult()
    data object NoRecommendationsAvailable : RecommendationResult()
    data object BackendUnavailable : RecommendationResult()
}

