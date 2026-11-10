package com.facefit.kiosk.ui.processing

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.os.SystemClock
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.facefit.kiosk.R
import com.facefit.kiosk.facefit.analysis.KioskAnalyzer
import com.facefit.kiosk.facefit.analysis.KioskAnalysisOutcome
import com.facefit.kiosk.data.session.KioskSessionStore
import com.facefit.kiosk.databinding.ScreenProcessingBinding
import com.facefit.kiosk.navigation.KioskDestination
import com.facefit.kiosk.navigation.KioskNavigator
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class KioskProcessingView(
    context: Context,
    private val sessionStore: KioskSessionStore,
    private val analyzer: KioskAnalyzer,
    private val navigator: KioskNavigator
) : FrameLayout(context) {

    private val binding = ScreenProcessingBinding.inflate(LayoutInflater.from(context), this, true)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var isViewAttached = false
    private var didStartAnalysis = false
    private var analysisStartedAt = 0L
    private var progressAnimator: ValueAnimator? = null

    private companion object {
        const val MINIMUM_PROCESSING_DURATION_MS = 5_000L
        const val LANDMARK_STAGE_DURATION_MS = 1_600L
    }

    init {
        showAnalyzing()
        binding.processingRetakeButton.setOnClickListener { retakeCapture() }
        binding.processingPrimaryButton.setOnClickListener { navigator.navigateTo(KioskDestination.Results) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isViewAttached = true
        if (!didStartAnalysis) {
            didStartAnalysis = true
            analysisStartedAt = SystemClock.uptimeMillis()
            startEstimatedProgress()
            analyzeCapturedImage()
        }
    }

    override fun onDetachedFromWindow() {
        isViewAttached = false
        progressAnimator?.cancel()
        progressAnimator = null
        mainHandler.removeCallbacksAndMessages(null)
        analysisExecutor.shutdownNow()
        super.onDetachedFromWindow()
    }

    private fun analyzeCapturedImage() {
        sessionStore.analysisResult = null
        sessionStore.facialFeatureMetrics = null
        sessionStore.faceLandmarkScan = null
        sessionStore.latestFaceAnalysisResult = null
        sessionStore.selectedRecommendation = null
        val imagePath = sessionStore.capturedFaceImagePath
        if (imagePath.isNullOrBlank()) {
            showFailure()
            return
        }
        analysisExecutor.execute {
            val outcome = try {
                analyzer.analyze(imagePath)
            } catch (_: Exception) {
                KioskAnalysisOutcome.Failure
            }
            if (outcome is KioskAnalysisOutcome.Success) {
                postAnalysisUi(LANDMARK_STAGE_DURATION_MS) { showExtractingFeatures() }
            }
            postAnalysisUi(MINIMUM_PROCESSING_DURATION_MS) { bindAnalysisResult(outcome) }
        }
    }
    // Keep fast local analysis visible without blocking the UI or the worker thread.
    private fun postAnalysisUi(minimumElapsedMs: Long, action: () -> Unit) {
        val remaining = (minimumElapsedMs - (SystemClock.uptimeMillis() - analysisStartedAt))
            .coerceAtLeast(0L)
        mainHandler.postDelayed({ if (isViewAttached) action() }, remaining)
    }

    private fun bindAnalysisResult(outcome: KioskAnalysisOutcome) {
        when (outcome) {
            is KioskAnalysisOutcome.Success -> {
                sessionStore.analysisResult = outcome.profile
                sessionStore.facialFeatureMetrics = outcome.profile.metrics
                sessionStore.faceLandmarkScan = outcome.landmarks
                progressAnimator?.cancel()
                updateProgress(100)
                mainHandler.postDelayed({
                    if (isViewAttached) navigator.navigateTo(KioskDestination.Results)
                }, 250L)
            }
            KioskAnalysisOutcome.NoFace -> showNoFace()
            KioskAnalysisOutcome.MultipleFaces -> showMultipleFaces()
            KioskAnalysisOutcome.Failure -> showFailure()
        }
    }
    // The gateway has no measured progress; this is a presentation estimate.
    // Only a successful analysis can advance to 100%.
    private fun startEstimatedProgress() {
        progressAnimator = ValueAnimator.ofInt(0, 95).apply {
            duration = MINIMUM_PROCESSING_DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { updateProgress(it.animatedValue as Int) }
            start()
        }
    }

    private fun updateProgress(percent: Int) {
        binding.processingProgress.progress = percent
        binding.processingLinearProgress.progress = percent
        binding.processingPercentText.text = context.getString(R.string.processing_percent, percent)
    }

    private fun showAnalyzing() {
        binding.processingProgress.visibility = View.VISIBLE
        binding.processingStages.visibility = View.VISIBLE
        binding.processingHintText.visibility = View.VISIBLE
        binding.processingActions.visibility = View.GONE
        binding.processingTitleText.text = context.getString(R.string.processing_analyzing_title)
        binding.processingBodyText.text = context.getString(R.string.processing_analyzing_body)
    }

    private fun showExtractingFeatures() {
        binding.processingStatusText.setText(R.string.processing_status_proportions)
    }

    private fun finishAnalyzing(eyebrow: Int) {
        progressAnimator?.cancel()
        progressAnimator = null
        binding.processingProgressContainer.visibility = View.GONE
        binding.processingProgress.visibility = View.GONE
        binding.processingStages.visibility = View.GONE
        binding.processingHintText.visibility = View.GONE
        binding.processingEyebrowText.setText(eyebrow)
    }

    private fun showNoFace() {
        showRetakeOnlyOutcome(
            title = context.getString(R.string.processing_no_face_title),
            body = context.getString(R.string.processing_no_face_body)
        )
    }

    private fun showMultipleFaces() {
        showRetakeOnlyOutcome(
            title = context.getString(R.string.processing_multiple_faces_title),
            body = context.getString(R.string.processing_multiple_faces_body)
        )
    }

    private fun showFailure() {
        showRetakeOnlyOutcome(
            title = context.getString(R.string.processing_failed_title),
            body = context.getString(R.string.processing_failed_body)
        )
    }

    private fun showRetakeOnlyOutcome(title: String, body: String) {
        finishAnalyzing(R.string.processing_retry_eyebrow)
        binding.processingProgress.visibility = View.GONE
        binding.processingActions.visibility = View.VISIBLE
        binding.processingRetakeButton.visibility = View.VISIBLE
        binding.processingPrimaryButton.visibility = View.GONE
        binding.processingTitleText.text = title
        binding.processingBodyText.text = body
    }

    private fun retakeCapture() {
        sessionStore.clearCustomerSession()
        navigator.navigateTo(KioskDestination.FacePositioning)
    }

}
