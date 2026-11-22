package com.example.facefit.facefit.recommendation

import com.example.facefit.facefit.analysis.FaceAnalyzer
import com.example.facefit.facefit.analysis.FaceShapeImageModel
import com.example.facefit.facefit.recommendation.FacialFeatureThresholds
import java.util.Locale

internal data class FacialFeatureResult(
    val category: String?,
    val isBorderline: Boolean = false,
    val ratio: Float = 0f,
    /** Uncertainty margin used for [isBorderline]; diagnostics only. */
    val margin: Float = Float.NaN,
) {
    /** Always one of the three categories for the feature (or "Unavailable"). */
    val displayValue: String get() = category ?: "Unavailable"
}

internal class FacialProfileResult(
    metrics: FaceAnalyzer.FaceMetrics,
    thresholds: FacialFeatureThresholds?,
    rules: FacialExplanationRules? = null
) {
    private val standardError = metrics.featureAggregate?.standardError

    val upperFace = feature(thresholds?.upperFace, metrics.foreheadWidth, standardError?.upperFace)
    val jawline = feature(thresholds?.jawline, metrics.jawline, standardError?.jawline)
    val eyeSpacing = feature(thresholds?.eyeSpacing, metrics.eyeSpacing, standardError?.eyeSpacing,
        eyeSpacing = true)

    private val shape = metrics.faceShape.takeIf {
        it in FaceShapeImageModel.CLASSES || it == "Diamond" || it == "Triangle"
    }
    private val wording = rules?.find(shape, jawline.category, upperFace.category, eyeSpacing.category)
    val suggestedStyle = wording?.suggestedStyle
    val headline = wording?.headline ?: shape?.let { "Estimated face shape: $it" } ?: "Face shape uncertain"
    val explanation: String = wording?.explanation ?: buildString {
        if (shape != null) {
            append("Your scan suggests a ${shape.lowercase(Locale.ROOT)} face shape. ")
        } else {
            append("This scan did not give a clear, consistent face-shape estimate. ")
        }
        val descriptions = listOf(
            upperFace.describe("upper-face width"),
            jawline.describe("jawline width"),
            eyeSpacing.describe("eye spacing")
        ).filterNotNull()
        append(descriptions.joinToString(" "))
    }.trim()

    private fun FacialFeatureResult.describe(name: String): String? =
        category?.let { "Your $name is ${it.lowercase(Locale.ROOT)}." }

    private companion object {
        fun feature(
            band: FacialFeatureThresholds.Band?, ratio: Float, standardError: Float?,
            eyeSpacing: Boolean = false
        ): FacialFeatureResult {
            if (band == null) return FacialFeatureResult(null, ratio = ratio)
            // Scans without a multi-frame aggregate (e.g. saved before this change) keep the old rule.
            if (standardError == null) {
                return FacialFeatureResult(band.classify(ratio, eyeSpacing), band.isBorderline(ratio), ratio)
            }
            val margin = band.uncertaintyMargin(standardError)
            val assessment = band.assess(ratio, margin, eyeSpacing)
                ?: return FacialFeatureResult(null, ratio = ratio)
            return FacialFeatureResult(
                category = assessment.category,
                isBorderline = assessment.uncertain,
                ratio = ratio,
                margin = margin,
            )
        }
    }
}
