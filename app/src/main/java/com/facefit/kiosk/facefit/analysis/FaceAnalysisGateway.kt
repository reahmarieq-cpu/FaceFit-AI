package com.facefit.kiosk.facefit.analysis

import com.facefit.kiosk.model.FaceScanResult

interface FaceAnalysisGateway {
    fun analyze(capturedImageId: String): FaceAnalysisResult
}

sealed class FaceAnalysisResult {
    data class Success(val scanResult: FaceScanResult) : FaceAnalysisResult()
    data object NoFaceDetected : FaceAnalysisResult()
    data object MultipleFacesDetected : FaceAnalysisResult()
    data object Failed : FaceAnalysisResult()
}

