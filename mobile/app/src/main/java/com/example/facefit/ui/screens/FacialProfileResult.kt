package com.example.facefit.ui.screens

import com.example.facefit.logic.FaceAnalyzer
import com.example.facefit.logic.FaceShapeOnnxModel
import com.example.facefit.logic.FacialFeatureThresholds
import java.util.Locale

internal data class FacialFeatureResult(val ratio: Float?, val category: String? = null) {
    val percent: String? get() = ratio?.takeIf { it.isFinite() && it > 0f }?.let {
        String.format(Locale.getDefault(), "%.1f%%", it * 100)
    }
    val displayValue: String get() = category ?: percent ?: "Unavailable"
}

internal class FacialProfileResult(
    metrics: FaceAnalyzer.FaceMetrics,
    thresholds: FacialFeatureThresholds?
) {
    val upperFace = FacialFeatureResult(
        metrics.foreheadWidth, thresholds?.upperFace?.classify(metrics.foreheadWidth)
    )
    val jawline = FacialFeatureResult(
        metrics.jawline, thresholds?.jawline?.classify(metrics.jawline)
    )
    val eyeSpacing = FacialFeatureResult(
        metrics.eyeSpacing, thresholds?.eyeSpacing?.classify(metrics.eyeSpacing, eyeSpacing = true)
    )
    val faceLength = FacialFeatureResult(metrics.shapeMeasurements?.lengthToWidth)
    val hasCategories = listOf(upperFace, jawline, eyeSpacing).any { it.category != null }

    private val shape = metrics.faceShape.takeIf {
        it in FaceShapeOnnxModel.CLASSES || it == "Diamond" || it == "Triangle"
    }
    val headline = shape?.let { "Estimated face shape: $it" } ?: "Face shape uncertain"
    val explanation: String = buildString {
        if (shape != null) {
            append("Your scan suggests a ${shape.lowercase(Locale.ROOT)} face shape. ")
        } else {
            append("This scan did not give a clear, consistent face-shape estimate. ")
        }
        val descriptions = listOf(
            upperFace.describe("upper-face width"),
            jawline.describe("jawline width"),
            eyeSpacing.describe("inner-eye gap")
        ).filterNotNull()
        if (descriptions.isNotEmpty()) {
            append("Relative to your cheek width, your measurements were ")
            append(descriptions.joinToString(", "))
            append(".")
        }
    }.trim()

    private fun FacialFeatureResult.describe(name: String): String? {
        val value = percent ?: return null
        return "$name $value" + (category?.let { " (${it.lowercase(Locale.ROOT)})" } ?: "")
    }
}
