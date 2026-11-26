package com.facefit.kiosk.facefit.mobilescan

import android.content.Context
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

internal class FaceShapeImageModel private constructor(
    private val interpreter: Interpreter,
    private val modelBuffer: ByteBuffer
) : AutoCloseable {
    fun predict(rgb: ByteArray): FaceShapePrediction {
        val original = predictSingle(rgb, flip = false)
        val flipped = predictSingle(rgb, flip = true)
        val scores = CLASSES.indices.map { (original[it] + flipped[it]) / 2f }
        val best = scores.indices.maxBy { scores[it] }
        return FaceShapePrediction(CLASSES[best], CLASSES.zip(scores).toMap())
    }

    internal fun predictSingle(rgb: ByteArray, flip: Boolean = false): FloatArray {
        require(rgb.size == SIZE * SIZE * 3)
        val input = ByteBuffer.allocateDirect(rgb.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val sourceX = if (flip) SIZE - 1 - x else x
                val offset = (y * SIZE + sourceX) * 3
                input.putFloat((rgb[offset].toInt() and 255).toFloat())
                input.putFloat((rgb[offset + 1].toInt() and 255).toFloat())
                input.putFloat((rgb[offset + 2].toInt() and 255).toFloat())
            }
        }
        input.rewind()
        val output = arrayOf(FloatArray(CLASSES.size))
        interpreter.run(input, output)
        check(output[0].all { it.isFinite() && it in 0f..1.00001f } && abs(output[0].sum() - 1f) < .01f)
        return output[0]
    }

    override fun close() = interpreter.close()

    companion object {
        const val MODEL_ID = "face_shape_max_efficientnetb0"
        const val POLICY_ID = "originalAndHorizontalFlipMeanV1"
        const val SIZE = 256
        val CLASSES = listOf("Oval", "Round", "Square", "Oblong", "Heart")

        fun open(context: Context): FaceShapeImageModel {
            val bytes = context.assets.open("face_shape_max/face_shape_max.tflite").use { it.readBytes() }
            val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buffer.put(bytes).rewind()
            val options = Interpreter.Options().apply { setNumThreads(2) }
            val interpreter = Interpreter(buffer, options)
            try {
                require(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(1, SIZE, SIZE, 3)))
                require(interpreter.getInputTensor(0).dataType() == DataType.FLOAT32)
                require(interpreter.getOutputTensor(0).shape().contentEquals(intArrayOf(1, CLASSES.size)))
                require(interpreter.getOutputTensor(0).dataType() == DataType.FLOAT32)
                return FaceShapeImageModel(interpreter, buffer)
            } catch (error: Throwable) {
                interpreter.close()
                throw error
            }
        }
    }
}
