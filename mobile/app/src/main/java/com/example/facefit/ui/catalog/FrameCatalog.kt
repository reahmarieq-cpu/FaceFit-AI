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

// Catalog: 2D try-on frames, two per style: "<Style> Classic" (no lens effect) and "<Style> Shade" (smoke gradient tinted lenses). Lens centres are measured
// from each image; tryOnScale sizes the frame as a 140 mm frame on eyes about 63 mm apart.
val sampleFrames = listOf(
    SampleFrame("Square Shade", "Square", 90, Color(0xFF1A1A1A), "Acetate", "Black",
        "Square frames add structure and definition with a bold, balanced outline.",
        assetPath = "frames/square/square_classic_tinted_lens.png",
        lensAnchors = FrameLensAnchors(0.27218f, 0.50694f, 0.72306f, 0.50694f), tryOnScale = 1.16f),
    SampleFrame("Square Classic", "Square", 90, Color(0xFF1A1A1A), "Acetate", "Black",
        "Square frames add structure and definition with a bold, balanced outline.",
        assetPath = "frames/square/square_classic_no_lens.png",
        lensAnchors = FrameLensAnchors(0.27218f, 0.50694f, 0.72306f, 0.50694f), tryOnScale = 1.16f),
    SampleFrame("Rectangle Shade", "Rectangle", 89, Color(0xFF1A1A1A), "Acetate", "Black",
        "Rectangular lenses create clean, structured lines that add a polished, focused feel.",
        assetPath = "frames/rectangle/rectangle_classic_tinted_lens.png",
        lensAnchors = FrameLensAnchors(0.26768f, 0.50625f, 0.73128f, 0.50625f), tryOnScale = 1.12f),
    SampleFrame("Rectangle Classic", "Rectangle", 89, Color(0xFF1A1A1A), "Acetate", "Black",
        "Rectangular lenses create clean, structured lines that add a polished, focused feel.",
        assetPath = "frames/rectangle/rectangle_classic_no_lens.png",
        lensAnchors = FrameLensAnchors(0.26768f, 0.50625f, 0.73128f, 0.50625f), tryOnScale = 1.12f),
    SampleFrame("Oval Shade", "Oval", 88, Color(0xFF7A4A21), "Acetate", "Tortoise",
        "Soft oval lenses follow natural facial curves for a gentle, balanced look.",
        assetPath = "frames/oval/oval_classic_tinted_lens.png",
        lensAnchors = FrameLensAnchors(0.28075f, 0.50426f, 0.71726f, 0.50426f), tryOnScale = 1.1f),
    SampleFrame("Oval Classic", "Oval", 88, Color(0xFF7A4A21), "Acetate", "Tortoise",
        "Soft oval lenses follow natural facial curves for a gentle, balanced look.",
        assetPath = "frames/oval/oval_classic_no_lens.png",
        lensAnchors = FrameLensAnchors(0.28075f, 0.50426f, 0.71726f, 0.50426f), tryOnScale = 1.1f),
    SampleFrame("Round Shade", "Round", 87, Color(0xFF1A1A1A), "Acetate", "Black",
        "Round lenses bring soft curves that can complement more defined facial features.",
        assetPath = "frames/round/round_classic_tinted_lens.png",
        lensAnchors = FrameLensAnchors(0.27556f, 0.51014f, 0.71880f, 0.51014f), tryOnScale = 1.1f),
    SampleFrame("Round Classic", "Round", 87, Color(0xFF1A1A1A), "Acetate", "Black",
        "Round lenses bring soft curves that can complement more defined facial features.",
        assetPath = "frames/round/round_classic_no_lens.png",
        lensAnchors = FrameLensAnchors(0.27556f, 0.51014f, 0.71880f, 0.51014f), tryOnScale = 1.1f),
    SampleFrame("Aviator Shade", "Aviator", 86, Color(0xFFC9A54A), "Metal", "Gold",
        "A teardrop lens shape with a double bridge adds a classic, confident look.",
        assetPath = "frames/aviator/aviator_classic_tinted_lens.png",
        lensAnchors = FrameLensAnchors(0.25232f, 0.50251f, 0.74261f, 0.50251f), tryOnScale = 1.2f),
    SampleFrame("Aviator Classic", "Aviator", 86, Color(0xFFC9A54A), "Metal", "Gold",
        "A teardrop lens shape with a double bridge adds a classic, confident look.",
        assetPath = "frames/aviator/aviator_classic_no_lens.png",
        lensAnchors = FrameLensAnchors(0.25232f, 0.50251f, 0.74261f, 0.50251f), tryOnScale = 1.2f),
    SampleFrame("Wayfarer Shade", "Wayfarer", 85, Color(0xFF1A1A1A), "Acetate", "Black",
        "A bold, slightly tapered outline adds definition and a timeless everyday style.",
        assetPath = "frames/wayfarer/wayfarer_classic_tinted_lens.png",
        lensAnchors = FrameLensAnchors(0.27884f, 0.50456f, 0.71951f, 0.50456f), tryOnScale = 1.11f),
    SampleFrame("Wayfarer Classic", "Wayfarer", 85, Color(0xFF1A1A1A), "Acetate", "Black",
        "A bold, slightly tapered outline adds definition and a timeless everyday style.",
        assetPath = "frames/wayfarer/wayfarer_classic_no_lens.png",
        lensAnchors = FrameLensAnchors(0.27884f, 0.50456f, 0.71951f, 0.50456f), tryOnScale = 1.11f),
    SampleFrame("Cat-Eye Shade", "Cat-Eye", 84, Color(0xFF1A1A1A), "Acetate", "Black",
        "Lifted outer corners draw attention to the eyes and add an expressive accent.",
        assetPath = "frames/cat-eye/cat_eye_classic_tinted_lens.png",
        lensAnchors = FrameLensAnchors(0.27278f, 0.51843f, 0.72629f, 0.51843f), tryOnScale = 1.1f),
    SampleFrame("Cat-Eye Classic", "Cat-Eye", 84, Color(0xFF1A1A1A), "Acetate", "Black",
        "Lifted outer corners draw attention to the eyes and add an expressive accent.",
        assetPath = "frames/cat-eye/cat_eye_classic_no_lens.png",
        lensAnchors = FrameLensAnchors(0.27278f, 0.51843f, 0.72629f, 0.51843f), tryOnScale = 1.1f),
    SampleFrame("Browline Shade", "Browline", 83, Color(0xFF1A1A1A), "Acetate & Metal", "Black / Gold",
        "A bold upper rim highlights the brow while thin metal lower rims keep the look light.",
        assetPath = "frames/browline/brownline_classic_tinted_lens.png",
        lensAnchors = FrameLensAnchors(0.27796f, 0.52227f, 0.72156f, 0.52227f), tryOnScale = 1.13f),
    SampleFrame("Browline Classic", "Browline", 83, Color(0xFF1A1A1A), "Acetate & Metal", "Black / Gold",
        "A bold upper rim highlights the brow while thin metal lower rims keep the look light.",
        assetPath = "frames/browline/brownline_classic_no_lens.png",
        lensAnchors = FrameLensAnchors(0.27796f, 0.52227f, 0.72156f, 0.52227f), tryOnScale = 1.13f),
    SampleFrame("Geometric Shade", "Geometric", 82, Color(0xFFA8A8A8), "Metal", "Silver",
        "Angled, many-sided lenses add a modern, distinctive accent.",
        assetPath = "frames/geometric/geometric_classic_tinted_lens.png",
        lensAnchors = FrameLensAnchors(0.25738f, 0.49935f, 0.73801f, 0.49935f), tryOnScale = 1.17f),
    SampleFrame("Geometric Classic", "Geometric", 82, Color(0xFFA8A8A8), "Metal", "Silver",
        "Angled, many-sided lenses add a modern, distinctive accent.",
        assetPath = "frames/geometric/geometric_classic_no_lens.png",
        lensAnchors = FrameLensAnchors(0.25738f, 0.49935f, 0.73801f, 0.49935f), tryOnScale = 1.17f),
    SampleFrame("Rimless Shade", "Rimless", 81, Color(0xFFC0C0C0), "Metal", "Silver",
        "Frameless lenses keep the look light and barely there.",
        assetPath = "frames/rimless/rimless_classic_tinted_lens.png",
        lensAnchors = FrameLensAnchors(0.24723f, 0.51498f, 0.74697f, 0.51498f), tryOnScale = 1.24f),
    SampleFrame("Rimless Classic", "Rimless", 81, Color(0xFFC0C0C0), "Metal", "Silver",
        "Frameless lenses keep the look light and barely there.",
        assetPath = "frames/rimless/rimless_classic_no_lens.png",
        lensAnchors = FrameLensAnchors(0.24723f, 0.51498f, 0.74697f, 0.51498f), tryOnScale = 1.24f)
)
