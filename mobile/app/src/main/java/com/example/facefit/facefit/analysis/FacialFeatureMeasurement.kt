package com.example.facefit.facefit.analysis

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Upper-face width (103-332), jawline width (172-397) and eye spacing (133-362), each divided by
 * cheek width (234-454) using pixel distances. The ratio is unaffected by resolution and uniform
 * scaling. Upper-face width is also adjusted for head pitch, a measured effect.
 *
 * A pinhole-camera perspective correction built from MediaPipe's canonical face was tried and
 * removed: on real phone scans at 42-52 cm it over-corrected upper-face width and reversed the
 * observed eye-spacing trend, increasing scan-to-scan variation. MediaPipe landmarks do not follow
 * the canonical pinhole model closely enough for that correction.
 *
 * What this component does instead: per-frame pose, distance, framing and motion checks; outlier
 * removal; and the median of accepted frames. The face-shape classifier never uses it.
 */
object FacialFeatureMeasurement {
    data class Vec3(val x: Float, val y: Float, val z: Float)

    /**
     * MediaPipe canonical face cheek vertices in cm (+z toward the camera), read from
     * geometry_pipeline_metadata_landmarks.binarypb inside assets/face_landmarker.task. Used only
     * to estimate camera distance from cheek width.
     */
    val CANONICAL: Map<Int, Vec3> = mapOf(
        234 to Vec3(-7.664182f, 0.673132f, -2.435867f),
        454 to Vec3(7.664182f, 0.673132f, -2.435867f),
    )

    val CHEEK = 234 to 454
    val UPPER_FACE = 103 to 332
    val JAWLINE = 172 to 397
    val EYES = 133 to 362
    private val REQUIRED = listOf(1, 234, 454, 103, 332, 172, 397, 133, 362)

    /**
     * Fallback when the camera does not report its focal length. The test device's front camera
     * reports 2.78 mm on a 3.65 mm-wide sensor, i.e. 0.76 x the long side of the analysis image.
     */
    const val NOMINAL_FOCAL_TO_LONG_SIDE = 0.75f

    /**
     * Upper-face ratio change per degree of head pitch, removed before aggregation so each frame
     * reads as if the head were level. Measured on phone scans: 0.093 and 0.088 band widths per
     * degree in two sessions (band width 0.0253), matching the 0.09 the canonical-face projection
     * predicts. Jawline and eye spacing showed no consistent pitch trend on the phone, so they
     * are not adjusted.
     */
    const val UPPER_FACE_PITCH_SLOPE_PER_DEG = 0.0023f

    /**
     * Required camera distance to the cheek plane. The live guide shows the same range, and the
     * repeated phone scans were most consistent there. The estimate is repeatable to about 1 cm
     * for one person but may be a few cm off in absolute terms.
     */
    const val TARGET_MIN_CM = 45f
    const val TARGET_MAX_CM = 50f

    /**
     * Highest accepted MediaPipe smile score (mean of mouthSmileLeft/Right, 0-1). On the test
     * phone, neutral frames scored at most 0.008 and slight smiles 0.018-0.028; slight smiles
     * moved eye spacing by -0.64, jawline by +0.47 and upper-face width by -0.35 band widths,
     * enough to change categories. 0.015 sits between the two. Measured on one person; widen it
     * if neutral faces are refused.
     */
    const val MAX_SMILE_SCORE = 0.015f

    /**
     * Highest accepted upper-lip raise (mean of MediaPipe mouthUpperUpLeft/Right, 0-1). A subtle
     * closed-mouth smile barely moves the mouth-smile score but lifts the upper lip: on the test
     * phone neutral frames scored at most 0.015 (right) / 0.008 (left) and subtle-smile frames at
     * least 0.039 / 0.036. Measured on one person; widen it if neutral faces are refused.
     */
    const val MAX_UPPER_LIP_RAISE = 0.025f

