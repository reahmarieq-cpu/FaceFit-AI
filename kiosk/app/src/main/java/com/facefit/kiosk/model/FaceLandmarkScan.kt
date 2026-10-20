package com.facefit.kiosk.model

data class FaceLandmarkScan(
    val landmarks: List<FaceLandmarkPoint>,
    val imageWidth: Int,
    val imageHeight: Int,
    val sourceImagePath: String
)
