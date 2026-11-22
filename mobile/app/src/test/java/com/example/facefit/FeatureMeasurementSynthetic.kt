package com.example.facefit

import com.example.facefit.facefit.analysis.FaceAnalyzer.LandmarkPoint
import com.example.facefit.facefit.recommendation.FacialFeatureThresholds
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** MediaPipe's canonical metric face (cm), read from the face_landmarker.task the app ships. */
internal object CanonicalFace {
    val vertices: List<DoubleArray> by lazy {
        val bytes = ZipFile(File("src/main/assets/face_landmarker.task")).use { zip ->
            zip.getInputStream(zip.getEntry("geometry_pipeline_metadata_landmarks.binarypb")).readBytes()
        }
        val mesh = ProtoReader(bytes).fields().first { it.number == 1 }.bytes!!
        val floats = mutableListOf<Float>()
        ProtoReader(mesh).fields().filter { it.number == 3 }.forEach { field ->
            if (field.fixed32 != null) floats += field.fixed32
            else field.bytes?.let { packed ->
                java.nio.ByteBuffer.wrap(packed).order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                    .let { buffer -> while (buffer.hasRemaining()) floats += buffer.get() }
            }
        }
        // Vertex layout: x, y, z, u, v.
        floats.chunked(5).map { doubleArrayOf(it[0].toDouble(), it[1].toDouble(), it[2].toDouble()) }
    }

    private class ProtoReader(private val data: ByteArray) {
        class Field(val number: Int, val bytes: ByteArray? = null, val fixed32: Float? = null)
        private var i = 0
        private fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = data[i++].toInt() and 0xff
                result = result or ((b and 0x7f).toLong() shl shift)
                if (b < 0x80) return result
                shift += 7
            }
        }
        fun fields(): List<Field> = buildList {
            while (i < data.size) {
                val key = varint()
                val number = (key ushr 3).toInt()
                when ((key and 7).toInt()) {
                    0 -> { varint(); add(Field(number)) }
                    1 -> { i += 8; add(Field(number)) }
                    2 -> { val n = varint().toInt(); add(Field(number, bytes = data.copyOfRange(i, i + n))); i += n }
                    5 -> {
                        val bits = (data[i].toInt() and 0xff) or ((data[i + 1].toInt() and 0xff) shl 8) or
                            ((data[i + 2].toInt() and 0xff) shl 16) or ((data[i + 3].toInt() and 0xff) shl 24)
                        i += 4
                        add(Field(number, fixed32 = java.lang.Float.intBitsToFloat(bits)))
                    }
                    else -> error("Unsupported wire type")
                }
            }
        }
    }
}

/** The thresholds the app loads from assets/facial_feature_thresholds.json. */
internal object AppThresholds {
    private val values: List<Float> by lazy {
        val text = File("src/main/assets/facial_feature_thresholds.json").readText()
        Regex("\"(low|high)\"\\s*:\\s*([0-9.]+)").findAll(text).map { it.groupValues[2].toFloat() }.toList()
    }
    val upperFace get() = FacialFeatureThresholds.Band(values[0], values[1])
    val jawline get() = FacialFeatureThresholds.Band(values[2], values[3])
    val eyeSpacing get() = FacialFeatureThresholds.Band(values[4], values[5])
}

internal data class SyntheticCamera(val width: Int = 480, val height: Int = 640, val focalPx: Double = 487.5)

internal data class HeadPose(val yaw: Double = 0.0, val pitch: Double = 0.0, val roll: Double = 0.0)

/**
 * A face projected through a pinhole camera. [cheekDistanceCm] is the camera distance to the
 * 234/454 cheek plane, matching FacialFeatureMeasurement.Frame.cheekDistanceCm.
 */
internal class SyntheticFace(private val mesh: List<DoubleArray>) {
    data class Capture(val points: List<LandmarkPoint>, val poseMatrix: FloatArray, val camera: SyntheticCamera)

