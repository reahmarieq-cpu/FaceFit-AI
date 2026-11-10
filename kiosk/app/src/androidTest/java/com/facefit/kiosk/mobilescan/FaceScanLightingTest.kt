package com.facefit.kiosk.mobilescan

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.facefit.kiosk.facefit.mobilescan.FaceAnalyzer
import com.facefit.kiosk.facefit.mobilescan.FaceScanLighting
import org.junit.Assert.*
import org.junit.Test

class FaceScanLightingTest {
    @Test fun strictScanGraphCanInitializeAndRelease() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val analyzer = FaceAnalyzer(context, maxFaces = 2, scanQualityChecks = true) {}
        analyzer.close()
    }

    @Test fun faceSamplingDetectsDarkBrightAndUnevenExposure() {
        val points = MutableList(478) { FaceAnalyzer.LandmarkPoint(.5f, .5f) }
        points[234] = FaceAnalyzer.LandmarkPoint(.2f, .5f)
        points[454] = FaceAnalyzer.LandmarkPoint(.8f, .5f)
        points[10] = FaceAnalyzer.LandmarkPoint(.5f, .15f)
        points[152] = FaceAnalyzer.LandmarkPoint(.5f, .85f)
        for (mode in listOf("even", "dark", "bright", "uneven")) {
            val pixels = IntArray(100 * 100) { index ->
                when (mode) {
                    "dark" -> Color.BLACK
                    "bright" -> Color.WHITE
                    "uneven" -> if (index % 100 < 50) Color.rgb(30, 30, 30) else Color.rgb(180, 180, 180)
                    else -> Color.rgb(128, 128, 128)
                }
            }
            val bitmap = Bitmap.createBitmap(pixels, 100, 100, Bitmap.Config.ARGB_8888)
            try {
                val result = FaceScanLighting.measure(bitmap, points)!!
                if (mode == "even") {
                    assertNull(result.guidance())
                    assertEquals(128f / 255f, result.mean, .001f)
                    assertEquals(0f, result.sideDifference, .001f)
                } else assertNotNull(result.guidance())
            } finally {
                bitmap.recycle()
            }
        }
    }
}
