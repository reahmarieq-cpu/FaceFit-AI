package com.example.facefit.facefit.recommendation

import com.example.facefit.facefit.analysis.FaceAnalyzer

/** Adapter for the existing results layout; values come from the measured profile. */
internal data class RecommendationResult(
    val jawlineLabel: String,
    val foreheadLabel: String,
    val eyeSpacingLabel: String,
    val suggestedStyle: String?,
    val headline: String,
    val explanation: String,
    val jawlineBorderline: Boolean = false,
    val foreheadBorderline: Boolean = false,
    val eyeSpacingBorderline: Boolean = false
) {
    companion object {
        fun from(metrics: FaceAnalyzer.FaceMetrics, thresholds: FacialFeatureThresholds? = null,
            rules: FacialExplanationRules? = null
        ): RecommendationResult {
            val profile = FacialProfileResult(metrics, thresholds, rules)
            return RecommendationResult(
                profile.jawline.displayValue,
                profile.upperFace.displayValue,
                profile.eyeSpacing.displayValue,
                profile.suggestedStyle,
                profile.headline,
                profile.explanation,
                profile.jawline.isBorderline,
                profile.upperFace.isBorderline,
                profile.eyeSpacing.isBorderline
            )
        }
    }
}
