package com.example.facefit.ui.screens

import androidx.compose.ui.graphics.Color

data class FrameLensAnchors(
    val leftX: Float = 0.25f,
    val leftY: Float = 0.5f,
    val rightX: Float = 0.75f,
    val rightY: Float = 0.5f
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
    val has3DModel: Boolean = false
)

// Preview catalog until the real frame dataset and scoring are connected.
val sampleFrames = listOf(
    SampleFrame("Pax", "Rectangle", 96, Color(0xFF596B7C), "Metal", "Black",
        "Rectangular lenses create sharp, structured lines that contrast with softer features and add a polished, focused feel.",
        assetPath = "frames/pax.png", has3DModel = true),
    SampleFrame("Milo", "Square", 92, Color(0xFF367CE5), "Acetate", "Blue",
        "Square frames add structure and definition with a bold, balanced outline."),
    SampleFrame("Nova", "Cat-Eye", 89, Color(0xFFD65E8B), "Acetate", "Rosewood",
        "Lifted outer corners draw attention to the eyes and add an expressive accent."),
    SampleFrame("Leo", "Aviator", 86, Color(0xFFB18B42), "Metal", "Gold",
        "Aviator frames combine a curved outline and a light metal finish for a classic look."),
    SampleFrame("Browline Black", "Browline", 85, Color(0xFF303030), "Metal & Acetate", "Black",
        "A bold upper rim emphasizes the brow, while slim metal lower rims keep the frame visually light.",
        assetPath = "frames/black_browline_front_depth.png",
        tryOnAssetPath = "frames/black_browline_front_layer.png",
        lensAnchors = FrameLensAnchors(leftX = 0.255f, leftY = 0.55f, rightX = 0.745f, rightY = 0.55f),
        templeAssetPath = "frames/black_browline_temple.png", has3DModel = true),
    SampleFrame("Luna", "Round", 83, Color(0xFF6C987C), "Metal", "Green",
        "Round lenses bring soft curves that can complement more defined facial features.")
)
