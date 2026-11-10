package com.example.facefit.facefit.recommendation

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.util.Locale

/** Assessor-based visual suitability using C = ((mean rating - 1) / 4) * 100. */
class FrameCompatibility private constructor(
    private val assessments: Map<Pair<String, String>, Assessment>,
    val loadError: String? = null
) {
    data class Entry(val faceShape: String, val frameCategory: String, val ratings: List<Double>)
    data class Assessment(val meanRating: Double, val ratingCount: Int, val score: Double)

    fun lookup(faceShape: String?, frameCategory: String): Assessment? {
        if (faceShape.isNullOrBlank()) return null
        return assessments[normalize(faceShape) to normalize(frameCategory)]
    }

    companion object {
        fun calculate(ratings: List<Double>): Assessment? {
            if (ratings.isEmpty()) return null
            require(ratings.all { it.isFinite() && it in 1.0..5.0 }) {
                "Every assessor rating must be between 1 and 5"
            }
            val mean = ratings.average()
            return Assessment(mean, ratings.size, (mean - 1.0) / 4.0 * 100.0)
        }

        private fun normalize(value: String) = value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

        fun fromEntries(entries: List<Entry>): FrameCompatibility {
            val results = mutableMapOf<Pair<String, String>, Assessment>()
            val seen = mutableSetOf<Pair<String, String>>()
            entries.forEach { entry ->
                val key = normalize(entry.faceShape) to normalize(entry.frameCategory)
                require(key.first.isNotEmpty() && key.second.isNotEmpty()) {
                    "Face shape and frame category are required"
                }
                require(seen.add(key)) { "Duplicate compatibility entry for $key" }
                calculate(entry.ratings)?.let { results[key] = it }
            }
            return FrameCompatibility(results)
        }

        fun load(context: Context): FrameCompatibility = try {
            val root = context.assets.open("frame_compatibility_ratings.json")
                .bufferedReader().use { JSONObject(it.readText()) }
            require(root.getString("version") == "1.0") { "Unsupported ratings format" }
            val entries = root.getJSONArray("entries")
            fromEntries(List(entries.length()) { index ->
                val entry = entries.getJSONObject(index)
                val ratings = entry.getJSONArray("ratings")
                Entry(entry.getString("faceShape"), entry.getString("frameCategory"),
                    List(ratings.length()) { ratings.getDouble(it) })
            })
        } catch (error: Exception) {
            Log.w("FrameCompatibility", "Compatibility ratings could not be loaded", error)
            FrameCompatibility(emptyMap(), "Compatibility ratings could not be loaded.")
        }
    }
}
