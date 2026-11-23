package com.example.facefit.ui.camera

import org.junit.Assert.*
import org.junit.Test

class VtoPreviewBoundsTest {
    @Test fun acceptsFullSizeAndCenterCroppedCameraImages() {
        assertTrue(VtoPreviewBounds.coversView(0f, 0f, 1080f, 2400f, 1080, 2400))
        assertTrue(VtoPreviewBounds.coversView(-1593f, 0f, 2673f, 2400f, 1080, 2400))
        assertTrue(VtoPreviewBounds.coversView(-1f, 2f, 1080f, 2398f, 1080, 2400))
    }
    @Test fun rejectsBottomCornerSliverAndLetterboxedAnalysisBuffers() {
        assertFalse(VtoPreviewBounds.coversView(1060f, 2370f, 1700f, 2850f, 1080, 2400))
        assertFalse(VtoPreviewBounds.coversView(0f, 700f, 1080f, 1700f, 1080, 2400))
        assertFalse(VtoPreviewBounds.coversView(0f, 0f, 640f, 480f, 1080, 2400))
    }
    @Test fun rejectsInvalidOrUnmeasuredGeometry() {
        assertFalse(VtoPreviewBounds.coversView(Float.NaN, 0f, 1080f, 2400f, 1080, 2400))
        assertFalse(VtoPreviewBounds.coversView(0f, 0f, Float.POSITIVE_INFINITY, 2400f, 1080, 2400))
        assertFalse(VtoPreviewBounds.coversView(0f, 0f, 1080f, 2400f, 0, 2400))
        assertFalse(VtoPreviewBounds.coversView(-1e20f, 0f, 1e20f, 2400f, 1080, 2400))
    }
}
