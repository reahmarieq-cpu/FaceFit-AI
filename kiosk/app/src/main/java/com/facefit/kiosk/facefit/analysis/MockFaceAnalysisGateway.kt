package com.facefit.kiosk.facefit.analysis

class MockFaceAnalysisGateway(
    private val result: FaceAnalysisResult = FaceAnalysisResult.AnalysisFailure
) : FaceAnalysisGateway {

    override fun analyze(capturedImagePath: String): FaceAnalysisResult = result
}
