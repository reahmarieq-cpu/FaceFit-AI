package com.example.facefit.facefit.recommendation

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.util.Locale

/**
 * Short visual-compatibility explanation for a face shape and frame category
 * (FaceFit_AI_Final_Frame_Explanations.csv, in assets/frame_explanations.json).
 * Text only: it never affects compatibility scores, ranking or facial analysis.
 */
class FrameExplanations(texts: Map<String, Map<String, List<String>>>) {
    private val texts: Map<Pair<String, String>, List<String>> = buildMap {
        texts.forEach { (face, frames) ->
            frames.forEach { (category, variants) ->
                val usable = variants.map { it.trim() }.filter { it.isNotEmpty() }
                if (usable.isNotEmpty()) put(faceKey(face) to categoryKey(category), usable)
            }
        }
    }

    /** Main explanation first, then the alternatives. Empty when the pair isn't covered. */
    fun variants(faceShape: String?, frameCategory: String): List<String> {
        if (faceShape.isNullOrBlank()) return emptyList()
        return texts[faceKey(faceShape) to categoryKey(frameCategory)].orEmpty()
    }

    /**
     * One explanation for [frameId]. Frames sharing a category get different wording
     * (main, then alternatives), assigned by sorted frame id so a frame always keeps the
     * same text whatever the ranking order. [catalogFrames] = (id, category) of every frame.
     */
    fun forFrame(
        faceShape: String?,
        frameId: String,
        frameCategory: String,
        catalogFrames: List<Pair<String, String>>
    ): String? {
        val options = variants(faceShape, frameCategory)
        if (options.isEmpty()) return null
        val category = categoryKey(frameCategory)
        val peers = (catalogFrames.filter { categoryKey(it.second) == category }.map { it.first } + frameId)
            .distinct().sorted()
        return options[peers.indexOf(frameId) % options.size]
    }

    companion object {
        private fun letters(value: String) = value.lowercase(Locale.ROOT).filter { it.isLetter() }

        internal fun faceKey(shape: String) = letters(shape).let { if (it == "long") "oblong" else it }

        internal fun categoryKey(category: String) = letters(category).let { if (it == "rectangular") "rectangle" else it }

        fun load(context: Context): FrameExplanations = runCatching {
            val root = context.assets.open("frame_explanations.json")
                .bufferedReader().use { JSONObject(it.readText()) }
            val byFace = root.getJSONObject("explanations")
            FrameExplanations(byFace.keys().asSequence().associateWith { face ->
                val byFrame = byFace.getJSONObject(face)
                byFrame.keys().asSequence().associateWith { category ->
                    val list = byFrame.getJSONArray(category)
                    List(list.length()) { list.getString(it) }
                }
            })
        }.onFailure {
            Log.w("FrameExplanations", "Could not load the frame explanations", it)
        }.getOrElse { FrameExplanations(emptyMap()) }
    }
}
