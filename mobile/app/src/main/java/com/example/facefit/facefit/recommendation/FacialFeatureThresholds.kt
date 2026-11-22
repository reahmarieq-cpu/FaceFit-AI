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

        val width: Float get() = high - low

        /** Distance from [ratio] to the nearest cutoff boundary. */
        fun distanceToBoundary(ratio: Float): Float {
            if (!ratio.isFinite() || ratio <= 0f) return Float.NaN
            return minOf(kotlin.math.abs(ratio - low), kotlin.math.abs(ratio - high))
        }

        /** Proximity to nearest boundary as a fraction of band width (e.g. 0.05 = 5%). */
        fun boundaryProximity(ratio: Float): Float {
            val dist = distanceToBoundary(ratio)
            return if (dist.isFinite() && width > 0f) dist / width else Float.POSITIVE_INFINITY
        }

        /** True if [ratio] falls within [marginFraction] of either boundary. */
        fun isBorderline(ratio: Float, marginFraction: Float = 0.06f): Boolean {
            return boundaryProximity(ratio) < marginFraction
        }

        /** Result of comparing a multi-frame median with the cutoffs, allowing for its uncertainty. */
        data class Assessment(
            val category: String,
            /** True when the nearest cutoff lies within [margin] of the ratio. */
            val uncertain: Boolean,
            /** Diagnostics only: the two categories either side of a nearby cutoff, e.g. "Medium–Wide". */
            val label: String,
            val nearestBoundary: Float,
            val distanceToBoundary: Float,
            val margin: Float,
        )

        /**
         * Margin for a median with standard error [standardError]: two standard errors (about 95 %
         * of the median's sampling spread) plus an allowance for between-scan variation
         * (FacialFeatureMeasurement.MODEL_MARGIN_BAND_FRACTION). Used only to flag near-cutoff results.
         */
        fun uncertaintyMargin(standardError: Float): Float =
            2f * standardError.coerceAtLeast(0f) +
                com.example.facefit.facefit.analysis.FacialFeatureMeasurement.MODEL_MARGIN_BAND_FRACTION * width

        fun assess(ratio: Float, margin: Float, eyeSpacing: Boolean = false): Assessment? {
            val category = classify(ratio, eyeSpacing) ?: return null
            val narrow = if (eyeSpacing) "Close-set" else "Narrow"
            val medium = if (eyeSpacing) "Average" else "Medium"
            val wide = if (eyeSpacing) "Wide-set" else "Wide"
            val nearLow = kotlin.math.abs(ratio - low) <= kotlin.math.abs(ratio - high)
            val boundary = if (nearLow) low else high
            val distance = kotlin.math.abs(ratio - boundary)
            val uncertain = margin.isFinite() && distance < margin
            val label = if (!uncertain) category else if (nearLow) "$narrow–$medium" else "$medium–$wide"
            return Assessment(category, uncertain, label, boundary, distance, margin)
        }

        /**
         * Classify [ratio] into Narrow/Medium/Wide (or Close-set/Average/Wide-set for eye spacing).
         *
         * Uses the population percentile boundaries from facial_feature_thresholds.json.
         * When [previous] is provided (stateful tracking), a 5% hysteresis deadband prevents
         * jitter oscillation around the boundary.
         */
        fun classify(ratio: Float, eyeSpacing: Boolean = false, previous: String? = null): String? {
            if (!ratio.isFinite() || ratio <= 0f) return null
            val narrow = if (eyeSpacing) "Close-set" else "Narrow"
            val medium = if (eyeSpacing) "Average" else "Medium"
            val wide = if (eyeSpacing) "Wide-set" else "Wide"

            // If a previous category is provided (stateful tracking), apply hysteresis margin:
            if (previous != null) {
                val margin = (high - low) * 0.05f
                if (previous == narrow && ratio <= low + margin) return narrow
                if (previous == wide && ratio >= high - margin) return wide
                if (previous == medium && ratio >= low - margin && ratio <= high + margin) return medium
            }

            return when {
                ratio < low -> narrow
                ratio > high -> wide
                else -> medium
            }
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
