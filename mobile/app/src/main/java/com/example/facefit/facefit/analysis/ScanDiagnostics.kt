package com.example.facefit.facefit.analysis

import android.content.Context
import android.util.Log
import com.example.facefit.BuildConfig
import com.example.facefit.facefit.recommendation.FacialFeatureThresholds
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Numeric capture records for comparing actual camera inputs with desktop inference. */
internal object ScanDiagnostics {
    /** Short JSON lines (logcat truncates lines near 4,000 characters): one "scan", three "feature", one per "frame". */
    const val FEATURE_JSON_TAG = "FACEFIT_FEATURE_JSON"

    private data class Feature(
        val key: String, val band: FacialFeatureThresholds.Band?, val ratio: Float,
        val standardError: Float?, val eyeSpacing: Boolean
    )

    private fun features(metrics: FaceAnalyzer.FaceMetrics, thresholds: FacialFeatureThresholds?): List<Feature> {
        val se = metrics.featureAggregate?.standardError
        return listOf(
            Feature("upper_face_width", thresholds?.upperFace, metrics.foreheadWidth, se?.upperFace, false),
            Feature("jawline_width", thresholds?.jawline, metrics.jawline, se?.jawline, false),
            Feature("eye_spacing", thresholds?.eyeSpacing, metrics.eyeSpacing, se?.eyeSpacing, true),
        )
    }

    private fun Float?.json(): Double? = this?.takeIf { it.isFinite() }?.toDouble()

    private fun median(values: List<Float>): Float? =
        if (values.isEmpty()) null else values.sorted().let { it[it.size / 2] }

    private fun featureRecord(f: Feature): JSONObject {
        val margin = f.standardError?.let { f.band?.uncertaintyMargin(it) }
        val assessment = margin?.let { f.band?.assess(f.ratio, it, f.eyeSpacing) }
        return JSONObject()
            .put("feature", f.key)
            .put("median_ratio", f.ratio.json())
            .put("standard_error", f.standardError.json())
            .put("low", f.band?.low.json()).put("high", f.band?.high.json())
            .put("distance_to_boundary", f.band?.distanceToBoundary(f.ratio).json())
            .put("distance_in_band_widths", f.band?.let { it.distanceToBoundary(f.ratio) / it.width }.json())
            .put("uncertainty_margin", margin.json())
            .put("category", f.band?.classify(f.ratio, f.eyeSpacing) ?: "Unavailable")
            .put("near_cutoff", assessment?.uncertain)
            .put("near_cutoff_between", assessment?.takeIf { it.uncertain }?.label)
    }

    private fun scanRecord(metrics: FaceAnalyzer.FaceMetrics): JSONObject {
        val aggregate = metrics.featureAggregate
        val frames = aggregate?.accepted.orEmpty()
        return JSONObject()
            .put("face_shape", metrics.faceShape)
            .put("method", if (aggregate != null) "gated_median_pitch_upper_v3" else "single_frame")
            .put("accepted_frames", frames.size)
            .put("outlier_frames", aggregate?.outliers?.size ?: 0)
            .put("rejected_frames", JSONObject().apply {
                aggregate?.rejections?.forEach { (reason, count) -> put(reason.name, count) }
            })
            .put("median_distance_cm", median(frames.map { it.cheekDistanceCm }).json())
            .put("median_yaw", median(frames.map { it.yawDeg }).json())
            .put("median_pitch", median(frames.map { it.pitchDeg }).json())
            .put("median_roll", median(frames.map { it.rollDeg }).json())
            .put("median_smile_score", median(frames.mapNotNull { it.smileScore }).json())
            .put("median_upper_lip_raise", median(frames.mapNotNull { it.upperLipRaise }).json())
            .put("focal_px", frames.firstOrNull()?.focalLengthPx.json())
            .put("focal_source", frames.firstOrNull()?.focalSource)
            .put("classifier_frames", metrics.shapeSampleCount)
            .put("classifier_frames_rejected", metrics.rejectedSampleCount)
    }

    private fun frameRecord(index: Int, frame: FacialFeatureMeasurement.Frame, outlier: Boolean) = JSONObject()
        .put("i", index)
        .put("outlier", outlier)
        .put("upper", frame.ratios.upperFace.json())
        .put("upper_raw", frame.rawUpperFace.json())
        .put("jaw", frame.ratios.jawline.json())
        .put("eye", frame.ratios.eyeSpacing.json())
        .put("yaw", frame.yawDeg.json()).put("pitch", frame.pitchDeg.json()).put("roll", frame.rollDeg.json())
        .put("distance_cm", frame.cheekDistanceCm.json())
        .put("cheek_px", frame.cheekWidthPx.json())
        .put("smile_score", frame.smileScore.json())
        .put("mouth_width", frame.mouthWidthRatio.json())
        .put("upper_lip_raise", frame.upperLipRaise.json())
        .put("expr", JSONObject().apply {
            // Rounded to 4 decimals to keep each line short (logcat truncates near 4,000 chars).
            frame.expressionScores.forEach { (name, score) ->
                if (score.isFinite()) put(name, Math.round(score * 10000.0) / 10000.0)
            }
        })

