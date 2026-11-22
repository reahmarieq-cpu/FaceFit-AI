package com.example.facefit.facefit.recommendation

import android.content.Context
import android.util.Log
import java.util.Locale
import org.json.JSONObject

internal class FacialExplanationRules private constructor(
    private val rules: JSONObject,
    private val suggestedStyles: JSONObject
) {
    data class Copy(val headline: String, val explanation: String, val suggestedStyle: String)

    fun find(shape: String?, jawline: String?, upperFace: String?, eyeSpacing: String?): Copy? {
        if (shape == null || jawline == null || upperFace == null || eyeSpacing == null) return null
        val key = listOf(shape, jawline, upperFace, eyeSpacing)
            .joinToString("_").lowercase(Locale.ROOT).replace('-', '_')
        val entry = rules.optJSONObject(key) ?: return null
        val headline = entry.optString("headline")
        val explanation = entry.optString("explanation")
        val style = suggestedStyles.optString(shape)
        if (headline.isBlank() || explanation.isBlank() || style.isBlank()) return null
        return Copy(headline, explanation, style)
    }

    companion object {
        fun load(context: Context): FacialExplanationRules? = runCatching {
            val root = context.assets.open("facial_explanation_rules.json")
                .bufferedReader().use { JSONObject(it.readText()) }
            FacialExplanationRules(root.getJSONObject("rules"), root.getJSONObject("suggestedStyles"))
        }.onFailure {
            Log.w("FacialExplanationRules", "Could not load the facial explanations", it)
        }.getOrNull()
    }
}
