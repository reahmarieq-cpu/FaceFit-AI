package com.example.facefit.logic

import android.content.Context
import android.util.Log
import com.example.facefit.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Numeric capture records for comparing actual camera inputs with desktop inference. */
internal object ScanDiagnostics {
    fun json(metrics: FaceAnalyzer.FaceMetrics): String = JSONObject()
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
        .put("feature_order", JSONArray(ScanStabilityFeatures.names))
        .put("median_features", JSONArray(metrics.stabilityFeatures))
        .put("frame_features", JSONArray(metrics.shapeFeatureSamples.map { JSONArray(it) }))
        .toString(2)

    fun saveLatest(context: Context, metrics: FaceAnalyzer.FaceMetrics) {
        if (!BuildConfig.DEBUG) return
        try {
            context.filesDir.resolve("latest-scan-diagnostics.json").writeText(json(metrics))
        } catch (error: IOException) {
            Log.w("FaceShapeModel", "Could not save local scan diagnostics", error)
        }
    }
}
