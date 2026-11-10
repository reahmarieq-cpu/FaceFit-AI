package com.example.facefit

import androidx.test.platform.app.InstrumentationRegistry
import com.example.facefit.facefit.analysis.FaceShapeModel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FaceShapeModelTest {
    @Test
    fun batchedFrameConsensusAcceptsStrongCasesAndRejectsTiesAndDisagreement() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val metadata = context.assets.open("facefit_7classes_v2.json").bufferedReader().use {
            JSONObject(it.readText())
        }
        val cases = metadata.getJSONArray("smoke_tests")
        fun features(index: Int) = cases.getJSONObject(index).getJSONArray("features").let { array ->
            List(array.length()) { array.getDouble(it).toFloat() }
        }
        FaceShapeModel.open(context).use { model ->
            val batch = model.predictBatch(List(cases.length()) { features(it) })
            batch.forEachIndexed { i, p -> assertEquals(cases.getJSONObject(i).getString("label"), p.label) }
            val strong = features(1)
            val stable = model.assess(List(10) { strong }, strong)
            assertEquals("Oblong", stable.label)
            assertEquals("accepted", stable.reason)
            val tied = features(0)
            assertEquals("Uncertain", model.assess(List(10) { tied }, tied).label)
            val mixed = model.assess(List(7) { strong } + List(3) { features(2) }, strong)
            assertEquals("Uncertain", mixed.label)
            assertEquals("unstablePredictions", mixed.reason)
        }
    }

    @Test
    fun bundledModelMatchesOriginalPythonPredictionsAndClassOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val metadata = context.assets.open("facefit_7classes_v2.json").bufferedReader().use {
            JSONObject(it.readText())
        }
        val classes = metadata.getJSONObject("outputs").getJSONObject("probabilities")
            .getJSONArray("class_order").let { array -> List(array.length()) { array.getString(it) } }
        val cases = metadata.getJSONArray("smoke_tests")
        FaceShapeModel.open(context).use { model ->
            repeat(cases.length()) { index ->
                val case = cases.getJSONObject(index)
                val features = case.getJSONArray("features").let { array ->
                    List(array.length()) { array.getDouble(it).toFloat() }
                }
                val prediction = model.predict(features)
                assertEquals(case.getString("label"), prediction.label)
                classes.forEachIndexed { i, label ->
                    assertEquals(case.getJSONArray("probabilities").getDouble(i).toFloat(),
                        prediction.scores.getValue(label), 0.00002f)
                }
            }
            try {
                model.predict(listOf(Float.NaN))
                fail("Invalid model inputs must be rejected")
            } catch (_: IllegalArgumentException) { }
        }
    }
}
