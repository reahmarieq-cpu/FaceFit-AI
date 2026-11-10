package com.facefit.kiosk.model

data class FacialFeatureMetrics(
    val faceLengthImageRatio: Double,
    val faceWidthToFaceLengthRatio: Double,
    val estimatedForeheadWidthToFaceLengthRatio: Double,
    val jawlineWidthToFaceLengthRatio: Double,
    val eyeSpacingToFaceLengthRatio: Double,
    val faceAspectRatio: Double,
    val foreheadToFaceWidthRatio: Double,
    val jawToFaceWidthRatio: Double,
    val eyeSpacingToFaceWidthRatio: Double,
    val foreheadToJawRatio: Double,
    val faceShape: FaceShape
)
