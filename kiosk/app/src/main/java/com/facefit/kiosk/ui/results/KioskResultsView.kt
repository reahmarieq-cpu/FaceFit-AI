package com.facefit.kiosk.ui.results

import com.facefit.kiosk.facefit.features.FacialFeatureThresholds
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import com.facefit.kiosk.model.FaceScanResult
import com.facefit.kiosk.model.FrameRecommendation
import android.view.View
import com.facefit.kiosk.data.frames.FrameCatalog
import com.facefit.kiosk.databinding.ItemFrameRecommendationBinding
import android.graphics.Matrix
import android.graphics.RectF
import android.widget.ImageView
import androidx.core.view.doOnLayout
import kotlin.math.max
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.facefit.kiosk.R
import com.facefit.kiosk.model.FaceShape
import com.facefit.kiosk.data.session.KioskSessionStore
import com.facefit.kiosk.databinding.ScreenResultsBinding
import com.facefit.kiosk.navigation.KioskDestination
import com.facefit.kiosk.navigation.KioskNavigator
import java.util.Locale

class KioskResultsView(
    context: Context,
    private val sessionStore: KioskSessionStore,
    private val navigator: KioskNavigator,
    private val frameCatalog: FrameCatalog
) : FrameLayout(context) {
    private val binding = ScreenResultsBinding.inflate(LayoutInflater.from(context), this, true)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val catalogExecutor = Executors.newSingleThreadExecutor()
    private var attached = false
    private var loading = false
    private val profile = requireNotNull(sessionStore.analysisResult)
    private val shopId = sessionStore.activeShop?.id.orEmpty()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        loadFrameMatches()
    }

    override fun onDetachedFromWindow() {
        attached = false
        catalogExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    init {
        val metrics = profile.metrics
        bindFaceShape(profile.faceShape)
        binding.resultsShapeLabel.setText(if (profile.isEstimate) R.string.results_shape_label else R.string.profile_shape_label)
        binding.resultsShapeNote.text = profile.confidencePercent?.let {
            context.getString(R.string.profile_confidence, it)
        } ?: profile.modelScores[profile.faceShape.name]?.let {
            context.getString(R.string.scan_model_score, (it * 100).toInt())
        } ?: context.getString(R.string.results_shape_note)
        if (profile.notes.isNotEmpty()) binding.resultsProfileSummary.text = profile.notes.joinToString("\n")
        binding.resultsFrameRetry.setOnClickListener { loadFrameMatches() }
        val thresholds = FacialFeatureThresholds.load(context)
        binding.resultsAspectText.text = metrics?.foreheadToFaceWidthRatio.featureLabel(thresholds?.upperFace)
        binding.resultsJawText.text = metrics?.jawToFaceWidthRatio.featureLabel(thresholds?.jawline)
        binding.resultsEyesText.text = metrics?.eyeSpacingToFaceWidthRatio.featureLabel(thresholds?.eyeSpacing, eyeSpacing = true)
        binding.resultsRescanButton.setOnClickListener {
            sessionStore.clearCustomerSession()
            navigator.navigateTo(KioskDestination.FacePositioning)
        }
    }

    private fun loadFrameMatches() {
        if (loading || !attached) return
        loading = true
        binding.resultsFrameRetry.visibility = View.GONE
        binding.resultsEmptyMatches.visibility = View.GONE
        binding.resultsFrames.visibility = View.GONE
        binding.resultsInventoryNote.visibility = View.GONE
        binding.resultsInventoryLabel.setText(R.string.frame_inventory_label)
        binding.resultsInventorySubtitle.setText(R.string.frame_inventory_loading)
        catalogExecutor.execute {
            val result = runCatching {
                frameCatalog.recommendationsFor(profile, shopId).filter { it.frame.isAvailable }
            }
            mainHandler.post {
                if (!attached) return@post
                loading = false
                result.onSuccess { bindFrameMatches(it) }.onFailure {
                    binding.resultsInventorySubtitle.setText(R.string.frame_inventory_error)
                    binding.resultsFrameRetry.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun bindFrameMatches(matches: List<FrameRecommendation>) {
        binding.resultsEmptyMatches.visibility = if (matches.isEmpty()) View.VISIBLE else View.GONE
        binding.resultsFrames.visibility = if (matches.isEmpty()) View.GONE else View.VISIBLE
        binding.resultsFrames.removeAllViews()
        binding.resultsInventorySubtitle.text = if (matches.isEmpty()) {
            context.getString(R.string.frame_inventory_empty)
        } else resources.getQuantityString(R.plurals.frame_inventory_count, matches.size, matches.size)
        // Retain provider order; the screen does not manufacture rankings or scores.
        matches.forEach { match ->
            val row = ItemFrameRecommendationBinding.inflate(LayoutInflater.from(context), binding.resultsFrames, false)
            row.frameName.text = match.frame.name
            row.frameShape.text = match.frame.shape
            row.frameArtwork.frameShape = match.frame.shape
            row.frameArtwork.imageAssetPath = match.frame.imageAssetPath
            val score = match.compatibilityPercent
            row.frameScore.text = score?.let { context.getString(R.string.frame_score, it) }
                ?: context.getString(R.string.results_value_unavailable)
            row.frameScoreLabel.setText(if (score == null) R.string.frame_score_pending else R.string.frame_score_label)
            row.frameProgress.visibility = if (score == null) View.GONE else View.VISIBLE
            row.frameProgress.progress = score ?: 0
            row.frameTopMatch.visibility = View.GONE
            row.root.contentDescription = context.getString(R.string.frame_accessibility, match.frame.name, match.frame.shape)
            row.root.setOnClickListener {
                sessionStore.selectedRecommendation = match
                navigator.navigateTo(KioskDestination.FrameDetails)
            }
            binding.resultsFrames.addView(row.root)
        }
    }
    private fun bindFaceShape(shape: FaceShape) {
        val (label, illustration) = when (shape) {
            FaceShape.Oval -> R.string.results_shape_oval to R.drawable.face_shape_oval
            FaceShape.Round -> R.string.results_shape_round to R.drawable.face_shape_round
            FaceShape.Square -> R.string.results_shape_square to R.drawable.face_shape_square
            FaceShape.Heart -> R.string.results_shape_heart to R.drawable.face_shape_heart
            FaceShape.Oblong -> R.string.results_shape_oblong to R.drawable.face_shape_oblong
            FaceShape.Diamond -> R.string.results_shape_diamond to R.drawable.ic_results_face
            FaceShape.Undetermined -> R.string.results_shape_unavailable to R.drawable.ic_results_face
        }
        binding.resultsFaceShapeText.setText(label)
        binding.resultsFaceShapeImage.setImageResource(illustration)
        binding.resultsFaceShapeImage.contentDescription = context.getString(label)
        val portrait = binding.resultsFaceShapeImage
        if (shape == FaceShape.Undetermined || shape == FaceShape.Diamond) {
            portrait.scaleType = ImageView.ScaleType.FIT_CENTER
        } else {
            portrait.scaleType = ImageView.ScaleType.MATRIX
            portrait.doOnLayout { fitFacePortrait() }
        }
    }

    private fun fitFacePortrait() {
        val portrait = binding.resultsFaceShapeImage
        val drawable = portrait.drawable ?: return
        val width = drawable.intrinsicWidth.toFloat()
        val height = drawable.intrinsicHeight.toFloat()
        if (width <= 0f || height <= 0f || portrait.width <= 0 || portrait.height <= 0) return

        // The supplied PNGs include a card border and a caption below the face.
        // Frame only the portrait in the view; preserve the original asset and its proportions.
        val faceArea = RectF(width * 0.05f, height * 0.05f, width * 0.95f, height * 0.725f)
        val scale = max(portrait.width / faceArea.width(), portrait.height / faceArea.height())
        portrait.imageMatrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                portrait.width / 2f - faceArea.centerX() * scale,
                portrait.height / 2f - faceArea.centerY() * scale
            )
        }
    }

    private fun Double?.featureLabel(band: FacialFeatureThresholds.Band?, eyeSpacing: Boolean = false): String {
        val ratio = this?.toFloat()?.takeIf { it.isFinite() && it > 0f }
            ?: return context.getString(R.string.results_value_unavailable)
        return band?.classify(ratio, eyeSpacing)
            ?: String.format(Locale.getDefault(), "%.1f%%", ratio * 100)
    }
}