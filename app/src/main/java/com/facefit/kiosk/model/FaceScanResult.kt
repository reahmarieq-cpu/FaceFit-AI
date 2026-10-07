package com.facefit.kiosk.model

data class FaceScanResult(
    val faceShape: String,
    val confidencePercent: Int,
    val notes: List<String>
)

