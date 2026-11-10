package com.example.facefit.facefit.recommendation

import android.content.Context
import android.util.Log
import org.json.JSONObject

internal class FacialFeatureThresholds private constructor(
    val upperFace: Band,
    val jawline: Band,
    val eyeSpacing: Band
) {
    data class Band(val low: Float, val high: Float) {
        init {
            require(low.isFinite() && high.isFinite() && low > 0f && high > low)
        }

        fun classify(ratio: Float, eyeSpacing: Boolean = false): String? = when {
            !ratio.isFinite() || ratio <= 0f -> null
            ratio < low -> if (eyeSpacing) "Close-set" else "Narrow"
            ratio > high -> if (eyeSpacing) "Wide-set" else "Wide"
            else -> if (eyeSpacing) "Average" else "Medium"
        }
    }

    companion object {
        fun load(context: Context): FacialFeatureThresholds? = runCatching {
            val raw = context.assets.open("facial_feature_thresholds.json")
                .bufferedReader().use { it.readText() }
            val config = JSONObject(raw)
            require(config.getInt("version") == 1 && config.getString("status") == "provisional")
            require(config.getInt("sampleSize") >= 100)
            fun JSONObject.usesPair(key: String, first: Int, second: Int): Boolean {
                val pair = getJSONArray(key)
                return pair.length() == 2 && pair.getInt(0) == first && pair.getInt(1) == second
            }
            require(config.usesPair("referenceLandmarks", 234, 454))
            val features = config.getJSONObject("features")
            fun band(key: String, first: Int, second: Int): Band {
                val item = features.getJSONObject(key)
                require(item.usesPair("landmarks", first, second))
                return Band(item.getDouble("low").toFloat(), item.getDouble("high").toFloat())
            }
            FacialFeatureThresholds(
                band("upperFaceWidth", 103, 332),
                band("jawlineWidth", 172, 397),
                band("eyeSpacing", 133, 362)
            )
        }.onFailure {
            Log.w("FacialFeatureThresholds", "Displaying ratios without categories", it)
        }.getOrNull()
    }
}
