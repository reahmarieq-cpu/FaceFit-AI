package com.facefit.kiosk.facefit.analysis

import com.facefit.kiosk.model.FaceLandmarkScan

interface FaceAnalysisGateway {
    fun analyze(capturedImagePath: String): FaceAnalysisResult
}

sealed class FaceAnalysisResult {
    data class SingleFace(val scan: FaceLandmarkScan) : FaceAnalysisResult()
    data object NoFaceDetected : FaceAnalysisResult()
    data object MultipleFacesDetected : FaceAnalysisResult()
    data object AnalysisFailure : FaceAnalysisResult()
}