    fun capture(
        cheekDistanceCm: Double,
        pose: HeadPose = HeadPose(),
        camera: SyntheticCamera = SyntheticCamera(),
        offsetCm: Pair<Double, Double> = 0.0 to 0.0,
        /** Pose reported in the matrix; defaults to the true pose. */
        reportedPose: HeadPose = pose,
        noise: Random? = null,
        noisePx: Double = 0.0,
    ): Capture {
        val r = rotation(pose)
        fun rotated(v: DoubleArray) = DoubleArray(3) { row -> r[row][0] * v[0] + r[row][1] * v[1] + r[row][2] * v[2] }
        val cheekDepth = (rotated(mesh[234])[2] + rotated(mesh[454])[2]) / 2
        val origin = cheekDistanceCm + cheekDepth
        val points = (0 until 478).map { index ->
            val p = rotated(mesh[if (index < mesh.size) index else 1])
            val depth = origin - p[2]
            var x = camera.width / 2.0 + camera.focalPx * (p[0] + offsetCm.first) / depth
            var y = camera.height / 2.0 - camera.focalPx * (p[1] + offsetCm.second) / depth
            if (noise != null && noisePx > 0) {
                x += gaussian(noise) * noisePx
                y += gaussian(noise) * noisePx
            }
            LandmarkPoint((x / camera.width).toFloat(), (y / camera.height).toFloat(), 0f)
        }
        val m = rotation(reportedPose)
        // Column-major, as MediaPipe Android returns it.
        val matrix = doubleArrayOf(
            m[0][0], m[1][0], m[2][0], 0.0,
            m[0][1], m[1][1], m[2][1], 0.0,
            m[0][2], m[1][2], m[2][2], 0.0,
            offsetCm.first, offsetCm.second, -origin, 1.0,
        ).let { values -> FloatArray(16) { values[it].toFloat() } }
        return Capture(points, matrix, camera)
    }

    companion object {
        fun canonical() = SyntheticFace(CanonicalFace.vertices)

        /** A different person: overall width/depth scaled, optionally one pair moved apart/together. */
        fun variant(widthScale: Double = 1.0, depthScale: Double = 1.0,
            pairWidthScale: Map<Pair<Int, Int>, Double> = emptyMap()): SyntheticFace {
            val scaled = CanonicalFace.vertices.map { v -> doubleArrayOf(v[0] * widthScale, v[1], v[2] * depthScale) }
                .toMutableList()
            pairWidthScale.forEach { (pair, scale) ->
                listOf(pair.first, pair.second).forEach { i -> scaled[i] = scaled[i].copyOf().also { it[0] *= scale } }
            }
            return SyntheticFace(scaled)
        }

        /** R = Rz(roll) Ry(yaw) Rx(pitch), the convention FaceScanPose.angles decodes. */
        fun rotation(pose: HeadPose): Array<DoubleArray> {
            val (y, p, r) = listOf(pose.yaw, pose.pitch, pose.roll).map(Math::toRadians)
            val rx = arrayOf(doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, cos(p), -sin(p)), doubleArrayOf(0.0, sin(p), cos(p)))
            val ry = arrayOf(doubleArrayOf(cos(y), 0.0, sin(y)), doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(-sin(y), 0.0, cos(y)))
            val rz = arrayOf(doubleArrayOf(cos(r), -sin(r), 0.0), doubleArrayOf(sin(r), cos(r), 0.0), doubleArrayOf(0.0, 0.0, 1.0))
            fun mul(a: Array<DoubleArray>, b: Array<DoubleArray>) =
                Array(3) { i -> DoubleArray(3) { j -> (0 until 3).sumOf { k -> a[i][k] * b[k][j] } } }
            return mul(rz, mul(ry, rx))
        }

        fun gaussian(random: Random): Double {
            val u = random.nextDouble().coerceAtLeast(1e-12)
            return sqrt(-2 * ln(u)) * cos(2 * Math.PI * random.nextDouble())
        }
    }
}
