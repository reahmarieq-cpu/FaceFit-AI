package com.facefit.kiosk.facefit.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaceGeometryTest {
    @Test
    fun distance2D_usesEuclideanDistance() {
        val distance = FaceGeometry.distance2D(
            GeometryPoint(0.0, 0.0),
            GeometryPoint(3.0, 4.0)
        )

        assertEquals(5.0, distance, 0.0001)
    }

    @Test
    fun midpoint_returnsCenterPoint() {
        val midpoint = FaceGeometry.midpoint(
            GeometryPoint(2.0, 4.0),
            GeometryPoint(6.0, 8.0)
        )

        assertEquals(4.0, midpoint.x, 0.0001)
        assertEquals(6.0, midpoint.y, 0.0001)
    }

    @Test
    fun average_returnsMeanPoint() {
        val average = FaceGeometry.average(
            listOf(
                GeometryPoint(2.0, 4.0),
                GeometryPoint(4.0, 8.0),
                GeometryPoint(6.0, 12.0)
            )
        )

        requireNotNull(average)
        assertEquals(4.0, average.x, 0.0001)
        assertEquals(8.0, average.y, 0.0001)
    }

    @Test
    fun ratio_rejectsZeroReference() {
        assertNull(FaceGeometry.ratio(10.0, 0.0))
    }

    @Test
    fun ratio_rejectsInvalidValues() {
        assertNull(FaceGeometry.ratio(Double.NaN, 10.0))
        assertNull(FaceGeometry.ratio(10.0, Double.POSITIVE_INFINITY))
    }
}
