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
    SampleFrame("Leo", "Aviator", 86, Color(0xFFB18B42), "Metal", "Gold",
        "Aviator frames combine a curved outline and a light metal finish for a classic look."),
    SampleFrame("Browline Black", "Browline", 85, Color(0xFF303030), "Metal & Acetate", "Black",
        "A bold upper rim emphasizes the brow, while slim metal lower rims keep the frame visually light.",
        assetPath = "frames/browline/black_browline_front_depth.png",
        tryOnAssetPath = "frames/browline/black_browline_front_layer.png",
        lensAnchors = FrameLensAnchors(leftX = .25114f, leftY = .55f, rightX = .74724f, rightY = .55f),
        templeAssetPath = "frames/browline/black_browline_temple.png"),
    SampleFrame("Luna", "Round", 83, Color(0xFFB18B42), "Not specified", "Gold",
        "Round lenses bring soft curves that can complement more defined facial features.",
        assetPath = "frames/Round/model.glb", has3DModel = true,
        thumbnailAssetPath = "frames/Round/thumbnail.png")
)