    /** Mean of the left/right upper-lip-raise scores, or null when unavailable. */
    fun upperLipRaise(expression: Map<String, Float>): Float? =
        listOfNotNull(expression["mouthUpperUpLeft"], expression["mouthUpperUpRight"])
            .filter { it.isFinite() }.takeIf { it.isNotEmpty() }?.average()?.toFloat()

    /** True when either expression score shows a smile. */
    fun isSmiling(smileScore: Float?, upperLipRaise: Float?) =
        (smileScore ?: 0f) > MAX_SMILE_SCORE || (upperLipRaise ?: 0f) > MAX_UPPER_LIP_RAISE

    const val MIN_FRAMES = 5
    /** A spread estimated from 5 frames is itself unreliable, so early stopping needs more. */
    const val EARLY_STOP_FRAMES = 8
    const val MAX_FRAMES = 15

    /**
     * Extra allowance, as a fraction of each threshold band width, for variation the frames of one
     * scan cannot show (distance and posture differ between scans). Used only to flag results
     * near a cutoff in diagnostics; categories are not changed by it.
     */
    const val MODEL_MARGIN_BAND_FRACTION = 0.12f

    data class Ratios(val upperFace: Float, val jawline: Float, val eyeSpacing: Float) {
        fun toList() = listOf(upperFace, jawline, eyeSpacing)
        fun isUsable() = toList().all { it.isFinite() && it > 0f }

        companion object {
            fun of(values: List<Float>) = Ratios(values[0], values[1], values[2])
        }
    }

    /**
     * Acceptable pose, distance, framing and motion for feature measurement. Pitch is the largest
     * pose effect: on 8 phone scans upper-face width changed 0.093 band widths per degree of
     * pitch (the canonical-face projection predicts 0.09), and keeping only scans within
     * +-3 degrees halved its scan-to-scan spread. Roll does not change the ratios. Distance must
     * be within [TARGET_MIN_CM, TARGET_MAX_CM], and smiles above [MAX_SMILE_SCORE] are rejected.
     */
    data class QualityLimits(
        val maxYawDeg: Float = 6f,
        val maxPitchDeg: Float = 3f,
        val maxRollDeg: Float = 5f,
        val minCheekDistanceCm: Float = TARGET_MIN_CM,
        val maxCheekDistanceCm: Float = TARGET_MAX_CM,
        val maxSmileScore: Float = MAX_SMILE_SCORE,
        val maxUpperLipRaise: Float = MAX_UPPER_LIP_RAISE,
        val edgeMargin: Float = 0.04f,
        /** Between consecutive analysed frames, relative to cheek width. */
        val maxCenterShift: Float = 0.03f,
        val maxScaleChange: Float = 0.03f,
        val maxPoseChangeDeg: Float = 3f,
    )

    enum class Rejection(val guidance: String) {
        POSE_UNAVAILABLE("Look straight at the camera"),
        LANDMARKS_OUT_OF_FRAME("Keep your whole face inside the frame"),
        HEAD_TURNED("Look straight at the camera"),
        HEAD_TILTED("Keep your chin level with the phone at eye level"),
        HEAD_ROLLED("Keep your head level"),
        TOO_CLOSE("Move slightly farther away"),
        TOO_FAR("Move slightly closer"),
        SMILING("Keep a neutral expression, no smile"),
        MOVING("Hold still for a moment"),
    }

