package com.example.facefit

import com.example.facefit.facefit.analysis.FaceAnalyzer.LandmarkPoint
import com.example.facefit.facefit.analysis.FaceShapeFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaceShapeFeaturesTest {
    @Test fun normalizedLandmarksMatchPythonInputOrderAndSignedAngle() {
        val points = MutableList(478) { LandmarkPoint(0f, 0f) }
        points[10] = LandmarkPoint(.51f, .20f, -.11f)
        points[152] = LandmarkPoint(.49f, .83f, -.04f)
        points[234] = LandmarkPoint(.19f, .56f, .22f)
        points[454] = LandmarkPoint(.80f, .51f, .17f)
        points[21] = LandmarkPoint(.23f, .35f, .17f)
        points[251] = LandmarkPoint(.77f, .33f, .14f)
        points[172] = LandmarkPoint(.25f, .70f, .13f)
        points[397] = LandmarkPoint(.74f, .67f, .18f)

        // Reference extractor run on the same float32 landmark coordinates.
        val expected = listOf(.968294037091, .911779500868, .803565372785,
            .881315462807, .389447837022, -.061148103292)
        val actual = FaceShapeFeatures.extract(points)!!
        assertEquals(expected.size, actual.size)
        expected.forEachIndexed { index, value -> assertEquals(value, actual[index], 1e-10) }

        points[397] = points[397].copy(z = Float.NaN)
        assertNull(FaceShapeFeatures.extract(points))
    }

    @Test fun missingOrDegenerateLandmarksAreRejected() {
        assertNull(FaceShapeFeatures.extract(emptyList()))
        assertNull(FaceShapeFeatures.extract(List(478) { LandmarkPoint(.5f, .5f) }))
    }
}
