package com.facefit.kiosk.vto.ui.catalog

import androidx.compose.ui.graphics.Color

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

data class VtoFrame(
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
    val rightArmAnchors: FrameArmAnchors? = null
)

// Preview catalog until the real frame dataset and scoring are connected.
val sampleFrames = listOf(
    VtoFrame("Pax", "Rectangle", 96, Color(0xFF596B7C), "Metal", "Black",
        "Rectangular lenses create sharp, structured lines that contrast with softer features and add a polished, focused feel.",
        assetPath = "frames/pax/pax.png",
        lensAnchors = FrameLensAnchors(.23918f,.4975f,.75939f,.4975f)),
    VtoFrame("Milo", "Square", 92, Color(0xFF367CE5), "Acetate", "Blue",
        "Square frames add structure and definition with a bold, balanced outline."),
    VtoFrame("Square Tortoise", "Square", 91, Color(0xFF8B5A2B), "Acetate", "Tortoise",
        "A classic square tortoise shell frame with 3D Virtual Try-On.",
        assetPath = "frames/square_tortoise/SquareTortoise.glb", has3DModel = true,
        thumbnailAssetPath = "frames/square_tortoise/tumbnail.png"),
    VtoFrame("Rectangle Rose Gold", "Rectangle", 90, Color(0xFFC98D73), "Not specified", "Rose Gold",
        "Original frame photo with separate arm layers for image-based 2.5D try-on. Side fit is approximate.",
        assetPath = "frames/rectangle/model.png",
        tryOnAssetPath = "frames/rectangle/front",
        lensAnchors = FrameLensAnchors(.24878f,.46969f,.74944f,.46969f),
        leftArmAssetPath = "frames/rectangle/arm_image_left",
        rightArmAssetPath = "frames/rectangle/arm_image_right",
        leftArmAnchors = FrameArmAnchors(.86f,.234f,.465f,.725f,.86f,.28f,.035f,.285f),
        rightArmAnchors = FrameArmAnchors(.13f,.28f,.475f,.69f,.13f,.325f,.965f,.285f)),
    VtoFrame("Nova", "Cat-Eye", 89, Color(0xFFB18B42), "Not specified", "Gold",
        "Lifted outer corners draw attention to the eyes and add an expressive accent.",
        assetPath = "frames/cat-eye/model.glb", has3DModel = true,
        thumbnailAssetPath = "frames/cat-eye/thumbail.png"),
    VtoFrame("Aviator", "Aviator", 86, Color(0xFFB18B42), "Metal", "Gold",
        "A classic aviator frame with a lightweight metal finish.",
        assetPath = "frames/aviator/model.glb", has3DModel = true,
        thumbnailAssetPath = "frames/aviator/thumbnail.png"),
    VtoFrame("Browline Black", "Browline", 85, Color(0xFF303030), "Metal & Acetate", "Black",
        "A bold upper rim emphasizes the brow, while slim metal lower rims keep the frame visually light.",
        assetPath = "frames/browline/model.glb", has3DModel = true,
        thumbnailAssetPath = "frames/browline/thumbnail.png"),
    VtoFrame("Luna", "Round", 83, Color(0xFFB18B42), "Not specified", "Gold",
        "Round lenses bring soft curves that can complement more defined facial features.",
        assetPath = "frames/Round/model.glb", has3DModel = true,
        thumbnailAssetPath = "frames/Round/thumbnail.png"),
    VtoFrame("Geometric", "Geometric", 82, Color(0xFF596B7C), "Not specified", "Not specified",
        "An angular geometric frame silhouette.",
        assetPath = "frames/geometric/model.glb", has3DModel = true,
        thumbnailAssetPath = "frames/geometric/thumbnail.png"),
    VtoFrame("Oval", "Oval", 81, Color(0xFF596B7C), "Not specified", "Not specified",
        "A gently curved oval frame silhouette.",
        assetPath = "frames/oval/model.glb", has3DModel = true,
        thumbnailAssetPath = "frames/oval/thumbnail.png"),
    VtoFrame("Oval Variant", "Oval", 80, Color(0xFF596B7C), "Not specified", "Not specified",
        "An alternative oval frame design.",
        assetPath = "frames/oval/model1.glb", has3DModel = true,
        thumbnailAssetPath = "frames/oval/thumbnail1.png"),
    VtoFrame("Wayfarer", "Wayfarer", 79, Color(0xFF596B7C), "Not specified", "Not specified",
        "A classic wayfarer frame silhouette.",
        assetPath = "frames/wayfarer/model.glb", has3DModel = true,
        thumbnailAssetPath = "frames/wayfarer/thumbnail.png")
)
