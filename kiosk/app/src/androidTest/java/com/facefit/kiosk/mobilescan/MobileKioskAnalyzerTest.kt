package com.facefit.kiosk.mobilescan

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.facefit.kiosk.data.session.InMemoryKioskSessionStore
import com.facefit.kiosk.facefit.analysis.KioskAnalysisOutcome
import com.facefit.kiosk.facefit.analysis.MobileKioskAnalyzer
import com.facefit.kiosk.facefit.mobilescan.FaceAnalyzer
import com.facefit.kiosk.facefit.mobilescan.FaceScanSamples
import com.facefit.kiosk.facefit.mobilescan.FaceShapeDecision
import com.facefit.kiosk.facefit.mobilescan.FaceShapeImageInput
import com.facefit.kiosk.facefit.mobilescan.FaceShapeImageModel
import com.facefit.kiosk.facefit.mobilescan.FacialGeometry
import com.facefit.kiosk.facefit.recommendation.FacialExplanationRules
import com.facefit.kiosk.facefit.recommendation.FrameCompatibility
import com.facefit.kiosk.model.FaceShape
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class MobileKioskAnalyzerTest {
    @Test fun stableBatchUsesMobileImagePredictionAndCalibratedScores() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testAssets = instrumentation.context.assets
        val bitmap = testAssets.open("max_classifier/oval_00.png").use {
            requireNotNull(BitmapFactory.decodeStream(it))
        }
        val rgb = try {
            FaceShapeImageInput(context).prepare(bitmap)
        } finally {
            bitmap.recycle()
        }
        val cases = testAssets.open("exp18b_geometry_reference.json").bufferedReader().use { JSONArray(it.readText()) }
        val row = cases.getJSONObject(0)
        val points = row.getJSONArray("points")
        val landmarks = List(points.length()) { i ->
            val point = points.getJSONArray(i)
            FaceAnalyzer.LandmarkPoint(point.getDouble(0).toFloat(), point.getDouble(1).toFloat())
        }
        val width = row.getInt("width")
        val height = row.getInt("height")
        val geometry = requireNotNull(FacialGeometry.measure(landmarks, width, height))
        val expected = FaceShapeImageModel.open(context).use {
            FaceShapeDecision.calibrate(it.predict(rgb), geometry)
        }

        val source = "/scan-reference.jpg"
        val session = InMemoryKioskSessionStore()
        session.capturedFaceImagePath = source
        session.capturedScanMetrics = FaceAnalyzer.FaceMetrics(
            .8f, .8f, .2f, "",
            landmarks = landmarks,
            imageWidth = width,
            imageHeight = height,
            isFaceDetected = true,
            shapeMeasurements = geometry,
            shapeSampleCount = FaceScanSamples.MIN_SAMPLES,
            shapeImageRgb = rgb
        )
        val success = MobileKioskAnalyzer(context, session).analyze(source) as KioskAnalysisOutcome.Success
        assertEquals(FaceShape.fromLabel(expected.label), success.profile.faceShape)
        assertEquals(FaceShapeImageModel.MODEL_ID, success.profile.modelId)
        assertEquals("geometricAspectCalibratedV1", success.profile.modelDecisionPolicy)
        assertEquals(FaceShapeImageModel.CLASSES.toSet(), success.profile.modelScores.keys)
        expected.scores.forEach { (label, score) ->
            assertEquals(score, success.profile.modelScores.getValue(label), 0.0001f)
        }
        assertNull(success.profile.confidencePercent)
        assertEquals(source, success.landmarks?.sourceImagePath)
        val explanation = requireNotNull(
            FacialExplanationRules.load(context)?.find(expected.label, "Medium", "Wide", "Close-set")
        )
        assertEquals(explanation.headline, success.profile.profileHeadline)
        assertEquals(listOf(explanation.noticed), success.profile.notes)
        assertEquals(explanation.styles, success.profile.profileStyleExplanation)
        assertEquals(explanation.suggestedStyle, success.profile.suggestedFrameStyle)
        session.clearCustomerSession()
        assertNull(session.capturedScanMetrics)
    }

    @Test fun bundledAssessorDataMatchesMobileRatingForOvalAndRoundFrames() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val compatibility = FrameCompatibility.load(context)
        assertNull(compatibility.loadError)
        val rating = requireNotNull(compatibility.lookup("Oval", "Round"))
        assertEquals(58.3581, rating.score, 0.0001)
        assertNull(compatibility.lookup("Undetermined", "Round"))
    }

    @Test fun photoWithoutValidMultiFrameBatchCannotUseOldHeuristic() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = InMemoryKioskSessionStore()
        session.capturedFaceImagePath = "/old-photo.jpg"
        assertEquals(KioskAnalysisOutcome.Failure, MobileKioskAnalyzer(context, session).analyze("/old-photo.jpg"))
    }
}
