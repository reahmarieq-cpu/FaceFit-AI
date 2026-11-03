package com.example.facefit.logic

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer
import org.json.JSONObject

class FaceShapeModel private constructor(
    private val environment: OrtEnvironment,
    private val session: OrtSession,
    private val classes: List<String>
) : AutoCloseable {
    fun predict(features: List<Float>): FaceShapePrediction = predictBatch(listOf(features)).single()

    fun predictBatch(samples: List<List<Float>>): List<FaceShapePrediction> {
        require(samples.isNotEmpty() && samples.size <= 16 && samples.all { FaceShapeFeatures.isValid(it) }) {
            "Invalid face-shape input"
        }
        val buffer = FloatBuffer.allocate(samples.size * FaceShapeFeatures.names.size)
        samples.forEach { buffer.put(it.toFloatArray()) }
        buffer.rewind()
        OnnxTensor.createTensor(environment, buffer,
            longArrayOf(samples.size.toLong(), FaceShapeFeatures.names.size.toLong())).use { input ->
            session.run(mapOf("features" to input)).use { result ->
                val labels = result.get("label").get().value as Array<*>
                val rows = result.get("probabilities").get().value as Array<*>
                require(labels.size == samples.size && rows.size == samples.size) { "Invalid model batch" }
                return samples.indices.map { index ->
                    val label = labels[index] as String
                    val scores = rows[index] as FloatArray
                    require(label in classes && scores.size == classes.size &&
                        scores.all { it.isFinite() && it in 0f..1.00001f }) { "Invalid model result" }
                    FaceShapePrediction(label, classes.zip(scores.toList()).toMap())
                }
            }
        }
    }

    fun assess(samples: List<List<Float>>, median: List<Float>): FaceShapeDecision.Assessment {
        require(samples.size in FaceShapeDecision.MIN_FRAMES..15) { "Insufficient scan frames" }
        val predictions = predictBatch(samples + listOf(median))
        return FaceShapeDecision.assess(predictions.dropLast(1), predictions.last())
    }

    override fun close() = session.close()

    companion object {
        const val MODEL_ID = "facefit_7classes_v2"

        fun open(context: Context): FaceShapeModel {
            val metadata = context.assets.open("$MODEL_ID.json").bufferedReader().use {
                JSONObject(it.readText())
            }
            val featureOrder = metadata.getJSONArray("feature_order").let { array ->
                List(array.length()) { array.getString(it) }
            }
            require(featureOrder == FaceShapeFeatures.names) { "Face model feature order mismatch" }
            val classes = metadata.getJSONObject("outputs").getJSONObject("probabilities")
                .getJSONArray("class_order").let { array -> List(array.length()) { array.getString(it) } }
            val environment = OrtEnvironment.getEnvironment()
            val session = OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(1)
                options.setInterOpNumThreads(1)
                context.assets.open("$MODEL_ID.onnx").use { source ->
                    environment.createSession(source.readBytes(), options)
                }
            }
            return FaceShapeModel(environment, session, classes)
        }
    }
}