    data class CameraIntrinsics(
        val focalLengthMm: Float,
        val sensorWidthMm: Float,
        val pixelArrayWidth: Int,
        val activeArrayWidth: Int,
        val activeArrayHeight: Int,
        /** LENS_INTRINSIC_CALIBRATION fx in active-array pixels, when the device reports it. */
        val calibratedFocalPx: Float? = null,
    ) {
        val source: String get() = if (calibratedFocalPx != null) "intrinsic_calibration" else "lens_focal_length"

        /** Focal length in pixels of an analysis image (either orientation) scaled from the active array. */
        fun focalLengthPx(imageWidth: Int, imageHeight: Int): Float? {
            if (imageWidth <= 0 || imageHeight <= 0 || activeArrayWidth <= 0 || activeArrayHeight <= 0) return null
            val activeFocal = calibratedFocalPx
                ?: if (sensorWidthMm > 0f && pixelArrayWidth > 0) focalLengthMm * pixelArrayWidth / sensorWidthMm else return null
            val activePerImagePixel = min(
                max(activeArrayWidth, activeArrayHeight).toFloat() / max(imageWidth, imageHeight),
                min(activeArrayWidth, activeArrayHeight).toFloat() / min(imageWidth, imageHeight)
            )
            return (activeFocal / activePerImagePixel).takeIf { it.isFinite() && it > 0f }
        }
    }

    data class Frame(
        /**
         * Pixel distance / cheek width, the original formula, with upper-face width adjusted to
         * zero pitch ([UPPER_FACE_PITCH_SLOPE_PER_DEG]).
         */
        val ratios: Ratios,
        /** Upper-face ratio before the pitch adjustment. */
        val rawUpperFace: Float,
        val yawDeg: Float,
        val pitchDeg: Float,
        val rollDeg: Float,
        /** Estimated camera distance to the cheek plane, from focal length and cheek width. */
        val cheekDistanceCm: Float,
        val cheekWidthPx: Float,
        /** Cheek width / shorter image side (the readiness size indicator). */
        val faceWidthFraction: Float,
        val centerXPx: Float,
        val centerYPx: Float,
        val landmarksInFrame: Boolean,
        val focalLengthPx: Float,
        val focalSource: String,
        /** MediaPipe smile score 0-1, or null when expression scores are unavailable. */
        val smileScore: Float? = null,
        /** All MediaPipe expression scores (blendshapes) by name; diagnostics only. */
        val expressionScores: Map<String, Float> = emptyMap(),
        /** Mean upper-lip raise (mouthUpperUpLeft/Right), or null when unavailable. */
        val upperLipRaise: Float? = null,
        /** Mouth-corner distance (61-291) / cheek width; diagnostics only. */
        val mouthWidthRatio: Float? = null,
    )

    data class Aggregate(
        val median: Ratios,
        /** Standard error of each median: 1.2533 x max(MAD sigma, SD) / sqrt(n). */
        val standardError: Ratios,
        val accepted: List<Frame>,
        val outliers: List<Frame>,
        val rejections: Map<Rejection, Int>,
    )

    private val CANONICAL_CHEEK_WIDTH = CANONICAL.getValue(CHEEK.second).x - CANONICAL.getValue(CHEEK.first).x

    fun nominalFocalLengthPx(imageWidth: Int, imageHeight: Int) =
        NOMINAL_FOCAL_TO_LONG_SIDE * max(imageWidth, imageHeight)

