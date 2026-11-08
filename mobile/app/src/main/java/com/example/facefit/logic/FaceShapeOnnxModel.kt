package com.example.facefit.logic

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer

internal class FaceShapeOnnxModel private constructor(
    private val environment: OrtEnvironment,
    private val session: OrtSession
) : AutoCloseable {
    fun assess(frames: List<List<Float>>, median: List<Float>): FaceShapeDecision.Assessment {
        val predictions = predict(frames + listOf(median))
        val assessment = FaceShapeDecision.assess(predictions.dropLast(1), predictions.last())
        check(assessment.label in CLASSES) { "The model did not return valid face-shape scores" }
        return assessment
    }

    fun predict(samples: List<List<Float>>): List<FaceShapePrediction> {
        require(samples.isNotEmpty() && samples.all(ScanStabilityFeatures::isValid))
        val featureCount = ScanStabilityFeatures.names.size
        val features = FloatBuffer.allocate(samples.size * featureCount)
        samples.forEach { row -> row.forEach { features.put(it) } }
        features.rewind()
        val shape = longArrayOf(samples.size.toLong(), featureCount.toLong())
        return OnnxTensor.createTensor(environment, features, shape).use { input ->
            session.run(mapOf(INPUT to input)).use { result ->
                val labels = result.get(LABEL).get().value as Array<*>
                val scores = result.get(PROBABILITIES).get().value as Array<*>
                require(labels.size == samples.size && scores.size == samples.size)
                labels.indices.map { i ->
                    val label = labels[i] as String
                    val row = scores[i] as FloatArray
                    require(label in CLASSES && row.size == CLASSES.size)
                    FaceShapePrediction(label, CLASSES.zip(row.toList()).toMap())
                }
            }
        }
    }

    override fun close() = session.close()

    companion object {
        const val MODEL_ID = "facefit_18b"
        const val POLICY_ID = "highestAverageScoreV1"
        val CLASSES = listOf("Heart", "Oblong", "Oval", "Round", "Square")
        private const val INPUT = "float_input"
        private const val LABEL = "label"
        private const val PROBABILITIES = "probabilities"
        private const val ASSET_DIRECTORY = "experiment_18b"

        fun open(context: Context): FaceShapeOnnxModel {
            fun readOrder(kind: String) = context.assets.open("$ASSET_DIRECTORY/$kind.txt")
                .bufferedReader().use { it.readLines() }
            require(readOrder("features") == ScanStabilityFeatures.names)
            require(readOrder("classes") == CLASSES)
            val model = context.assets.open("$ASSET_DIRECTORY/$MODEL_ID.onnx").use { it.readBytes() }
            val environment = OrtEnvironment.getEnvironment()
            val session = OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(1)
                environment.createSession(model, options)
            }
            return FaceShapeOnnxModel(environment, session)
        }
    }
}
