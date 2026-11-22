package com.example.facefit

import com.example.facefit.facefit.analysis.FaceScanGuide
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceScanGuideTest {
    @Test fun rejectsChinBelowVisibleGuideEvenWhenInsideCameraPreview() {
        val width = 360
        val height = 760
        val outline = FaceScanGuide.points(width.toFloat(), height.toFloat())
        val chin = outline.maxOf { it.y }
        assertTrue(FaceScanGuide.containsFace(width, height, floatArrayOf(180f, chin - 6f)))
        assertTrue(chin + 12f < height * .88f)
        assertFalse(FaceScanGuide.containsFace(width, height, floatArrayOf(180f, chin + 12f)))
    }

    @Test fun rejectsPointsInTheCornersOfTheGuidesBoundingRectangle() {
        val outline = FaceScanGuide.points(360f, 760f)
        val x = outline.minOf { it.x } + 3f
        val y = outline.minOf { it.y } + 3f
        assertFalse(FaceScanGuide.containsFace(360, 760, floatArrayOf(x, y)))
        assertFalse(FaceScanGuide.containsFace(360, 760, floatArrayOf(360f - x, y)))
    }
}
