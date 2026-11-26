package com.facefit.kiosk.data.frames

import com.facefit.kiosk.data.auth.MockShopAuthRepository
import com.facefit.kiosk.facefit.recommendation.FrameCompatibility
import com.facefit.kiosk.model.FaceScanResult
import com.facefit.kiosk.model.FrameItem
import com.facefit.kiosk.model.FrameRecommendation
import kotlin.math.roundToInt

/** Temporary visual-preview inventory. Scores below are fixtures, not analyzer output. */
class MockFrameCatalog(private val compatibility: (() -> FrameCompatibility)? = null) : FrameCatalog {
    override fun framesFor(shopId: String): List<FrameItem> =
        previewRecommendations(shopId).map { it.frame }

    override fun recommendationsFor(profile: FaceScanResult, shopId: String): List<FrameRecommendation> {
        val recommendations = previewRecommendations(shopId)
        if (recommendations.isEmpty()) return recommendations
        val assessorRatings = compatibility?.invoke() ?: return recommendations
        return recommendations.map { sample ->
            val assessorScore = assessorRatings.lookup(profile.faceShape.name, sample.frame.shape)?.score?.roundToInt()
            FrameRecommendation(
                frame = sample.frame,
                compatibilityPercent = assessorScore,
                explanation = sample.explanation,
                assessorBased = assessorScore != null
            )
        }.sortedByDescending { it.compatibilityPercent ?: -1 }
    }

    private fun previewRecommendations(shopId: String): List<FrameRecommendation> {
        if (shopId != MockShopAuthRepository.demoShop.id) return emptyList()
        return com.facefit.kiosk.vto.ui.catalog.sampleFrames.map { source ->
            FrameRecommendation(
                frame = FrameItem("local-${source.name.lowercase().replace(' ', '-')}",
                    source.name, source.category, source.colorName, source.material, true, source,
                    imageAssetPath = source.thumbnailAssetPath ?: source.assetPath?.takeUnless {
                        it.endsWith(".glb", ignoreCase = true)
                    }),
                compatibilityPercent = source.score,
                explanation = listOf(source.explanation),
                faceShapeScore = (source.score + 2).coerceAtMost(100),
                proportionFitScore = (source.score - 1).coerceAtLeast(0),
                styleColorScore = (source.score - 2).coerceAtLeast(0)
            )
        }
    }
}
