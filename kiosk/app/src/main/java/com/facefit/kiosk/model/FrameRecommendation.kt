package com.facefit.kiosk.model

data class FrameRecommendation(
    val frame: FrameItem,
    val compatibilityPercent: Int? = null,
    val explanation: List<String> = emptyList(),
    val faceShapeScore: Int? = null,
    val proportionFitScore: Int? = null,
    val styleColorScore: Int? = null
) {
    init {
        require(listOf(compatibilityPercent, faceShapeScore, proportionFitScore, styleColorScore)
            .all { it == null || it in 0..100 })
    }
}