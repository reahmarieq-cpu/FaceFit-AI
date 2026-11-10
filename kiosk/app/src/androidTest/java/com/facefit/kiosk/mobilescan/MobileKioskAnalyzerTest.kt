package com.facefit.kiosk.mobilescan

import androidx.test.platform.app.InstrumentationRegistry
import com.facefit.kiosk.data.session.InMemoryKioskSessionStore
import com.facefit.kiosk.facefit.analysis.MobileKioskAnalyzer
import com.facefit.kiosk.facefit.analysis.KioskAnalysisOutcome
import com.facefit.kiosk.facefit.mobilescan.FaceAnalyzer
import com.facefit.kiosk.facefit.mobilescan.FaceShapeOnnxModel
import com.facefit.kiosk.model.FaceShape
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class MobileKioskAnalyzerTest {
    @Test fun collectedBatchMapsMobilePredictionAndScoresIntoKioskResult() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val cases = instrumentation.context.assets.open("exp18b_geometry_reference.json")
            .bufferedReader().use { JSONArray(it.readText()) }
        val session = InMemoryKioskSessionStore()
        val adapter = MobileKioskAnalyzer(instrumentation.targetContext, session)
        repeat(cases.length()) { index ->
            val row = cases.getJSONObject(index)
            val points = row.getJSONArray("points")
            val features = row.getJSONArray("features").let { values ->
                List(values.length()) { values.getDouble(it).toFloat() }
            }
            val source = "/scan-reference-$index.jpg"
            session.capturedFaceImagePath = source
            session.capturedScanMetrics = FaceAnalyzer.FaceMetrics(.8f, .8f, .2f, "",
                landmarks = List(points.length()) { i ->
                    val point = points.getJSONArray(i)
                    FaceAnalyzer.LandmarkPoint(point.getDouble(0).toFloat(), point.getDouble(1).toFloat())
                }, imageWidth = row.getInt("width"), imageHeight = row.getInt("height"), isFaceDetected = true,
                stabilityFeatures = features, shapeSampleCount = 15, shapeFeatureSamples = List(15) { features })
            val success = adapter.analyze(source) as KioskAnalysisOutcome.Success
            assertEquals(FaceShape.fromLabel(row.getString("label")), success.profile.faceShape)
            assertEquals(FaceShapeOnnxModel.MODEL_ID, success.profile.modelId)
            assertEquals(FaceShapeOnnxModel.POLICY_ID, success.profile.modelDecisionPolicy)
            assertNull(success.profile.confidencePercent)
            assertEquals(source, success.landmarks?.sourceImagePath)
            val probabilities = row.getJSONArray("probabilities")
            FaceShapeOnnxModel.CLASSES.forEachIndexed { i, label ->
                assertEquals(probabilities.getDouble(i), success.profile.modelScores.getValue(label).toDouble(), .00002)
            }
        }
        session.clearCustomerSession()
        assertNull(session.capturedScanMetrics)
        assertNull(session.analysisResult)
    }

    @Test fun photoWithoutValidMultiFrameBatchCannotUseOldHeuristic() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = InMemoryKioskSessionStore()
        session.capturedFaceImagePath = "/old-photo.jpg"
        assertEquals(KioskAnalysisOutcome.Failure, MobileKioskAnalyzer(context, session).analyze("/old-photo.jpg"))
    }
}
