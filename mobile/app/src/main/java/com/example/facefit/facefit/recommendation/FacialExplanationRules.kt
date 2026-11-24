package com.example.facefit.facefit.recommendation

import android.content.Context
import android.util.Log
import java.util.Locale
import org.json.JSONObject

internal class FacialExplanationRules private constructor(
    private val rules: JSONObject,
    private val suggestedStyles: JSONObject
) {
    /** [noticed] = "What we noticed", [styles] = "Styles to explore" (FaceFitAI_ExplanationAnalysis.csv). */
    data class Copy(val headline: String, val explanation: String, val suggestedStyle: String,
        val noticed: String = explanation, val styles: String? = null)

    fun find(shape: String?, jawline: String?, upperFace: String?, eyeSpacing: String?): Copy? {
        if (shape == null || jawline == null || upperFace == null || eyeSpacing == null) return null
        val key = listOf(shape, jawline, upperFace, eyeSpacing)
            .joinToString("_").lowercase(Locale.ROOT).replace('-', '_')
        val entry = rules.optJSONObject(key) ?: return null
        val headline = entry.optString("headline")
        val noticed = entry.optString("noticed")
        val styles = entry.optString("styles").takeIf { it.isNotBlank() }
        val style = suggestedStyles.optString(shape)
        if (headline.isBlank() || noticed.isBlank() || style.isBlank()) return null
        // Full text (sharing, fallbacks) = "What we noticed" + "Styles to explore".
        val explanation = listOfNotNull(noticed, styles).joinToString("\n\n")
        return Copy(headline, explanation, style, noticed, styles)
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
