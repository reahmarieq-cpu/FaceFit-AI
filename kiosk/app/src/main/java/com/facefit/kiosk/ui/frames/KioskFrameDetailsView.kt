package com.facefit.kiosk.ui.frames

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.facefit.kiosk.R
import com.facefit.kiosk.data.session.KioskSessionStore
import com.facefit.kiosk.databinding.ScreenFrameDetailsBinding
import com.facefit.kiosk.navigation.KioskDestination
import com.facefit.kiosk.navigation.KioskNavigator

/** Displays the selected provider record; never manufactures compatibility scores. */
class KioskFrameDetailsView(
    context: Context,
    sessionStore: KioskSessionStore,
    navigator: KioskNavigator
) : FrameLayout(context) {
    private val binding = ScreenFrameDetailsBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        val recommendation = requireNotNull(sessionStore.selectedRecommendation)
        val frame = recommendation.frame
        binding.detailsBack.setOnClickListener { navigator.navigateTo(KioskDestination.Results) }
        binding.detailsName.text = frame.name
        binding.detailsSubtitle.text = context.getString(R.string.details_subtitle, frame.shape, frame.color)
        binding.detailsArtwork.frameShape = frame.shape
        binding.detailsArtwork.imageAssetPath = frame.imageAssetPath
        binding.detailsArtwork.contentDescription = context.getString(R.string.details_artwork, frame.name, frame.shape)
        binding.detailsMaterial.text = frame.material
        binding.detailsColor.text = frame.color
        binding.detailsScore.text = recommendation.compatibilityPercent?.toString()
            ?: context.getString(R.string.results_value_unavailable)
        binding.detailsExplanation.text = recommendation.explanation.filter { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }?.joinToString("\n")
            ?: context.getString(if (recommendation.compatibilityPercent == null)
                R.string.details_unscored else R.string.details_scored)
        bindScore(binding.detailsFaceValue, binding.detailsFaceProgress, recommendation.faceShapeScore)
        bindScore(binding.detailsFitValue, binding.detailsFitProgress, recommendation.proportionFitScore)
        bindScore(binding.detailsStyleValue, binding.detailsStyleProgress, recommendation.styleColorScore)
        val ready = frame.tryOn?.let { it.has3DModel || it.tryOnAssetPath != null } == true
        binding.detailsTryOn.isEnabled = ready
        binding.detailsTryOn.alpha = if (ready) 1f else 0.65f
        binding.detailsTryOnNotice.visibility = if (ready) View.GONE else View.VISIBLE
        binding.detailsTryOn.setOnClickListener { navigator.navigateTo(KioskDestination.VirtualTryOn) }
    }

    private fun bindScore(label: TextView, progress: ProgressBar, score: Int?) {
        label.text = score?.toString() ?: context.getString(R.string.results_value_unavailable)
        progress.visibility = if (score == null) View.GONE else View.VISIBLE
        progress.progress = score ?: 0
    }
}
