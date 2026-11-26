package com.example.facefit

import com.example.facefit.facefit.analysis.FaceScanGuide
import com.example.facefit.facefit.analysis.FacialFeatureMeasurement
import org.junit.Assert.assertEquals
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

    @Test fun outlineIsSizedForAnAverageFaceAt45Cm() {
        // Test phone: 487.5 px focal length on the 480x640 analysis image, shown 3.75x on a 1080x2400 screen.
        val halfWidth = FaceScanGuide.halfWidthForTargetDistance(487.5f, 3.75f)!!
        val cheekAt45 = 487.5f * FacialFeatureMeasurement.CANONICAL_CHEEK_WIDTH_CM / 45f * 3.75f
        assertEquals(cheekAt45 / 2f * FaceScanGuide.FIT_MARGIN, halfWidth, 0.01f)
        val outline = FaceScanGuide.points(1080f, 2400f, halfWidth)
        assertEquals(2 * halfWidth, outline.maxOf { it.x } - outline.minOf { it.x }, 1f)
        // At 45 cm the cheeks sit well inside the outline; at 37 cm they no longer fit.
        val y = outline.filter { kotlin.math.abs(it.x - (540f + halfWidth)) < 1f }.map { it.y }.average().toFloat()
        fun cheeks(distanceCm: Float): FloatArray {
            val half = 487.5f * FacialFeatureMeasurement.CANONICAL_CHEEK_WIDTH_CM / distanceCm * 3.75f / 2f
            return floatArrayOf(540f - half, y, 540f + half, y)
        }
        assertTrue(FaceScanGuide.containsFace(1080, 2400, cheeks(45f), halfWidth))
        assertTrue(FaceScanGuide.containsFace(1080, 2400, cheeks(44.5f), halfWidth))
        assertTrue(FaceScanGuide.containsFace(1080, 2400, cheeks(41f), halfWidth))
        assertFalse(FaceScanGuide.containsFace(1080, 2400, cheeks(37f), halfWidth))
    }

    @Test fun outlineNeverGrowsOverTheBottomControls() {
        val outline = FaceScanGuide.points(1080f, 2400f, halfWidth = 5000f)
        assertTrue(outline.maxOf { it.y } <= 2400f * .74f + 1f)
        assertTrue(outline.maxOf { it.x } - outline.minOf { it.x } <= 1080f * .9f + 1f)
        // Without camera geometry the original size is used.
        assertEquals(FaceScanGuide.points(360f, 760f), FaceScanGuide.points(360f, 760f, null))
    }
}
