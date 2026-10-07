package com.facefit.kiosk.ui.processing

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.facefit.kiosk.R
import com.facefit.kiosk.data.session.KioskSessionStore
import com.facefit.kiosk.databinding.ScreenProcessingBinding
import com.facefit.kiosk.facefit.analysis.FaceAnalysisGateway
import com.facefit.kiosk.facefit.analysis.FaceAnalysisResult
import com.facefit.kiosk.navigation.KioskDestination
import com.facefit.kiosk.navigation.KioskNavigator
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class KioskProcessingView(
    context: Context,
    private val sessionStore: KioskSessionStore,
    private val faceAnalysisGateway: FaceAnalysisGateway,
    private val navigator: KioskNavigator
) : FrameLayout(context) {

    private val binding = ScreenProcessingBinding.inflate(LayoutInflater.from(context), this, true)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var isViewAttached = false
    private var didStartAnalysis = false

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
            analyzeCapturedImage()
        }
    }

    override fun onDetachedFromWindow() {
        isViewAttached = false
        analysisExecutor.shutdownNow()
        super.onDetachedFromWindow()
    }

    private fun analyzeCapturedImage() {
        val capturedImagePath = sessionStore.capturedFaceImagePath
        if (capturedImagePath.isNullOrBlank()) {
            showFailure()
            return
        }

        analysisExecutor.execute {
            val result = faceAnalysisGateway.analyze(capturedImagePath)
            mainHandler.post {
                if (!isViewAttached) return@post
                bindAnalysisResult(result)
            }
        }
    }

    private fun bindAnalysisResult(result: FaceAnalysisResult) {
        sessionStore.latestFaceAnalysisResult = result
        when (result) {
            is FaceAnalysisResult.SingleFace -> {
                sessionStore.faceLandmarkScan = result.scan
                showSuccess(result.scan.landmarks.size)
            }
            FaceAnalysisResult.NoFaceDetected -> {
                sessionStore.faceLandmarkScan = null
                showNoFace()
            }
            FaceAnalysisResult.MultipleFacesDetected -> {
                sessionStore.faceLandmarkScan = null
                showMultipleFaces()
            }
            FaceAnalysisResult.AnalysisFailure -> {
                sessionStore.faceLandmarkScan = null
                showFailure()
            }
        }
    }

    private fun showAnalyzing() {
        binding.processingProgress.visibility = View.VISIBLE
        binding.processingActions.visibility = View.GONE
        binding.processingTitleText.text = context.getString(R.string.processing_analyzing_title)
        binding.processingBodyText.text = context.getString(R.string.processing_analyzing_body)
    }

    private fun showSuccess(landmarkCount: Int) {
        binding.processingProgress.visibility = View.GONE
        binding.processingActions.visibility = View.VISIBLE
        binding.processingRetakeButton.visibility = View.VISIBLE
        binding.processingPrimaryButton.visibility = View.VISIBLE
        binding.processingPrimaryButton.text = context.getString(R.string.processing_continue_button)
        binding.processingTitleText.text = context.getString(R.string.processing_success_title)
        binding.processingBodyText.text = context.getString(
            R.string.processing_success_body,
            landmarkCount
        )
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
