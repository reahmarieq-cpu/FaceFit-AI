package com.example.facefit.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.example.facefit.facefit.analysis.FaceAnalyzer
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Try-on only: MediaPipe's lighter 468-point face mesh (`vto/face_landmark.tflite`, 192x192 input)
 * run on a crop around where the face was in the previous frame, as MediaPipe's own face-mesh
 * tracking does. The heavy face landmarker is still used to find the face ([seed]); this model
 * then follows it each frame until its face score drops ([track] returns null).
 *
 * Never used by the face scan, its measurements or the face-shape classifier.
 * Not thread-safe: use from the single tracking worker.
 */
internal class VtoLightFaceMesh private constructor(
    cpu: Interpreter,
    /** Same model on the GPU, when this phone supports it; [warmUp] keeps whichever is faster. */
    private var gpu: Interpreter?,
    private var gpuDelegate: GpuDelegate?,
    private val model: ByteBuffer,
    private val landmarksOutput: Int,
    private val scoreOutput: Int,
) : AutoCloseable {
    /** Square crop: centre, side and the unit eye-line direction, all in source pixels. */
    private data class Roi(val cx: Float, val cy: Float, val size: Float, val ux: Float, val uy: Float)

    private val input = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder())
    private val landmarks = ByteBuffer.allocateDirect(LANDMARKS * 3 * 4).order(ByteOrder.nativeOrder())
    private val score = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
    private val crop = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(crop)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pixels = IntArray(SIZE * SIZE)
    private val toSource = Matrix()
    private val toCrop = Matrix()
    private var interpreter: Interpreter = cpu
    private var cpu: Interpreter? = cpu
    private var roi: Roi? = null
    /** Light-model depth * zScale ~= heavy-model depth, calibrated at each [seed]. */
    private var zScale = 1f

    val tracking: Boolean get() = roi != null

    /** Last [track] or [seed] diagnostics, for debug logs. */
    var lastScore = Float.NaN
        private set
    var lastSeedDisagreementPx = Float.NaN
        private set

    fun reset() { roi = null; previousRoi = null; shape.reset() }

    private val shape = com.example.facefit.facefit.tryon.tracking.VtoFaceShapeSmoother()
    /** Face-relative point movement per mesh before/after smoothing, in pixels at this eye span (debug). */
    val shapeJitter: Pair<Float, Float> get() = shape.lastRawJitter to shape.lastSmoothedJitter

    /** "CPU" or "GPU" after [warmUp]; median benchmark times per backend, for logs. */
    var backend = "CPU"
        private set
    var benchmark: Map<String, Double> = emptyMap()
        private set

    /**
     * First inferences are slow (graph and shader preparation): run them before the camera starts,
     * and time CPU against GPU on this phone, keeping the faster one. Must run on the tracking thread
     * (the GPU delegate is bound to the thread that uses it).
     */
    fun warmUp(source: Bitmap) {
        roi = Roi(source.width * .5f, source.height * .5f, minOf(source.width, source.height).toFloat(), 1f, 0f)
        try {
            val times = LinkedHashMap<String, Double>()
            for ((name, candidate) in listOf("CPU" to cpu, "GPU" to gpu)) {
                if (candidate == null) continue
                interpreter = candidate
                try {
                    repeat(BENCHMARK_WARMUP) { infer(source) }
                    val runs = DoubleArray(BENCHMARK_RUNS) {
                        val start = System.nanoTime(); infer(source); (System.nanoTime() - start) / 1e6
                    }
                    times[name] = runs.sorted()[runs.size / 2]
                } catch (error: Exception) {
                    android.util.Log.w("FaceFitVTO", "Light face mesh $name backend failed", error)
                }
            }
            benchmark = times
            val gpuMs = times["GPU"]
            val cpuMs = times["CPU"]
            // GPU only for a clear win; the CPU result is the safe default.
            if (gpuMs != null && (cpuMs == null || gpuMs < cpuMs * GPU_REQUIRED_RATIO)) {
                interpreter = requireNotNull(gpu); backend = "GPU"
                cpu?.close(); cpu = null
            } else {
                interpreter = requireNotNull(cpu); backend = "CPU"
                closeGpu()
            }
        } finally { roi = null }
    }

    private fun closeGpu() {
        gpu?.close(); gpu = null
        gpuDelegate?.close(); gpuDelegate = null
    }

    /**
     * Starts tracking from heavy-model [points] (normalized to [source]). Also runs the light model
     * once on the same frame to calibrate its depth scale against the heavy model.
     */
    fun seed(source: Bitmap, points: List<FaceAnalyzer.LandmarkPoint>) {
        shape.reset()
        roi = roiFrom(points, source.width, source.height)
        val light = infer(source) ?: run { roi = null; return }
        val heavy = points.take(LANDMARKS)
        fun spread(z: List<Float>) = z.average().let { m -> sqrt(z.sumOf { ((it - m) * (it - m)) } / z.size) }
        val heavySpread = spread(heavy.map { it.z })
        val lightSpread = spread(light.map { it.z })
        if (heavySpread > 0 && lightSpread > 0) zScale = (heavySpread / lightSpread).toFloat().coerceIn(.25f, 4f)
        lastSeedDisagreementPx = heavy.indices.map { i ->
            hypot((heavy[i].x - light[i].x) * source.width, (heavy[i].y - light[i].y) * source.height)
        }.average().toFloat()
        roi = roiFrom(heavy, source.width, source.height)
    }

    /** 468 landmarks normalized to [source], or null when the face was lost (call [seed] again). */
    fun track(source: Bitmap, moveX: Float = 0f, moveY: Float = 0f): List<FaceAnalyzer.LandmarkPoint>? {
        // Centre the crop where the face is now (motion measured by camera-rate eye tracking since
        // the last mesh): off-centre faces pull the model's answer back toward the crop centre.
        val current = roi?.let { it.copy(cx = it.cx + moveX, cy = it.cy + moveY) } ?: return null
        roi = current
        var result = infer(source)
        if (result == null) {
            // A fast move can carry the face out of the crop. Before the slow full model, look once
            // more where it was heading, over a larger area.
            val before = previousRoi
            val dx = if (before != null) current.cx - before.cx else 0f
            val dy = if (before != null) current.cy - before.cy else 0f
            roi = current.copy(cx = current.cx + dx, cy = current.cy + dy, size = current.size * RETRY_SCALE)
            result = infer(source)
            retries++
        }
        if (result == null) { roi = null; previousRoi = null; return null }
        val scaled = result.map { it.copy(z = it.z * zScale) }
        previousRoi = current
        roi = roiFrom(scaled, source.width, source.height)
        // Steady face shape (head fit, temple hiding) without delaying head motion.
        return shape.smooth(scaled, source.width, source.height, android.os.SystemClock.elapsedRealtimeNanos())
    }

    private var previousRoi: Roi? = null
    /** Times the wider retry ran (debug logs). */
    var retries = 0

    private fun infer(source: Bitmap): List<FaceAnalyzer.LandmarkPoint>? {
        val r = roi ?: return null
        // Crop (u, v) in 0..SIZE maps to source = centre + (u - SIZE/2) * s * e1 + (v - SIZE/2) * s * e2,
        // with e1 along the eye line and e2 perpendicular (down): the face is upright in the crop.
        val s = r.size / SIZE
        toSource.setValues(floatArrayOf(
            r.ux * s, -r.uy * s, r.cx - (r.ux - r.uy) * r.size * .5f,
            r.uy * s, r.ux * s, r.cy - (r.uy + r.ux) * r.size * .5f,
            0f, 0f, 1f))
        if (!toSource.invert(toCrop)) return null
        canvas.drawColor(android.graphics.Color.BLACK)
        canvas.drawBitmap(source, toCrop, paint)
        crop.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        input.rewind()
        for (p in pixels) {
            input.putFloat(((p shr 16) and 0xFF) / 255f)
            input.putFloat(((p shr 8) and 0xFF) / 255f)
            input.putFloat((p and 0xFF) / 255f)
        }
        input.rewind(); landmarks.rewind(); score.rewind()
        interpreter.runForMultipleInputsOutputs(arrayOf(input), mapOf(landmarksOutput to landmarks, scoreOutput to score))
        score.rewind()
        lastScore = 1f / (1f + exp(-score.float))
        if (!(lastScore >= MIN_FACE_SCORE)) return null
        landmarks.rewind()
        val point = FloatArray(2)
        val depth = s / source.width // Crop pixels to source-width-normalized depth, as MediaPipe projects z.
        val out = ArrayList<FaceAnalyzer.LandmarkPoint>(LANDMARKS)
        for (i in 0 until LANDMARKS) {
            point[0] = landmarks.float; point[1] = landmarks.float
            val z = landmarks.float
            toSource.mapPoints(point)
            val x = point[0] / source.width
            val y = point[1] / source.height
            if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return null
            out.add(FaceAnalyzer.LandmarkPoint(x, y, z * depth))
        }
        return out
    }

    override fun close() { cpu?.close(); cpu = null; closeGpu(); crop.recycle() }

    companion object {
        const val ASSET = "vto/face_landmark.tflite"
        const val SIZE = 192
        const val LANDMARKS = 468
        /** Below this face score the crop no longer holds the face; find it again with the heavy model. */
        const val MIN_FACE_SCORE = .5f
        /** MediaPipe's face-mesh crop: 1.5x the landmark box, square on its longer side. */
        const val ROI_SCALE = 1.5f
        const val RETRY_SCALE = 1.6f
        const val BENCHMARK_WARMUP = 3
        const val BENCHMARK_RUNS = 9
        /** GPU must be at least this much faster (time ratio) to be chosen. */
        const val GPU_REQUIRED_RATIO = .85

        fun open(context: Context): VtoLightFaceMesh {
            val bytes = context.assets.open(ASSET).use { it.readBytes() }
            val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); rewind() }
            val interpreter = Interpreter(model, Interpreter.Options().apply { setNumThreads(4) })
            var gpuDelegate: GpuDelegate? = null
            var gpu: Interpreter? = null
            try {
                val inputTensor = interpreter.getInputTensor(0)
                require(inputTensor.shape().contentEquals(intArrayOf(1, SIZE, SIZE, 3)) && inputTensor.dataType() == DataType.FLOAT32) {
                    "Unexpected face mesh input ${inputTensor.shape().contentToString()}"
                }
                var landmarksOutput = -1
                var scoreOutput = -1
                for (i in 0 until interpreter.outputTensorCount) {
                    when (interpreter.getOutputTensor(i).numElements()) {
                        LANDMARKS * 3 -> landmarksOutput = i
                        1 -> scoreOutput = i
                    }
                }
                require(landmarksOutput >= 0 && scoreOutput >= 0) { "Unexpected face mesh outputs" }
                try {
                    val compatibility = CompatibilityList()
                    try {
                        if (compatibility.isDelegateSupportedOnThisDevice) {
                            gpuDelegate = GpuDelegate(compatibility.bestOptionsForThisDevice)
                            gpu = Interpreter(model, Interpreter.Options().addDelegate(gpuDelegate))
                        }
                    } finally { compatibility.close() }
                } catch (error: Throwable) {
                    android.util.Log.w("FaceFitVTO", "GPU unavailable for the light face mesh; using CPU", error)
                    gpu?.close(); gpu = null
                    gpuDelegate?.close(); gpuDelegate = null
                }
                return VtoLightFaceMesh(interpreter, gpu, gpuDelegate, model, landmarksOutput, scoreOutput)
            } catch (error: Throwable) {
                interpreter.close()
                gpu?.close(); gpuDelegate?.close()
                throw error
            }
        }

        /** MediaPipe face-mesh ROI: box of all landmarks, rotated to the eye line (33 -> 263), x1.5, square. */
        private fun roiFrom(points: List<FaceAnalyzer.LandmarkPoint>, width: Int, height: Int): Roi? {
            if (points.size < LANDMARKS) return null
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (i in 0 until LANDMARKS) {
                val x = points[i].x * width; val y = points[i].y * height
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
            }
            val ex = (points[263].x - points[33].x) * width
            val ey = (points[263].y - points[33].y) * height
            val length = hypot(ex, ey)
            if (!(length > 1f)) return null
            val size = maxOf(maxX - minX, maxY - minY) * ROI_SCALE
            if (!(size > 8f) || !size.isFinite()) return null
            return Roi((minX + maxX) * .5f, (minY + maxY) * .5f, size, ex / length, ey / length)
        }
    }
}
