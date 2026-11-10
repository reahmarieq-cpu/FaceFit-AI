package com.facefit.kiosk.mobilescan

import androidx.test.platform.app.InstrumentationRegistry
import com.facefit.kiosk.facefit.mobilescan.FaceShapeOnnxModel
import com.facefit.kiosk.facefit.mobilescan.FaceAnalyzer
import com.facefit.kiosk.facefit.mobilescan.ScanStabilityFeatures
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class FaceShapeOnnxModelTest {
    @Test fun landmarksMatchPythonGeometryAndOriginalForestScores() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val cases = instrumentation.context.assets.open("exp18b_geometry_reference.json")
            .bufferedReader().use { JSONArray(it.readText()) }
        FaceShapeOnnxModel.open(instrumentation.targetContext).use { model ->
            repeat(cases.length()) { index ->
                val reference = cases.getJSONObject(index)
                val coordinates = reference.getJSONArray("points")
                val points = List(coordinates.length()) { i ->
                    val pair = coordinates.getJSONArray(i)
                    FaceAnalyzer.LandmarkPoint(pair.getDouble(0).toFloat(), pair.getDouble(1).toFloat())
                }
                val features = ScanStabilityFeatures.extract(points,
                    reference.getInt("width"), reference.getInt("height"))!!
                val expected = reference.getJSONArray("features")
                features.forEachIndexed { i, value ->
                    assertEquals(expected.getDouble(i), value.toDouble(), .000002)
                }
                val prediction = model.predict(listOf(features)).single()
                assertEquals(reference.getString("label"), prediction.label)
                val scores = reference.getJSONArray("probabilities")
                FaceShapeOnnxModel.CLASSES.forEachIndexed { i, label ->
                    assertEquals(scores.getDouble(i), prediction.scores.getValue(label).toDouble(), .00002)
                }
            }
        }
    }

    @Test fun androidBatchMatchesExportedReferenceScoresAndLabels() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val cases = instrumentation.context.assets.open("exp18b_shape_reference.json")
            .bufferedReader().use { JSONArray(it.readText()) }
        val inputs = List(cases.length()) { index ->
            val row = cases.getJSONObject(index).getJSONArray("features")
            List(row.length()) { row.getDouble(it).toFloat() }
        }
        FaceShapeOnnxModel.open(instrumentation.targetContext).use { model ->
            val predictions = model.predict(inputs)
            assertEquals(cases.length(), predictions.size)
            predictions.forEachIndexed { i, prediction ->
                val expected = cases.getJSONObject(i)
                assertEquals(expected.getString("label"), prediction.label)
                val scores = expected.getJSONArray("probabilities")
                FaceShapeOnnxModel.CLASSES.forEachIndexed { j, label ->
                    assertEquals(scores.getDouble(j), prediction.scores.getValue(label).toDouble(), 0.00002)
                }
                // Reuse one session across different faces to catch stale input/output state.
                val single = model.predict(listOf(inputs[i])).single()
                assertEquals(prediction.label, single.label)
                FaceShapeOnnxModel.CLASSES.forEach { label ->
                    assertEquals(prediction.scores.getValue(label), single.scores.getValue(label), 0.00002f)
                }
                val assessment = model.assess(List(10) { inputs[i] }, inputs[i])
                assertEquals("highestScore", assessment.reason)
                assertEquals(prediction.label, assessment.label)
            }
        }
    }
}
