package com.facefit.kiosk.model

data class FaceScanResult(
    val faceShape: FaceShape,
    val confidencePercent: Int? = null,
    val notes: List<String> = emptyList(),
    val metrics: FacialFeatureMetrics? = null,
    val isEstimate: Boolean = true,
    val analysisId: String? = null,
    val modelId: String? = null,
    val modelScores: Map<String, Float> = emptyMap(),
    val modelDecisionPolicy: String? = null,
    val modelAgreement: Float? = null
) {
    init {
        require(confidencePercent == null || confidencePercent in 0..100)
    }
}