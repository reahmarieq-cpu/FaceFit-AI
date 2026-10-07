package com.facefit.kiosk.facefit.features

import com.facefit.kiosk.model.FaceLandmarkPoint
import com.facefit.kiosk.model.FaceLandmarkScan
import com.facefit.kiosk.model.FaceShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FacialFeatureExtractorTest {
    private val extractor = FacialFeatureExtractor()

    @Test
    fun extract_returnsExpectedRatiosForValidSyntheticScan() {
        val result = extractor.extract(validScan())

        assertTrue(result is FacialFeatureExtractionResult.Success)
        val metrics = (result as FacialFeatureExtractionResult.Success).metrics
        assertEquals(0.5657, metrics.faceLengthImageRatio, 0.0001)
        assertEquals(1.0, metrics.faceWidthToFaceLengthRatio, 0.0001)
        assertEquals(0.625, metrics.estimatedForeheadWidthToFaceLengthRatio, 0.0001)
        assertEquals(0.5, metrics.jawlineWidthToFaceLengthRatio, 0.0001)
        assertEquals(0.25, metrics.eyeSpacingToFaceLengthRatio, 0.0001)
        assertEquals(1.0, metrics.faceAspectRatio, 0.0001)
        assertEquals(0.625, metrics.foreheadToFaceWidthRatio, 0.0001)
        assertEquals(0.5, metrics.jawToFaceWidthRatio, 0.0001)
        assertEquals(0.25, metrics.eyeSpacingToFaceWidthRatio, 0.0001)
        assertEquals(1.25, metrics.foreheadToJawRatio, 0.0001)
        assertEquals(FaceShape.Undetermined, metrics.faceShape)
    }

    @Test
    fun extract_isRepeatableForSameInput() {
        val first = extractor.extract(validScan())
        val second = extractor.extract(validScan())

        assertEquals(first, second)
    }

    @Test
    fun extract_returnsFailureWhenRequiredLandmarkIsMissing() {
        val incompleteScan = validScan().copy(
            landmarks = validScan().landmarks.filterNot {
                it.index == FaceLandmarkIndices.FACE_OVAL_TOP
            }
        )

        val result = extractor.extract(incompleteScan)

        assertTrue(result is FacialFeatureExtractionResult.Failure)
    }

    @Test
    fun extract_returnsFailureWhenReferenceDistanceIsZero() {
        val zeroLengthScan = validScan().replaceLandmark(
            FaceLandmarkIndices.CHIN,
            x = 0.5f,
            y = 0.1f
        )

        val result = extractor.extract(zeroLengthScan)

        assertTrue(result is FacialFeatureExtractionResult.Failure)
    }

    @Test
    fun irisCenter_isDerivedFromAllIrisContourPoints() {
        val asymmetricIrisScan = validScan()
            .replaceLandmark(474, x = 0.58f, y = 0.45f)
            .replaceLandmark(475, x = 0.62f, y = 0.45f)
            .replaceLandmark(476, x = 0.60f, y = 0.43f)
            .replaceLandmark(477, x = 0.60f, y = 0.47f)

        val result = extractor.extract(asymmetricIrisScan)

        assertTrue(result is FacialFeatureExtractionResult.Success)
        val metrics = (result as FacialFeatureExtractionResult.Success).metrics
        assertEquals(0.25, metrics.eyeSpacingToFaceWidthRatio, 0.0001)
    }

    private fun validScan(): FaceLandmarkScan {
        return FaceLandmarkScan(
            landmarks = listOf(
                landmark(FaceLandmarkIndices.FACE_OVAL_TOP, 0.5f, 0.1f),
                landmark(FaceLandmarkIndices.CHIN, 0.5f, 0.9f),
                landmark(FaceLandmarkIndices.FACE_WIDTH_LEFT, 0.1f, 0.5f),
                landmark(FaceLandmarkIndices.FACE_WIDTH_RIGHT, 0.9f, 0.5f),
                landmark(FaceLandmarkIndices.UPPER_FACE_LEFT, 0.25f, 0.25f),
                landmark(FaceLandmarkIndices.UPPER_FACE_RIGHT, 0.75f, 0.25f),
                landmark(FaceLandmarkIndices.JAWLINE_LEFT, 0.3f, 0.75f),
                landmark(FaceLandmarkIndices.JAWLINE_RIGHT, 0.7f, 0.75f),
                landmark(469, 0.38f, 0.45f),
                landmark(470, 0.42f, 0.45f),
                landmark(471, 0.4f, 0.43f),
                landmark(472, 0.4f, 0.47f),
                landmark(474, 0.58f, 0.45f),
                landmark(475, 0.62f, 0.45f),
                landmark(476, 0.6f, 0.43f),
                landmark(477, 0.6f, 0.47f)
            ),
            imageWidth = 1000,
            imageHeight = 1000,
            sourceImagePath = "synthetic.jpg"
        )
    }

    private fun FaceLandmarkScan.replaceLandmark(index: Int, x: Float, y: Float): FaceLandmarkScan {
        return copy(
            landmarks = landmarks.map {
                if (it.index == index) landmark(index, x, y) else it
            }
        )
    }

    private fun landmark(index: Int, x: Float, y: Float): FaceLandmarkPoint {
        return FaceLandmarkPoint(
            index = index,
            x = x,
            y = y,
            z = 0f
        )
    }
}
