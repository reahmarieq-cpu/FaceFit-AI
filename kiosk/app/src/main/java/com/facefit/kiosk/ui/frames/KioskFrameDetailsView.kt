package com.facefit.kiosk.ui.frames

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.facefit.kiosk.R
import com.facefit.kiosk.data.session.KioskSessionStore
import com.facefit.kiosk.databinding.ScreenFrameDetailsBinding
import com.facefit.kiosk.model.FrameItem
import com.facefit.kiosk.navigation.KioskDestination
import com.facefit.kiosk.navigation.KioskNavigator
import java.text.NumberFormat

/** Displays the selected provider record; never manufactures compatibility scores. */
class KioskFrameDetailsView(
    context: Context,
    sessionStore: KioskSessionStore,
    navigator: KioskNavigator,
    backTo: KioskDestination = KioskDestination.Results
) : FrameLayout(context) {
    private val binding = ScreenFrameDetailsBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        val recommendation = requireNotNull(sessionStore.selectedRecommendation)
        val frame = recommendation.frame
        binding.detailsBack.setOnClickListener { navigator.navigateTo(backTo) }
        binding.detailsName.text = frame.name
        binding.detailsSubtitle.text = context.getString(R.string.details_subtitle, frame.shape, frame.color)
        binding.detailsArtwork.frameShape = frame.shape
        binding.detailsArtwork.imageAssetPath = frame.imageAssetPath
        binding.detailsArtwork.previewStoragePath = frame.previewStoragePath
        binding.detailsArtwork.contentDescription = context.getString(R.string.details_artwork, frame.name, frame.shape)
        bindProductInfo(frame)
        binding.detailsMaterial.text = frame.material
        binding.detailsColor.text = frame.color
        binding.detailsScore.visibility = if (recommendation.compatibilityPercent == null) View.GONE else View.VISIBLE
        binding.detailsExplanationHeading.setText(
            if (recommendation.styleExplanation.isNullOrBlank() && recommendation.explanation.none { it.isNotBlank() })
                R.string.details_about_this_frame else R.string.details_why_it_suits_you
        )
        binding.detailsScore.text = recommendation.compatibilityPercent?.toString()
            ?: context.getString(R.string.results_value_unavailable)
        binding.detailsStyleExplanation.text = recommendation.styleExplanation
        binding.detailsStyleSection.visibility =
            if (recommendation.styleExplanation.isNullOrBlank()) View.GONE else View.VISIBLE
        val explanations = recommendation.explanation.filter { it.isNotBlank() }.toMutableList()
        if (!recommendation.assessorBased && recommendation.compatibilityPercent != null) {
            explanations.add(context.getString(R.string.details_scored))
        }
        val explanationText = explanations.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
            ?: if (recommendation.styleExplanation.isNullOrBlank() && recommendation.compatibilityPercent == null) {
                context.getString(R.string.details_unscored)
            } else null
        binding.detailsExplanation.text = explanationText
        binding.detailsExplanation.visibility = if (explanationText == null) View.GONE else View.VISIBLE
        val ready = frame.tryOn?.canTryOn == true
        binding.detailsTryOn.isEnabled = ready
        binding.detailsTryOn.alpha = if (ready) 1f else 0.65f
        binding.detailsTryOnNotice.setText(
            if (ready) R.string.details_try_on_disclaimer else R.string.details_try_on_unavailable
        )
        binding.detailsTryOn.setOnClickListener { navigator.navigateTo(KioskDestination.VirtualTryOn) }
    }

    private fun bindProductInfo(frame: FrameItem) {
        val numberFormat = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }
        val measurements = listOf(
            "frameWidthMm" to R.string.details_frame_width,
            "lensWidthMm" to R.string.details_lens_width,
            "lensHeightMm" to R.string.details_lens_height,
            "bridgeWidthMm" to R.string.details_bridge_width,
            "templeLengthMm" to R.string.details_temple_length
        ).mapNotNull { (key, label) ->
            frame.measurementsMm[key]?.let {
                context.getString(R.string.details_measurement_mm, context.getString(label), numberFormat.format(it))
            }
        }
        val productInfo = listOfNotNull(frame.description).plus(measurements)
        binding.detailsProductInfo.text = productInfo.joinToString("\n")
        binding.detailsProductInfo.visibility = if (productInfo.isEmpty()) View.GONE else View.VISIBLE
    }
}
