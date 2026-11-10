package com.example.facefit.facefit.recommendation

import com.example.facefit.facefit.analysis.FaceAnalyzer

/** Adapter for the existing results layout; values come from the measured profile. */
internal data class RecommendationResult(
    val jawlineLabel: String, val foreheadLabel: String, val eyeSpacingLabel: String,
    val recommendedFrame: String, val headline: String, val explanation: String
) {
    companion object {
        fun from(metrics: FaceAnalyzer.FaceMetrics, thresholds: FacialFeatureThresholds? = null): RecommendationResult {
            val profile = FacialProfileResult(metrics, thresholds)
            return RecommendationResult(profile.jawline.displayValue, profile.upperFace.displayValue,
                profile.eyeSpacing.displayValue, "Browse frames and compare styles in virtual try-on",
                profile.headline, profile.explanation)
        }
    }
}
