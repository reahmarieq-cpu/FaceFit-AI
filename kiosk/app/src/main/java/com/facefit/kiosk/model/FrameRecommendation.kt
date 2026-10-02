package com.facefit.kiosk.model

data class FrameRecommendation(
    val frame: FrameItem,
    val compatibilityPercent: Int,
    val explanation: List<String>
)

