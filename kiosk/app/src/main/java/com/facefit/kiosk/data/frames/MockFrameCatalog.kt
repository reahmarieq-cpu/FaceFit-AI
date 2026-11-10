package com.facefit.kiosk.data.frames

import com.facefit.kiosk.model.FaceScanResult
import com.facefit.kiosk.model.FrameItem
import com.facefit.kiosk.model.FrameRecommendation

/** Temporary visual-preview inventory. Scores below are fixtures, not analyzer output. */
class MockFrameCatalog : FrameCatalog {
    override fun recommendationsFor(profile: FaceScanResult, shopId: String): List<FrameRecommendation> {
        if (shopId != "demo-shop") return emptyList()
        return com.facefit.kiosk.vto.ui.catalog.sampleFrames.mapIndexed { index, source ->
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
