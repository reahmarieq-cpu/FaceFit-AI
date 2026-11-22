package com.example.facefit

import com.example.facefit.facefit.analysis.FacialFeatureMeasurement
import com.example.facefit.facefit.analysis.FacialFeatureMeasurement.Rejection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Tests for the upper-face, jawline and eye-spacing measurements. Faces are MediaPipe's canonical
 * metric mesh projected through a pinhole camera, so pose, distance and noise are controlled.
 * Real MediaPipe landmarks do not follow this projection exactly, so distance behaviour is
 * verified on the phone, not here.
 */
class FacialFeatureStabilityTest {
    private val bands = listOf(AppThresholds.upperFace, AppThresholds.jawline, AppThresholds.eyeSpacing)

    private fun measure(c: SyntheticFace.Capture): FacialFeatureMeasurement.Frame =
        requireNotNull(FacialFeatureMeasurement.measure(c.points, c.camera.width, c.camera.height,
            c.poseMatrix, c.camera.focalPx.toFloat(), "test"))

    private fun bandWidths(a: FacialFeatureMeasurement.Ratios, b: FacialFeatureMeasurement.Ratios) =
        a.toList().zip(b.toList()).mapIndexed { i, (x, y) -> abs(x - y) / bands[i].width }

    private fun sd(values: List<Float>): Float {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)).toFloat()
    }

    // ---------------------------------------------------------------- inputs

    @Test fun canonicalCheekVerticesMatchTheShippedMediaPipeModel() {
        assertEquals(468, CanonicalFace.vertices.size)
        FacialFeatureMeasurement.CANONICAL.forEach { (index, v) ->
            val actual = CanonicalFace.vertices[index]
            assertEquals("x of $index", actual[0], v.x.toDouble(), 1e-5)
            assertEquals("y of $index", actual[1], v.y.toDouble(), 1e-5)
            assertEquals("z of $index", actual[2], v.z.toDouble(), 1e-5)
        }
    }

    @Test fun focalLengthFromTestDeviceCharacteristics() {
        // Front camera of the 25078RA3EY test device: 2.78 mm, 3.65 mm sensor, 3264x2448.
        val intrinsics = FacialFeatureMeasurement.CameraIntrinsics(2.78f, 3.65f, 3264, 3264, 2448)
        assertEquals(487.5f, intrinsics.focalLengthPx(640, 480)!!, 0.5f)
        assertEquals(487.5f, intrinsics.focalLengthPx(480, 640)!!, 0.5f)
        assertEquals(975f, intrinsics.focalLengthPx(960, 1280)!!, 1f)
        assertEquals(487.5f / 640f, FacialFeatureMeasurement.NOMINAL_FOCAL_TO_LONG_SIDE, 0.02f)
    }

    @Test fun cameraDistanceEstimateIsAccurateForTheAverageFace() {
        val face = SyntheticFace.canonical()
        for (distance in listOf(35.0, 42.0, 50.0, 58.0)) {
            for (pose in listOf(HeadPose(), HeadPose(yaw = 6.0), HeadPose(pitch = -6.0, roll = 5.0))) {
                val estimate = measure(face.capture(distance, pose)).cheekDistanceCm
                assertEquals("$distance cm $pose", distance, estimate.toDouble(), distance * 0.02)
            }
        }
    }

    // ---------------------------------------------------------------- Test A: scale invariance

    @Test fun testA_ratiosAreInvariantToResolutionAndUniformImageScale() {
        val face = SyntheticFace.variant(widthScale = 1.04, depthScale = 1.1)
        val pose = HeadPose(yaw = 2.0, pitch = -1.5, roll = 3.0)
        val reference = measure(face.capture(47.5, pose))
        for ((width, height) in listOf(240 to 320, 720 to 960, 960 to 1280, 1080 to 1440, 1536 to 2048)) {
            // Same lens and framing at another resolution: focal length in pixels scales with it.
            val camera = SyntheticCamera(width, height, 487.5 * width / 480.0)
            val frame = measure(face.capture(47.5, pose, camera))
            bandWidths(reference.ratios, frame.ratios).forEach { assertTrue("ratios $width", it < 1e-3f) }
            assertEquals(reference.cheekDistanceCm, frame.cheekDistanceCm, 1e-2f)
        }
    }

    @Test fun testA_normalizedCoordinatesAreConvertedWithImageWidthAndHeight() {
        // Roll gives the pairs a vertical component, so x and y scaling both matter.
        val camera = SyntheticCamera(480, 640)
        val capture = SyntheticFace.canonical().capture(47.5, HeadPose(roll = 15.0), camera)
        val frame = measure(capture)
        fun pixel(a: Int, b: Int) = hypot((capture.points[a].x - capture.points[b].x) * camera.width,
            (capture.points[a].y - capture.points[b].y) * camera.height)
        assertEquals(pixel(103, 332) / pixel(234, 454), frame.ratios.upperFace, 1e-5f)
        assertEquals(pixel(172, 397) / pixel(234, 454), frame.ratios.jawline, 1e-5f)
        assertEquals(pixel(133, 362) / pixel(234, 454), frame.ratios.eyeSpacing, 1e-5f)
        // Roll does not change the ratio.
        val upright = measure(SyntheticFace.canonical().capture(47.5, camera = camera))
        bandWidths(upright.ratios, frame.ratios).forEach { assertTrue(it < 0.01f) }
    }

    @Test fun testA_mediaPipeRelativeDepthIsNotUsed() {
        val capture = SyntheticFace.canonical().capture(47.5, HeadPose(yaw = 3.0))
        val random = Random(7)
        val withDepth = capture.points.map { it.copy(z = random.nextFloat() * 0.2f - 0.1f) }
        val a = measure(capture)
        val b = requireNotNull(FacialFeatureMeasurement.measure(withDepth, 480, 640, capture.poseMatrix, 487.5f, "test"))
        assertEquals(a, b)
    }

    // ---------------------------------------------------------------- Test B: head pose and framing

    @Test fun testB_unsuitableHeadAnglesDistancesAndFramingAreRejected() {
        val face = SyntheticFace.canonical()
        fun reason(c: SyntheticFace.Capture) = FacialFeatureMeasurement.assess(measure(c))
        assertNull(reason(face.capture(47.5, HeadPose(yaw = 5.5, pitch = -2.5, roll = 4.5))))
        assertNull(reason(face.capture(47.5, HeadPose(pitch = 2.8))))
        assertEquals(Rejection.HEAD_TILTED, reason(face.capture(47.5, HeadPose(pitch = 4.0))))
        assertEquals(Rejection.HEAD_TILTED, reason(face.capture(47.5, HeadPose(pitch = -4.0))))
        assertEquals(Rejection.HEAD_TURNED, reason(face.capture(47.5, HeadPose(yaw = 8.0))))
        assertEquals(Rejection.HEAD_TURNED, reason(face.capture(47.5, HeadPose(yaw = -8.0))))
        assertEquals(Rejection.HEAD_TILTED, reason(face.capture(47.5, HeadPose(pitch = 8.0))))
        assertEquals(Rejection.HEAD_TILTED, reason(face.capture(47.5, HeadPose(pitch = -8.0))))
        assertEquals(Rejection.HEAD_ROLLED, reason(face.capture(47.5, HeadPose(roll = 7.0))))
        assertEquals(Rejection.TOO_CLOSE, reason(face.capture(43.0)))
        assertEquals(Rejection.TOO_FAR, reason(face.capture(53.0)))
        assertEquals(Rejection.LANDMARKS_OUT_OF_FRAME, reason(face.capture(47.5, offsetCm = 14.0 to 0.0)))
        // No transformation matrix: no measurement, rejected by the collector.
        val capture = face.capture(47.5)
        assertNull(FacialFeatureMeasurement.measure(capture.points, 480, 640, null, 487.5f, "test"))
        assertEquals(Rejection.POSE_UNAVAILABLE, FacialFeatureMeasurement.Collector().offer(null))
        assertEquals("Look straight at the camera", Rejection.HEAD_TURNED.guidance)
        assertEquals("Move slightly farther away", Rejection.TOO_CLOSE.guidance)
    }

    @Test fun testB_distanceMustBeWithin45To50Cm() {
        val face = SyntheticFace.canonical()
        fun reason(cm: Double) = FacialFeatureMeasurement.assess(measure(face.capture(cm)))
        assertEquals(Rejection.TOO_CLOSE, reason(44.0))
        assertNull(reason(45.5))
        assertNull(reason(49.5))
        assertEquals(Rejection.TOO_FAR, reason(51.0))
    }

    @Test fun testB_smilingFramesAreRejected() {
        val c = SyntheticFace.canonical().capture(47.5)
        fun frame(smile: Float?) = requireNotNull(FacialFeatureMeasurement.measure(c.points, 480, 640,
            c.poseMatrix, 487.5f, "test", smileScore = smile))
        assertNull(FacialFeatureMeasurement.assess(frame(0.005f)))
        // A slight smile (0.02-0.03 on the phone) is refused too.
        assertEquals(Rejection.SMILING, FacialFeatureMeasurement.assess(frame(0.025f)))
        assertNull(FacialFeatureMeasurement.assess(frame(null)))
        assertEquals(Rejection.SMILING, FacialFeatureMeasurement.assess(frame(0.6f)))
        assertEquals("Keep a neutral expression, no smile", Rejection.SMILING.guidance)
        // A subtle closed-mouth smile: little mouth-smile score, but a raised upper lip.
        fun lips(left: Float, right: Float) = requireNotNull(FacialFeatureMeasurement.measure(c.points, 480, 640,
            c.poseMatrix, 487.5f, "test", smileScore = 0.002f,
            expressionScores = mapOf("mouthUpperUpLeft" to left, "mouthUpperUpRight" to right)))
        assertNull(FacialFeatureMeasurement.assess(lips(0.005f, 0.012f)))
        assertEquals(Rejection.SMILING, FacialFeatureMeasurement.assess(lips(0.040f, 0.050f)))
        assertEquals(0.045f, lips(0.040f, 0.050f).upperLipRaise!!, 1e-6f)
        // A capture where the person keeps smiling asks for another scan with that guidance.
        val collector = FacialFeatureMeasurement.Collector()
        repeat(10) { collector.offer(frame(0.7f)) }
        assertNull(collector.aggregate())
        assertEquals("Keep a neutral expression, no smile", collector.guidance())
    }

    @Test fun testB_rejectedFramesDoNotAffectTheAggregatedResult() {
        val face = SyntheticFace.variant(depthScale = 1.15)
        val random = Random(3)
        val good = (0 until 7).map { measure(face.capture(47.5, noise = random, noisePx = 0.3)) }
        val bad = listOf(HeadPose(yaw = 15.0), HeadPose(pitch = 12.0), HeadPose(pitch = -12.0), HeadPose(roll = 9.0))
            .map { measure(face.capture(47.5, it)) } + measure(face.capture(30.0))
        val collector = FacialFeatureMeasurement.Collector()
        // Interleave, breaking continuity so the motion check does not compare across a bad frame.
        good.forEachIndexed { i, frame ->
            bad.getOrNull(i)?.let { collector.offer(it); collector.breakContinuity() }
            assertNull(collector.offer(frame))
        }
        val mixed = requireNotNull(collector.aggregate())
        val clean = requireNotNull(FacialFeatureMeasurement.aggregate(good))
        assertEquals(clean.median, mixed.median)
        assertEquals(7, mixed.accepted.size)
        assertEquals(5, mixed.rejections.values.sum())
    }

    @Test fun testB_upperFaceWidthIsAdjustedForHeadPitch() {
        val face = SyntheticFace.canonical()
        val frames = listOf(-3.0, -1.5, 0.0, 1.5, 3.0).map { measure(face.capture(47.0, HeadPose(pitch = it))) }
        val band = bands[0]
        val raw = (frames.maxOf { it.rawUpperFace } - frames.minOf { it.rawUpperFace }) / band.width
        val adjusted = (frames.maxOf { it.ratios.upperFace } - frames.minOf { it.ratios.upperFace }) / band.width
        println("Test B: upper-face spread over pitch -3..3 deg: raw %.3f -> adjusted %.3f band widths".format(raw, adjusted))
        assertTrue(raw > 0.4f)
        assertTrue(adjusted < 0.1f)
        // Frontal frames are unchanged; jawline and eye spacing are never adjusted.
        assertEquals(frames[2].rawUpperFace, frames[2].ratios.upperFace, 1e-4f)
        frames.forEach { f ->
            assertEquals(f.rawUpperFace - FacialFeatureMeasurement.UPPER_FACE_PITCH_SLOPE_PER_DEG * f.pitchDeg,
                f.ratios.upperFace, 1e-6f)
        }
    }

    @Test fun testB_movementBetweenFramesIsRejected() {
        val face = SyntheticFace.canonical()
        val collector = FacialFeatureMeasurement.Collector()
        assertNull(collector.offer(measure(face.capture(47.5))))
        assertEquals(Rejection.MOVING, collector.offer(measure(face.capture(47.5, offsetCm = 0.8 to 0.0))))
        assertEquals(Rejection.MOVING, collector.offer(measure(face.capture(46.0, offsetCm = 0.8 to 0.0))))
        assertNull(collector.offer(measure(face.capture(46.1, offsetCm = 0.8 to 0.0))))
    }

    // ---------------------------------------------------------------- Test D: repeated scans (simulated noise)

    /**
     * Ten scans at one distance with assumed noise (0.6 px landmark jitter, 5 % glitch frames).
     * Aggregation must remove most frame-level noise. Posture that differs between scans is a
     * per-scan offset a median cannot remove, so with it the test only requires a reduction.
     * Distance effects are measured on the phone instead.
     */
    @Test fun testD_aggregationReducesScanToScanVariation() {
        val noiseOnly = repeatedScans(postureSdDeg = 0.0)
        val withPosture = repeatedScans(postureSdDeg = 2.0)
        println("Test D: between-scan SD in band widths (single frame -> gated median)\n" +
            "  jitter + glitches:            %s\n  + 2 deg posture between scans: %s".format(
                noiseOnly.describe(), withPosture.describe()))
        (0 until 3).forEach { assertTrue("noise feature $it", noiseOnly.second[it] < 0.5f * noiseOnly.first[it]) }
        (0 until 3).forEach { assertTrue("posture feature $it", withPosture.second[it] < withPosture.first[it]) }
    }

    private fun Pair<List<Float>, List<Float>>.describe() =
        first.zip(second).joinToString { (a, b) -> "%.3f -> %.3f".format(a, b) }

    private fun repeatedScans(postureSdDeg: Double): Pair<List<Float>, List<Float>> {
        val random = Random(2026)
        fun g() = SyntheticFace.gaussian(random)
        val face = SyntheticFace.variant(widthScale = 0.97, depthScale = 1.05)
        val target = FacialFeatureMeasurement.Ratios(bands[0].width * .15f, bands[1].width * .15f, bands[2].width * .15f)
        val singles = mutableListOf<FacialFeatureMeasurement.Ratios>()
        val medians = mutableListOf<FacialFeatureMeasurement.Ratios>()
        repeat(10) {
            // Pitch kept within the 3 degree limit so every scan collects enough frames.
            val base = HeadPose(g() * postureSdDeg, (g() * postureSdDeg).coerceIn(-2.0, 2.0), g() * postureSdDeg / 2)
            val collector = FacialFeatureMeasurement.Collector(target)
            var attempt = 0
            while (attempt < 70 && !collector.isComplete()) {
                attempt++
                val pose = HeadPose(base.yaw + g() * .3, base.pitch + g() * .3, base.roll + g() * .2)
                var capture = face.capture(47.0, pose, noise = random, noisePx = 0.6)
                if (random.nextDouble() < 0.05) {
                    capture = capture.copy(points = capture.points.toMutableList().also {
                        it[362] = it[362].copy(x = it[362].x + 3f / 480f)
                    })
                }
                val frame = measure(capture)
                if (attempt == 1) singles += frame.ratios
                collector.offer(frame)
            }
            medians += requireNotNull(collector.aggregate()).median
        }
        val singleSd = (0 until 3).map { f -> sd(singles.map { it.toList()[f] }) / bands[f].width }
        val medianSd = (0 until 3).map { f -> sd(medians.map { it.toList()[f] }) / bands[f].width }
        return singleSd to medianSd
    }

    // ---------------------------------------------------------------- aggregation and thresholds

    @Test fun obviousOutlierFramesAreRemovedBeforeTheMedian() {
        val face = SyntheticFace.canonical()
        val random = Random(11)
        val frames = (0 until 8).map { measure(face.capture(47.5, noise = random, noisePx = 0.3)) }.toMutableList()
        val glitch = face.capture(47.5).let { c ->
            c.copy(points = c.points.toMutableList().also { it[362] = it[362].copy(x = it[362].x + 12f / 480f) })
        }
        frames += measure(glitch)
        val result = requireNotNull(FacialFeatureMeasurement.aggregate(frames))
        assertEquals(1, result.outliers.size)
        assertEquals(8, result.accepted.size)
    }

    @Test fun tooFewReliableFramesRequestsAnotherScan() {
        val face = SyntheticFace.canonical()
        val collector = FacialFeatureMeasurement.Collector()
        repeat(3) { assertNull(collector.offer(measure(face.capture(47.5)))) }
        repeat(6) { collector.breakContinuity(); collector.offer(measure(face.capture(47.5, HeadPose(yaw = 12.0)))) }
        assertNull(collector.aggregate())
        assertTrue(!collector.isComplete())
        assertEquals("Look straight at the camera", collector.guidance())
    }

    @Test fun nearCutoffResultsAreFlaggedButStillGetOneCategory() {
        val band = AppThresholds.eyeSpacing
        val margin = band.uncertaintyMargin(0.001f)
        assertEquals(2 * 0.001f + FacialFeatureMeasurement.MODEL_MARGIN_BAND_FRACTION * band.width, margin, 1e-6f)
        val nearHigh = band.assess(band.high + margin / 2, margin, eyeSpacing = true)!!
        assertEquals("Wide-set", nearHigh.category)
        assertTrue(nearHigh.uncertain)
        val clear = band.assess(band.high + 2 * margin, margin, eyeSpacing = true)!!
        assertTrue(!clear.uncertain)
        assertEquals("Wide-set", clear.category)
    }

    // ---------------------------------------------------------------- Test E: classifier protection

    @Test fun testE_faceShapeClassifierFilesAreUnchanged() {
        fun sha256(path: String, text: Boolean): String {
            var bytes = File(path).readBytes()
            if (text) bytes = String(bytes, Charsets.UTF_8).replace("\r\n", "\n").toByteArray(Charsets.UTF_8)
            return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        }
        val analysis = "src/main/java/com/example/facefit/facefit/analysis"
        // Recorded before the feature-measurement change. Update only when deliberately changing the classifier.
        val binaries = mapOf(
            "src/main/assets/face_shape_max/face_shape_max.tflite" to "cf32c6cccad5600f4fa949601baf90b9bfc8fdac93a768b3b4e7a3de08e5e0ea",
            "src/main/assets/face_shape_max/metadata.json" to "9388bbaca09133cebdc42a93a639011249c73c139a38720a563fef9d0b01f148",
            "src/main/assets/face_shape_max/haarcascade_frontalface_default.xml" to "a71c83e9d0ffd53a00fdb3f1fd91905149d8d001e1076eabc32f2edb945f8ef3",
            "src/main/assets/face_landmarker.task" to "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff",
        )
        binaries.forEach { (path, hash) -> assertEquals(path, hash, sha256(path, text = false)) }
        val sources = mapOf(
            "$analysis/FaceShapeImageModel.kt" to FACE_SHAPE_IMAGE_MODEL,
            "$analysis/FaceShapeImageInput.kt" to FACE_SHAPE_IMAGE_INPUT,
            "$analysis/FaceShapeDecision.kt" to FACE_SHAPE_DECISION,
            "$analysis/FacialGeometry.kt" to FACIAL_GEOMETRY,
        )
        sources.forEach { (path, hash) -> assertEquals(path, hash, sha256(path, text = true)) }
    }

    private companion object {
        // SHA-256 with CRLF normalised to LF, recorded before the change.
        const val FACE_SHAPE_IMAGE_MODEL = "1f1b33a7c69ba986794ec492ae0565277f053efd4c0fce12591f76a340b59bd5"
        const val FACE_SHAPE_IMAGE_INPUT = "76b2244c990a9abd01869681d16e61416640b951b584377ef14e4a1679f6cb49"
        const val FACE_SHAPE_DECISION = "0c0d6621ae63087aa46c4902e0feb15ff0aee2eaec823d90a3e3f51e390b3f08"
        const val FACIAL_GEOMETRY = "1c5d099b00808464484e31fa10974f0bb27798c4ee9ca4bd13db89f19ce60463"
    }
}
