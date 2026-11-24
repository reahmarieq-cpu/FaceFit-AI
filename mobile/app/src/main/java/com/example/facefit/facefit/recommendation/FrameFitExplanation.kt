package com.example.facefit.facefit.recommendation

import java.util.Locale

/**
 * Plain-language reason a frame style does or doesn't flatter a face shape.
 * Text only: it never changes the face-shape result or the compatibility score.
 */
object FrameFitExplanation {

    private val faces = mapOf(
        "round" to "Your round face has soft curves, with similar width and length.",
        "oval" to "Your oval face has balanced proportions, slightly longer than it is wide.",
        "square" to "Your square face has a strong, angular jawline.",
        "heart" to "Your heart-shaped face is wider at the forehead and narrower at the chin.",
        "oblong" to "Your oblong face is noticeably longer than it is wide."
    )

    private val frames = mapOf(
        "square" to "Square frames have straight, angular lines",
        "rectangle" to "Rectangular frames have a wide, angular outline",
        "round" to "Round frames have soft, circular lenses",
        "oval" to "Oval frames have gently curved lenses",
        "aviator" to "Aviator frames have a teardrop shape that is fuller at the bottom",
        "wayfarer" to "Wayfarer frames have a bold, slightly tapered top line",
        "cateye" to "Cat-eye frames have lifted outer corners",
        "browline" to "Browline frames have a strong upper rim"
    )

    private fun effect(face: String, frame: String): String? = when (face) {
        "round" -> when (frame) {
            "square", "rectangle", "wayfarer", "browline" ->
                "which add definition to your soft features and can make your face look a little longer."
            "round", "oval" -> "which repeat the curves of your face and can make it look rounder."
            "aviator", "cateye" -> "which draw the eye upward and add lift to a round face."
            else -> null
        }
        "oval" -> "which suit your balanced proportions. Most styles work, so choose by personal taste."
        "square" -> when (frame) {
            "round", "oval", "aviator", "cateye" -> "which soften your angular jawline."
            "square", "rectangle", "wayfarer", "browline" ->
                "which echo your strong jawline for a bold, structured look."
            else -> null
        }
        "heart" -> when (frame) {
            "aviator", "round", "oval" ->
                "which add width lower on the face and balance a wider forehead."
            "square", "rectangle" -> "which add structure that balances a narrower chin."
            "wayfarer", "browline", "cateye" ->
                "which add weight to the top of the face, where it is already widest."
            else -> null
        }
        "oblong" -> when (frame) {
            "square", "rectangle", "wayfarer", "browline", "aviator" ->
                "which add width and help a long face look more balanced."
            "round", "oval" -> "and deeper lenses help break up the length of the face."
            "cateye" -> "which add width at the sides of the face."
            else -> null
        }
        else -> null
    }

    private fun normalize(value: String) = value.lowercase(Locale.ROOT).filter { it.isLetter() }

    private fun faceKey(shape: String) = normalize(shape).let { if (it == "long") "oblong" else it }

    private fun frameKey(category: String) = normalize(category).let { if (it == "rectangular") "rectangle" else it }

    /** "Strong match" etc. for a 0–100 compatibility score. */
    fun matchLabel(score: Double): String = when {
        score >= 70 -> "Strong match"
        score >= 60 -> "Good match"
        score >= 50 -> "Fair match"
        else -> "Weaker match"
    }

    /** Null when the face shape or frame style is unknown. */
    fun explain(faceShape: String?, frameCategory: String, score: Double?): String? {
        if (faceShape.isNullOrBlank()) return null
        val face = faceKey(faceShape)
        val frame = frameKey(frameCategory)
        val faceText = faces[face] ?: return null
        val frameText = frames[frame] ?: return null
        val effectText = effect(face, frame) ?: return null
        val scoreText = if (score == null) {
            "There is no survey rating for this frame style yet, so no score is shown."
        } else {
            "${matchLabel(score)}: people rated this style ${kotlin.math.round(score).toInt()} out of 100 " +
                "on ${faceText.substringAfter("Your ").substringBefore(" face")} faces."
        }
        return "$faceText $frameText, $effectText $scoreText"
    }
}
