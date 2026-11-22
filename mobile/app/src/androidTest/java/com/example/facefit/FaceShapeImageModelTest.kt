package com.example.facefit

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.facefit.facefit.analysis.FaceShapeImageModel
import com.example.facefit.facefit.analysis.FaceShapeImageInput
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class FaceShapeImageModelTest {
    @Test fun convertedModelMatchesKerasReferenceImages() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = instrumentation.context.assets.open("max_classifier/reference.json")
            .bufferedReader().use { JSONObject(it.readText()) }
        val cases = root.getJSONArray("cases")
        FaceShapeImageModel.open(instrumentation.targetContext).use { model ->
            repeat(cases.length()) { index ->
                val sample = cases.getJSONObject(index)
                val bitmap = instrumentation.context.assets.open("max_classifier/" + sample.getString("image"))
                    .use { BitmapFactory.decodeStream(it) }!!
                if (index == 0) {
                    val cropped = FaceShapeImageInput(instrumentation.targetContext).prepare(bitmap)
                    assertEquals(256 * 256 * 3, cropped.size)
                }
                val pixels = IntArray(256 * 256)
                bitmap.getPixels(pixels, 0, 256, 0, 0, 256, 256)
                bitmap.recycle()
                val rgb = ByteArray(pixels.size * 3)
                pixels.forEachIndexed { i, pixel ->
                    rgb[i * 3] = (pixel shr 16).toByte()
                    rgb[i * 3 + 1] = (pixel shr 8).toByte()
                    rgb[i * 3 + 2] = pixel.toByte()
                }
                val scores = model.predictSingle(rgb)
                val expected = sample.getJSONArray("keras_scores")
                scores.forEachIndexed { j, score ->
                    assertEquals(expected.getDouble(j), score.toDouble(), .0002)
                }
                assertEquals(sample.getString("label"), FaceShapeImageModel.CLASSES[scores.indices.maxBy { scores[it] }])
                val original = model.predictSingle(rgb)
                val flipped = model.predictSingle(rgb, flip = true)
                val averaged = model.predict(rgb)
                FaceShapeImageModel.CLASSES.forEachIndexed { j, label ->
                    assertEquals((original[j] + flipped[j]) / 2f, averaged.scores.getValue(label), .00001f)
                }
            }
        }
    }
}