    /**
     * Measures one frame. [poseMatrix] is MediaPipe's 4x4 column-major facial transformation
     * matrix, used for head angles. Returns null when the inputs cannot produce a measurement.
     */
    fun measure(
        points: List<FaceAnalyzer.LandmarkPoint>,
        imageWidth: Int,
        imageHeight: Int,
        poseMatrix: FloatArray?,
        focalLengthPx: Float,
        focalSource: String,
        edgeMargin: Float = QualityLimits().edgeMargin,
        smileScore: Float? = null,
        expressionScores: Map<String, Float> = emptyMap(),
    ): Frame? {
        if (imageWidth <= 0 || imageHeight <= 0 || !focalLengthPx.isFinite() || focalLengthPx <= 0f) return null
        if (REQUIRED.any { it !in points.indices }) return null
        if (REQUIRED.any { !points[it].x.isFinite() || !points[it].y.isFinite() }) return null
        val m = poseMatrix?.takeIf { it.size == 16 && it.all(Float::isFinite) } ?: return null
        val angles = FaceScanPose.angles(m) ?: return null
        val scale = sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2])
        if (scale < 1e-4f) return null

        fun px(index: Int) = points[index].x * imageWidth
        fun py(index: Int) = points[index].y * imageHeight
        fun distance(a: Int, b: Int) = hypot(px(a) - px(b), py(a) - py(b))

        val cheekPx = distance(CHEEK.first, CHEEK.second)
        if (!cheekPx.isFinite() || cheekPx < 1f) return null
        val rawUpper = distance(UPPER_FACE.first, UPPER_FACE.second) / cheekPx
        val ratios = Ratios(
            rawUpper - UPPER_FACE_PITCH_SLOPE_PER_DEG * angles.pitch,
            distance(JAWLINE.first, JAWLINE.second) / cheekPx,
            distance(EYES.first, EYES.second) / cheekPx,
        )
        if (!ratios.isUsable()) return null
        // Cheek width projected for the current head rotation, then pinhole distance.
        val projectedXAxis = hypot(m[0], m[1]) / scale
        val cheekDistance = focalLengthPx * CANONICAL_CHEEK_WIDTH * projectedXAxis / cheekPx
        val inFrame = REQUIRED.all { index ->
            val p = points[index]
            p.x in edgeMargin..(1f - edgeMargin) && p.y in edgeMargin..(1f - edgeMargin)
        }
        return Frame(
            ratios = ratios,
            rawUpperFace = rawUpper,
            yawDeg = angles.yaw,
            pitchDeg = angles.pitch,
            rollDeg = angles.roll,
            cheekDistanceCm = cheekDistance,
            cheekWidthPx = cheekPx,
            faceWidthFraction = cheekPx / min(imageWidth, imageHeight),
            centerXPx = (px(CHEEK.first) + px(CHEEK.second)) / 2f,
            centerYPx = (py(CHEEK.first) + py(CHEEK.second)) / 2f,
            landmarksInFrame = inFrame,
            focalLengthPx = focalLengthPx,
            focalSource = focalSource,
            smileScore = smileScore?.takeIf { it.isFinite() },
            expressionScores = expressionScores,
            upperLipRaise = upperLipRaise(expressionScores),
            mouthWidthRatio = if (61 in points.indices && 291 in points.indices)
                (distance(61, 291) / cheekPx).takeIf { it.isFinite() } else null,
        )
    }

    /** Single-frame quality checks; motion is checked by [Collector]. */
    fun assess(frame: Frame, limits: QualityLimits = QualityLimits()): Rejection? = when {
        !frame.landmarksInFrame -> Rejection.LANDMARKS_OUT_OF_FRAME
        (frame.smileScore ?: 0f) > limits.maxSmileScore ||
            (frame.upperLipRaise ?: 0f) > limits.maxUpperLipRaise -> Rejection.SMILING
        abs(frame.yawDeg) > limits.maxYawDeg -> Rejection.HEAD_TURNED
        abs(frame.pitchDeg) > limits.maxPitchDeg -> Rejection.HEAD_TILTED
        abs(frame.rollDeg) > limits.maxRollDeg -> Rejection.HEAD_ROLLED
        frame.cheekDistanceCm < limits.minCheekDistanceCm -> Rejection.TOO_CLOSE
        frame.cheekDistanceCm > limits.maxCheekDistanceCm -> Rejection.TOO_FAR
        else -> null
    }

    private fun moved(previous: Frame, current: Frame, limits: QualityLimits): Boolean {
        val width = (previous.cheekWidthPx + current.cheekWidthPx) / 2f
        return hypot(current.centerXPx - previous.centerXPx, current.centerYPx - previous.centerYPx) / width >
            limits.maxCenterShift ||
            abs(current.cheekWidthPx - previous.cheekWidthPx) / width > limits.maxScaleChange ||
            abs(current.yawDeg - previous.yawDeg) > limits.maxPoseChangeDeg ||
            abs(current.pitchDeg - previous.pitchDeg) > limits.maxPoseChangeDeg ||
            abs(current.rollDeg - previous.rollDeg) > limits.maxPoseChangeDeg
    }

    /**
     * Collects accepted frames for one capture. Only normalized ratios are aggregated; categories
     * are assigned afterwards from the median.
     */
    class Collector(
        /** Stop early once every standard error is at or below this; null waits for [MAX_FRAMES]. */
        private val targetStandardError: Ratios? = null,
        private val limits: QualityLimits = QualityLimits(),
    ) {
        private val frames = mutableListOf<Frame>()
        private val rejections = linkedMapOf<Rejection, Int>()
        private var previous: Frame? = null
        private var cached: Aggregate? = null
        private var cachedSize = -1

        val acceptedCount get() = frames.size

        /** Returns the rejection reason, or null when the frame was accepted. */
        fun offer(frame: Frame?, rejection: Rejection? = null): Rejection? {
            val reason = rejection ?: if (frame == null) Rejection.POSE_UNAVAILABLE else
                assess(frame, limits) ?: previous?.let { if (moved(it, frame, limits)) Rejection.MOVING else null }
            previous = frame
            if (reason != null) {
                rejections[reason] = (rejections[reason] ?: 0) + 1
                return reason
            }
            if (frames.size < MAX_FRAMES) frames += requireNotNull(frame)
            return null
        }

        /** Call when a frame was not offered (e.g. failed readiness) so motion is not compared across it. */
        fun breakContinuity() {
            previous = null
        }

        fun aggregate(): Aggregate? {
            if (cachedSize != frames.size) {
                cached = FacialFeatureMeasurement.aggregate(frames, rejections.toMap())
                cachedSize = frames.size
            }
            return cached
        }

        fun isComplete(): Boolean {
            val result = aggregate() ?: return false
            if (result.accepted.size + result.outliers.size >= MAX_FRAMES) return true
            if (result.accepted.size < EARLY_STOP_FRAMES) return false
            val target = targetStandardError ?: return false
            return result.standardError.toList().zip(target.toList()).all { (se, limit) -> se <= limit }
        }

        /** Most frequent rejection, for telling the person what to change. */
        fun guidance(): String? = rejections.maxByOrNull { it.value }?.key?.guidance

        fun rejectionCounts(): Map<Rejection, Int> = rejections.toMap()
    }

    /**
     * Rejects frames whose ratio is more than 3.5 robust sigmas from the median on any feature
     * (modified z-score), then takes the median of the remaining frames.
     */
    fun aggregate(frames: List<Frame>, rejections: Map<Rejection, Int> = emptyMap()): Aggregate? {
        if (frames.size < MIN_FRAMES) return null
        val columns = (0 until 3).map { feature -> frames.map { it.ratios.toList()[feature] } }
        val centers = columns.map(::medianValue)
        val sigmas = columns.mapIndexed { i, values ->
            // Floor of 0.4 % stops identical frames from making every other frame an outlier.
            max(robustSigma(values, centers[i]), centers[i] * 0.004f)
        }
        val (inliers, outliers) = frames.partition { frame ->
            frame.ratios.toList().withIndex().all { (i, v) -> abs(v - centers[i]) <= 3.5f * sigmas[i] }
        }
        if (inliers.size < MIN_FRAMES) return null
        val inlierColumns = (0 until 3).map { feature -> inliers.map { it.ratios.toList()[feature] } }
        val medians = inlierColumns.map(::medianValue)
        val errors = inlierColumns.mapIndexed { i, values ->
            // The larger of the MAD-based and ordinary spread, so a lucky small MAD cannot hide noise.
            val sigma = max(robustSigma(values, medians[i]), standardDeviation(values))
            1.2533f * sigma / sqrt(values.size.toFloat())
        }
        return Aggregate(Ratios.of(medians), Ratios.of(errors), inliers, outliers, rejections)
    }

    private fun standardDeviation(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)).toFloat()
    }

    private fun robustSigma(values: List<Float>, center: Float) =
        1.4826f * medianValue(values.map { abs(it - center) })
}
