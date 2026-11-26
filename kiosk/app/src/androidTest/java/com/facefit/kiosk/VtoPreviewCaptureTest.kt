package com.facefit.kiosk

import android.graphics.Matrix
import com.facefit.kiosk.vto.facefit.analysis.FaceAnalyzer
import com.facefit.kiosk.vto.ui.camera.VtoCameraFrame
import org.junit.Assert.assertEquals
import org.junit.Test

class VtoPreviewCaptureTest {
    @Test fun analysisCoordinatesFollowPreviewRotationCropAndMirror() {
        val face = FaceAnalyzer.FaceMetrics(0f, 0f, 0f, "",
            listOf(FaceAnalyzer.LandmarkPoint(.25f, .75f, -.1f)), 640, 480, true)
        val transform = Matrix().apply {
            setValues(floatArrayOf(0f, 2f, -240f, 2f, 0f, 40f, 0f, 0f, 1f))
        }
        val mapped = VtoCameraFrame.map(face, transform, 600, 1200).landmarks.single()
        assertEquals(.8f, mapped.x, .001f)
        assertEquals(.3f, mapped.y, .001f)
        assertEquals(-128f / 600f, mapped.z, .001f)
    }
}