    fun featureJson(metrics: FaceAnalyzer.FaceMetrics, thresholds: FacialFeatureThresholds?): JSONObject {
        val aggregate = metrics.featureAggregate
        return scanRecord(metrics)
            .put("raw_measurements_px", JSONObject()
                .put("upper_face_width", metrics.rawForeheadWidthPx.json())
                .put("jawline_width", metrics.rawJawlineWidthPx.json())
                .put("eye_distance", metrics.rawEyeSpacingPx.json())
                .put("reference_face_width_cheek", metrics.rawCheekWidthPx.json()))
            .put("final", JSONArray().apply { features(metrics, thresholds).forEach { put(featureRecord(it)) } })
            .put("frames", JSONArray().apply {
                aggregate?.accepted?.forEachIndexed { i, f -> put(frameRecord(i + 1, f, false)) }
                aggregate?.outliers?.forEachIndexed { i, f -> put(frameRecord(i + 1, f, true)) }
            })
            .put("classifier_rejected_reasons", JSONArray(metrics.rejectedSampleReasons))
    }

    fun json(metrics: FaceAnalyzer.FaceMetrics, context: Context? = null, scanId: String = UUID.randomUUID().toString().take(8)): String {
        val thresholds = context?.let { FacialFeatureThresholds.load(it) }
        return JSONObject()
            .put("scan_id", scanId)
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("model_id", metrics.shapeModelId)
            .put("predicted_shape", metrics.faceShape)
            .put("decision_reason", metrics.shapeDecisionReason)
            .put("scores", JSONObject(metrics.shapeModelScores))
            .put("frame_agreement", metrics.shapeAgreement)
            .put("sample_count", metrics.shapeSampleCount)
            .put("image_width", metrics.imageWidth)
            .put("image_height", metrics.imageHeight)
            .put("head_pose_degrees", metrics.headPose?.let {
                JSONObject().put("pitch", it.pitch).put("yaw", it.yaw).put("roll", it.roll)
            })
            .put("lighting", metrics.scanLighting?.let {
                JSONObject().put("mean", it.mean).put("dark_fraction", it.darkFraction)
                    .put("bright_fraction", it.brightFraction).put("side_difference", it.sideDifference)
            })
            .put("facial_features", featureJson(metrics, thresholds))
            .put("classifier_input_shape", JSONArray(listOf(1, 256, 256, 3)))
            .put("classifier_input_color", "RGB")
            .put("classifier_input_range", JSONArray(listOf(0, 255)))
            .toString(2)
    }

    fun saveLatest(context: Context, metrics: FaceAnalyzer.FaceMetrics) {
        if (!BuildConfig.DEBUG || !context.applicationContext
            .getSharedPreferences("profile_scan_history", Context.MODE_PRIVATE)
            .getBoolean("historyEnabled", true)) return
        try {
            val scanId = "scan_${System.currentTimeMillis()}"
            context.filesDir.resolve("latest-scan-diagnostics.json").writeText(json(metrics, context, scanId))
            val thresholds = FacialFeatureThresholds.load(context)
            // Short lines for collecting repeated-scan data with `adb logcat -s FACEFIT_FEATURE_JSON`.
            fun emit(type: String, record: JSONObject) =
                Log.i(FEATURE_JSON_TAG, record.put("type", type).put("scan_id", scanId).toString())
            emit("scan", scanRecord(metrics))
            features(metrics, thresholds).forEach { emit("feature", featureRecord(it)) }
            metrics.featureAggregate?.let { aggregate ->
                aggregate.accepted.forEachIndexed { i, f -> emit("frame", frameRecord(i + 1, f, false)) }
                aggregate.outliers.forEachIndexed { i, f -> emit("frame", frameRecord(i + 1, f, true)) }
            }

            val debugSummary = buildString {
                appendLine("==================== FACEFIT SCAN DIAGNOSTICS ====================")
                appendLine("Scan ID          : $scanId")
                appendLine("Face Shape (CNN) : ${metrics.faceShape} (Protected Classifier)")
                appendLine("Classifier frames: ${metrics.shapeSampleCount} accepted | ${metrics.rejectedSampleCount} rejected")
                metrics.featureAggregate?.let { aggregate ->
                    appendLine("Feature frames   : ${aggregate.accepted.size} accepted | ${aggregate.outliers.size} outliers | rejected ${aggregate.rejections}")
                    aggregate.accepted.forEachIndexed { i, f ->
                        appendLine(String.format("  #%02d U=%.4f J=%.4f E=%.4f | yaw=%.1f pitch=%.1f roll=%.1f dist=%.1fcm",
                            i + 1, f.ratios.upperFace, f.ratios.jawline, f.ratios.eyeSpacing,
                            f.yawDeg, f.pitchDeg, f.rollDeg, f.cheekDistanceCm))
                    }
                }
                features(metrics, thresholds).forEach { f ->
                    val margin = f.standardError?.let { f.band?.uncertaintyMargin(it) }
                    val assessment = margin?.let { f.band?.assess(f.ratio, it, f.eyeSpacing) }
                    appendLine(String.format("  %-16s median=%.4f se=%.4f | low=%.4f high=%.4f | dist=%.4f margin=%.4f | %s%s",
                        f.key, f.ratio, f.standardError ?: Float.NaN,
                        f.band?.low ?: Float.NaN, f.band?.high ?: Float.NaN,
                        f.band?.distanceToBoundary(f.ratio) ?: Float.NaN, margin ?: Float.NaN,
                        f.band?.classify(f.ratio, f.eyeSpacing) ?: "Unavailable",
                        if (assessment?.uncertain == true) " (near cutoff)" else ""))
                }
                appendLine("==================================================================")
            }
            Log.i("FaceFitFeatureDebug", debugSummary)
            Log.i("FACEFIT_FEATURE_DEBUG", debugSummary)
        } catch (error: Exception) {
            // Diagnostics must never fail a scan.
            Log.w("FaceShapeModel", "Could not save local scan diagnostics", error)
        }
    }
}
