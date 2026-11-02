package com.facefit.kiosk.facefit.features

import com.facefit.kiosk.facefit.geometry.FaceGeometry
import com.facefit.kiosk.facefit.geometry.GeometryPoint
import com.facefit.kiosk.model.FaceLandmarkPoint
import com.facefit.kiosk.model.FaceLandmarkScan
import com.facefit.kiosk.model.FaceShape
import com.facefit.kiosk.model.FacialFeatureMetrics
import kotlin.math.hypot

class FacialFeatureExtractor(
    private val faceShapeClassifier: FaceShapeClassifier = UndeterminedFaceShapeClassifier
) {
    fun extract(scan: FaceLandmarkScan): FacialFeatureExtractionResult {
        if (scan.imageWidth <= 0 || scan.imageHeight <= 0) {
            return FacialFeatureExtractionResult.Failure("Invalid image dimensions.")
        }

        val top = scan.pointAt(FaceLandmarkIndices.FACE_OVAL_TOP)
            ?: return FacialFeatureExtractionResult.Failure("Missing face oval top landmark.")
        val chin = scan.pointAt(FaceLandmarkIndices.CHIN)
            ?: return FacialFeatureExtractionResult.Failure("Missing chin landmark.")
        val faceWidthLeft = scan.pointAt(FaceLandmarkIndices.FACE_WIDTH_LEFT)
            ?: return FacialFeatureExtractionResult.Failure("Missing left face-width landmark.")
        val faceWidthRight = scan.pointAt(FaceLandmarkIndices.FACE_WIDTH_RIGHT)
            ?: return FacialFeatureExtractionResult.Failure("Missing right face-width landmark.")
        val upperFaceLeft = scan.pointAt(FaceLandmarkIndices.UPPER_FACE_LEFT)
            ?: return FacialFeatureExtractionResult.Failure("Missing left upper-face landmark.")
        val upperFaceRight = scan.pointAt(FaceLandmarkIndices.UPPER_FACE_RIGHT)
            ?: return FacialFeatureExtractionResult.Failure("Missing right upper-face landmark.")
        val jawlineLeft = scan.pointAt(FaceLandmarkIndices.JAWLINE_LEFT)
            ?: return FacialFeatureExtractionResult.Failure("Missing left jawline landmark.")
        val jawlineRight = scan.pointAt(FaceLandmarkIndices.JAWLINE_RIGHT)
            ?: return FacialFeatureExtractionResult.Failure("Missing right jawline landmark.")
        val leftIrisCenter = scan.averagePoint(FaceLandmarkIndices.LEFT_IRIS_CONTOUR)
            ?: return FacialFeatureExtractionResult.Failure("Missing left iris landmarks.")
        val rightIrisCenter = scan.averagePoint(FaceLandmarkIndices.RIGHT_IRIS_CONTOUR)
            ?: return FacialFeatureExtractionResult.Failure("Missing right iris landmarks.")

        val imageDiagonal = hypot(scan.imageWidth.toDouble(), scan.imageHeight.toDouble())
        val faceLength = FaceGeometry.distance2D(top, chin)
        val faceWidth = FaceGeometry.distance2D(faceWidthLeft, faceWidthRight)
        val foreheadWidth = FaceGeometry.distance2D(upperFaceLeft, upperFaceRight)
        val jawlineWidth = FaceGeometry.distance2D(jawlineLeft, jawlineRight)
        val eyeSpacing = FaceGeometry.distance2D(leftIrisCenter, rightIrisCenter)

        val metricsWithoutShape = FacialFeatureMetrics(
            faceLengthImageRatio = FaceGeometry.ratio(faceLength, imageDiagonal)
                ?: return FacialFeatureExtractionResult.Failure("Invalid face length reference."),
            faceWidthToFaceLengthRatio = FaceGeometry.ratio(faceWidth, faceLength)
                ?: return FacialFeatureExtractionResult.Failure("Invalid face width ratio."),
            estimatedForeheadWidthToFaceLengthRatio = FaceGeometry.ratio(foreheadWidth, faceLength)
                ?: return FacialFeatureExtractionResult.Failure("Invalid forehead width ratio."),
            jawlineWidthToFaceLengthRatio = FaceGeometry.ratio(jawlineWidth, faceLength)
                ?: return FacialFeatureExtractionResult.Failure("Invalid jawline width ratio."),
            eyeSpacingToFaceLengthRatio = FaceGeometry.ratio(eyeSpacing, faceLength)
                ?: return FacialFeatureExtractionResult.Failure("Invalid eye spacing ratio."),
            faceAspectRatio = FaceGeometry.ratio(faceLength, faceWidth)
                ?: return FacialFeatureExtractionResult.Failure("Invalid face aspect ratio."),
            foreheadToFaceWidthRatio = FaceGeometry.ratio(foreheadWidth, faceWidth)
                ?: return FacialFeatureExtractionResult.Failure("Invalid forehead-to-face width ratio."),
            jawToFaceWidthRatio = FaceGeometry.ratio(jawlineWidth, faceWidth)
                ?: return FacialFeatureExtractionResult.Failure("Invalid jaw-to-face width ratio."),
            eyeSpacingToFaceWidthRatio = FaceGeometry.ratio(eyeSpacing, faceWidth)
                ?: return FacialFeatureExtractionResult.Failure("Invalid eye-spacing-to-face width ratio."),
            foreheadToJawRatio = FaceGeometry.ratio(foreheadWidth, jawlineWidth)
                ?: return FacialFeatureExtractionResult.Failure("Invalid forehead-to-jaw ratio."),
            faceShape = FaceShape.Undetermined
        )

        val faceShape = faceShapeClassifier.classify(metricsWithoutShape)
        if (!metricsWithoutShape.hasValidValues()) {
            return FacialFeatureExtractionResult.Failure("Feature metrics contain invalid values.")
        }

        return FacialFeatureExtractionResult.Success(
            metricsWithoutShape.copy(faceShape = faceShape)
        )
    }

    private fun FaceLandmarkScan.pointAt(index: Int): GeometryPoint? {
        return landmarks.firstOrNull { it.index == index }?.toImagePoint(imageWidth, imageHeight)
    }

    private fun FaceLandmarkScan.averagePoint(indices: List<Int>): GeometryPoint? {
        val points = indices.map { index -> pointAt(index) ?: return null }
        return FaceGeometry.average(points)
    }

    private fun FaceLandmarkPoint.toImagePoint(imageWidth: Int, imageHeight: Int): GeometryPoint {
        return GeometryPoint(
            x = x.toDouble() * imageWidth,
            y = y.toDouble() * imageHeight
        )
    }

    private fun FacialFeatureMetrics.hasValidValues(): Boolean {
        return listOf(
            faceLengthImageRatio,
            faceWidthToFaceLengthRatio,
            estimatedForeheadWidthToFaceLengthRatio,
            jawlineWidthToFaceLengthRatio,
            eyeSpacingToFaceLengthRatio,
            faceAspectRatio,
            foreheadToFaceWidthRatio,
            jawToFaceWidthRatio,
            eyeSpacingToFaceWidthRatio,
            foreheadToJawRatio
        ).all { it.isFinite() && it > 0.0 }
    }
}
