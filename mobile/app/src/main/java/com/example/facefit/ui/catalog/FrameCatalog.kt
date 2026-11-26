package com.example.facefit.ui.catalog

import androidx.compose.ui.graphics.Color
import com.example.facefit.facefit.recommendation.FrameCompatibility

data class FrameLensAnchors(
    val leftX: Float = 0.25f,
    val leftY: Float = 0.5f,
    val rightX: Float = 0.75f,
    val rightY: Float = 0.5f
)

data class FrameArmAnchors(
    val hingeX: Float,val hingeY: Float,val tipX: Float,val tipY: Float,
    val widthX: Float,val widthY: Float,val frontX: Float,val frontY: Float
)

data class SampleFrame(
    val name: String,
    val category: String,
    val score: Int,
    val color: Color,
    val material: String,
    val colorName: String,
    val explanation: String,
    val assetPath: String? = null,
    val tryOnAssetPath: String? = assetPath,
    val lensAnchors: FrameLensAnchors = FrameLensAnchors(),
    val templeAssetPath: String? = null,
    val has3DModel: Boolean = false,
    val thumbnailAssetPath: String? = null,
    val leftArmAssetPath: String? = null,
    val rightArmAssetPath: String? = null,
    val leftArmAnchors: FrameArmAnchors? = null,
    val compatibilityAssessment: FrameCompatibility.Assessment? = null,
    val rightArmAnchors: FrameArmAnchors? = null,
    /**
     * 2D try-on only: size relative to placing the lens centres exactly on the eye centres.
     * Lens centres are usually farther apart than the eyes (wide lenses), so real size is above 1.
     */
    val tryOnScale: Float = 1f,
    val id: String = android.net.Uri.encode(name),
) {
    val compatibilityScore: Double? get() = compatibilityAssessment?.score
}

// Preview catalog until the real frame dataset and scoring are connected.
val sampleFrames = listOf(
    SampleFrame("Square Tortoise", "Square", 91, Color(0xFF8B5A2B), "Acetate", "Tortoise",
        "A classic square tortoise shell frame with 3D Virtual Try-On.",
        assetPath = "frames/square_tortoise/SquareTortoise.glb", has3DModel = true,
        thumbnailAssetPath = "frames/square_tortoise/tumbnail.png"),
    SampleFrame("Nova", "Cat-Eye", 89, Color(0xFFB18B42), "Not specified", "Gold",
        "Lifted outer corners draw attention to the eyes and add an expressive accent.",
        assetPath = "frames/cat-eye/model.glb", has3DModel = true,
        thumbnailAssetPath = "frames/cat-eye/thumbail.png"),
    SampleFrame("Luna", "Round", 83, Color(0xFFB18B42), "Not specified", "Gold",
        "Round lenses bring soft curves that can complement more defined facial features.",
        assetPath = "frames/Round/model.glb", has3DModel = true,
        thumbnailAssetPath = "frames/Round/thumbnail.png"),
    SampleFrame("Aviator Gold", "Aviator", 88, Color(0xFFC9A54A), "Metal", "Gold",
        "A teardrop lens shape with a double bridge adds a classic, confident look.",
        assetPath = "frames/aviator/model.glb", has3DModel = true,
        thumbnailAssetPath = "frames/aviator/thumbnail.png"),
    SampleFrame("Aviator Gold 2D", "Aviator", 88, Color(0xFFC9A54A), "Metal", "Gold / Dark lens",
        "Classic aviator sunglasses: teardrop dark lenses and a double bridge add a confident look.",
        assetPath = "frames/aviator_2d/aviator_gold_2d.png",
        lensAnchors = FrameLensAnchors(.22936f, .47534f, .76678f, .47534f),
        // Lens centres about 75 mm apart on a 140 mm aviator, against about 63 mm between the eyes.
        tryOnScale = 1.2f),
    SampleFrame("Wayfarer Black", "Wayfarer", 87, Color(0xFF1A1A1A), "Acetate", "Black",
        "A bold, slightly tapered outline adds definition and a timeless everyday style.",
        assetPath = "frames/wayfarer/model.glb", has3DModel = true,
        thumbnailAssetPath = "frames/wayfarer/thumbnail.png"),
    SampleFrame("Rectangle Rose Gold", "Rectangle", 86, Color(0xFFC98D73), "Metal", "Rose Gold",
        "Rectangular lenses create clean, structured lines that add a polished, focused feel.",
        assetPath = "frames/rectangle/rectangle_rose_gold_metal_frame_028.glb", has3DModel = true,
        thumbnailAssetPath = "frames/rectangle/thumbnail.png"),
    SampleFrame("Oval Brown", "Oval", 82, Color(0xFF4A2A18), "Acetate", "Brown",
        "Soft oval lenses follow natural facial curves for a gentle, balanced look.",
        assetPath = "frames/oval/model.glb", has3DModel = true,
        thumbnailAssetPath = "frames/oval/thumbnail.png"),
    SampleFrame("Oval Gold", "Oval", 81, Color(0xFFC9A54A), "Metal", "Gold",
        "Thin oval metal rims keep the frame light while softening angular features.",
        assetPath = "frames/oval/model1.glb", has3DModel = true,
        thumbnailAssetPath = "frames/oval/thumbnail1.png"),
    SampleFrame("Geometric Rose Gold", "Geometric", 80, Color(0xFFC98D73), "Metal", "Rose Gold",
        "Angled, many-sided lenses add a modern, distinctive accent.",
        assetPath = "frames/geometric/model.glb", has3DModel = true,
        thumbnailAssetPath = "frames/geometric/thumbnail.png"),
    SampleFrame("Browline Crystal", "Browline", 79, Color(0xFFE8DDD6), "Acetate & Metal", "Crystal",
        "A clear upper rim highlights the brow while thin metal lower rims keep the look light.",
        assetPath = "frames/browline/browline_crystal_frame_049.glb", has3DModel = true,
        thumbnailAssetPath = "frames/browline/thumbnail.png")
)
