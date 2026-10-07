package com.facefit.kiosk.facefit.analysis

import com.facefit.kiosk.model.FaceLandmarkPoint
import com.facefit.kiosk.model.FaceLandmarkScan

object FaceAnalysisResultMapper {
    fun fromLandmarks(
        detectedFaces: List<List<FaceLandmarkPoint>>,
        imageWidth: Int,
        imageHeight: Int,
        sourceImagePath: String
    ): FaceAnalysisResult {
        return when {
            detectedFaces.isEmpty() -> FaceAnalysisResult.NoFaceDetected
            detectedFaces.size > 1 -> FaceAnalysisResult.MultipleFacesDetected
            detectedFaces.first().isEmpty() -> FaceAnalysisResult.AnalysisFailure
            else -> FaceAnalysisResult.SingleFace(
                FaceLandmarkScan(
                    landmarks = detectedFaces.first(),
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    sourceImagePath = sourceImagePath
                )
            )
        }
    }
}
